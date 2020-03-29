package org.signal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.annotation.behavior.InputRequirement.Requirement;
import org.apache.nifi.annotation.behavior.TriggerSerially;
import org.apache.nifi.annotation.behavior.WritesAttribute;
import org.apache.nifi.annotation.behavior.WritesAttributes;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.SeeAlso;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.annotation.lifecycle.OnUnscheduled;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.AbstractSessionFactoryProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessSessionFactory;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.messages.SignalServiceReceiptMessage;

@InputRequirement(Requirement.INPUT_FORBIDDEN)
@CapabilityDescription("Consumes signal messages. "
        + "The message of each received sinal message are written as contents of the FlowFile")
@Tags({ "Signal", "Get", "Ingest", "Ingress", "Message", "Consume" })
@TriggerSerially
@SeeAlso({PutSignalMessage.class})
@WritesAttributes({
	@WritesAttribute(attribute=ConsumeSignalMessage.ATTRIBUTE_RECEIPT, description="Values true or false depending on if the message is a receipt or not"),
	@WritesAttribute(attribute=ConsumeSignalMessage.ATTRIBUTE_MESSAGE, description="The content of the message sent"),
	@WritesAttribute(attribute=ConsumeSignalMessage.ATTRIBUTE_MESSAGE_VIEW_ONCE, description="Values true or false depending on if the message is a view once message"),
	@WritesAttribute(attribute=ConsumeSignalMessage.ATTRIBUTE_TIMESTAMP, description="Time when the message was sent"),
	@WritesAttribute(attribute=ConsumeSignalMessage.ATTRIBUTE_RECEIVING_NUMBER, description="The number that received the message (this is the same as the one in the controller used)"),
	@WritesAttribute(attribute=ConsumeSignalMessage.ATTRIBUTE_SENDER_NUMBER, description="The number that sent the message"),
	@WritesAttribute(attribute=ConsumeSignalMessage.ATTRIBUTE_SENDER_IDENTIFIED, description="If the number is verified"),
	@WritesAttribute(attribute=ConsumeSignalMessage.ATTRIBUTE_ERROR_MESSAGE, description="If an error occurs, the detailed error message will be put in this attribute")
	})
public class ConsumeSignalMessage extends AbstractSessionFactoryProcessor {

	public static final String ATTRIBUTE_RECEIPT = 						"signal.receipt";
	public  static final String ATTRIBUTE_MESSAGE = 					"signal.message";
	public  static final String ATTRIBUTE_MESSAGE_VIEW_ONCE = 			"signal.message.viewonce";
	public  static final String ATTRIBUTE_TIMESTAMP = 					"signal.timestamp";
	public  static final String ATTRIBUTE_RECEIVING_NUMBER = 			"signal.receiving.number";
	public  static final String ATTRIBUTE_SENDER_NUMBER = 				"signal.sender.number";
	public  static final String ATTRIBUTE_SENDER_IDENTIFIED = 			"signal.sender.identified";
	
	public  static final String ATTRIBUTE_ERROR_MESSAGE = 				"signal.error.message";

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
    
	private AtomicReference<ProcessSessionFactory> sessionFactoryReference = new AtomicReference<>();
	private SignalMessageReceiverThread threadListen;

    @Override
    protected void init(final ProcessorInitializationContext context) {
        final List<PropertyDescriptor> descriptors = new ArrayList<PropertyDescriptor>();
        descriptors.add(SIGNAL_SERVICE);
        descriptors.add(IGNORE_RECEIPT_MESSAGE);
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
    public void startWebSocket(ProcessContext context) {
    	SignalControllerService service = context.getProperty(SIGNAL_SERVICE).asControllerService(SignalControllerService.class);
    	boolean ignoreReceipts =  context.getProperty(IGNORE_RECEIPT_MESSAGE).asBoolean();
    	ComponentLog logger = getLogger();

    	try {
			SignalServiceMessageReceiver messageReceiver = service.getMessageReceiver();

			threadListen = new SignalMessageReceiverThread(
								service,
								messageReceiver,
								envelope -> handleEnvelope(service, ignoreReceipts, envelope));
			
			threadListen.setWaitUntil(() -> sessionFactoryReference.get() != null);
			threadListen.setOnError(e -> logger.error(e.getMessage(), e));
			threadListen.start();
    	} catch (Throwable e) {
    		logger.error(e.getMessage(), e);
		}
    }
    
    @OnUnscheduled
    public void unschedule() {
    	if(threadListen != null) {
    		threadListen.interrupt();
    		threadListen = null;
		}
    }
    
	@Override
	public void onTrigger(ProcessContext context, ProcessSessionFactory sessionFactory) throws ProcessException {
        sessionFactoryReference.compareAndSet(null, sessionFactory);
        context.yield();
	}

	private void handleEnvelope(SignalControllerService service, boolean ignoreReceipts, SignalServiceEnvelope envelope) {
		ProcessSessionFactory sessionFactory = sessionFactoryReference.get();
		if(sessionFactory == null)
			return;
		
		if(envelope.isReceipt() && ignoreReceipts)
			return;

		ProcessSession session = sessionFactory.createSession();
		FlowFile flowFile = session.create();
		
		Map<String, String> attributes = new HashMap<>(7);
		try {
			attributes.put(ATTRIBUTE_SENDER_IDENTIFIED, Boolean.toString(!envelope.isUnidentifiedSender()));
			attributes.put(ATTRIBUTE_SENDER_NUMBER, 	envelope.getSourceAddress().getNumber());
			attributes.put(ATTRIBUTE_TIMESTAMP, 		Long.toString(envelope.getTimestamp()));
			attributes.put(ATTRIBUTE_RECEIPT, 			Boolean.toString(Boolean.FALSE));

			SignalServiceContent decryptedMessage = service.decryptMessage(envelope);
			String senderNumber = decryptedMessage.getSender();
			attributes.put(ATTRIBUTE_SENDER_NUMBER, senderNumber);

			Optional<SignalServiceReceiptMessage> receiptMessage = decryptedMessage.getReceiptMessage();
			if(envelope.isReceipt() || receiptMessage.isPresent()) {
				attributes.put(ATTRIBUTE_RECEIPT, Boolean.toString(Boolean.TRUE));
			} else {
				attributes.put(ATTRIBUTE_MESSAGE_VIEW_ONCE, Boolean.toString(Boolean.FALSE));

				Optional<SignalServiceDataMessage> optionalDataMessage = decryptedMessage.getDataMessage();
				if(optionalDataMessage.isPresent()) {
					SignalServiceDataMessage dataMessage = optionalDataMessage.get();
					attributes.put(ATTRIBUTE_MESSAGE, dataMessage.getBody().or(""));

					boolean viewOnce = dataMessage.isViewOnce();
					attributes.put(ATTRIBUTE_MESSAGE_VIEW_ONCE, Boolean.toString(viewOnce));
				}
			}

			attributes.put("filename",	"Message from: " + attributes.get(ATTRIBUTE_SENDER_NUMBER));
			flowFile = session.putAllAttributes(flowFile, attributes);
			session.transfer(flowFile, SUCCESS);
		} catch (Throwable e) {
			getLogger().error(e.getMessage(), e);
			
			attributes.put(ATTRIBUTE_ERROR_MESSAGE, e.getMessage());
			
			flowFile = session.putAllAttributes(flowFile, attributes);
			session.transfer(flowFile, FAILURE);
		}
		session.commit();
	}
}
