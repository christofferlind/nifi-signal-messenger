package org.signal;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
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
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SendMessageResult.IdentityFailure;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;
import org.whispersystems.signalservice.internal.push.http.ResumableUploadSpec;

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
				
			SignalServiceAttachmentStream attachment = null;

			if(useAttachment) {
				attachment = loadFlowFileContentAsAttachment(session, flowFile);
			} else {
				if(messageContent == null || messageContent.isEmpty()) {
					try {
						getLogger().info("Message is empty, using content as message");
						StringBuilder content = loadFlowFileContentAsMessageContent(session, flowFile);
						messageContent = content.toString();
					} catch (Throwable e) {
						getLogger().error(e.getMessage(), e);
						session.transfer(flowFile, FAILURE);
						return;
					}
				}
			}

			Collection<SendMessageResult> results = new LinkedList<>();

			//Try send to all groups
			if(groups.size() > 0) {
				List<SendMessageResult> r = signalService.sendGroupMessage(groups, messageContent, attachment);
				if(r != null)
					results.addAll(r);
			}

			//Try send to all recipients
			if(recipients.size() > 0) {
				List<SendMessageResult> r = signalService.sendMessage(recipients, messageContent, attachment);
				if(r != null)
					results.addAll(r);
			}
			
			boolean allOk = true;
			for (SendMessageResult result : results) {
				if(result.getSuccess() == null || result.isNetworkFailure() || result.isUnregisteredFailure()) {
					allOk = false;
					transferFailedFlowFile(session, flowFile, result);
				}
			}
			
			if(allOk) {
				session.transfer(flowFile, SUCCESS);
			} else {
				session.putAttribute(flowFile, "signal.send.failed", Boolean.toString(true));
				session.transfer(flowFile, SUCCESS);
			}
		} catch(InvocationTargetException e) {
			Throwable target = e.getTargetException();
			if(target == null) {
				getLogger().error(e.getMessage(), e);
				transferToFailureWithMessage(session, flowFile, e.getMessage());
 			} else if(target instanceof UnregisteredUserException){
				getLogger().error(e.getMessage(), e);
				flowFile = session.putAttribute(flowFile, "signal.send.failed.unregistered", Boolean.toString(true));
				transferToFailureWithMessage(session, flowFile, e.getMessage());
 			} else {
				getLogger().error(target.getMessage(), target);
				transferToFailureWithMessage(session, flowFile, target.getMessage());
 			}
		} catch(Throwable e) {
			getLogger().error(e.getMessage(), e);
			transferToFailureWithMessage(session, flowFile, e.getMessage());
		}
	}

	private void transferToFailureWithMessage(ProcessSession session, FlowFile flowFile, String message) {
		session.transfer(session.putAttribute(flowFile, "signal.send.error.message", message), FAILURE);		
	}

	private void transferFailedFlowFile(final ProcessSession session, FlowFile flowFile, SendMessageResult result) {
		FlowFile failed = session.clone(flowFile);
		
		Map<String, String> attribs = new HashMap<>();
		
		Optional<String> number = result.getAddress().getNumber();

		boolean failedNetwork = result.isNetworkFailure();
		boolean failedUnregistered = result.isUnregisteredFailure();
		IdentityFailure failedIdentity = result.getIdentityFailure();

		attribs.put("signal.send.failed.number", number.or("UNKNOWN"));
		attribs.put("signal.send.failed.network", Boolean.toString(failedNetwork));
		attribs.put("signal.send.failed.unregistered", Boolean.toString(failedUnregistered));
		attribs.put("signal.send.failed.identity", failedIdentity == null ? "" : failedIdentity.getIdentityKey().getFingerprint());
		
		failed = session.putAllAttributes(failed, attribs);
		session.transfer(failed, FAILURE);
	}


	private SignalServiceAttachmentStream loadFlowFileContentAsAttachment(ProcessSession session, FlowFile flowFile) {
		String mimeType = flowFile.getAttribute(CoreAttributes.MIME_TYPE.key());
		String filename = flowFile.getAttribute(CoreAttributes.FILENAME.key());
		
		if(mimeType == null || mimeType.isEmpty()) {
			throw new NullPointerException("mime.type attribute can not be empty");
		}

		getLogger().debug("Mime type: " + mimeType);

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		session.read(flowFile, inputStream -> copy(inputStream, outputStream));

		getLogger().debug("Flowfile content read");

		Optional<byte[]> preview = Optional.absent();
		Optional<String> caption = Optional.absent();
		Optional<String> blurHash = Optional.absent();
		
		Optional<ResumableUploadSpec> resumableUploadSpec = Optional.absent();

		final long uploadTimestamp = System.currentTimeMillis();

		return new SignalServiceAttachmentStream(
				new ByteArrayInputStream(outputStream.toByteArray()), 
				mimeType, 
				(long) outputStream.size(), 
				Optional.of(filename), 
				false, 
				false,
				preview, 
				0, 
				0, 
				uploadTimestamp,
				caption, 
				blurHash, 
				null,
				null,
				resumableUploadSpec);
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
