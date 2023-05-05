package org.signal;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.nifi.annotation.behavior.ReadsAttribute;
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
//import org.whispersystems.signalservice.api.push.exceptions.EncapsulatedExceptions;
//import org.whispersystems.signalservice.api.util.InvalidNumberException;

import com.google.gson.JsonElement;

@Tags({ "Signal", "Put", "Message", "Send", "Reaction" })
@CapabilityDescription("Sends a reaction on Signal message. This reads the attributes that ConsumeSignalMessage produces.")
@SeeAlso({ConsumeSignalMessage.class})
@ReadsAttributes({
	@ReadsAttribute(attribute=Constants.ATTRIBUTE_SENDER_NUMBER, description="The target number to react to"),
	@ReadsAttribute(attribute=Constants.ATTRIBUTE_TIMESTAMP, description="The target message timestamp to react to"),
})
public class PutSignalReaction extends AbstractSignalSenderProcessor {
	private static final Map<String, String> EMOJI_NAMES;
	static {
		Map<String, String> tmp = new LinkedHashMap<String, String>();
		tmp.put("thumbs-up", 		"0x1F44D");
		tmp.put("thumbs-down", 		"0x1F44E");

		tmp.put("check-green", 		"0x2705");
		tmp.put("cross-red", 		"0x274C");
		tmp.put("red-heart", 		"0x2764");
		tmp.put("fire", 			"0x1F525");
		tmp.put("star", 			"0x2B50");
		tmp.put("eyes", 			"0x1F440");
		
		tmp.put("circle-red", 		"0x1F534");
		tmp.put("circle-yellow", 	"0x1F7E1");
		tmp.put("circle-green", 	"0x1F7E2");
		
		EMOJI_NAMES = Collections.unmodifiableMap(tmp);
	}

	public static final PropertyDescriptor PROP_REACTION_EMOJI = new PropertyDescriptor
			.Builder().name("ReactionEmoji")
			.displayName("Reaction emoji")
			.description("Emoji to use in format 0x...., an empty value will remove the reaction. "
					+ "Some emoji names is translated to the emoji. Currently the following names are valid: "
					+ String.join(", ", EMOJI_NAMES.keySet()))
			.required(true)
			.addValidator(StandardValidators.ATTRIBUTE_EXPRESSION_LANGUAGE_VALIDATOR)
			.expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
			.defaultValue("")
			.build();

	public static final PropertyDescriptor PROP_MESSAGE_REACTION_TIMESTAMP_ATTRIBUTE = new PropertyDescriptor
			.Builder().name("ReactionTimestampAttribute")
			.displayName("Reaction timestamp")
			.description("Attribute on the flowfile that contains the message timestamp to react to")
			.required(true)
			.defaultValue(Constants.ATTRIBUTE_TIMESTAMP)
			.addValidator(StandardValidators.ATTRIBUTE_EXPRESSION_LANGUAGE_VALIDATOR)
			.expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
			.build();

	public static final PropertyDescriptor PROP_MESSAGE_REACTION_SENDER_ATTRIBUTE = new PropertyDescriptor
			.Builder().name("ReactionSenderAttribute")
			.displayName("Reaction sender")
			.description("Attribute on the flowfile that contains the author number to react to")
			.required(true)
			.defaultValue(Constants.ATTRIBUTE_SENDER_NUMBER)
			.addValidator(StandardValidators.ATTRIBUTE_EXPRESSION_LANGUAGE_VALIDATOR)
			.expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
			.build();

	public static final PropertyDescriptor PROP_REMOVE_REACTION = new PropertyDescriptor
			.Builder().name("RemoveReaction")
			.displayName("Remove reaction")
			.description("")
			.addValidator(StandardValidators.ATTRIBUTE_EXPRESSION_LANGUAGE_VALIDATOR)
			.expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
			.defaultValue(Boolean.toString(Boolean.FALSE))
			.build();
	
