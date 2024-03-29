package org.signal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.apache.nifi.reporting.InitializationException;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.signal.model.SignalData;
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
        runner.setProperty(AbstractSignalSenderProcessor.PROP_SIGNAL_SERVICE, serviceIdentifierA);
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
    	
    	Consumer<SignalData> listener = msg -> {
    		if(!numberB.equals(msg.getAccount()))
    			return;
    		
    		refGroup.set(msg.getGroupName());
    		if(msg instanceof SignalMessage)
    			refContent.set(((SignalMessage) msg).getMessage());
    	};
    	
    	serviceA.addMessageListener(listener);

    	runner.clearTransferState();
    	runner.setProperty(AbstractSignalSenderProcessor.PROP_GROUPS, TEST_GROUP);
    	runner.setProperty(PutSignalMessage.PROP_MESSAGE_CONTENT, content);
    	runner.setProperty(AbstractSignalSenderProcessor.PROP_ACCOUNT, numberA);
    	runner.enqueue("");
    	runner.run();

    	String result = Constants.getAndWait(refContent);
    	serviceA.removeMessageListener(listener);
    	
    	runner.assertAllFlowFilesTransferred(AbstractSignalSenderProcessor.SUCCESS);
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
    	
    	Consumer<SignalData> listener = msg -> {
    		if(!numberB.equals(msg.getAccount()))
    			return;
    		
    		refGroup.set(msg.getGroupName());
    		if(msg instanceof SignalMessage)
    			refContent.set(((SignalMessage) msg).getMessage());
    	};
    	
    	serviceA.addMessageListener(listener);

    	runner.clearTransferState();
    	runner.setProperty(AbstractSignalSenderProcessor.PROP_GROUPS, TEST_GROUP);
    	runner.setProperty(AbstractSignalSenderProcessor.PROP_ACCOUNT, numberA);
    	runner.enqueue(content.getBytes(StandardCharsets.UTF_8));
    	runner.run();

    	String result = Constants.getAndWait(refContent);
    	serviceA.removeMessageListener(listener);
    	
    	runner.assertAllFlowFilesTransferred(AbstractSignalSenderProcessor.SUCCESS);
    	assertEquals(content, result);
    	assertEquals(TEST_GROUP, refGroup.get());
	}
	
	@Test
	public void putGroupMessageOnlyOnce() throws InterruptedException {
		if(isSettingsEmpty()) {
			IllegalStateException exc = new IllegalStateException("No configuration set, skipping test");
			LOGGER.warn(exc.getMessage(), exc);
			return;
		}

    	String content = "Testing " + TestPutSignalMessageForGroups.class.getSimpleName() + " " + Math.random();
    	
    	AtomicReference<String> refContent = new AtomicReference<String>(null);
    	AtomicReference<String> refGroup = new AtomicReference<String>(null);
    	AtomicInteger counter = new AtomicInteger(0);
    	
    	Consumer<SignalData> listener = msg -> {
    		if(!numberB.equals(msg.getAccount()))
    			return;
    		
    		refGroup.set(msg.getGroupName());
    		if(msg instanceof SignalMessage) {
				SignalMessage signalMessage = (SignalMessage) msg;
				System.out.println("TEST Received: " + signalMessage.toString() + " " + signalMessage.getMessage() + " @ " + signalMessage.getTimestamp());
				refContent.set(signalMessage.getMessage());
				counter.incrementAndGet();
			}
    	};
    	
    	serviceA.addMessageListener(listener);

    	runner.clearTransferState();
    	runner.setProperty(AbstractSignalSenderProcessor.PROP_GROUPS, TEST_GROUP + ", " + TEST_GROUP);
    	runner.setProperty(PutSignalMessage.PROP_MESSAGE_CONTENT, content);
    	runner.setProperty(AbstractSignalSenderProcessor.PROP_ACCOUNT, numberA);
    	runner.enqueue("");
    	runner.run();

    	String result = Constants.getAndWait(refContent);
    	serviceA.removeMessageListener(listener);
    	
    	runner.assertAllFlowFilesTransferred(AbstractSignalSenderProcessor.SUCCESS, 1);
    	
    	assertEquals(content, result);
    	assertEquals(TEST_GROUP, refGroup.get());
    	assertEquals(1, counter.get());
	}
	

	@Test
	public void putGroupMessageMissingGroup() {
		if(isSettingsEmpty()) {
			IllegalStateException exc = new IllegalStateException("No configuration set, skipping test");
			LOGGER.warn(exc.getMessage(), exc);
			return;
		}

		runner.clearTransferState();
		runner.setProperty(AbstractSignalSenderProcessor.PROP_GROUPS, TEST_GROUP + " missing" + Math.random()*10_000);
		runner.setProperty(PutSignalMessage.PROP_MESSAGE_CONTENT, "Testing Group Message");
		runner.enqueue(new byte[0]);
		runner.run();
		runner.assertAllFlowFilesTransferred(AbstractSignalSenderProcessor.FAILURE);
	}
}
