package org.signal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnDisabled;
import org.apache.nifi.annotation.lifecycle.OnEnabled;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.controller.AbstractControllerService;
import org.apache.nifi.controller.ConfigurationContext;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.reporting.InitializationException;
import org.signal.model.SignalAttachment;
import org.signal.model.SignalGroup;
import org.signal.model.SignalIdentity;
import org.signal.model.SignalMessage;
import org.signal.model.SignalQuote;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

@Tags({ "Signal", "Messenger"})
@CapabilityDescription("Signal Messenger service")
public class SignalMessengerService extends AbstractControllerService implements SignalControllerService {

	public static final PropertyDescriptor PROP_DAEMON_URL = new PropertyDescriptor
			.Builder().name("DaemonUrl")
			.displayName("Daemon URL")
			.description("URL to the signal-cli daemon")
			.required(true)
			.addValidator(StandardValidators.URI_VALIDATOR)
			.build();

	private static final List<PropertyDescriptor> properties;
	
	private TypeToken<ArrayList<SignalIdentity>> gsonTypeListIdentities =  new TypeToken<ArrayList<SignalIdentity>>() {};
	private TypeToken<ArrayList<SignalGroup>> gsonTypeListGroups =  new TypeToken<ArrayList<SignalGroup>>() {};
	
	private final static Gson GSON = new GsonBuilder().create();
	
	private final static Object LOCK_LISTENERS = new Object();

	private Collection<Consumer<SignalMessage>> messageListeners = new LinkedHashSet<>(10);
//	private Map<String, Long> messageListenersLastMessage = new HashMap<>(10);

	static {
		final List<PropertyDescriptor> props = new ArrayList<>();
		props.add(PROP_DAEMON_URL);
		properties = Collections.unmodifiableList(props);
	}

	private Thread receiveMessagesThread;

	private volatile boolean started = false;

//	private EvictingQueue<SignalMessage> messageQueue;

	private URL urlRpc;

	private URL urlEvents;

    private CacheLoader<String, Map<String, SignalIdentity>> loaderIdentities = new CacheLoader<>() {
        @Override
        public Map<String, SignalIdentity> load(String account) throws UnsupportedOperationException, IOException {
        	JsonElement responce = sendJsonRpc("listIdentities", getAccountParam(account));
    		List<SignalIdentity> result = GSON.fromJson(responce, gsonTypeListIdentities);

    		if(result == null)
    			return Collections.emptyMap();
    		
    		Map<String, SignalIdentity> identities = 
    				result.stream()
    					.filter(e -> e.getNumber() != null)
    					.collect(Collectors.toMap(
	    				SignalIdentity::getNumber, 
	    				Function.identity(), 
	    				(a,b) -> a) // This will ignore any duplicates. Will listIdentities ever return the same number twice?!
	    				);
    		
    		return identities;
        }
    };

    private CacheLoader<String, Map<String, SignalGroup>> loaderGroups = new CacheLoader<>() {
        @Override
        public Map<String, SignalGroup> load(String account) throws UnsupportedOperationException, IOException {
        	JsonElement responce = sendJsonRpc("listGroups", getAccountParam(account));
    		List<SignalGroup> result = GSON.fromJson(responce, gsonTypeListGroups);

    		if(result == null)
    			return Collections.emptyMap();
    		
    		Map<String, SignalGroup> groups = 
    				result.stream().collect(Collectors.toMap(
	    				SignalGroup::getId, 
	    				Function.identity(), 
	    				(a,b) -> a) // This will ignore any duplicates
	    				);
    		
    		return groups;
        }
    };

    private LoadingCache<String, Map<String, SignalIdentity>> cacheIdentities; 
    private LoadingCache<String, Map<String, SignalGroup>> cacheGroups;

	private final AtomicBoolean listeningEvents = new AtomicBoolean(false); 

