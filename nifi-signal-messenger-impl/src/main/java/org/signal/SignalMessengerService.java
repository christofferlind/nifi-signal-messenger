package org.signal;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnDisabled;
import org.apache.nifi.annotation.lifecycle.OnEnabled;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.controller.AbstractControllerService;
import org.apache.nifi.controller.ConfigurationContext;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.reporting.InitializationException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.Manager.ReceiveMessageHandler;
import org.asamk.signal.manager.ServiceConfig;
import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.groups.GroupIdFormatException;
import org.asamk.signal.manager.groups.GroupIdV2;
import org.asamk.signal.manager.groups.GroupUtils;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.storage.groups.GroupInfo;
import org.asamk.signal.util.SecurityProvider;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.zkgroup.auth.AuthCredentialResponse;
import org.signal.zkgroup.groups.GroupSecretParams;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Api;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2AuthorizationString;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupContext;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupV2;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.EncapsulatedExceptions;
import org.whispersystems.signalservice.api.util.InvalidNumberException;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.EvictingQueue;

@Tags({ "Signal", "Messenger"})
@CapabilityDescription("Signal Messenger service")
public class SignalMessengerService extends AbstractControllerService implements SignalControllerService {

	private static final String USER_AGENT = "nifi-signal-messenger";

	static {
		Security.insertProviderAt(new SecurityProvider(), 1);
		Security.addProvider(new BouncyCastleProvider());
	}

	public static final PropertyDescriptor PROP_STORE_PATH = new PropertyDescriptor
			.Builder().name("StorePath")
			.displayName("Store path")
			.description("Path where signal-cli stores the data")
			.required(true)
			.addValidator(StandardValidators.FILE_EXISTS_VALIDATOR)
			.build();

	public static final PropertyDescriptor PROP_NUMBER = new PropertyDescriptor
			.Builder().name("Number")
			.displayName("Number (username)")
			.description("User name for signal, usually the number in form +<country_code><number>")
			.required(true)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
			.build();

	private static final List<PropertyDescriptor> properties;

	private Manager manager;

	private SignalAccount account;

	private SignalServiceAccountManager accountManager = null;

	private String number;

	private Method methodDecrypt;

	private Cache<Integer, AuthCredentialResponse> cacheGroupAuthorization = null;

	//Package visibility for testing
	Thread receiveMessagesThread;

	private EvictingQueue<Pair<SignalServiceEnvelope, SignalServiceContent>> messageQueue;
	
	private final static Object lockListeners = new Object();
	private Collection<BiConsumer<SignalServiceEnvelope, SignalServiceContent>> messageListeners = 
			new LinkedHashSet<>(10);

	private Map<String, Long> messageListenersLastMessage = new HashMap<>(10);
	
	static {
		final List<PropertyDescriptor> props = new ArrayList<>();
		props.add(PROP_STORE_PATH);
		props.add(PROP_NUMBER);
		properties = Collections.unmodifiableList(props);
	}

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
		String storeFileString = context.getProperty(PROP_STORE_PATH).getValue();
		String number = context.getProperty(PROP_NUMBER).getValue();

