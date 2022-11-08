package org.signal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.annotation.behavior.InputRequirement.Requirement;
import org.apache.nifi.annotation.behavior.TriggerSerially;
import org.apache.nifi.annotation.behavior.WritesAttribute;
import org.apache.nifi.annotation.behavior.WritesAttributes;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.SeeAlso;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.annotation.lifecycle.OnStopped;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.flowfile.attributes.CoreAttributes;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.AbstractSessionFactoryProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessSessionFactory;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.signal.model.SignalMessage;

@InputRequirement(Requirement.INPUT_FORBIDDEN)
@CapabilityDescription("Consumes signal messages. "
        + "The message of each received sinal message are written as contents of the FlowFile")
@Tags({ "Signal", "Get", "Ingest", "Ingress", "Message", "Consume" })
@TriggerSerially
@SeeAlso({PutSignalMessage.class})
@WritesAttributes({
	@WritesAttribute(attribute=ConsumeSignalMessage.ATTRIBUTE_RECEIPT, description="Values true or false depending on if the message is a receipt or not"),
	@WritesAttribute(attribute=ConsumeSignalMessage.ATTRIBUTE_RECEIPT_DELIVERY, description="Values true or false depending on if the message is a receipt and if the receipt is a delivery or not"),
	@WritesAttribute(attribute=ConsumeSignalMessage.ATTRIBUTE_RECEIPT_READ, description="Values true or false depending on if the message is a receipt and if the receipt is a read-message or not"),

	@WritesAttribute(attribute=ConsumeSignalMessage.ATTRIBUTE_CALL_MESSAGE, description="true if it is a call"),
	
	@WritesAttribute(attribute=ConsumeSignalMessage.ATTRIBUTE_MESSAGE, description="The content of the message sent"),
	@WritesAttribute(attribute=ConsumeSignalMessage.ATTRIBUTE_MESSAGE_VIEW_ONCE, description="Values true or false depending on if the message is a view once message"),
	@WritesAttribute(attribute=ConsumeSignalMessage.ATTRIBUTE_TIMESTAMP, description="Time when the message was sent"),
	@WritesAttribute(attribute=ConsumeSignalMessage.ATTRIBUTE_RECEIVING_NUMBER, description="The number that received the message (this is the same as the one in the controller used)"),
	@WritesAttribute(attribute=ConsumeSignalMessage.ATTRIBUTE_SENDER_NUMBER, description="The number that sent the message"),
	@WritesAttribute(attribute=ConsumeSignalMessage.ATTRIBUTE_SENDER_VERIFIED, description="If the sender number is verified. One of: DEFAULT (trusted but not yet verified), VERIFIED (trusted and verified), UNVERIFIED (untrusted)"),
	@WritesAttribute(attribute=ConsumeSignalMessage.ATTRIBUTE_SENDER_IDENTIFIED, description="If the sender number is identified"),
	@WritesAttribute(attribute=ConsumeSignalMessage.ATTRIBUTE_ERROR_MESSAGE, description="If an error occurs, the detailed error message will be put in this attribute"),

	@WritesAttribute(attribute=ConsumeSignalMessage.ATTRIBUTE_SENDER_TYPING_STARTED, description="This attribute will be present if the ignore typing messages is set to false and the received message is a typing message"),
	@WritesAttribute(attribute=ConsumeSignalMessage.ATTRIBUTE_SENDER_TYPING_STOPPED, description="This attribute will be present if the ignore typing messages is set to false and the received message is a typing message"),
	
	@WritesAttribute(attribute=ConsumeSignalMessage.ATTRIBUTE_MESSAGE_REACTION_EMOJI, description="If the data-message is a reaction, then this attribute will be populated with the unicode grapheme cluster"),
	@WritesAttribute(attribute=ConsumeSignalMessage.ATTRIBUTE_MESSAGE_REACTION_TARGET_AUTHOR, description="If the data-message is a reaction, then this attribute will be populated with the target author number"),
	@WritesAttribute(attribute=ConsumeSignalMessage.ATTRIBUTE_MESSAGE_REACTION_TARGET_TIMESTAMP, description="If the data-message is a reaction, then this attribute will be populated with the timestamp of the target message"),

	@WritesAttribute(attribute=ConsumeSignalMessage.ATTRIBUTE_MESSAGE_QUOTE_ID, description="If the data-message contains a quote, then this attribute will be populated with the id (long)"),

	@WritesAttribute(attribute=ConsumeSignalMessage.ATTRIBUTE_MESSAGE_GROUP_ID, description="If the data-message is a message to a group, then this attribute will be populated with the base64 encoded group id"),
	@WritesAttribute(attribute=ConsumeSignalMessage.ATTRIBUTE_MESSAGE_GROUP_TITLE, description="If the data-message is a message to a group, then this attribute will be populated with the title of the group"),
	})
