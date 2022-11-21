package org.signal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.nifi.annotation.behavior.ReadsAttribute;
import org.apache.nifi.annotation.behavior.ReadsAttributes;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.SeeAlso;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.flowfile.attributes.CoreAttributes;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;
import org.signal.model.SignalAttachment;

@Tags({ "Signal", "Put", "Message", "Send" })
@CapabilityDescription("Sends a message on Signal, with or without attachment")
@SeeAlso({})
@ReadsAttributes({
	@ReadsAttribute(attribute="mime.type", description="If attachment is set to 'true', then this attribute is read and set as the mime type for the attachment"),
	@ReadsAttribute(attribute="filename", description="If attachment is set to 'true', then this attribute is read and set as the file name for the attachment")
})
//@WritesAttributes({@WritesAttribute(attribute="", description="")})
public class PutSignalMessage extends AbstractProcessor {

	public static final PropertyDescriptor SIGNAL_SERVICE = new PropertyDescriptor
			.Builder().name("SignalService")
			.displayName("Signal Service")
			.description("The signal service to use")
			.required(true)
			.identifiesControllerService(SignalControllerService.class)
			.build();

	public static final PropertyDescriptor GROUPS = new PropertyDescriptor
			.Builder().name("groups")
			.displayName("Groups")
			.description("You can either specify the group title or the base64 encoded id. Multiple groups can be provided using comma (,)")
			.required(false)
			.addValidator(StandardValidators.ATTRIBUTE_EXPRESSION_LANGUAGE_VALIDATOR)
			.expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
			.build();

	public static final PropertyDescriptor SOURCE = new PropertyDescriptor
			.Builder().name("Source")
			.displayName("Source")
			.description("From which number to send from")
			.required(false)
			.defaultValue("${" + Constants.ATTRIBUTE_RECEIVING_NUMBER + "}")
			.addValidator(StandardValidators.ATTRIBUTE_EXPRESSION_LANGUAGE_VALIDATOR)
			.expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
			.build();

	public static final PropertyDescriptor RECIPIENTS = new PropertyDescriptor
			.Builder().name("Recipients")
			.displayName("Recipients")
			.description("Multiple numbers can be provided using comma (,)")
			.required(false)
			.addValidator(StandardValidators.ATTRIBUTE_EXPRESSION_LANGUAGE_VALIDATOR)
			.expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
			.build();

	public static final PropertyDescriptor MESSAGE_CONTENT = new PropertyDescriptor
			.Builder().name("Content")
			.displayName("Message")
			.description("Message content. If this attribute is empty then the flowfile content will be used instead")
			.required(false)
			.addValidator(StandardValidators.ATTRIBUTE_EXPRESSION_LANGUAGE_VALIDATOR)
			.expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
			.build();

	public static final PropertyDescriptor ATTACHMENT = new PropertyDescriptor
			.Builder().name("Attachment")
			.displayName("Flowfile content as attachment")
			.description("If set to 'true' then the flowfile content is used as attachment")
			.required(false)
			.defaultValue(Boolean.FALSE.toString())
			.addValidator(StandardValidators.ATTRIBUTE_EXPRESSION_LANGUAGE_VALIDATOR)
			.expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
			.build();

	public static final Relationship SUCCESS = new Relationship.Builder()
			.name("success")
			.description("Successful send")
			.build();

	public static final Relationship FAILURE = new Relationship.Builder()
			.name("failure")
			.description("Failed sends will be routed to this relationship. An extra flowfile per recipient will also be sent with additional details in the flowfile attributes")
			.build();

	private List<PropertyDescriptor> descriptors;

	private Set<Relationship> relationships;

