package org.signal;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import org.apache.nifi.reporting.InitializationException;
import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestConsumeSignalMessage extends AbstractMultiNumberTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(TestConsumeSignalMessage.class);

	private static final String TEST_GROUP = System.getenv("nifi-signal-messenger.test.group");

    private TestRunner runner;

    @Before
    public void init() throws InitializationException, InterruptedException {
    	if(isSettingsEmpty()) {
    		return;
    	}
    	
		runner = TestRunners.newTestRunner(ConsumeSignalMessage.class);
		setSignaleService(runner);
		runner.enableControllerService(serviceA);
        runner.setProperty(ConsumeSignalMessage.SIGNAL_SERVICE, serviceIdentifierA);
        
        Instant maxWait = Instant.now().plus(5, ChronoUnit.SECONDS);
        while(!serviceA.isListeningEvents()) {
        	Thread.sleep(101);
        	
        	if(Instant.now().isAfter(maxWait))
        		throw new IllegalStateException("Never up and listening");
        }
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
    public void consumeMessage() throws UnsupportedOperationException, IOException, ExecutionException, InterruptedException {
    	if(isSettingsEmpty()) {
    		IllegalStateException exc = new IllegalStateException("No configuration set, skipping test");
    		LOGGER.warn(exc.getMessage(), exc);
			return;
		}

    	runner.clearTransferState();
    	String message = "Testing consumeMessage " + Double.toString(Math.random());

    	runner.run(1, false, true, 5_000);
    	
    	serviceA.sendMessage(
    			numberB, 
    			message, 
    			Optional.of(Arrays.asList(numberA)), 
    			Optional.empty(), 
    			Optional.empty(), 
    			Optional.empty());
    	
    	
    	Thread.sleep(1_000);
    	runner.stop();
    	
    	runner.assertAllFlowFilesTransferred(ConsumeSignalMessage.SUCCESS, 1);

    	List<MockFlowFile> flowFiles = runner.getFlowFilesForRelationship(ConsumeSignalMessage.SUCCESS);
    	MockFlowFile flowFile = flowFiles.get(0);
    	
    	flowFile.assertAttributeEquals(Constants.ATTRIBUTE_MESSAGE, message);
    	assertBasicAttributes(flowFile, numberA, numberB);
    	flowFile.assertAttributeEquals(Constants.ATTRIBUTE_MESSAGE, message);
    }

    @Test
    @Ignore("Used for manual testing")
    public void consumeMessageManual() throws InterruptedException {
    	if(isSettingsEmpty()) {
    		IllegalStateException exc = new IllegalStateException("No configuration set, skipping test");
    		LOGGER.warn(exc.getMessage(), exc);
			return;
		}

    	runner.clearTransferState();
    	
    	runner.run(1, false, true, 5_000);
    	
    	Thread.sleep(10_000);
    	runner.stop();
    	
    	runner.assertAllFlowFilesTransferred(ConsumeSignalMessage.SUCCESS, 1);

    	List<MockFlowFile> flowFiles = runner.getFlowFilesForRelationship(ConsumeSignalMessage.SUCCESS);
    	assertEquals(1, flowFiles.size());
    }

    @Test
    public void consumeGroupMessage() throws UnsupportedOperationException, IOException, ExecutionException, InterruptedException {
    	if(isSettingsEmpty() || TEST_GROUP == null || TEST_GROUP.isBlank()) {
    		IllegalStateException exc = new IllegalStateException("No configuration set, skipping test");
    		LOGGER.warn(exc.getMessage(), exc);
			return;
		}

    	runner.clearTransferState();
    	String message = "Testing consumeGroupMessage " + Double.toString(Math.random());

    	runner.run(1, false, true, 5_000);
    	
    	serviceA.sendMessage(
    			numberB, 
    			message, 
    			Optional.empty(), 
    			Optional.of(Arrays.asList(TEST_GROUP)), 
    			Optional.empty(), 
    			Optional.empty());

    	
    	Thread.sleep(1_000);
    	runner.stop();

    	runner.assertAllFlowFilesTransferred(ConsumeSignalMessage.SUCCESS, 1);
    	
    	List<MockFlowFile> flowFiles = runner.getFlowFilesForRelationship(ConsumeSignalMessage.SUCCESS);
    	assertEquals(1, flowFiles.size());
    	MockFlowFile flowFile = flowFiles.get(0);
    	
    	assertBasicAttributes(flowFile, numberA, numberB);

    	flowFile.assertAttributeEquals(Constants.ATTRIBUTE_MESSAGE_GROUP_TITLE, TEST_GROUP);
    	flowFile.assertAttributeExists(Constants.ATTRIBUTE_MESSAGE_GROUP_ID);
    	flowFile.assertAttributeNotEquals(Constants.ATTRIBUTE_MESSAGE_GROUP_ID, "");
    	flowFile.assertAttributeEquals(Constants.ATTRIBUTE_MESSAGE, message);
    }
    
	public static final void assertBasicAttributes(MockFlowFile flowFile, String numberA, String numberB) {
		flowFile.assertAttributeEquals(Constants.ATTRIBUTE_SENDER_NUMBER, numberB);
		flowFile.assertAttributeExists(Constants.ATTRIBUTE_TIMESTAMP);
		flowFile.assertAttributeExists(Constants.ATTRIBUTE_TIMESTAMP_STRING);
		flowFile.assertAttributeEquals(Constants.ATTRIBUTE_ACCOUNT_NUMBER, numberA);
		flowFile.assertAttributeEquals(Constants.ATTRIBUTE_RECEIVING_NUMBER, numberA);
		flowFile.assertAttributeExists(Constants.ATTRIBUTE_SENDER_VERIFIED);
		flowFile.assertAttributeExists(Constants.ATTRIBUTE_MESSAGE_VIEW_ONCE);
		
		flowFile.assertAttributeNotEquals(Constants.ATTRIBUTE_TIMESTAMP_STRING, "");
	}


}