	@Override
	protected void init(final ProcessorInitializationContext context) {
		super.init(context);
		descriptors.remove(PROP_RECIPIENTS);
		descriptors.remove(PROP_GROUPS);
		
		descriptors.add(PROP_REACTION_EMOJI);
		descriptors.add(PROP_MESSAGE_REACTION_SENDER_ATTRIBUTE);
		descriptors.add(PROP_MESSAGE_REACTION_TIMESTAMP_ATTRIBUTE);
		descriptors.add(PROP_REMOVE_REACTION);
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
			
			String removeReactionString= context.getProperty(PROP_REMOVE_REACTION).getValue();
			boolean removeReaction = "true".equalsIgnoreCase(removeReactionString);
			
			String reactSenderAttribute = context
					.getProperty(PROP_MESSAGE_REACTION_SENDER_ATTRIBUTE)
					.evaluateAttributeExpressions(flowFile)
					.getValue();
			
			String reactTimestampAttribute = context
					.getProperty(PROP_MESSAGE_REACTION_TIMESTAMP_ATTRIBUTE)
					.evaluateAttributeExpressions(flowFile)
					.getValue();
			
			List<String> vitalAttributes = Arrays.asList(	reactSenderAttribute, 
															reactTimestampAttribute);

			if(!hasVitalAttributes(flowFile, vitalAttributes)) {
				flowFile = session.putAttribute(flowFile, Constants.ATTRIBUTE_ERROR_MESSAGE, "Flow file is missing one of the following attributes: " + String.join(", ", vitalAttributes));
				session.transfer(flowFile, FAILURE);
				return;
			}
			
			String targetAuthor = flowFile.getAttribute(reactSenderAttribute);
			String targetTimestampString = flowFile.getAttribute(reactTimestampAttribute);
			
			String attribute = flowFile.getAttribute(reactSenderAttribute);
			Optional<List<String>> groups = Optional.empty();
			Optional<List<String>> recipients = Optional.of(Arrays.asList(attribute));
			
//			Optional<List<String>> groups = getList(context, flowFile, PROP_GROUPS);
//			Optional<List<String>> recipients = getList(context, flowFile, PROP_RECIPIENTS);

			if(groups.isEmpty() && recipients.isEmpty())
				throw new IllegalStateException(Constants.MSG_MISSING_RECIPIENT_AND_GROUP);
			
			targetTimestampString = targetTimestampString.trim();
			long targetTimestamp = Long.decode(targetTimestampString);
			
			String emoji = context.getProperty(PROP_REACTION_EMOJI).evaluateAttributeExpressions(flowFile).getValue();
			emoji = fixEmojiString(emoji);

			JsonElement result = signalService.sendReaction(
													account, 
													recipients, 
													groups, 
													targetAuthor, 
													targetTimestamp, 
													emoji, 
													Optional.of(removeReaction));
			
			if(getLogger().isDebugEnabled())
				getLogger().debug(result.toString());

			session.transfer(flowFile, SUCCESS);
		} catch (Throwable e) {
			getLogger().error(e.getMessage(), e);
			session.transfer(flowFile, FAILURE);
		}
	}

	private boolean hasVitalAttributes(FlowFile flowFile, List<String> asList) {
		for (String attr : asList) {
			String tmp = flowFile.getAttribute(attr);
			
			if(tmp == null) {
				logError(new NullPointerException("Flowfile is missing attribute: " + attr));
				return false;
			}
			
			tmp = tmp.trim();
			if(tmp.isBlank()) {
				logError(new NullPointerException("Flowfile is missing attribute: " + attr));
				return false;
			}
		}
		
		return true;
	}


	private String fixEmojiString(String emoji) {
		if(emoji == null)
			return "";
		
		emoji = emoji.trim();
		
		if(emoji.isEmpty())
			return emoji;

		String string = EMOJI_NAMES.get(emoji);
		if(string != null)
			emoji = string;
		
		if(emoji.startsWith("0x"))
			return new String(Character.toChars(Integer.decode(emoji)));

		throw new IllegalArgumentException("Could not convert the value " + emoji + " to an emoji");
	}
}
