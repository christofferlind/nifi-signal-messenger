package org.signal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.nifi.annotation.behavior.ReadsAttributes;
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

@Tags({ "Signal", "Delete", "Message", "Remove" })
@CapabilityDescription("Remote delete a message on Signal")
@SeeAlso({})
@ReadsAttributes({
//	@ReadsAttribute(attribute="mime.type", description="If attachment is set to 'true', then this attribute is read and set as the mime type for the attachment"),
//	@ReadsAttribute(attribute="filename", description="If attachment is set to 'true', then this attribute is read and set as the file name for the attachment")
})
//@WritesAttributes({@WritesAttribute(attribute="", description="")})
public class DeleteSignalMessage extends AbstractProcessor {

	public static final PropertyDescriptor SIGNAL_SERVICE = new PropertyDescriptor
			.Builder().name("SignalService")
			.displayName("Signal Service")
			.description("The signal service to use")
			.required(true)
			.identifiesControllerService(SignalControllerService.class)
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

	public static final PropertyDescriptor GROUPS = new PropertyDescriptor
			.Builder().name("groups")
			.displayName("Groups")
			.description("You can either specify the group title or the base64 encoded id (see: " + Constants.ATTRIBUTE_MESSAGE_GROUP_ID + "and" + Constants.ATTRIBUTE_MESSAGE_GROUP_TITLE + "). Multiple groups can be provided using comma (,)")
			.required(false)
			.addValidator(StandardValidators.ATTRIBUTE_EXPRESSION_LANGUAGE_VALIDATOR)
			.expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
			.build();

	public static final PropertyDescriptor PROP_TIMESTAMP = new PropertyDescriptor
			.Builder().name("MessageTimestamp")
			.displayName("Timestamp")
			.description("Timestamp of the message that should be deleted")
			.required(true)
			.defaultValue("${" + Constants.ATTRIBUTE_TIMESTAMP + "}")
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
		descriptors.add(PROP_TIMESTAMP);
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
		
		String timestampString = context.getProperty(PROP_TIMESTAMP).evaluateAttributeExpressions(flowFile).getValue();

		List<String> groups = Constants.getCommaSeparatedList(groupsString);
		List<String> recipients = Constants.getCommaSeparatedList(recipientsString);

		if(groups.isEmpty() && recipients.isEmpty())
			throw new IllegalStateException(Constants.MSG_MISSING_RECIPIENT_AND_GROUP);

		try {
			signalService.deleteMessage(source, 
					Optional.of(recipients), 
					Optional.of(groups), 
					Long.decode(timestampString));
			
			session.transfer(flowFile, SUCCESS);
		} catch (Exception e) {
			getLogger().error(e.getMessage(), e);
			flowFile = session.putAllAttributes(flowFile, Map.of(Constants.ATTRIBUTE_ERROR_MESSAGE, e.getMessage()));
			session.transfer(flowFile, FAILURE);
		}
	}
	
}