public class ConsumeSignalMessage extends AbstractSessionFactoryProcessor {

	public static final String ATTRIBUTE_RECEIPT = 								"signal.receipt";
	public static final String ATTRIBUTE_RECEIPT_DELIVERY = 					"signal.receipt.delivery";
	public static final String ATTRIBUTE_RECEIPT_READ = 						"signal.receipt.read";

	public static final String ATTRIBUTE_CALL_MESSAGE = 						"signal.call";

	public static final String ATTRIBUTE_MESSAGE = 								"signal.message";
	public static final String ATTRIBUTE_MESSAGE_VIEW_ONCE = 					"signal.message.viewonce";
	public static final String ATTRIBUTE_TIMESTAMP = 							"signal.timestamp";
	public static final String ATTRIBUTE_RECEIVING_NUMBER = 					"signal.receiving.number";
	public static final String ATTRIBUTE_SENDER_NUMBER = 						"signal.sender.number";
	public static final String ATTRIBUTE_SENDER_VERIFIED = 						"signal.sender.verified";
	public static final String ATTRIBUTE_SENDER_IDENTIFIED = 					"signal.sender.identified";

	public static final String ATTRIBUTE_SENDER_TYPING_STARTED = 				"signal.sender.typing.started";
	public static final String ATTRIBUTE_SENDER_TYPING_STOPPED = 				"signal.sender.typing.stopped";
	
	public static final String ATTRIBUTE_MESSAGE_REACTION_EMOJI = 				"signal.message.reaction.emoji";
	public static final String ATTRIBUTE_MESSAGE_REACTION_TARGET_AUTHOR = 		"signal.message.reaction.target.author";
	public static final String ATTRIBUTE_MESSAGE_REACTION_TARGET_TIMESTAMP = 	"signal.message.reaction.target.timestamp";

	public static final String ATTRIBUTE_MESSAGE_QUOTE_ID = 					"signal.message.quote.id";
	
	public static final String ATTRIBUTE_MESSAGE_GROUP_ID = 					"signal.message.group.id";
	public static final String ATTRIBUTE_MESSAGE_GROUP_TITLE = 					"signal.message.group.title";

	public  static final String ATTRIBUTE_ERROR_MESSAGE = 						"signal.error.message";

	public static final PropertyDescriptor SIGNAL_SERVICE = new PropertyDescriptor
            .Builder().name("SignalService")
            .displayName("Signal Service")
            .description("The signal service to use")
            .required(true)
            .identifiesControllerService(SignalControllerService.class)
            .build();

	public static final PropertyDescriptor IGNORE_RECEIPT_MESSAGE = new PropertyDescriptor
            .Builder().name("ReceiptMessages")
            .displayName("Ignore receipts")
            .description("Don't transfer any receipt messages")
            .allowableValues(Boolean.toString(Boolean.TRUE), Boolean.toString(Boolean.FALSE))
            .defaultValue(Boolean.toString(Boolean.TRUE))
            .build();

