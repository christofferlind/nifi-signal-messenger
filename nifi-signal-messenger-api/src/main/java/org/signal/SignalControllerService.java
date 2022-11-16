package org.signal;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.controller.ControllerService;
import org.signal.model.SignalGroup;
import org.signal.model.SignalIdentity;
import org.signal.model.SignalMessage;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

@Tags({"Signal", "Messenger"})
@CapabilityDescription("Signal Messenger API")
public interface SignalControllerService extends ControllerService {

	public void sendMessage(String account, List<String> recipients, String message, Object attachment) throws IOException, UnsupportedOperationException;
	
	public void sendGroupMessage(String account, List<String> groups, String message, Object attachment);

	public void addMessageListener(Consumer<SignalMessage> messageListener);

	public void removeMessageListener(Consumer<SignalMessage> messageListener);

	public Map<String, SignalIdentity> getIdentities(String account) throws UnsupportedOperationException, IOException, ExecutionException;
	public Map<String, SignalGroup> getGroups(String account) throws UnsupportedOperationException, IOException, ExecutionException;
	
	public JsonElement sendJsonRpc(String method, JsonObject params) throws UnsupportedOperationException, IOException; 	
	public JsonElement sendJsonRpc(String method, Map<String, String> params) throws UnsupportedOperationException, IOException;
	
	public JsonElement sendJsonRpc(String method, Map<String, String> params, String msgId) throws UnsupportedOperationException, IOException;
	public JsonElement sendJsonRpc(String method, JsonObject params, String msgId) throws UnsupportedOperationException, IOException;

	
}
