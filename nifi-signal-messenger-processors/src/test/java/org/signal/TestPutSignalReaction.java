package org.signal;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
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

public class TestPutSignalReaction extends AbstractMultiNumberTest {
	private TestRunner runner;

	private String testMessage;

    @Before
    public void init() throws InitializationException, InterruptedException {
    	if(isSettingsEmpty()) {
    		return;
    	}
    	
		runner = TestRunners.newTestRunner(ConsumeSignalMessage.class);
		setSignaleService(runner);
		runner.enableControllerService(serviceA);
        runner.setProperty(ConsumeSignalMessage.PROP_SIGNAL_SERVICE, serviceIdentifierA);

    	testMessage = "Testing consumeMessage " + Double.toString(Math.random());
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
	public void testPutReaction() throws InterruptedException, UnsupportedOperationException, IOException, ExecutionException, InitializationException {
    	if(isSettingsEmpty()) {
    		return;
    	}

    	Instant maxWait = Instant.now().plus(5, ChronoUnit.SECONDS);
        while(!serviceA.isListeningEvents()) {
        	Thread.sleep(101);
        	
        	if(Instant.now().isAfter(maxWait))
        		throw new IllegalStateException("Never up and listening");
        }
        
    	runner.clearTransferState();

    	runner.run(1, false, true, 5_000);
    	
    	serviceA.sendMessage(
    			numberB, 
    			testMessage, 
    			Optional.of(Arrays.asList(numberA)), 
    			Optional.empty(), 
    			Optional.empty(), 
    			Optional.empty());

    	Thread.sleep(1_000);
    	runner.stop();
    	
    	runner.assertAllFlowFilesTransferred(ConsumeSignalMessage.SUCCESS, 1);
    	MockFlowFile flowFile = runner.getFlowFilesForRelationship(ConsumeSignalMessage.SUCCESS).get(0);
    	
		TestRunner runnerReaction = TestRunners.newTestRunner(PutSignalReaction.class);
		setSignaleService(runnerReaction);
		runnerReaction.enableControllerService(serviceA);
		runnerReaction.setProperty(PutSignalReaction.PROP_SIGNAL_SERVICE, serviceIdentifierA);
    	runnerReaction.setProperty(PutSignalReaction.PROP_ACCOUNT, numberA);
    	runnerReaction.setProperty(PutSignalReaction.PROP_REACTION_EMOJI, "0x1F44D");
		runnerReaction.enqueue(flowFile);
    	runnerReaction.run();
    	
    	runnerReaction.assertAllFlowFilesTransferred(PutSignalReaction.SUCCESS, 1);
	}
	
    @Test
    @Ignore("Manual test")
    public void testManualReaction() throws InterruptedException, InitializationException {
    	if(isSettingsEmpty()) {
			return;
		}
    	
        runner.setRunSchedule(1_000);
        runner.run(5);
    	
        runner.assertAllFlowFilesTransferred(ConsumeSignalMessage.SUCCESS, 1);
        
    	MockFlowFile flowFile = runner.getFlowFilesForRelationship(ConsumeSignalMessage.SUCCESS).get(0);
    	flowFile.assertAttributeExists(Constants.ATTRIBUTE_TIMESTAMP);
    	flowFile.assertAttributeExists(Constants.ATTRIBUTE_SENDER_NUMBER);
    	
		TestRunner runnerReaction = TestRunners.newTestRunner(PutSignalReaction.class);
		setSignaleService(runnerReaction);
		runnerReaction.enableControllerService(serviceA);
		runnerReaction.setProperty(PutSignalReaction.PROP_SIGNAL_SERVICE, serviceIdentifierA);
		runnerReaction.setProperty(PutSignalReaction.PROP_ACCOUNT, flowFile.getAttribute(Constants.ATTRIBUTE_ACCOUNT_NUMBER));
		runnerReaction.setProperty(PutSignalReaction.PROP_REACTION_EMOJI, "0x1F44D");
		runnerReaction.enqueue(flowFile);
		runnerReaction.run();

		runnerReaction.assertAllFlowFilesTransferred(PutSignalReaction.SUCCESS, 1);
		
		Thread.sleep(1_000);
		
		runnerReaction.clearTransferState();
		runnerReaction.setProperty(PutSignalReaction.PROP_REMOVE_REACTION, Boolean.toString(Boolean.TRUE));
		runnerReaction.enqueue(flowFile);
		runnerReaction.run();
		runner.assertAllFlowFilesTransferred(PutSignalReaction.SUCCESS, 1);
    }
}
