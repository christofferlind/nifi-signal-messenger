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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnDisabled;
import org.apache.nifi.annotation.lifecycle.OnEnabled;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.controller.AbstractControllerService;
import org.apache.nifi.controller.ConfigurationContext;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.reporting.InitializationException;
import org.signal.model.SignalMessage;

import com.google.common.collect.EvictingQueue;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

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
	
	private final static Object LOCK_LISTENERS = new Object();

	private Collection<Consumer<SignalMessage>> messageListeners = new LinkedHashSet<>(10);
	private Map<String, Long> messageListenersLastMessage = new HashMap<>(10);

	static {
		final List<PropertyDescriptor> props = new ArrayList<>();
		props.add(PROP_DAEMON_URL);
		properties = Collections.unmodifiableList(props);
	}

	private String url;

	private Thread receiveMessagesThread;

	private volatile boolean started = false;

	private EvictingQueue<SignalMessage> messageQueue;

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
		url = context.getProperty(PROP_DAEMON_URL).getValue();
		
		this.started = true;
		
		if(getLogger().isDebugEnabled()) getLogger().debug("Starting receive message thread");
		messageQueue = EvictingQueue.create(1_000);
		
//		ReceiveMessageHandler handler = SignalMessengerService.this::handleMessage;
		
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
			} catch (Throwable e) {
				onError(e);
			} finally {
				if(getLogger().isDebugEnabled()) getLogger().debug("Stopped listening for messages: " + url);
			}
		}, "SignalMsgRec");

		receiveMessagesThread.setDaemon(true);
		receiveMessagesThread.start();
	}
	
	private void connectAndRecieveMessaged(String url2) throws InterruptedException, JsonSyntaxException, IOException {
		URL urls = new URL(url + "/api/v1/events");
		URLConnection connection = urls.openConnection();
		if(connection instanceof HttpURLConnection) {
			HttpURLConnection httpConnection = (HttpURLConnection) connection;
			httpConnection.setRequestMethod("GET");
			httpConnection.setRequestProperty("Content-Type", "application/json");

			try(
					InputStream inputStream = httpConnection.getInputStream();
					InputStreamReader reader = new InputStreamReader(inputStream);
					BufferedReader bufferedReader = new BufferedReader(reader);
					){

				String line = null;
				boolean nextLineIsData = false;
				while((line = bufferedReader.readLine()) != null) {

					if(Thread.currentThread().isInterrupted()) {
						Thread.currentThread().interrupt();
						throw new InterruptedException();
					}

					//Connection keep alive
					if(line.equalsIgnoreCase(":"))
						continue;

					if(line.equalsIgnoreCase("event:receive")) {
						nextLineIsData = true;
					}

					if(nextLineIsData && line.startsWith("data:")) {
						String jsonData = line.substring(5);

						JsonElement element = JsonParser.parseString(jsonData);
						processData(element);
						continue;
					}
				}
			}
		} else {
			throw new UnsupportedOperationException();
		}
	}

	private void processData(JsonElement element) {
		if(element.isJsonObject()){
			JsonObject jsonObject = element.getAsJsonObject();
			if(!jsonObject.has("account") || !jsonObject.has("envelope")) {
				UnsupportedOperationException exc = new UnsupportedOperationException("Unsupporterd signal message");
				getLogger().error(exc.getMessage(), exc);
				return;
			}
			
			JsonObject jsonEnvelope = jsonObject.get("envelope").getAsJsonObject();
			if(!jsonEnvelope.has("timestamp")) {
				UnsupportedOperationException exc = new UnsupportedOperationException("Unsupporterd signal message");
				getLogger().error(exc.getMessage(), exc);
				return;
			}
			
			long timestamp = jsonEnvelope.get("timestamp").getAsLong();
			Optional<String> sourceName = getFieldString(jsonEnvelope, "sourceName");
			Optional<String> source = getFieldString(jsonEnvelope, "sourceNumber");
			
				
			if(jsonEnvelope.has("dataMessage")) {
				JsonObject dataMessage = jsonEnvelope.get("dataMessage").getAsJsonObject();
				String message = dataMessage.get("message").getAsString();
				
				SignalMessage msg = new SignalMessage();
				msg.setMessage(message);
				msg.setSource(source.orElseGet(() -> "Unknown"));
				msg.setSourceName(sourceName.orElseGet(() -> "Unknown"));
				msg.setTimestamp(timestamp);

				synchronized (LOCK_LISTENERS) {
					messageQueue.add(msg);
					
					notifyListeners(msg);
				}

			} else if(jsonEnvelope.has("receiptMessage")) {
				//TODO implement
			} else if(jsonEnvelope.has("typingMessage")) {
				//TODO: implement
			}
		}
	}

	private static final Optional<String> getFieldString(JsonObject jsonObject, String field) {
		if(!jsonObject.has(field))
			return Optional.empty();
		
		return Optional.of(jsonObject.get(field).getAsString());
	}

	public void onError(Throwable e) {
		getLogger().error(e.getMessage(), e);
	}

	private int sendJsonRPC(Object rpc) {
		String payload = "";
		try {
			URL urls = new URL(url + "/api/v1/rpc");
			URLConnection connection = urls.openConnection();
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
				if(response == HttpURLConnection.HTTP_OK) {
					try(
							InputStream inputStream = httpConnection.getInputStream();
							InputStreamReader reader = new InputStreamReader(inputStream);
							BufferedReader bufferedReader = new BufferedReader(reader);
							){
						
						JsonElement element = JsonParser.parseReader(bufferedReader);
					}
				}
			} else {
				throw new UnsupportedOperationException();
			}
			
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return 0;
	}

	@OnDisabled
	public void onDisable() {
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
			messageListenersLastMessage.clear();
		}
	}
	
	public boolean isStarted() {
		return started;
	}

	@Override
	public void sendMessage(String account, List<String> recipients, String message, Object attachment) {
		JsonArray array = new JsonArray(recipients.size());
		recipients.forEach(array::add);
		
		JsonObject jsonParams = new JsonObject();
		jsonParams.add("recipient", array);
		jsonParams.addProperty("message", message);
		jsonParams.addProperty("account", account);
		
		JsonObject rpc = new JsonObject();
		rpc.addProperty("id", 42);
		rpc.addProperty("jsonrpc", "2.0");
		rpc.addProperty("method", "send");
		rpc.add("params", jsonParams);
	}

	@Override
	public void sendGroupMessage(String account, List<String> groups, String message, Object attachment) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void addMessageListener(Consumer<SignalMessage> listener) {
		synchronized (LOCK_LISTENERS) {
			messageListeners.add(Objects.requireNonNull(listener));

			// For new listener, send all cached messages
			Long lastMessageTimestamp = messageListenersLastMessage.get(listener.getClass().getCanonicalName());
			var stream = messageQueue.stream();

			if(lastMessageTimestamp != null)
				stream = stream.filter(msg -> msg.getTimestamp() > lastMessageTimestamp);

			stream.forEach(msg -> {
				listener.accept(msg);
				messageListenersLastMessage.put(listener.getClass().getCanonicalName(), msg.getTimestamp());
			});
		}
	}

	@Override
	public void removeMessageListener(Consumer<SignalMessage> messageListener) {
		synchronized (LOCK_LISTENERS) {
			messageListeners.remove(Objects.requireNonNull(messageListener));
		}
	}
	
	private void notifyListeners(SignalMessage message) {
		for (Consumer<SignalMessage> consumer : messageListeners) {
			consumer.accept(message);
		}
	}

}