	public static final PropertyDescriptor IGNORE_TYPING_MESSAGE = new PropertyDescriptor
            .Builder().name("TypingMessages")
            .displayName("Ignore typing messages")
            .description("Don't transfer any typing messages")
            .allowableValues(Boolean.toString(Boolean.TRUE), Boolean.toString(Boolean.FALSE))
            .defaultValue(Boolean.toString(Boolean.TRUE))
            .build();

	public static final PropertyDescriptor IGNORE_UNVERIFIED_SENDER = new PropertyDescriptor
            .Builder().name("IgnoreUnverifiedSender")
            .displayName("Ignore unverified sender")
            .description("If set to to true then only messages sent by a trusted and verified sender identity (at least one) is transfered to success relationship.")
            .allowableValues(Boolean.toString(Boolean.TRUE), Boolean.toString(Boolean.FALSE))
            .defaultValue(Boolean.toString(Boolean.FALSE))
            .build();

    public static final Relationship SUCCESS = new Relationship.Builder()
            .name("success")
            .description("Successful send")
            .build();

    public static final Relationship FAILURE = new Relationship.Builder()
            .name("failure")
            .description("Unsuccessful send")
            .build();

    private List<PropertyDescriptor> descriptors;

    private Set<Relationship> relationships;
    
    private SignalControllerService service = null;

    private AtomicReference<ProcessSessionFactory> sessionFactoryReference = new AtomicReference<>();

	private boolean ignoreReceipts;
	private boolean ignoreTyping;
	private boolean ignoreUnverifiedSenders;
	private volatile Consumer<SignalMessage> messageListener;

    @Override
    protected void init(final ProcessorInitializationContext context) {
        final List<PropertyDescriptor> descriptors = new ArrayList<PropertyDescriptor>();
        descriptors.add(SIGNAL_SERVICE);
        descriptors.add(IGNORE_RECEIPT_MESSAGE);
        descriptors.add(IGNORE_TYPING_MESSAGE);
        descriptors.add(IGNORE_UNVERIFIED_SENDER);
        this.descriptors = Collections.unmodifiableList(descriptors);

        final Set<Relationship> relationships = new HashSet<Relationship>();
        relationships.add(SUCCESS);
        relationships.add(FAILURE);
        this.relationships = Collections.unmodifiableSet(relationships);
    }

    @Override
    public Set<Relationship> getRelationships() {
        return this.relationships;
    }