	@Override
	protected void init(final ProcessorInitializationContext context) {
		final List<PropertyDescriptor> descriptors = new ArrayList<PropertyDescriptor>();
		descriptors.add(SIGNAL_SERVICE);
		descriptors.add(SOURCE);
		descriptors.add(RECIPIENTS);
		descriptors.add(GROUPS);
		descriptors.add(MESSAGE_CONTENT);
		descriptors.add(ATTACHMENT);
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
		String groupsString = context.getProperty(GROUPS).evaluateAttributeExpressions(flowFile).getValue();
		String source = context.getProperty(SOURCE).evaluateAttributeExpressions(flowFile).getValue();
		String recipientsString = context.getProperty(RECIPIENTS).evaluateAttributeExpressions(flowFile).getValue();
		String messageContent = context.getProperty(MESSAGE_CONTENT).evaluateAttributeExpressions(flowFile).getValue();

		String useAttachmentString = context.getProperty(ATTACHMENT).evaluateAttributeExpressions(flowFile).getValue();
		boolean useAttachment = "true".equalsIgnoreCase(useAttachmentString);

		getLogger().debug("Using attachments: " + useAttachment);

		try {
			List<String> groups = getCommaSeparatedList(groupsString);
			List<String> recipients = getCommaSeparatedList(recipientsString);
			
			if(groups.isEmpty() && recipients.isEmpty())
				throw new IllegalStateException("Both groups and recipients can not be empty. At least one must be set");
				
			SignalAttachment attachment = null;

			if(useAttachment) {
				attachment = loadFlowFileContentAsBase64(session, flowFile);
			} else {
				if(messageContent == null || messageContent.isEmpty()) {
					try {
						getLogger().info("Message is empty, using content as message");
						messageContent = loadFlowFileContentAsMessageContent(session, flowFile);
					} catch (Throwable e) {
						getLogger().error(e.getMessage(), e);
						session.transfer(flowFile, FAILURE);
						return;
					}
				}
			}

			//Try send to all groups
			if(groups.size() > 0) {
				signalService.sendGroupMessage(source, groups, messageContent, attachment);
			}

			//Try send to all recipients
			if(recipients.size() > 0) {
				signalService.sendMessage(source, recipients, messageContent, attachment);
			}
			
			session.putAttribute(flowFile, "signal.send.failed", Boolean.toString(true));
			session.transfer(flowFile, SUCCESS);
		} catch(Throwable e) {
			getLogger().error(e.getMessage(), e);
			transferToFailureWithMessage(session, flowFile, e.getMessage());
		}
	}

	private void transferToFailureWithMessage(ProcessSession session, FlowFile flowFile, String message) {
		if(message == null)
			message = "";
		
		Map<String, String> attributes = Map.of(
				Constants.ATTRIBUTE_ERROR_MESSAGE, message,
				Constants.ATTRIBUTE_ERROR_MESSAGE_SEND, message
				);
		
		session.transfer(session.putAllAttributes(flowFile, attributes), FAILURE);		
	}

	private SignalAttachment loadFlowFileContentAsBase64(ProcessSession session, FlowFile flowFile) throws IOException {
		String mimeType = flowFile.getAttribute(CoreAttributes.MIME_TYPE.key());
		String filename = flowFile.getAttribute(CoreAttributes.FILENAME.key());
		
		if(mimeType == null || mimeType.isEmpty()) {
			throw new NullPointerException("mime.type attribute can not be empty");
		}

		ComponentLog log = getLogger();
		if(log.isDebugEnabled()) log.debug("Mime type: " + mimeType);

		try(ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
				OutputStream base64Output = Base64.getEncoder().wrap(outputStream)){

			session.read(flowFile, inputStream -> copy(inputStream, base64Output));
			
			if(log.isDebugEnabled()) log.debug("Flowfile content read");
			
			String content = outputStream.toString(StandardCharsets.UTF_8);
			return new SignalAttachment(mimeType, filename, content);
		}
	}

	public static final List<String> getCommaSeparatedList(String string) {
		if(string == null)
			return Collections.emptyList();
		
		String[] split = string.split(",");
		List<String> recipients = new ArrayList<>(split.length);
		for (String element : split) {
			String trimmed = element.trim();
			if(trimmed.isEmpty())
				continue;

			recipients.add(trimmed);
		}
		return recipients;
	}

	private String loadFlowFileContentAsMessageContent(final ProcessSession session, FlowFile flowFile) throws IOException {
	    try(	ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
	    		InputStream inputStream = session.read(flowFile)){
	    	copy(inputStream, outputStream);
	    	return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
	    }
	}
	private static final int BUF_SIZE = 0x1000; // 4K

	public static long copy(InputStream from, OutputStream to) throws IOException {
		Objects.nonNull(from);
		Objects.nonNull(to);
		byte[] buf = new byte[BUF_SIZE];
		long total = 0;
		while (true) {
			int r = from.read(buf);
			if (r == -1) {
				break;
			}
			to.write(buf, 0, r);
			total += r;
		}
		return total;
	}
}