	@Override
	protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
		return properties;
	}

	/**
	 * @param context
	 *            the configuration context
	 * @throws InitializationException
	 *             if unable to create a database connection
	 */
	@OnEnabled
	public void onEnabled(final ConfigurationContext context) throws InitializationException {
		logDebugMessage("Enabling SignalMessengerService");

		String url = context.getProperty(PROP_DAEMON_URL).getValue();
		try {
			urlRpc = new URL(url + "/api/v1/rpc");
			urlEvents = new URL(url + "/api/v1/events");
		} catch (MalformedURLException e1) {
			throw new InitializationException(e1);
		}
		
    	cacheIdentities = CacheBuilder.newBuilder()
    			.expireAfterAccess(6, TimeUnit.HOURS)
    			.build(loaderIdentities);
		
    	cacheGroups = CacheBuilder.newBuilder()
    			.expireAfterAccess(6, TimeUnit.HOURS)
    			.build(loaderGroups);

    	String version = getSignalVersion();
    	if(version == null || version.isBlank())
    		throw new InitializationException("Could not obtain version from signal-cli daemon. Please check the configured URL");
    	
		this.started = true;
		
		if(getLogger().isDebugEnabled()) getLogger().debug("Starting receive message thread");
//		messageQueue = EvictingQueue.create(1_000);
		receiveMessagesThread = new Thread(() -> {
			try {
				while(!Thread.currentThread().isInterrupted()) {
					//If the service is not enabled, return
					if(!isStarted())
						return;

					if(getLogger().isDebugEnabled()) getLogger().debug("Listening for messages: " + url);
					
					connectAndRecieveMessaged(url);
				}
			} catch (AssertionError e) {
				if(e.getCause() instanceof InterruptedException) {
					Thread.currentThread().interrupt();
					return;
				}
				onError(e);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			} catch (Throwable e) {
				onError(e);
			} finally {
				if(getLogger().isDebugEnabled()) getLogger().debug("Stopped listening for messages: " + url);
			}
		}, "SignalMsgRec");

		receiveMessagesThread.setDaemon(true);
		receiveMessagesThread.start();
		
		getLogger().info("Connected to signal-cli version: " + version);
	}
	
	public String getSignalVersion() {
		try {
			JsonElement result = sendJsonRpc("version", Collections.emptyMap());
			
			return result.getAsJsonObject()
						.get("version").getAsString();
		} catch (Exception e) {
			getLogger().error(e.getMessage(), e);
			return null;
		}
	}

	private void connectAndRecieveMessaged(String url2) throws InterruptedException, JsonSyntaxException, IOException {
		URLConnection connection = urlEvents.openConnection();
		if(connection instanceof HttpURLConnection) {
			HttpURLConnection httpConnection = (HttpURLConnection) connection;
			httpConnection.setRequestMethod("GET");
			httpConnection.setRequestProperty("Content-Type", "application/json");

			try(
					InputStream inputStream = httpConnection.getInputStream();
					InputStreamReader reader = new InputStreamReader(inputStream);
					BufferedReader bufferedReader = new BufferedReader(reader);
					){

				ComponentLog log = getLogger();
				
				listeningEvents.set(true);
				String line = null;
				boolean nextLineIsData = false;
				while((line = bufferedReader.readLine()) != null) {

					if(Thread.currentThread().isInterrupted() || !isStarted()) {
						Thread.currentThread().interrupt();
						throw new InterruptedException();
					}

					//Connection keep alive
					if(line.equalsIgnoreCase(":")) {
						if(log.isDebugEnabled()) log.debug("Connection keep-alive received");
						continue;
					}

					if(line.equalsIgnoreCase("event:receive")) {
						if(log.isDebugEnabled()) log.debug("Got an receive event");
						nextLineIsData = true;
						continue;
					}

					if(nextLineIsData && line.startsWith("data:")) {
						if(log.isDebugEnabled()) log.debug("Processing data message");
						String jsonData = line.substring(5);

						JsonElement element = JsonParser.parseString(jsonData);
						SignalMessage msg = processData(element);
						if(msg != null) {
							if(log.isDebugEnabled()) log.debug("Notifying listeners");
							synchronized (LOCK_LISTENERS) {
//								messageQueue.add(msg);
								notifyListeners(msg);
							}
						}
						continue;
					}
				}
			} finally {
				listeningEvents.set(false);
			}
		} else {
			throw new UnsupportedOperationException();
		}
	}

	private SignalMessage processData(JsonElement element) {
		if(!element.isJsonObject()){
			return null;
		}
		
		JsonObject jsonObject = element.getAsJsonObject();
		if(!jsonObject.has("account") || !jsonObject.has("envelope")) {
			UnsupportedOperationException exc = new UnsupportedOperationException("Unsupporterd signal message");
			getLogger().error(exc.getMessage(), exc);
			return null;
		}
		
		String account = jsonObject.get("account").getAsString();

		JsonObject jsonEnvelope = jsonObject.get("envelope").getAsJsonObject();
		if(!jsonEnvelope.has("timestamp")) {
			UnsupportedOperationException exc = new UnsupportedOperationException("Unsupporterd signal message");
			getLogger().error(exc.getMessage(), exc);
			return null;
		}

		long timestamp = jsonEnvelope.get("timestamp").getAsLong();
		Optional<String> sourceName = getFieldString(jsonEnvelope, "sourceName");
		Optional<String> source = getFieldString(jsonEnvelope, "sourceNumber");

		if(jsonEnvelope.has("dataMessage")) {
			logDebugMessage("Processing received data message");

			JsonObject dataMessage = jsonEnvelope.get("dataMessage").getAsJsonObject();
			String message = dataMessage.get("message").getAsString();

			SignalMessage msg = new SignalMessage();
			msg.setMessage(message);
			msg.setSource(source.orElse("Unknown"));
			msg.setSourceName(sourceName.orElse("Unknown"));
			msg.setTimestamp(timestamp);
			msg.setAccount(account);
			msg.setViewOnce(dataMessage.get("viewOnce").getAsBoolean());
			msg.setExpires(dataMessage.get("expiresInSeconds").getAsLong());

			if(dataMessage.has("groupInfo")) {
				JsonObject jsonGroupInfo = dataMessage.get("groupInfo").getAsJsonObject();
				String groupId = jsonGroupInfo.has("groupId") ? jsonGroupInfo.get("groupId").getAsString() : null;
				msg.setGroupId(groupId);
				
				//Try to load the group name
				try {
					Map<String, SignalGroup> groups = getGroups(account);
					SignalGroup signalGroup = groups.get(groupId);
					if(signalGroup != null)
						msg.setGroupName(signalGroup.getName());
				} catch (Exception e) {
					ComponentLog log = getLogger();
					if(log.isErrorEnabled()) log.error(e.getMessage(), e);
				}
			}

			return msg;
		} else if(jsonEnvelope.has("receiptMessage")) {
			logDebugMessage("Processing receipt message, not implemented yet");
			//TODO implement
		} else if(jsonEnvelope.has("typingMessage")) {
			logDebugMessage("Processing typing message, not implemented yet");
			//TODO: implement
		}
		
		return null;
	}

	private static final Optional<String> getFieldString(JsonObject jsonObject, String field) {
		if(!jsonObject.has(field))
			return Optional.empty();
		
		return Optional.of(jsonObject.get(field).getAsString());
	}

	public void onError(Throwable e) {
		getLogger().error(e.getMessage(), e);
	}


	@OnDisabled
	public void onDisable() {
		logDebugMessage("Disabling SignalMessengerService");
		started = false;
		if(receiveMessagesThread != null) {
			try {
				receiveMessagesThread.interrupt();
			} catch (Exception e) {
				getLogger().error(e.getMessage(), e);
			}
		}

		synchronized (LOCK_LISTENERS) {
			messageListeners.clear();
//			messageListenersLastMessage.clear();
		}
		
    	if(cacheIdentities != null) {
    		try {
				cacheIdentities.invalidateAll();
			} catch (Throwable e) { }
    		cacheIdentities = null;
    	}

    	if(cacheGroups != null) {
    		try {
    			cacheGroups.invalidateAll();
			} catch (Throwable e) { }
    		cacheGroups = null;
    	}
	}
	
	public boolean isStarted() {
		return started;
	}
	
	public boolean isListeningEvents() {
		return listeningEvents.get();
	}
	
	@Override
	public void sendMessage(String account, 
							String message, 
							Optional<List<String>> recipients,
							Optional<List<String>> groups,
							Optional<SignalQuote> quote,
							Optional<SignalAttachment> attachment) throws IOException, UnsupportedOperationException, ExecutionException {

		logDebugMessage("Sending signal message");

		if(groups.isPresent() && groups.get().size() > 0) {
			Map<String, SignalGroup> groupIds = getGroups(account);
			if(groupIds == null || groupIds.isEmpty()) {
				logError(new IllegalStateException("No groups found for account: " + account));
				return;
			}
			
			Collection<SignalGroup> groupInformation = groupIds.values();
			Collection<String> usedGroups = new HashSet<>();
			
			for (String groupNameOrId : groups.get()) {
				Optional<SignalGroup> group = Optional.ofNullable(groupIds.get(groupNameOrId)); 
						
				if(group.isEmpty())
					group = groupInformation
							.stream()
							.filter(gi -> gi.getName().equalsIgnoreCase(groupNameOrId))
							.findAny();
				
				if(group.isEmpty()) {
					logWarn("Could not find group id for group with name: \"" + groupNameOrId +"\"");
					continue;
				}
				
				String groupUuid = group.get().getId();
				
				if(usedGroups.contains(groupUuid))
					continue;
				
				JsonObject jsonParams = new JsonObject();
				jsonParams.addProperty("group-id", groupUuid);

				if(quote.isPresent()) {
					SignalQuote q = quote.get();
					jsonParams.addProperty("quote-timestamp", q.getTimestamp());
					jsonParams.addProperty("quote-autor", q.getAuthor());
					jsonParams.addProperty("quote-message", q.getMessage());
				}

				sendMessage(account, message, jsonParams, attachment);
				usedGroups.add(groupUuid);
			}	
		}
		// Send to multi recipients
		else {
			JsonArray array = new JsonArray(recipients.get().size());
			recipients.get().stream().distinct().forEach(array::add);
			
			JsonObject jsonParams = new JsonObject();
			jsonParams.add("recipient", array);
			
			if(quote.isPresent()) {
				SignalQuote q = quote.get();
				jsonParams.addProperty("quote-timestamp", q.getTimestamp());
				jsonParams.addProperty("quote-author", q.getAuthor());
				jsonParams.addProperty("quote-message", q.getMessage());
			}
			
			sendMessage(account, message, jsonParams, attachment);
		}
	}

	private void logWarn(String message) {
		ComponentLog log = getLogger();
		
		if(!log.isWarnEnabled())
			return;
		
		log.warn(message);
	}
	
	private void logError(Throwable e) {
		ComponentLog log = getLogger();
		
		if(!log.isErrorEnabled())
			return;
		
		log.error(e.getMessage(), e);
	}
	
	private void logDebugMessage(String message) {
		ComponentLog log = getLogger();
		
		if(!log.isDebugEnabled())
			return;
		
		log.debug(message);
	}
	
	public void sendMessage(String account, String message, JsonObject jsonParams, Optional<SignalAttachment> attachment) throws UnsupportedOperationException, IOException {
		jsonParams.addProperty("message", message);
		jsonParams.addProperty("account", account);

		if(attachment.isPresent()) {
			jsonParams.addProperty("attachment", attachment.get().toString());
		}
		
		sendJsonRpc("send", jsonParams);
	}

	@Override
	public void addMessageListener(Consumer<SignalMessage> listener) {
		synchronized (LOCK_LISTENERS) {
			messageListeners.add(Objects.requireNonNull(listener));

//			// For new listener, send all cached messages
//			Long lastMessageTimestamp = messageListenersLastMessage.get(listener.getClass().getCanonicalName());
//			var stream = messageQueue.stream();
//
//			if(lastMessageTimestamp != null)
//				stream = stream.filter(msg -> msg.getTimestamp() > lastMessageTimestamp);
//
//			try {
//				stream.forEach(msg -> {
//					listener.accept(msg);
//					messageListenersLastMessage.put(listener.getClass().getCanonicalName(), msg.getTimestamp());
//				});
//			} catch (Throwable e) {
//				logError(e);
//			}
		}
		
		logDebugMessage("Added message listener");
	}

	@Override
	public void removeMessageListener(Consumer<SignalMessage> messageListener) {
		synchronized (LOCK_LISTENERS) {
			messageListeners.remove(Objects.requireNonNull(messageListener));
		}
		logDebugMessage("Removed message listener");
	}
	
	private void notifyListeners(SignalMessage message) {
		for (Consumer<SignalMessage> consumer : messageListeners) {
			try {
				consumer.accept(message);
			} catch (Throwable e) {
				logError(new IllegalStateException("Listener " + consumer.toString() + " failed to process message", e));
			}
		}
	}

	@Override
	public Map<String, SignalIdentity> getIdentities(String account) throws UnsupportedOperationException, IOException, ExecutionException {
		if(cacheIdentities == null)
			return Collections.emptyMap();
		
		Map<String, SignalIdentity> result = cacheIdentities.get(account);
		if(result == null)
			return Collections.emptyMap();
		
		return result;
	}

	@Override
	public Map<String, SignalGroup> getGroups(String account) throws UnsupportedOperationException, IOException, ExecutionException {
		if(cacheGroups == null)
			return Collections.emptyMap();
		
		Map<String, SignalGroup> result = cacheGroups.get(account);
		if(result == null)
			return Collections.emptyMap();
		
		return result;
	}
	
	private static final JsonObject getAccountParam(String account) {
		JsonObject result = new JsonObject();
		result.addProperty("account", account);
		return result;
	}

	public JsonElement sendJsonRpc(String method, JsonObject params) throws UnsupportedOperationException, IOException {
		return sendJsonRpc(method, params, null);
	}
	
	public JsonElement sendJsonRpc(String method, Map<String, String> params) throws UnsupportedOperationException, IOException {
		return sendJsonRpc(method, params, null);
	}
	
	public JsonElement sendJsonRpc(String method, Map<String, String> params, String msgId) throws UnsupportedOperationException, IOException {
		JsonObject jsonParams = new JsonObject();
		params.forEach(jsonParams::addProperty);
		return sendJsonRpc(method, jsonParams, msgId);
	}
	
	public JsonElement sendJsonRpc(String method, JsonObject params, String msgId) throws UnsupportedOperationException, IOException {
		JsonObject rpc = new JsonObject();
		
		if(msgId == null || msgId.isBlank())
			msgId = String.format("%s-%s", method, (int)Math.random()*10_000);
		
		rpc.addProperty("id", msgId);
		
		rpc.addProperty("jsonrpc", "2.0");
		rpc.addProperty("method", method);
		rpc.add("params", Objects.requireNonNull(params));
		
		return internalSend(rpc, msgId);
	}

	private JsonElement internalSend(JsonObject rpc, String msgId) throws IOException, UnsupportedOperationException {
		String payload = GSON.toJson(rpc);

		URLConnection connection = urlRpc.openConnection();
		
		ComponentLog log = getLogger();
		
		if(log.isDebugEnabled()) log.debug("Sending RPC message: " + rpc.get("method"));

		// ************************
		// Send request
		// ************************
		if(connection instanceof HttpURLConnection) {
			HttpURLConnection httpConnection = (HttpURLConnection) connection;
			httpConnection.setRequestMethod("POST");
			httpConnection.setRequestProperty("Content-Type", "application/json");
			httpConnection.setDoOutput(true);

			try(OutputStream outputStream = httpConnection.getOutputStream()){
				outputStream.write(payload.getBytes(StandardCharsets.UTF_8));
				outputStream.flush();
			}
			
			int response = httpConnection.getResponseCode();
			if(response != HttpURLConnection.HTTP_OK) {
				throw new UnsupportedOperationException("Unexpected answer from server. Code: " + response);
			}
		} else {
			throw new UnsupportedOperationException("!?");
		}

		// ************************
		// Process response
		// ************************
		try(	InputStream inputStream = connection.getInputStream();
				InputStreamReader reader = new InputStreamReader(inputStream);
				BufferedReader bufferedReader = new BufferedReader(reader);
				){

			JsonObject element = JsonParser.parseReader(bufferedReader).getAsJsonObject();
			assertIsJsonRpc(element);
			assertCorrectMessageId(element, msgId);
			assertNoError(element);
			
			return element.get("result");
		}
	}

	private void assertNoError(JsonObject element) {
		//Check for error
		if(element.has("error")) {
			JsonObject jsonError = element.get("error").getAsJsonObject();
			throw new UnsupportedOperationException(String.format("%s (ErrorCode: %s)", jsonError.get("message").getAsString(), jsonError.get("code").getAsLong()));
		}
	}

	private void assertCorrectMessageId(JsonObject element, String msgId) {
		if(!msgId.equals(element.get("id").getAsString())){
			throw new UnsupportedOperationException("Unexpected message id from server: " + element.toString() + " (should be: " + msgId + ")");
		}
	}

	private void assertIsJsonRpc(JsonObject element) {
		if(!element.has("jsonrpc") && !"2.0".equals(element.get("jsonrpc").getAsString())){
			throw new UnsupportedOperationException("Unexpected answer from server: " + element.toString());
		}
	}
}
