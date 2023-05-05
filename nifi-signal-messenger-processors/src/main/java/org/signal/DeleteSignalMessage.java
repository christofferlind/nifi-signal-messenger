package org.signal;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.nifi.annotation.behavior.ReadsAttributes;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.SeeAlso;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;

import com.google.gson.JsonElement;

@Tags({ "Signal", "Delete", "Message", "Remove" })
@CapabilityDescription("Remote delete a message on Signal")
@SeeAlso({})
@ReadsAttributes({
//	@ReadsAttribute(attribute="mime.type", description="If attachment is set to 'true', then this attribute is read and set as the mime type for the attachment"),
//	@ReadsAttribute(attribute="filename", description="If attachment is set to 'true', then this attribute is read and set as the file name for the attachment")
})
//@WritesAttributes({@WritesAttribute(attribute="", description="")})
public class DeleteSignalMessage extends AbstractSignalSenderProcessor {

	public static final PropertyDescriptor PROP_TIMESTAMP = new PropertyDescriptor
			.Builder().name("MessageTimestamp")
			.displayName("Timestamp")
			.description("Timestamp of the message that should be deleted")
			.required(true)
			.defaultValue("${" + Constants.ATTRIBUTE_TIMESTAMP + "}")
			.addValidator(StandardValidators.ATTRIBUTE_EXPRESSION_LANGUAGE_VALIDATOR)
			.expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
			.build();

	@Override
	protected void init(final ProcessorInitializationContext context) {
		super.init(context);
		descriptors.add(PROP_TIMESTAMP);
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
			
			String timestampString = context.getProperty(PROP_TIMESTAMP).evaluateAttributeExpressions(flowFile).getValue();
			
			Optional<List<String>> groups = getList(context, flowFile, PROP_GROUPS);
			Optional<List<String>> recipients = getList(context, flowFile, PROP_RECIPIENTS);
			
			if(groups.isEmpty() && recipients.isEmpty())
				throw new IllegalStateException(Constants.MSG_MISSING_RECIPIENT_AND_GROUP);
			
			JsonElement result = signalService.deleteMessage(account, 
															recipients, 
															groups, 
															Long.decode(timestampString));
			if(getLogger().isDebugEnabled())
				getLogger().debug(result.toString());
			
			session.transfer(flowFile, SUCCESS);
		} catch (Exception e) {
			getLogger().error(e.getMessage(), e);
			flowFile = session.putAllAttributes(flowFile, Map.of(Constants.ATTRIBUTE_ERROR_MESSAGE, e.getMessage()));
			session.transfer(flowFile, FAILURE);
		}
	}
	
}
