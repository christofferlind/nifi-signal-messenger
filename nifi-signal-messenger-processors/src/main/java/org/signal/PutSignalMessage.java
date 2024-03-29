package org.signal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.nifi.annotation.behavior.ReadsAttribute;
import org.apache.nifi.annotation.behavior.ReadsAttributes;
import org.apache.nifi.annotation.behavior.WritesAttribute;
import org.apache.nifi.annotation.behavior.WritesAttributes;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.SeeAlso;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.flowfile.attributes.CoreAttributes;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;
import org.signal.model.SignalAttachment;
import org.signal.model.SignalQuote;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

@Tags({ "Signal", "Put", "Message", "Send" })
@CapabilityDescription("Sends a message on Signal, with or without attachment")
@SeeAlso({})
@ReadsAttributes({
	@ReadsAttribute(attribute="mime.type", description="If attachment is set to 'true', then this attribute is read and set as the mime type for the attachment"),
	@ReadsAttribute(attribute="filename", description="If attachment is set to 'true', then this attribute is read and set as the file name for the attachment")
})
@WritesAttributes({@WritesAttribute(attribute=Constants.ATTRIBUTE_TIMESTAMP, description="Timestamp of the sent message")})
public class PutSignalMessage extends AbstractSignalSenderProcessor {

	public static final PropertyDescriptor PROP_MESSAGE_CONTENT = new PropertyDescriptor
			.Builder().name("Content")
			.displayName("Message")
			.description("Message content. If this attribute is empty then the flowfile content will be used instead")
			.required(false)
			.addValidator(StandardValidators.ATTRIBUTE_EXPRESSION_LANGUAGE_VALIDATOR)
			.expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
			.build();

	public static final PropertyDescriptor PROP_ATTACHMENT = new PropertyDescriptor
			.Builder().name("Attachment")
			.displayName("Flowfile content as attachment")
			.description("If set to 'true' then the flowfile content is used as attachment")
			.required(false)
			.defaultValue(Boolean.FALSE.toString())
			.addValidator(StandardValidators.ATTRIBUTE_EXPRESSION_LANGUAGE_VALIDATOR)
			.expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
			.build();

	public static final PropertyDescriptor PROP_MESSAGE_QUOTE = new PropertyDescriptor
			.Builder().name("Quote")
			.displayName("Quote")
			.description("")
			.required(false)
			.defaultValue(Boolean.FALSE.toString())
			.addValidator(StandardValidators.ATTRIBUTE_EXPRESSION_LANGUAGE_VALIDATOR)
			.expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
			.build();

	public static final PropertyDescriptor PROP_MESSAGE_QUOTE_TIMESTAMP_ATTRIBUTE = new PropertyDescriptor
			.Builder().name("QuoteTimestampAttribute")
			.displayName("Quote timestamp")
			.description("Attribute on the flowfile that contains the message timestamp to quote")
			.required(false)
			.defaultValue(Constants.ATTRIBUTE_TIMESTAMP)
			.addValidator(StandardValidators.ATTRIBUTE_EXPRESSION_LANGUAGE_VALIDATOR)
			.expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
			.build();

	public static final PropertyDescriptor PROP_MESSAGE_QUOTE_AUTHOR_ATTRIBUTE = new PropertyDescriptor
			.Builder().name("QuoteAuthorAttribute")
			.displayName("Quote author")
			.description("Attribute on the flowfile that contains the author number to quote")
			.required(false)
			.defaultValue(Constants.ATTRIBUTE_SENDER_NUMBER)
			.addValidator(StandardValidators.ATTRIBUTE_EXPRESSION_LANGUAGE_VALIDATOR)
			.expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
			.build();



	@Override
	protected void init(final ProcessorInitializationContext context) {
		super.init(context);
		descriptors.add(PROP_MESSAGE_CONTENT);
		descriptors.add(PROP_ATTACHMENT);
		descriptors.add(PROP_MESSAGE_QUOTE);
		descriptors.add(PROP_MESSAGE_QUOTE_TIMESTAMP_ATTRIBUTE);
		descriptors.add(PROP_MESSAGE_QUOTE_AUTHOR_ATTRIBUTE);
	}
	
