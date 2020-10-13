package org.signal;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

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
import org.asamk.signal.manager.ServiceConfig;
import org.asamk.signal.storage.SignalAccount;
import org.asamk.signal.util.SecurityProvider;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.EncapsulatedExceptions;
import org.whispersystems.signalservice.api.util.InvalidNumberException;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;

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

	@SuppressWarnings("unused")
	private SignalServiceAccountManager accountManager;

	private String number;

	private Method methodDecrypt;

	private FileChannel accountFileChannel;

	private FileLock accountFileLock;

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
    	String storeFile = context.getProperty(PROP_STORE_PATH).getValue();
    	String number = context.getProperty(PROP_NUMBER).getValue();
    	
    	try {
    		SignalServiceConfiguration serviceConfiguration = ServiceConfig.createDefaultServiceConfiguration(USER_AGENT);
    		manager = Manager.init(number, storeFile, serviceConfiguration, USER_AGENT);
    		
            manager.checkAccountState();

			account = getField(manager, "account");
			accountManager = getField(manager, "accountManager");
			
			accountFileChannel = getField(account, "fileChannel");
			accountFileLock = getField(account, "lock");
			
			if(!manager.isRegistered()) {
				throw new InitializationException("Signal manager still not registered");
			}
			
			//Test
			getMessageReceiver();
			getMessageSender();
			
			methodDecrypt = Manager.class.getDeclaredMethod("decryptMessage", SignalServiceEnvelope.class);
			methodDecrypt.setAccessible(true);
			
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

	public SignalServiceMessageReceiver getMessageReceiver() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
		Method methodGetMessageReceiver = Manager.class.getDeclaredMethod("getMessageReceiver");
		methodGetMessageReceiver.setAccessible(true);
		SignalServiceMessageReceiver messageReceiver = (SignalServiceMessageReceiver) methodGetMessageReceiver.invoke(manager);
		return messageReceiver;
	}

	public SignalServiceMessageSender getMessageSender() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
		Method methodGetMessageReceiver = Manager.class.getDeclaredMethod("getMessageSender");
		methodGetMessageReceiver.setAccessible(true);
		SignalServiceMessageSender messageSender = (SignalServiceMessageSender) methodGetMessageReceiver.invoke(manager);
		return messageSender;
	}
	
	private List<SendMessageResult> sendMessageWithAttachment(SignalServiceDataMessage.Builder builder, Collection<SignalServiceAddress> recipients) throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		Method methodSendMessage = Manager.class.getDeclaredMethod("sendMessage", SignalServiceDataMessage.Builder.class, Collection.class);
		methodSendMessage.setAccessible(true);

		@SuppressWarnings("unchecked")
		List<SendMessageResult> results = (List<SendMessageResult>) methodSendMessage.invoke(manager, builder, recipients);
		return results;
	}

    @OnDisabled
    public void shutdown() {
    	if(accountFileChannel != null) {
    		try {
    			accountFileChannel.close();
    		} catch (IOException e) {}
    	}

    	if(accountFileLock != null) {
    		try {
    			accountFileLock.close();
    		} catch (IOException e) {}
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

	@Override
	public void sendMessage(List<String> address, String body, SignalServiceAttachmentStream attachment)  throws ProcessException, IOException {
		try {
			SignalServiceMessageSender messageSender = getMessageSender();
			SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder().withBody(body);

			//Upload attachments
			if (attachment != null) {
				List<SignalServiceAttachment> attachmentPointers = new ArrayList<>(1);
				if (attachment.isStream()) {
					SignalServiceAttachmentPointer pointer = messageSender.uploadAttachment(attachment.asStream());
					attachmentPointers.add(pointer);
                } else if (attachment.isPointer()) {
                    attachmentPointers.add(attachment.asPointer());
				}
				
				messageBuilder.withAttachments(attachmentPointers);
			}
			
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
			
			@SuppressWarnings("unused")
			List<SendMessageResult> sendResults = sendMessageWithAttachment(messageBuilder, numbers);
			//TODO should do something with the result
		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException(e.getMessage(), e);
		}
	}

	@Override
	public void sendMessageReaction(String emoji, boolean remove, String targetAuthor, long targetSentTimestamp, List<String> recipients) throws IOException, EncapsulatedExceptions, InvalidNumberException {
		manager.sendMessageReaction(emoji, remove, targetAuthor, targetSentTimestamp, recipients);
	}
}
