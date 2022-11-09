package org.signal;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.controller.ControllerService;
import org.signal.model.SignalMessage;

@Tags({"Signal", "Messenger"})
@CapabilityDescription("Signal Messenger API")
public interface SignalControllerService extends ControllerService {

	public void sendMessage(String account, List<String> recipients, String message, Object attachment) throws IOException, UnsupportedOperationException;
	
	public void sendGroupMessage(String account, List<String> groups, String message, Object attachment);

	public void addMessageListener(Consumer<SignalMessage> messageListener);

	public void removeMessageListener(Consumer<SignalMessage> messageListener);
}
