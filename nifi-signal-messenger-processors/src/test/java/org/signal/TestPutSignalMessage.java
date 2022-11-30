package org.signal;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.apache.nifi.reporting.InitializationException;
import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.signal.model.SignalMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestPutSignalMessage extends AbstractMultiNumberTest {
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
    public void putMessage() throws InterruptedException {
    	if(isSettingsEmpty()) {
    		IllegalStateException exc = new IllegalStateException("No configuration set, skipping test");
    		LOGGER.warn(exc.getMessage(), exc);
			return;
		}
    	
    	String content = "Testing " + TestPutSignalMessage.class.getSimpleName() + " " + Math.random();
    	
    	AtomicReference<String> refContent = new AtomicReference<String>(null);
    	
    	Consumer<SignalMessage> listener = msg -> {
    		if(!numberB.equals(msg.getAccount()))
    			return;
    		
    		refContent.set(msg.getMessage());
    	};
    	
    	serviceA.addMessageListener(listener);

    	runner.clearTransferState();
    	runner.setProperty(PutSignalMessage.RECIPIENTS, numberB);
    	runner.setProperty(PutSignalMessage.MESSAGE_CONTENT, content);
    	runner.setProperty(PutSignalMessage.SOURCE, numberA);
    	runner.enqueue(new byte[0]);
    	runner.run();

    	String result = Constants.getAndWait(refContent);
    	serviceA.removeMessageListener(listener);
    	
    	runner.assertAllFlowFilesTransferred(PutSignalMessage.SUCCESS);
    	assertEquals(content, result);
    }

    @Test
    public void testNoRecipietsNoGroup() throws InterruptedException {
    	if(isSettingsEmpty()) {
    		IllegalStateException exc = new IllegalStateException("No configuration set, skipping test");
    		LOGGER.warn(exc.getMessage(), exc);
			return;
		}
    	
    	String content = "Testing " + TestPutSignalMessage.class.getSimpleName() + " " + Math.random();
    	
    	runner.clearTransferState();
    	runner.setProperty(PutSignalMessage.RECIPIENTS, "");
    	runner.setProperty(PutSignalMessage.GROUPS, "");
    	runner.setProperty(PutSignalMessage.MESSAGE_CONTENT, content);
    	runner.setProperty(PutSignalMessage.SOURCE, numberA);
    	runner.enqueue(new byte[0]);
    	runner.run();

    	runner.assertAllFlowFilesTransferred(PutSignalMessage.FAILURE, 1);
    	MockFlowFile flowFile = runner.getFlowFilesForRelationship(PutSignalMessage.FAILURE).get(0);
    	flowFile.assertAttributeEquals(Constants.ATTRIBUTE_ERROR_MESSAGE, Constants.MSG_MISSING_RECIPIENT_AND_GROUP);
    }

    @Test
    public void putMessageContent() throws InterruptedException {
    	if(isSettingsEmpty()) {
    		IllegalStateException exc = new IllegalStateException("No configuration set, skipping test");
    		LOGGER.warn(exc.getMessage(), exc);
			return;
		}
    	
    	String content = "Testing " + TestPutSignalMessage.class.getSimpleName() + " " + Math.random();
    	
    	AtomicReference<String> refContent = new AtomicReference<String>(null);
    	
    	Consumer<SignalMessage> listener = msg -> {
    		if(!numberB.equals(msg.getAccount()))
    			return;
    		
    		refContent.set(msg.getMessage());
    	};
    	
    	serviceA.addMessageListener(listener);

    	runner.clearTransferState();
    	runner.setProperty(PutSignalMessage.RECIPIENTS, numberB);
    	runner.setProperty(PutSignalMessage.SOURCE, numberA);
    	runner.enqueue(content.getBytes(StandardCharsets.UTF_8));
    	runner.run();

    	String result = Constants.getAndWait(refContent);
    	serviceA.removeMessageListener(listener);
    	
    	runner.assertAllFlowFilesTransferred(PutSignalMessage.SUCCESS);
    	assertEquals(content, result);
    }

    @Test
//    @Ignore("Manual testing")
    public void putMessageReply() throws InterruptedException, InitializationException {
    	if(isSettingsEmpty()) {
    		IllegalStateException exc = new IllegalStateException("No configuration set, skipping test");
    		LOGGER.warn(exc.getMessage(), exc);
			return;
		}
    	
        TestRunner runnerConsumer = TestRunners.newTestRunner(ConsumeSignalMessage.class);

        setSignaleService(runnerConsumer);
        runnerConsumer.setProperty(ConsumeSignalMessage.SIGNAL_SERVICE, serviceIdentifierA);
        runnerConsumer.enableControllerService(serviceA);
        assertTrue(runnerConsumer.isControllerServiceEnabled(serviceA));
        
        runnerConsumer.setRunSchedule(1_000);
        runnerConsumer.run(5);
    	
        runnerConsumer.assertAllFlowFilesTransferred(ConsumeSignalMessage.SUCCESS);

    	List<MockFlowFile> flowFiles = runnerConsumer.getFlowFilesForRelationship(ConsumeSignalMessage.SUCCESS);
    	MockFlowFile flowFile = flowFiles.get(0);
    	flowFile.assertAttributeExists(Constants.ATTRIBUTE_TIMESTAMP);
    	flowFile.assertAttributeExists(Constants.ATTRIBUTE_SENDER_NUMBER);
    	
    	LOGGER.info("Received message at {}: {}", flowFile.getAttribute(Constants.ATTRIBUTE_TIMESTAMP), flowFile.getAttribute(Constants.ATTRIBUTE_MESSAGE));

    	String content = "Testing quote: " + " " + Math.random();
    	
    	runner.clearTransferState();
    	runner.setProperty(PutSignalMessage.RECIPIENTS, flowFile.getAttribute(Constants.ATTRIBUTE_SENDER_NUMBER));
    	runner.setProperty(PutSignalMessage.SOURCE, numberA);
    	runner.setProperty(PutSignalMessage.MESSAGE_QUOTE, Boolean.toString(Boolean.TRUE));
    	runner.setProperty(PutSignalMessage.MESSAGE_CONTENT, content);
    	runner.enqueue(flowFile);
    	runner.run();

    	runner.assertAllFlowFilesTransferred(PutSignalMessage.SUCCESS);
    }

	@Test
    public void putMessageFail() {
    	if(isSettingsEmpty()) {
    		IllegalStateException exc = new IllegalStateException("No configuration set, skipping test");
    		LOGGER.warn(exc.getMessage(), exc);
			return;
		}

    	runner.clearTransferState();
    	runner.setProperty(PutSignalMessage.RECIPIENTS, numberB+"12332,"+numberB);
    	runner.setProperty(PutSignalMessage.MESSAGE_CONTENT, "Testing " + PutSignalMessage.class.getSimpleName());
    	runner.enqueue(new byte[0]);
    	runner.run();
    	runner.assertAllFlowFilesTransferred(PutSignalMessage.FAILURE, 1);
    	MockFlowFile ff = runner.getFlowFilesForRelationship(PutSignalMessage.FAILURE).get(0);
    	ff.assertAttributeEquals(Constants.ATTRIBUTE_ERROR_MESSAGE, "Specified account does not exist (ErrorCode: -32602)");
    }


}
