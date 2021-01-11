package org.signal;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.controller.ControllerService;
import org.apache.nifi.processor.exception.ProcessException;
import org.signal.libsignal.metadata.InvalidMetadataMessageException;
import org.signal.libsignal.metadata.InvalidMetadataVersionException;
import org.signal.libsignal.metadata.ProtocolDuplicateMessageException;
import org.signal.libsignal.metadata.ProtocolInvalidKeyException;
import org.signal.libsignal.metadata.ProtocolInvalidKeyIdException;
import org.signal.libsignal.metadata.ProtocolInvalidMessageException;
import org.signal.libsignal.metadata.ProtocolInvalidVersionException;
import org.signal.libsignal.metadata.ProtocolLegacyMessageException;
import org.signal.libsignal.metadata.ProtocolNoSessionException;
import org.signal.libsignal.metadata.ProtocolUntrustedIdentityException;
import org.signal.libsignal.metadata.SelfSendException;
import org.whispersystems.signalservice.api.SignalServiceMessagePipe;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.push.exceptions.EncapsulatedExceptions;
import org.whispersystems.signalservice.api.util.InvalidNumberException;
import org.whispersystems.signalservice.internal.push.UnsupportedDataMessageException;

@Tags({"Signal", "Messenger"})
@CapabilityDescription("Signal Messenger API")
public interface SignalControllerService extends ControllerService {

	public List<SendMessageResult> sendMessage(List<String> address, String body, SignalServiceAttachmentStream attachment) throws ProcessException, IOException, InvocationTargetException;

	public List<SendMessageResult> sendGroupMessage(List<String> groups, String body, SignalServiceAttachmentStream attachment) throws ProcessException, IOException, InvocationTargetException;
	
	public void saveAccount();

	public SignalServiceMessagePipe getMessagePipe() throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException, InvocationTargetException;
	
	public SignalServiceMessageReceiver getMessageReceiver() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException;
	
	public String getSignalUsername();
	
	public SignalServiceContent decryptMessage(SignalServiceEnvelope envelope) throws InvalidMetadataMessageException, ProtocolInvalidMessageException, ProtocolDuplicateMessageException, ProtocolLegacyMessageException, ProtocolInvalidKeyIdException, InvalidMetadataVersionException, ProtocolInvalidVersionException, ProtocolNoSessionException, ProtocolInvalidKeyException, ProtocolUntrustedIdentityException, SelfSendException, UnsupportedDataMessageException;

	public void sendMessageReaction(String emoji, boolean remove, String targetAuthor, long targetSentTimestamp, List<String> recipients) throws IOException, EncapsulatedExceptions, InvalidNumberException;
}