    @Override
    public final List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return descriptors;
    }
    
    @OnScheduled
    public void onScheduled(ProcessContext context) throws ProcessException {
    	service = context.getProperty(SIGNAL_SERVICE).asControllerService(SignalControllerService.class);
    	ignoreReceipts = context.getProperty(IGNORE_RECEIPT_MESSAGE).asBoolean();
    	ignoreTyping = context.getProperty(IGNORE_TYPING_MESSAGE).asBoolean();
    	ignoreUnverifiedSenders = context.getProperty(IGNORE_UNVERIFIED_SENDER).asBoolean();
    }
    
    private void onError(Throwable e) {
    	ComponentLog logger = getLogger();
    	logger.error(e.getMessage(), e);
    }
    
    @OnStopped
    public void unschedule() {
    	if(messageListener != null && service != null) {
    		service.removeMessageListener(messageListener);
    	}
    	
    	messageListener = null;
    }

	@Override
	public void onTrigger(ProcessContext context, ProcessSessionFactory sessionFactory) throws ProcessException {
    	sessionFactoryReference.compareAndSet(null, sessionFactory);
    	
    	if(messageListener == null) {
	    	messageListener = this::handleMessage;
	    	service.addMessageListener(messageListener);
    	}
        
        context.yield();
	}
	
	private void handleMessage(SignalMessage message) {
		ProcessSessionFactory sessionFactory = sessionFactoryReference.get();
		if(sessionFactory == null) {
			getLogger().warn("Message received, but no ProcessSessionFactory is set so we cant handle the signal message");
			return;
		}
		
		ProcessSession session = sessionFactory.createSession();
		Map<String, String> attributes = new HashMap<>(20);
		try {
//			attributes.put(ATTRIBUTE_SENDER_IDENTIFIED, Boolean.toString(!envelope.isUnidentifiedSender()));
//			attributes.put(ATTRIBUTE_RECEIPT, 			Boolean.toString(envelope.isReceipt()));

			attributes.put(ATTRIBUTE_SENDER_NUMBER, 	message.getSource());
//			attributes.put(ATTRIBUTE_SENDER_VERIFIED, 	verifiedValue);
			attributes.put(ATTRIBUTE_TIMESTAMP, 		Long.toString(message.getTimestamp()));
			
//			if(opTypingMessage.isPresent()) {
//				SignalServiceTypingMessage typingMessage = opTypingMessage.get();
//				attributes.put(ATTRIBUTE_SENDER_TYPING_STARTED, 	Boolean.toString(typingMessage.isTypingStarted()));
//				attributes.put(ATTRIBUTE_SENDER_TYPING_STOPPED, 	Boolean.toString(typingMessage.isTypingStopped()));
//			}

//			//Check receipts
//			Optional<SignalServiceReceiptMessage> receiptMessage = decryptedMessage.getReceiptMessage();
//			if(receiptMessage.isPresent()) {
//				SignalServiceReceiptMessage msg = receiptMessage.get();
//				attributes.put(ATTRIBUTE_RECEIPT, 			Boolean.toString(Boolean.TRUE));
//				attributes.put(ATTRIBUTE_RECEIPT_DELIVERY, 	Boolean.toString(msg.isDeliveryReceipt()));
//				attributes.put(ATTRIBUTE_RECEIPT_READ, 		Boolean.toString(msg.isReadReceipt()));
//			}
			
//			//Check for calls
//			if(decryptedMessage.getCallMessage().isPresent()) {
//				attributes.put(ATTRIBUTE_CALL_MESSAGE, 		Boolean.toString(Boolean.TRUE));
//			}

			//Check data message
			attributes.put(ATTRIBUTE_MESSAGE_VIEW_ONCE, Boolean.toString(message.isViewOnce()));
			attributes.put(ATTRIBUTE_MESSAGE, message.getMessage());
			
//			Optional<SignalServiceDataMessage> optionalDataMessage = decryptedMessage.getDataMessage();
//			if(optionalDataMessage.isPresent()){
//				SignalServiceDataMessage dataMessage = optionalDataMessage.get();
//
//				if(dataMessage.getReaction().isPresent()) {
//					Reaction reaction = dataMessage.getReaction().get();
//					attributes.put(ATTRIBUTE_MESSAGE_REACTION_EMOJI, reaction.getEmoji());
//					attributes.put(ATTRIBUTE_MESSAGE_REACTION_TARGET_AUTHOR, reaction.getTargetAuthor().getNumber().get());
//					attributes.put(ATTRIBUTE_MESSAGE_REACTION_TARGET_TIMESTAMP, Long.toString(reaction.getTargetSentTimestamp()));
//				}
//
//				if(dataMessage.getQuote().isPresent()) {
//					Quote quote = dataMessage.getQuote().get();
//					attributes.put(ATTRIBUTE_MESSAGE_QUOTE_ID, Long.toString(quote.getId()));
//				}
//
//				if(dataMessage.getGroupContext().isPresent()) {
//					SignalServiceGroupContext groupContext = dataMessage.getGroupContext().get();
//					String groupId = service.getGroupId(groupContext);
//					attributes.put(ATTRIBUTE_MESSAGE_GROUP_ID, groupId);
//
//					String groupTitle = service.getGroupTitle(groupContext);
//					attributes.put(ATTRIBUTE_MESSAGE_GROUP_TITLE, groupTitle);
//				}
//			}

			attributes.put(CoreAttributes.FILENAME.key(),	"Message from: " + attributes.get(ATTRIBUTE_SENDER_NUMBER));

			FlowFile flowFile = session.create();
			flowFile = session.putAllAttributes(flowFile, attributes);
			session.transfer(flowFile, SUCCESS);
		} catch (Throwable e) {
			onError(e);
			
			attributes.put(ATTRIBUTE_ERROR_MESSAGE, e.getMessage());
			
			FlowFile flowFile = session.create();
			flowFile = session.putAllAttributes(flowFile, attributes);
			session.transfer(flowFile, FAILURE);
		}
		session.commit();
	}

