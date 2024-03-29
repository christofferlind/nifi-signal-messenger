package org.signal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
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

import com.google.gson.JsonElement;

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
        runner.setProperty(ConsumeSignalMessage.PROP_SIGNAL_SERVICE, serviceIdentifierA);
        
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
    	
    	TestUtilMethods.assertBasicAttributes(flowFile, numberA, numberB);
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
    	
    	TestUtilMethods.assertBasicAttributes(flowFile, numberA, numberB);

		flowFile.assertAttributeExists(Constants.ATTRIBUTE_MESSAGE_VIEW_ONCE);
    	flowFile.assertAttributeEquals(Constants.ATTRIBUTE_MESSAGE_GROUP_TITLE, TEST_GROUP);
    	flowFile.assertAttributeExists(Constants.ATTRIBUTE_MESSAGE_GROUP_ID);
    	flowFile.assertAttributeNotEquals(Constants.ATTRIBUTE_MESSAGE_GROUP_ID, "");
    	flowFile.assertAttributeEquals(Constants.ATTRIBUTE_MESSAGE, message);
    }

    @Test
    public void consumeReaction() throws UnsupportedOperationException, IOException, ExecutionException, InterruptedException {
    	if(isSettingsEmpty()) {
    		IllegalStateException exc = new IllegalStateException("No configuration set, skipping test");
    		LOGGER.warn(exc.getMessage(), exc);
			return;
		}

    	String message = "Testing consumeMessage " + Double.toString(Math.random());
    	JsonElement result = serviceA.sendMessage(
						    			numberA, 
						    			message, 
						    			Optional.of(Arrays.asList(numberB)), 
						    			Optional.empty(), 
						    			Optional.empty(), 
						    			Optional.empty());
    	Thread.sleep(500);
    	
    	runner.clearTransferState();

    	runner.run(1, false, true, 5_000);
    	
    	long timestamp = result.getAsJsonObject().get("timestamp").getAsLong();
    	
    	String emoji = "0x1F44D";
		serviceA.sendReaction(
    			numberB, 
    			Optional.of(Arrays.asList(numberA)), 
    			Optional.empty(), 
    			numberA, 
    			timestamp, 
    			emoji, 
    			Optional.of(false));
    	
    	Thread.sleep(1_000);
    	runner.stop();
    	
    	runner.assertAllFlowFilesTransferred(ConsumeSignalMessage.SUCCESS, 1);

    	List<MockFlowFile> flowFiles = runner.getFlowFilesForRelationship(ConsumeSignalMessage.SUCCESS);
    	MockFlowFile flowFile = flowFiles.get(0);
    	
    	TestUtilMethods.assertBasicAttributes(flowFile, numberA, numberB);
    	flowFile.assertAttributeNotExists(Constants.ATTRIBUTE_MESSAGE);
    	flowFile.assertAttributeNotExists(Constants.ATTRIBUTE_MESSAGE_VIEW_ONCE);
    	
    	flowFile.assertAttributeEquals(Constants.ATTRIBUTE_MESSAGE_REACTION_EMOJI, emoji);
    	flowFile.assertAttributeEquals(Constants.ATTRIBUTE_MESSAGE_REACTION_TARGET_AUTHOR, numberA);
    	flowFile.assertAttributeEquals(Constants.ATTRIBUTE_MESSAGE_REACTION_TARGET_TIMESTAMP, Long.toString(timestamp));
    }
    
    @Test
    public void consumeMessageUsingMessageQueue() throws UnsupportedOperationException, IOException, ExecutionException, InterruptedException {
    	if(isSettingsEmpty()) {
    		IllegalStateException exc = new IllegalStateException("No configuration set, skipping test");
    		LOGGER.warn(exc.getMessage(), exc);
			return;
		}

    	runner.clearTransferState();
    	int maxMessages = 5;
    	Collection<String> messageCounter = new LinkedList<>();
    	
    	//First, send messages with a random message
    	for (int i = 0; i < maxMessages; i++) {
    		String message = "Testing consumeMessage " + Double.toString(Math.random());
    		
    		serviceA.sendMessage(
    				numberB, 
    				message, 
    				Optional.of(Arrays.asList(numberA)), 
    				Optional.empty(), 
    				Optional.empty(), 
    				Optional.empty());
    		
    		messageCounter.add(message);

    		Thread.sleep(101);
		}
    	
    	Thread.sleep(1_000);
    	runner.run(1, false, true, 5_000);
    	Thread.sleep(1_000);
    	runner.stop();
    	
    	runner.assertAllFlowFilesTransferred(ConsumeSignalMessage.SUCCESS, maxMessages);
    	
    	
    	List<MockFlowFile> flowFiles = runner.getFlowFilesForRelationship(ConsumeSignalMessage.SUCCESS);
    	// Check that all messages was received. I'm not sure the come at the same order
    	// Remove the received messages from the messageCounter and check that we have received all 
    	for (MockFlowFile flowFile : flowFiles) {
    		TestUtilMethods.assertBasicAttributes(flowFile, numberA, numberB);
    		String message = flowFile.getAttribute(Constants.ATTRIBUTE_MESSAGE);
    		messageCounter.remove(message);
		}
    	
    	assertTrue(messageCounter.isEmpty());
    }
    
}