	@Override
	public void onTrigger(final ProcessContext context, final ProcessSession session) throws ProcessException {
		FlowFile flowFile = session.get();
		if ( flowFile == null ) {
			return;
		}

		try {
			SignalControllerService signalService = getSignalService(context);
			String account = getAccountNumber(context, flowFile);
			
			String messageContent = context.getProperty(PROP_MESSAGE_CONTENT).evaluateAttributeExpressions(flowFile).getValue();
			
			String useAttachmentString = context.getProperty(PROP_ATTACHMENT).evaluateAttributeExpressions(flowFile).getValue();
			boolean useAttachment = "true".equalsIgnoreCase(useAttachmentString);
			
			String useQuoteString = context.getProperty(PROP_MESSAGE_QUOTE).evaluateAttributeExpressions(flowFile).getValue();
			boolean useQuote = "true".equalsIgnoreCase(useQuoteString);
			
			SignalQuote quote = null;
			
			getLogger().debug("Using attachments: " + useAttachment);
			
			Optional<List<String>> groups = getList(context, flowFile, PROP_GROUPS);
			Optional<List<String>> recipients = getList(context, flowFile, PROP_RECIPIENTS);
			
			if(groups.isEmpty() && recipients.isEmpty())
				throw new IllegalStateException(Constants.MSG_MISSING_RECIPIENT_AND_GROUP);
				
			SignalAttachment attachment = null;

			if(useAttachment) {
				attachment = loadFlowFileContentAsBase64(session, flowFile);
			} else {
				if(messageContent == null || messageContent.isEmpty()) {
					getLogger().info("Message is empty, using content as message");
					messageContent = loadFlowFileContentAsMessageContent(session, flowFile);
				}
			}
			
			if(useQuote) {
				quote = createQuote(context, flowFile, messageContent);
			}

			JsonElement result = signalService.sendMessage(account, 
															messageContent, 
															recipients, 
															groups, 
															Optional.ofNullable(quote), 
															Optional.ofNullable(attachment));
			
			if(getLogger().isDebugEnabled())
				getLogger().debug(result.toString());

			if(result.isJsonObject()) {
				JsonObject object = result.getAsJsonObject();
				if(object.has("timestamp")) {
					String timestampString = object.get("timestamp").getAsString();
					flowFile = session.putAttribute(flowFile, Constants.ATTRIBUTE_TIMESTAMP, timestampString);
				}
			}
			
			flowFile = session.putAttribute(flowFile, "signal.send.failed", Boolean.toString(Boolean.FALSE));
			session.transfer(flowFile, SUCCESS);
		} catch(Throwable e) {
			getLogger().error(e.getMessage(), e);
			transferToFailureWithMessage(session, flowFile, e.getMessage());
		}
	}

	private SignalQuote createQuote(final ProcessContext context, FlowFile flowFile, String messageContent) {
		SignalQuote quote = null;
		
		try {
			String attrTimestamp = context.getProperty(PROP_MESSAGE_QUOTE_TIMESTAMP_ATTRIBUTE).evaluateAttributeExpressions(flowFile).getValue();
			long timestamp = Long.decode(flowFile.getAttribute(attrTimestamp));

			String attrAuthor = context.getProperty(PROP_MESSAGE_QUOTE_AUTHOR_ATTRIBUTE).evaluateAttributeExpressions(flowFile).getValue();
			String author = flowFile.getAttribute(attrAuthor);

			quote = new SignalQuote(timestamp, author, messageContent, null);
		} catch (Exception e) {
			logError(e);
		}

		return quote;
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

			session.read(flowFile, inputStream -> Constants.copy(inputStream, base64Output));
			
			if(log.isDebugEnabled()) log.debug("Flowfile content read");
			
			String content = outputStream.toString(StandardCharsets.UTF_8);
			return new SignalAttachment(mimeType, filename, content);
		}
	}

	private String loadFlowFileContentAsMessageContent(final ProcessSession session, FlowFile flowFile) throws IOException {
	    try(	ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
	    		InputStream inputStream = session.read(flowFile)){
	    	Constants.copy(inputStream, outputStream);
	    	return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
	    }
	}
}
