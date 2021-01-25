package org.signal;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.function.BiConsumer;

import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.controller.ControllerService;
import org.apache.nifi.processor.exception.ProcessException;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupContext;
import org.whispersystems.signalservice.api.push.exceptions.EncapsulatedExceptions;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

@Tags({"Signal", "Messenger"})
@CapabilityDescription("Signal Messenger API")
public interface SignalControllerService extends ControllerService {

	public List<SendMessageResult> sendMessage(List<String> address, String body, SignalServiceAttachmentStream attachment) throws ProcessException, IOException, InvocationTargetException;

	public List<SendMessageResult> sendGroupMessage(List<String> groups, String body, SignalServiceAttachmentStream attachment) throws ProcessException, IOException, InvocationTargetException;
	
	public void addMessageListener(BiConsumer<SignalServiceEnvelope, SignalServiceContent> listener);
	
	public void removeMessageListener(BiConsumer<SignalServiceEnvelope, SignalServiceContent> listener);

	public String getSignalUsername();

	/**
	 * Helper method that returns the base64 encoded id of the group
	 * @param groupContext, {@link SignalServiceGroupContext}
	 * @return
	 */
	public String getGroupId(SignalServiceGroupContext groupContext);

	/**
	 * Helper method that returns the group name
	 * @param groupContext, {@link SignalServiceGroupContext}
	 * @return
	 */
	public String getGroupTitle(SignalServiceGroupContext groupContext);
	
	public void sendMessageReaction(String emoji, boolean remove, String targetAuthor, long targetSentTimestamp, List<String> recipients) throws IOException, EncapsulatedExceptions, InvalidNumberException;

}
