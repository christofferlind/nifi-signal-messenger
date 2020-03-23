package org.signal;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.nifi.annotation.behavior.ReadsAttribute;
import org.apache.nifi.annotation.behavior.ReadsAttributes;
import org.apache.nifi.annotation.behavior.WritesAttribute;
import org.apache.nifi.annotation.behavior.WritesAttributes;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.SeeAlso;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;

@Tags({ "Signal", "Put", "Message", "Send" })
@CapabilityDescription("Sends a message on Signal")
@SeeAlso({})
@ReadsAttributes({@ReadsAttribute(attribute="", description="")})
@WritesAttributes({@WritesAttribute(attribute="", description="")})
public class PutSignalMessage extends AbstractProcessor {

    public static final PropertyDescriptor SIGNAL_SERVICE = new PropertyDescriptor
            .Builder().name("SignalService")
            .displayName("Signal Service")
            .description("The signal service to use")
            .required(true)
            .identifiesControllerService(SignalControllerService.class)
            .build();

    public static final PropertyDescriptor RECIPIENTS = new PropertyDescriptor
            .Builder().name("Recipients")
            .displayName("Recipients")
            .description("Whom to send the data. Multiple numbers are separeted with comma (,)")
            .required(true)
            .addValidator(StandardValidators.ATTRIBUTE_EXPRESSION_LANGUAGE_VALIDATOR)
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .build();

    public static final PropertyDescriptor MESSAGE_CONTENT = new PropertyDescriptor
            .Builder().name("Content")
            .displayName("Content")
            .description("Message content. If this attribute is empty then the flowfile content will be used instead")
            .required(false)
            .addValidator(StandardValidators.ATTRIBUTE_EXPRESSION_LANGUAGE_VALIDATOR)
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .build();

//    public static final PropertyDescriptor ATTACHMENT = new PropertyDescriptor
//            .Builder().name("Attachment")
//            .displayName("Attachment")
//            .description("Use the flowfile content as attachment. If this is set to 'true' then the content of the message will be taken from content property")
//            .required(false)
//            .allowableValues(Boolean.FALSE.toString(), Boolean.TRUE.toString())
//            .defaultValue(Boolean.FALSE.toString())
//            .addValidator(StandardValidators.BOOLEAN_VALIDATOR)
//            .build();

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

    @Override
    protected void init(final ProcessorInitializationContext context) {
        final List<PropertyDescriptor> descriptors = new ArrayList<PropertyDescriptor>();
        descriptors.add(SIGNAL_SERVICE);
        descriptors.add(RECIPIENTS);
        descriptors.add(MESSAGE_CONTENT);
//        descriptors.add(ATTACHMENT);
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
    public void onScheduled(final ProcessContext context) {

    }

    @Override
    public void onTrigger(final ProcessContext context, final ProcessSession session) throws ProcessException {
        FlowFile flowFile = session.get();
        if ( flowFile == null ) {
            return;
        }
        
        SignalControllerService signalService = context.getProperty(SIGNAL_SERVICE).asControllerService(SignalControllerService.class);
        String recipient = context.getProperty(RECIPIENTS).evaluateAttributeExpressions(flowFile).getValue();
        String messageContent = context.getProperty(MESSAGE_CONTENT).evaluateAttributeExpressions(flowFile).getValue();

//TODO: attachment implement
//        boolean useAttachment = Boolean.valueOf(context.getProperty(ATTACHMENT).getValue());

//        if(useAttachment) {
//        } else {
			if(messageContent == null || messageContent.isEmpty()) {
				try {
					StringBuilder content = loadFlowFileContentAsMessageContent(session, flowFile);
					messageContent = content.toString();
				} catch (Throwable e) {
					getLogger().error(e.getMessage(), e);
					session.transfer(flowFile, FAILURE);
					return;
				}
			}
//		}

		try {
			List<String> recipients = getCommaSeparatedRecipients(recipient);
			signalService.sendMessage(recipients, messageContent);
			session.transfer(flowFile, SUCCESS);
		} catch(Throwable e) {
			getLogger().error(e.getMessage(), e);
			session.transfer(flowFile, FAILURE);
		}
    }

	private List<String> getCommaSeparatedRecipients(String recipient) {
		String[] split = recipient.split(",");
		List<String> recipients = new ArrayList<>(split.length);
		for (String string : split) {
			String trimmed = string.trim();
			if(trimmed.isEmpty())
				continue;
			
			recipients.add(trimmed);
		}
		return recipients;
	}

	private StringBuilder loadFlowFileContentAsMessageContent(final ProcessSession session, FlowFile flowFile) {
		StringBuilder content = new StringBuilder();
        
        session.read(flowFile, inputstream -> {
        	try(
        			InputStreamReader streamReader = new InputStreamReader(inputstream, Charset.forName("UTF8"));
        			BufferedReader bufferedReader = new BufferedReader(streamReader);
        			){
        		String line = null;
        		while((line = bufferedReader.readLine()) != null) {
        			content.append(line).append("\n");
        		}
        	}
        });
		return content;
	}
}
