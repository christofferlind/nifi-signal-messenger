package org.signal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;
//import org.whispersystems.signalservice.api.push.exceptions.EncapsulatedExceptions;
//import org.whispersystems.signalservice.api.util.InvalidNumberException;

@Tags({ "Signal", "Put", "Message", "Send", "Reaction" })
@CapabilityDescription("Sends a reaction on Signal message. This reads the attributes that ConsumeSignalMessage produces.")
@SeeAlso({ConsumeSignalMessage.class})
@ReadsAttributes({
	@ReadsAttribute(attribute=ConsumeSignalMessage.ATTRIBUTE_SENDER_NUMBER, description="The target number to react to"),
	@ReadsAttribute(attribute=ConsumeSignalMessage.ATTRIBUTE_TIMESTAMP, description="The target message timestamp to react to"),
})
public class PutSignalReaction extends AbstractProcessor {
	private static final Map<String, String> EMOJI_NAMES;
	static {
		Map<String, String> tmp = new LinkedHashMap<String, String>();
		tmp.put("thumbs-up", 		"0x1F44D");
		tmp.put("thumbs-down", 		"0x1F44E");

		tmp.put("check-green", 		"0x2705");
		tmp.put("cross-red", 		"0x274C");
		EMOJI_NAMES = Collections.unmodifiableMap(tmp);
	}

	public static final PropertyDescriptor SIGNAL_SERVICE = new PropertyDescriptor
			.Builder().name("SignalService")
			.displayName("Signal Service")
			.description("The signal service to use")
			.required(true)
			.identifiesControllerService(SignalControllerService.class)
			.build();

	public static final PropertyDescriptor REACTION_EMOJI = new PropertyDescriptor
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
		descriptors.add(REACTION_EMOJI);
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

		String targetAuthor = flowFile.getAttribute(ConsumeSignalMessage.ATTRIBUTE_SENDER_NUMBER);
		if(Objects.isNull(targetAuthor)) {
			NullPointerException exc = new NullPointerException("Flowfile is missing attribute: " + ConsumeSignalMessage.ATTRIBUTE_SENDER_NUMBER);
			getLogger().error(exc.getMessage(), exc);
			session.transfer(flowFile, FAILURE);
			return;
		}
		
		targetAuthor = targetAuthor.trim();
		if(targetAuthor.isEmpty()) {
			NullPointerException exc = new NullPointerException("Flowfile has an empty " + ConsumeSignalMessage.ATTRIBUTE_SENDER_NUMBER + " attribute");
			getLogger().error(exc.getMessage(), exc);
			session.transfer(flowFile, FAILURE);
			return;
		}
		
		String targetTimestampString = flowFile.getAttribute(ConsumeSignalMessage.ATTRIBUTE_TIMESTAMP);
		if(targetTimestampString == null) {
			NullPointerException exc = new NullPointerException("Flowfile is missing attribute: " + ConsumeSignalMessage.ATTRIBUTE_TIMESTAMP);
			getLogger().error(exc.getMessage(), exc);
			session.transfer(flowFile, FAILURE);
			return;
		}

//		try {
//			targetTimestampString = targetTimestampString.trim();
//			long targetTimestamp = Long.decode(targetTimestampString);
//			
//			SignalControllerService signalService = context.getProperty(SIGNAL_SERVICE).asControllerService(SignalControllerService.class);
//			String emoji = context.getProperty(REACTION_EMOJI).evaluateAttributeExpressions(flowFile).getValue();
//			emoji = fixEmojiString(emoji);
//
//			boolean removeReaction = emoji.isEmpty();
//			
//			signalService.sendMessageReaction(emoji, removeReaction, targetAuthor, targetTimestamp, Arrays.asList(targetAuthor));
//			
//			session.transfer(flowFile, SUCCESS);
//		} catch (NumberFormatException e) {
//			getLogger().error(e.getMessage(), e);
//			session.transfer(flowFile, FAILURE);
//		} catch (IllegalArgumentException e) {
//			getLogger().error(e.getMessage(), e);
//			session.transfer(flowFile, FAILURE);
//		} catch (IOException e) {
//			getLogger().error(e.getMessage(), e);
//			session.transfer(flowFile, FAILURE);
//		} catch (EncapsulatedExceptions e) {
//			getLogger().error(e.getMessage(), e);
//			session.transfer(flowFile, FAILURE);
//		} catch (InvalidNumberException e) {
//			getLogger().error(e.getMessage(), e);
//			session.transfer(flowFile, FAILURE);
//		}
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
