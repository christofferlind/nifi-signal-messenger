package org.signal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.apache.nifi.reporting.InitializationException;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.signal.model.SignalMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestPutSignalMessageForGroups extends AbstractMultiNumberTest {
	private static final String TEST_GROUP = System.getenv("nifi-signal-messenger.test.group");
	
	private static final Logger LOGGER = LoggerFactory.getLogger(TestPutSignalMessage.class);

	private TestRunner runner;

	@Before
	public void init() throws InitializationException {
		if(isSettingsEmpty()) {
			return;
		}

		runner = TestRunners.newTestRunner(PutSignalMessage.class);

        setSignaleService(runner);
        runner.setProperty(PutSignalMessage.SIGNAL_SERVICE, serviceIdentifierA);
        runner.enableControllerService(serviceA);
	}

	@After
	public void deactivate() throws InitializationException {
    	if(runner == null)
    		return;
    	
    	if(runner.isControllerServiceEnabled(serviceA)) {
    		runner.disableControllerService(serviceA);
    	}
	}

	@Test
	public void putGroupMessage() throws InterruptedException {
		if(isSettingsEmpty()) {
			IllegalStateException exc = new IllegalStateException("No configuration set, skipping test");
			LOGGER.warn(exc.getMessage(), exc);
			return;
		}

    	String content = "Testing " + TestPutSignalMessageForGroups.class.getSimpleName() + " " + Math.random();
    	
    	AtomicReference<String> refContent = new AtomicReference<String>(null);
    	AtomicReference<String> refGroup = new AtomicReference<String>(null);
    	
    	Consumer<SignalMessage> listener = msg -> {
    		if(!numberB.equals(msg.getAccount()))
    			return;
    		
    		refGroup.set(msg.getGroupName());
    		refContent.set(msg.getMessage());
    	};
    	
    	serviceA.addMessageListener(listener);

    	runner.clearTransferState();
    	runner.setProperty(PutSignalMessage.GROUPS, TEST_GROUP);
    	runner.setProperty(PutSignalMessage.MESSAGE_CONTENT, content);
    	runner.setProperty(PutSignalMessage.SOURCE, numberA);
    	runner.enqueue("");
    	runner.run();

    	String result = TestUtilMethods.getAndWait(refContent);
    	serviceA.removeMessageListener(listener);
    	
    	runner.assertAllFlowFilesTransferred(PutSignalMessage.SUCCESS);
    	assertEquals(content, result);
    	assertEquals(TEST_GROUP, refGroup.get());
	}

	@Test
	public void putGroupMessageContent() throws InterruptedException {
		if(isSettingsEmpty()) {
			IllegalStateException exc = new IllegalStateException("No configuration set, skipping test");
			LOGGER.warn(exc.getMessage(), exc);
			return;
		}

    	String content = "Testing " + TestPutSignalMessageForGroups.class.getSimpleName() + " " + Math.random();
    	
    	AtomicReference<String> refContent = new AtomicReference<String>(null);
    	AtomicReference<String> refGroup = new AtomicReference<String>(null);
    	
    	Consumer<SignalMessage> listener = msg -> {
    		if(!numberB.equals(msg.getAccount()))
    			return;
    		
    		refGroup.set(msg.getGroupName());
    		refContent.set(msg.getMessage());
    	};
    	
    	serviceA.addMessageListener(listener);

    	runner.clearTransferState();
    	runner.setProperty(PutSignalMessage.GROUPS, TEST_GROUP);
    	runner.setProperty(PutSignalMessage.SOURCE, numberA);
    	runner.enqueue(content.getBytes(StandardCharsets.UTF_8));
    	runner.run();

    	String result = TestUtilMethods.getAndWait(refContent);
    	serviceA.removeMessageListener(listener);
    	
    	runner.assertAllFlowFilesTransferred(PutSignalMessage.SUCCESS);
    	assertEquals(content, result);
    	assertEquals(TEST_GROUP, refGroup.get());
	}

	@Test
	public void putGroupMessageMissingGroup() {
		if(isSettingsEmpty()) {
			IllegalStateException exc = new IllegalStateException("No configuration set, skipping test");
			LOGGER.warn(exc.getMessage(), exc);
			return;
		}

		runner.clearTransferState();
		runner.setProperty(PutSignalMessage.GROUPS, TEST_GROUP + " missing" + Math.random()*10_000);
		runner.setProperty(PutSignalMessage.MESSAGE_CONTENT, "Testing Group Message");
		runner.enqueue(new byte[0]);
		runner.run();
		runner.assertAllFlowFilesTransferred(PutSignalMessage.FAILURE);
	}
}
