package org.signal;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;

public abstract class AbstractSignalSenderProcessor extends AbstractProcessor {

	public static final PropertyDescriptor PROP_SIGNAL_SERVICE = new PropertyDescriptor
					.Builder().name("SignalService")
					.displayName("Signal Service")
					.description("The signal service to use")
					.required(true)
					.identifiesControllerService(SignalControllerService.class)
					.build();
	
	public static final PropertyDescriptor PROP_ACCOUNT = new PropertyDescriptor
				.Builder().name("Account")
				.displayName("Account")
				.description("From which number to send from")
				.required(false)
				.defaultValue("${" + Constants.ATTRIBUTE_RECEIVING_NUMBER + "}")
				.addValidator(StandardValidators.ATTRIBUTE_EXPRESSION_LANGUAGE_VALIDATOR)
				.expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
				.build();
	
	public static final PropertyDescriptor PROP_RECIPIENTS = new PropertyDescriptor
				.Builder().name("Recipients")
				.displayName("Recipients")
				.description("Multiple numbers can be provided using comma (,)")
				.required(false)
				.addValidator(StandardValidators.ATTRIBUTE_EXPRESSION_LANGUAGE_VALIDATOR)
				.expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
				.build();
	
	public static final PropertyDescriptor PROP_GROUPS = new PropertyDescriptor
				.Builder().name("groups")
				.displayName("Groups")
				.description("When this property is set, the recipient property is ignored. The recipients will be the specified group members. You can either specify the group title or the base64 encoded id (see: " + Constants.ATTRIBUTE_MESSAGE_GROUP_ID + "and" + Constants.ATTRIBUTE_MESSAGE_GROUP_TITLE + "). Multiple groups can be provided using comma (,)")
				.required(false)
				.addValidator(StandardValidators.ATTRIBUTE_EXPRESSION_LANGUAGE_VALIDATOR)
				.expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
				.build();
	
	public static final Relationship SUCCESS = new Relationship.Builder()
					.name("success")
					.description("Successful send")
					.build();
	
	public static final Relationship FAILURE = new Relationship.Builder()
					.name("failure")
					.description("Unsuccessful send")
					.build();
	
	protected List<PropertyDescriptor> descriptors = new ArrayList<>(10);
	protected Set<Relationship> relationships = new HashSet<>(3);
	
	@Override
	protected void init(ProcessorInitializationContext context) {
		descriptors.add(PROP_SIGNAL_SERVICE);
		descriptors.add(PROP_ACCOUNT);
		descriptors.add(PROP_RECIPIENTS);
		descriptors.add(PROP_GROUPS);
		
		relationships.add(SUCCESS);
		relationships.add(FAILURE);

	}

	@Override
	public final Set<Relationship> getRelationships() {
		return this.relationships;
	}

	@Override
	public final List<PropertyDescriptor> getSupportedPropertyDescriptors() {
		return descriptors;
	}

	protected void logError(Throwable e) {
		ComponentLog log = getLogger();
		
		if(!log.isErrorEnabled())
			return;
		
		log.error(e.getMessage(), e);
	}

	protected String getAccountNumber(final ProcessContext context, FlowFile flowFile) {
		String account = context.getProperty(PROP_ACCOUNT).evaluateAttributeExpressions(flowFile).getValue();
		if(account == null)
			throw new NullPointerException("Account can not be null nor empty");
		
		account = account.trim();
		
		if(account.isBlank())
			throw new ProcessException("Account can not be empty");
		
		return account;
	}

	protected SignalControllerService getSignalService(final ProcessContext context) {
		return context.getProperty(PROP_SIGNAL_SERVICE).asControllerService(SignalControllerService.class);
	}

	protected final static Optional<List<String>> getList(ProcessContext context, FlowFile flowFile, PropertyDescriptor prop){
		String tmp = context.getProperty(prop).evaluateAttributeExpressions(flowFile).getValue();
		return Constants.getCommaSeparatedList(tmp);
	}
}