		try {
			File storeFile = new File(storeFileString);

			SignalServiceConfiguration serviceConfiguration = ServiceConfig.createDefaultServiceConfiguration(USER_AGENT);
			manager = Manager.init(number, storeFile, serviceConfiguration, USER_AGENT);

			manager.checkAccountState();

			account = getField(manager, "account");
			accountManager = getField(manager, "accountManager");

			if(!manager.isRegistered()) {
				throw new InitializationException("Signal manager still not registered");
			}
			
			//Test
			getMessageSender();

			methodDecrypt = Manager.class.getDeclaredMethod("decryptMessage", SignalServiceEnvelope.class);
			methodDecrypt.setAccessible(true);

			initMessageReceiver();
			
			cacheGroupAuthorization = CacheBuilder.newBuilder()
				      .expireAfterAccess(5, TimeUnit.DAYS)
				      .initialCapacity(10)
				      .concurrencyLevel(1)
				      .build();
			
			updateGroupAuthorizationCache();

			this.number = number;
		} catch (IOException e) {
			throw new InitializationException(e.getMessage(), e);
		} catch (NoSuchMethodException e) {
			throw new InitializationException(e.getMessage(), e);
		} catch (SecurityException e) {
			throw new InitializationException(e.getMessage(), e);
		} catch (IllegalAccessException e) {
			throw new InitializationException(e.getMessage(), e);
		} catch (IllegalArgumentException e) {
			throw new InitializationException(e.getMessage(), e);
		} catch (InvocationTargetException e) {
			throw new InitializationException(e.getMessage(), e);
		} catch (NoSuchFieldException e) {
			throw new InitializationException(e.getMessage(), e);
		}
	}

	private void initMessageReceiver() {
		if(getLogger().isDebugEnabled()) getLogger().debug("Starting receive message thread");
		messageQueue = EvictingQueue.create(1_000);
		
		receiveMessagesThread = new Thread(() -> {
			try {
				ReceiveMessageHandler handler = SignalMessengerService.this::handleMessage;
				
				while(!Thread.currentThread().isInterrupted()) {
					this.manager.receiveMessages(
							15, 
							TimeUnit.SECONDS, 
							true, 
							false, 
							handler);
				}
			} catch (AssertionError e) {
				if(e.getCause() instanceof InterruptedException) {
					Thread.currentThread().interrupt();
					return;
				}
				onError(e);
			} catch (Throwable e) {
				onError(e);
			}
		});
		
		receiveMessagesThread.start();
	}
	
	public void handleMessage(SignalServiceEnvelope envelope, SignalServiceContent decryptedContent, Throwable e) {
		if(e != null) {
			getLogger().error(e.getMessage(), e);
		}
		
		Pair<SignalServiceEnvelope, SignalServiceContent> element = 
				new Pair<>(envelope, decryptedContent);
		
		synchronized (lockListeners) {
			messageQueue.add(element);
			
			notifyListeners(element);
		}
	}

	public void addMessageListener(BiConsumer<SignalServiceEnvelope, SignalServiceContent> listener) {
		synchronized (lockListeners) {
			messageListeners.add(listener);
			
			// For new listener, send all cached messages
			Long lastMessageTimestamp = messageListenersLastMessage.get(listener.getClass().getCanonicalName());
			var stream = messageQueue.stream();
			
			if(lastMessageTimestamp != null)
				stream = stream.filter(p -> p.second().getTimestamp() > lastMessageTimestamp);
			
			stream.forEach(p -> {
				listener.accept(p.first(), p.second());
				messageListenersLastMessage.put(listener.getClass().getCanonicalName(), p.second().getTimestamp());
			});
		}
	}

	public void removeMessageListener(BiConsumer<SignalServiceEnvelope, SignalServiceContent> listener) {
		synchronized (lockListeners) {
			messageListeners.remove(listener);
		}
	}

	private void notifyListeners(Pair<SignalServiceEnvelope, SignalServiceContent> element) {
		for (BiConsumer<SignalServiceEnvelope, SignalServiceContent> consumer : messageListeners) {
			SignalServiceContent msg = element.second();

			consumer.accept(element.first(), msg);
			messageListenersLastMessage.put(consumer.getClass().getCanonicalName(), msg.getTimestamp());
		}
	}
	
    public static final int currentTimeDays() {
        return (int) TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis());
    }

	private void updateGroupAuthorizationCache() throws IOException {
		// Returns credentials for the next 7 days
		int today = currentTimeDays();
        GroupsV2Api groupsV2Api = accountManager.getGroupsV2Api();
        HashMap<Integer, AuthCredentialResponse> credentials = groupsV2Api.getCredentials(today);
        cacheGroupAuthorization.putAll(credentials);
	}

	public SignalServiceMessageSender getMessageSender() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
		Method methodGetMessageReceiver = Manager.class.getDeclaredMethod("createMessageSender");
		methodGetMessageReceiver.setAccessible(true);
		SignalServiceMessageSender messageSender = (SignalServiceMessageSender) methodGetMessageReceiver.invoke(manager);
		return messageSender;
	}

	private List<SendMessageResult> sendMessageWithAttachment(SignalServiceDataMessage.Builder builder, Collection<SignalServiceAddress> recipients) throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		Method methodSendMessage = Manager.class.getDeclaredMethod("sendMessage", SignalServiceDataMessage.Builder.class, Collection.class);
		methodSendMessage.setAccessible(true);

		@SuppressWarnings("unchecked")
		Pair<Long, List<SendMessageResult>> results = (Pair<Long, List<SendMessageResult>>) methodSendMessage.invoke(manager, builder, recipients);
		return results.second();
	}

	@OnDisabled
	public void onDisable() {
		if(receiveMessagesThread != null) {
			try {
				receiveMessagesThread.interrupt();
			} catch (Exception e) {
				getLogger().error(e.getMessage(), e);
			}
		}

		synchronized (lockListeners) {
			messageListeners.clear();
			messageQueue.clear();
		}
		
		try {
			manager.close();
		} catch (IOException e) {}
	}

	@SuppressWarnings("unchecked")
	private static final <T> T getField(Object obj, String name) throws NoSuchFieldException, IllegalAccessException {
		Field fieldFileChannel = obj.getClass().getDeclaredField(name);
		fieldFileChannel.setAccessible(true);
		return (T) fieldFileChannel.get(obj);
	}

	@Override
	public String getSignalUsername() {
		return this.number;
	}

	public void saveAccount() {
		account.save();
	}

	public SignalServiceContent decryptMessage(SignalServiceEnvelope envelope) throws IllegalStateException {
		try {
			return (SignalServiceContent) methodDecrypt.invoke(manager, envelope);
		} catch (Exception e) {
			throw new IllegalStateException(e.getMessage(), e);
		}
	}

	public List<SendMessageResult> sendGroupMessage(List<String> groups, String body, SignalServiceAttachmentStream attachment)  throws ProcessException, IOException, InvocationTargetException {
		List<SendMessageResult> result = null;

		List<GroupInfo> availableGroups = account.getGroupStore().getGroups();
		if(availableGroups == null || availableGroups.size() < 1)
			throw new ProcessException("Could not find any joined groups for this account: " + account.getUsername());

		Map<String, GroupInfo> foundGroups = new LinkedHashMap<>(groups.size());

		for (String givenGroup : groups) {
			GroupInfo targetGroup = null;

			targetGroup = getGroupBasedOnTitle(availableGroups, givenGroup);

			//The target group has not been found using title, try id
			if(targetGroup == null) {
				getLogger().debug("Could not find a group using name, trying with base64 id");
				targetGroup = getGroupBasedOnId(availableGroups, givenGroup);
			}

			//The target group was not found using title nor id
			if(targetGroup == null) {
				//TODO what now!?
				throw new IllegalStateException("Could not find a group that matches: " + givenGroup);
			} else {
				GroupInfo existingGroup = foundGroups.put(givenGroup, targetGroup);
				if(existingGroup != null) {
					getLogger().warn("The given group identifier \"" + givenGroup + "\" matches at least two groups. Duplicate groups is ignored!");
				}
			}
		}

		try {
			SignalServiceMessageSender messageSender = getMessageSender();
			for (GroupInfo g : foundGroups.values()) {
				try {
					SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder().withBody(body);

					updateLoadAttachments(attachment, messageSender, messageBuilder);

					GroupUtils.setGroupContext(messageBuilder, g);
					messageBuilder.withExpiration(g.getMessageExpirationTime());

					return sendMessageWithAttachment(messageBuilder, g.getMembersWithout(account.getSelfAddress()));
				} catch (IOException e) {
					throw e;
				} catch (InvocationTargetException e) {
					throw e;
				} catch (Exception e) {
					throw new IllegalStateException(e.getMessage(), e);
				}
			}
		} catch (IOException e) {
			throw e;
		} catch (InvocationTargetException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException(e.getMessage(), e);
		}

		return result;
	}

	/**
	 * @param address - {@link List} of numbers to send the message to, can not be null. If the given list is empty, this method will return immediately.
	 * @param body - The message to send, can not be null
	 * @param attachment - attachments to send
	 * @return 
	 * @return 
	 * @throws InvocationTargetException 
	 */
	@Override
	public List<SendMessageResult> sendMessage(List<String> address, String body, SignalServiceAttachmentStream attachment)  throws ProcessException, IOException, InvocationTargetException {
		Objects.requireNonNull(address);
		Objects.requireNonNull(body);

		if(address.isEmpty())
			return Collections.emptyList();

		try {
			SignalServiceMessageSender messageSender = getMessageSender();
			SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder().withBody(body);

			updateLoadAttachments(attachment, messageSender, messageBuilder);

			messageBuilder.withProfileKey(account.getProfileKey().serialize());

			//Convert string numbers to SignalServiceAddress
			Collection<SignalServiceAddress> numbers = new LinkedHashSet<>(address.size());
			for (String string : address) {
				try {
					numbers.add(manager.canonicalizeAndResolveSignalServiceAddress(string));
				} catch (Throwable e) {
					IllegalArgumentException exc = 
							new IllegalArgumentException("Could not convert number " + string + " to SignalServiceAddress. Skipping...", e);
					getLogger().error(exc.getMessage(), exc);
				}
			}

			return sendMessageWithAttachment(messageBuilder, numbers);
		} catch (IOException e) {
			throw e;
		} catch (InvocationTargetException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException(e.getMessage(), e);
		}
	}

	private void updateLoadAttachments(SignalServiceAttachmentStream attachment, SignalServiceMessageSender messageSender,
			SignalServiceDataMessage.Builder messageBuilder) throws IOException {

		if(attachment == null)
			return;

		//Upload attachments
		List<SignalServiceAttachment> attachmentPointers = new ArrayList<>(1);
		if (attachment.isStream()) {
			SignalServiceAttachmentPointer pointer = messageSender.uploadAttachment(attachment.asStream());
			attachmentPointers.add(pointer);
		} else if (attachment.isPointer()) {
			attachmentPointers.add(attachment.asPointer());
		}

		messageBuilder.withAttachments(attachmentPointers);
	}

	@Override
	public void sendMessageReaction(String emoji, boolean remove, String targetAuthor, long targetSentTimestamp, List<String> recipients) throws IOException, EncapsulatedExceptions, InvalidNumberException {
		manager.sendMessageReaction(emoji, remove, targetAuthor, targetSentTimestamp, recipients);
	}

	private GroupInfo getGroupBasedOnId(List<GroupInfo> availableGroups, String givenGroup) {
		try {
			GroupId givenGroupId = GroupId.fromBase64(givenGroup);

			for (GroupInfo groupInfo : availableGroups) {
				if(givenGroupId.equals(groupInfo.getGroupId())) {
					return groupInfo;
				}
			}

		} catch (GroupIdFormatException e) {
			getLogger().debug("The given string \"" + givenGroup + "\" can not be converted to group id");
		}

		return null;
	}

	private GroupInfo getGroupBasedOnTitle(List<GroupInfo> availableGroups, String givenGroup) {
		for (GroupInfo groupInfo : availableGroups) {
			//Check if title matches the group name
			if(givenGroup.equals(groupInfo.getTitle())) {
				return groupInfo;
			}
		}

		return null;
	}

	@Override
	public String getGroupId(SignalServiceGroupContext groupContext) {
		if (groupContext.getGroupV1().isPresent()) {
            SignalServiceGroup groupInfo = groupContext.getGroupV1().get();
            return GroupId.v1(groupInfo.getGroupId()).toString();
        } else if (groupContext.getGroupV2().isPresent()) {
            SignalServiceGroupV2 groupInfo = groupContext.getGroupV2().get();
            return GroupUtils.getGroupIdV2(groupInfo.getMasterKey()).toBase64();
        }
		
		throw new UnsupportedOperationException("Unsupported group version");
	}
	
	public String getGroupTitle(SignalServiceGroupContext groupContext) {
		if (groupContext.getGroupV1().isPresent()) {
            SignalServiceGroup groupInfo = groupContext.getGroupV1().get();
            
            //Check local signal-cli cache first
            GroupInfo group = manager.getGroup(GroupId.v1(groupInfo.getGroupId()));
            if(group != null) {
            	String title = group.getTitle();
            	if(title != null)
            		return title;
            }
            
            return groupInfo.getName().or("");
        } else if(groupContext.getGroupV2().isPresent()) {
        	SignalServiceGroupV2 groupV2 = groupContext.getGroupV2().get();
        	
            //Check local signal-cli cache first
        	String title = getGroupTitleFromSignalCliCache(groupV2);
        	if(title != null)
        		return title;
        	
            //If not found in local cache try get the name from the server.
        	title = getGroupTitleFromSignalServer(groupV2);
        	if(title != null)
        		return title;
        }
		
        return "";
	}

	/**
	 * @param groupV2
	 * @return null if it fails
	 */
	private String getGroupTitleFromSignalServer(SignalServiceGroupV2 groupV2) {
		try {
			GroupSecretParams groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupV2.getMasterKey());
			int today = SignalMessengerService.currentTimeDays();
			
			AuthCredentialResponse credentials = cacheGroupAuthorization.getIfPresent(today);
			if(credentials == null) {
				updateGroupAuthorizationCache();
				credentials = cacheGroupAuthorization.getIfPresent(today);
			}
			
			if(credentials == null)
				throw new IllegalStateException("Could not load credentials");
			
			GroupsV2Api groupsV2Api = accountManager.getGroupsV2Api();
			GroupsV2AuthorizationString authorizationString = groupsV2Api.getGroupsV2AuthorizationString(
																account.getUuid(),
												                today,
												                groupSecretParams,
												                credentials);
			
			DecryptedGroup decryptedGroup = groupsV2Api.getGroup(groupSecretParams, authorizationString);
			return decryptedGroup.getTitle();
		} catch (Throwable e) {
			getLogger().error(e.getMessage(), e);
		}
		
		return null;
	}

	private String getGroupTitleFromSignalCliCache(SignalServiceGroupV2 groupInfo) {
		GroupIdV2 idV2 = GroupUtils.getGroupIdV2(groupInfo.getMasterKey());
		GroupInfo group = manager.getGroup(idV2);
		return group.getTitle();
	}
	
	public void onError(Throwable e) {
		getLogger().error(e.getMessage(), e);
	}
}