//	private void handleEnvelope(SignalServiceEnvelope envelope, SignalServiceContent decryptedMessage) {
//		if(envelope == null)
//			return;
//		
//		ProcessSessionFactory sessionFactory = sessionFactoryReference.get();
//		if(sessionFactory == null) {
//			getLogger().warn("Message received, but no ProcessSessionFactory is set so we cant handle the signal message");
//			return;
//		}
//
//		String senderNumber = decryptedMessage.getSender().getNumber().get();
//
//		//Check for receipt messages
//		Optional<SignalServiceReceiptMessage> opReceiptMessage = decryptedMessage.getReceiptMessage();
//		if((envelope.isReceipt() || opReceiptMessage.isPresent()) && ignoreReceipts) {
//			if(getLogger().isDebugEnabled()) getLogger().debug("Message is a receipt, but it should be ignored");
//			return;
//		}
//
//		//Check for typing messages
//		Optional<SignalServiceTypingMessage> opTypingMessage = decryptedMessage.getTypingMessage();
//		if(opTypingMessage.isPresent() && ignoreTyping) {
//			if(getLogger().isDebugEnabled()) getLogger().debug("Received typing message, but it should be ignored");
//			return;
//		}
//		
//		Optional<SignalServiceSyncMessage> opSyncMessage = decryptedMessage.getSyncMessage();
//		if(opSyncMessage.isPresent()) {
//			//Don't process sync messages, this is done by the manager
//			return;
//		}
//		
//		//Check the verified state of the sender identities
//		String verifiedValue = "";
//		try {
//			Map<IdentityKey, VerifiedState> senderNumberVerifiedStates = service.getIdentityState(senderNumber);
//			if(senderNumberVerifiedStates != null && senderNumberVerifiedStates.size() > 0) {
//				verifiedValue = senderNumberVerifiedStates.values().stream().map(Enum::toString).collect(Collectors.joining(", "));
//			}
//			
//			if(ignoreUnverifiedSenders) {
//				boolean isVerified = false;
//				
//				if(senderNumberVerifiedStates != null) {
//					isVerified = senderNumberVerifiedStates
//							.values()
//							.stream()
//							.filter(s -> VerifiedState.VERIFIED.equals(s))
//							.findAny()
//							.isPresent();
//				}
//				
//				if(!isVerified) {
//					if(getLogger().isWarnEnabled()) getLogger().warn("Sender identity is not verified, ignoring...");
//					return;
//				}
//			}
//		} catch (Throwable e1) {
//			getLogger().error(e1.getMessage(), e1);
//		}
//
//		if(getLogger().isDebugEnabled()) getLogger().debug("Received message");
//
//		ProcessSession session = sessionFactory.createSession();
//		
//		Map<String, String> attributes = new HashMap<>(20);
//		try {
//			attributes.put(ATTRIBUTE_SENDER_IDENTIFIED, Boolean.toString(!envelope.isUnidentifiedSender()));
//			attributes.put(ATTRIBUTE_RECEIPT, 			Boolean.toString(envelope.isReceipt()));
//
//			attributes.put(ATTRIBUTE_SENDER_NUMBER, 	senderNumber);
//			attributes.put(ATTRIBUTE_SENDER_VERIFIED, 	verifiedValue);
//			attributes.put(ATTRIBUTE_TIMESTAMP, 		Long.toString(decryptedMessage.getTimestamp()));
//			
//			if(opTypingMessage.isPresent()) {
//				SignalServiceTypingMessage typingMessage = opTypingMessage.get();
//				attributes.put(ATTRIBUTE_SENDER_TYPING_STARTED, 	Boolean.toString(typingMessage.isTypingStarted()));
//				attributes.put(ATTRIBUTE_SENDER_TYPING_STOPPED, 	Boolean.toString(typingMessage.isTypingStopped()));
//			}
//
//			//Check receipts
//			Optional<SignalServiceReceiptMessage> receiptMessage = decryptedMessage.getReceiptMessage();
//			if(receiptMessage.isPresent()) {
//				SignalServiceReceiptMessage msg = receiptMessage.get();
//				attributes.put(ATTRIBUTE_RECEIPT, 			Boolean.toString(Boolean.TRUE));
//				attributes.put(ATTRIBUTE_RECEIPT_DELIVERY, 	Boolean.toString(msg.isDeliveryReceipt()));
//				attributes.put(ATTRIBUTE_RECEIPT_READ, 		Boolean.toString(msg.isReadReceipt()));
//			}
//			
//			//Check for calls
//			if(decryptedMessage.getCallMessage().isPresent()) {
//				attributes.put(ATTRIBUTE_CALL_MESSAGE, 		Boolean.toString(Boolean.TRUE));
//			}
//
//			//Check data message
//			Optional<SignalServiceDataMessage> optionalDataMessage = decryptedMessage.getDataMessage();
//			if(optionalDataMessage.isPresent()){
//				SignalServiceDataMessage dataMessage = optionalDataMessage.get();
//				attributes.put(ATTRIBUTE_MESSAGE_VIEW_ONCE, Boolean.toString(dataMessage.isViewOnce()));
//				attributes.put(ATTRIBUTE_MESSAGE, dataMessage.getBody().or(""));
//
//				if(dataMessage.getReaction().isPresent()) {
//					Reaction reaction = dataMessage.getReaction().get();
//					attributes.put(ATTRIBUTE_MESSAGE_REACTION_EMOJI, reaction.getEmoji());
//					attributes.put(ATTRIBUTE_MESSAGE_REACTION_TARGET_AUTHOR, reaction.getTargetAuthor().getNumber().get());
//					attributes.put(ATTRIBUTE_MESSAGE_REACTION_TARGET_TIMESTAMP, Long.toString(reaction.getTargetSentTimestamp()));
//				}
//
//				if(dataMessage.getQuote().isPresent()) {
//					Quote quote = dataMessage.getQuote().get();
//					attributes.put(ATTRIBUTE_MESSAGE_QUOTE_ID, Long.toString(quote.getId()));
//				}
//
//				if(dataMessage.getGroupContext().isPresent()) {
//					SignalServiceGroupContext groupContext = dataMessage.getGroupContext().get();
//					String groupId = service.getGroupId(groupContext);
//					attributes.put(ATTRIBUTE_MESSAGE_GROUP_ID, groupId);
//
//					String groupTitle = service.getGroupTitle(groupContext);
//					attributes.put(ATTRIBUTE_MESSAGE_GROUP_TITLE, groupTitle);
//				}
//			}
//
//			attributes.put(CoreAttributes.FILENAME.key(),	"Message from: " + attributes.get(ATTRIBUTE_SENDER_NUMBER));
//
//			FlowFile flowFile = session.create();
//			flowFile = session.putAllAttributes(flowFile, attributes);
//			session.transfer(flowFile, SUCCESS);
//		} catch (Throwable e) {
//			onError(e);
//			
//			attributes.put(ATTRIBUTE_ERROR_MESSAGE, e.getMessage());
//			
//			FlowFile flowFile = session.create();
//			flowFile = session.putAllAttributes(flowFile, attributes);
//			session.transfer(flowFile, FAILURE);
//		}
//		session.commit();
//	}
}
