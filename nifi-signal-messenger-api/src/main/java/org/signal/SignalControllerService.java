package org.signal;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.controller.ControllerService;
import org.signal.model.SignalAttachment;
import org.signal.model.SignalGroup;
import org.signal.model.SignalIdentity;
import org.signal.model.SignalMessage;
import org.signal.model.SignalQuote;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

@Tags({"Signal", "Messenger"})
@CapabilityDescription("Signal Messenger API")
public interface SignalControllerService extends ControllerService {

	public void sendMessage(String account, 
							String message, 
							Optional<List<String>> recipients,
							Optional<List<String>> groups,
							Optional<SignalQuote> quote,
							Optional<SignalAttachment> attachment) throws IOException, UnsupportedOperationException, ExecutionException;
	
	public void addMessageListener(Consumer<SignalMessage> messageListener);

	public void removeMessageListener(Consumer<SignalMessage> messageListener);

	public Map<String, SignalIdentity> getIdentities(String account) throws UnsupportedOperationException, IOException, ExecutionException;
	/**
	 * 
	 * @param account
	 * @return {@link Map} with GroupId mapped with {@link SignalGroup}
	 * @throws UnsupportedOperationException
	 * @throws IOException
	 * @throws ExecutionException
	 */
	public Map<String, SignalGroup> getGroups(String account) throws UnsupportedOperationException, IOException, ExecutionException;

	/**
	 * @return null if not connected to the endpoint
	 */
	public String getSignalVersion();
	
	public JsonElement sendJsonRpc(String method, JsonObject params) throws UnsupportedOperationException, IOException; 	
	public JsonElement sendJsonRpc(String method, Map<String, String> params) throws UnsupportedOperationException, IOException;
	
	public JsonElement sendJsonRpc(String method, Map<String, String> params, String msgId) throws UnsupportedOperationException, IOException;
	public JsonElement sendJsonRpc(String method, JsonObject params, String msgId) throws UnsupportedOperationException, IOException;

	
}
