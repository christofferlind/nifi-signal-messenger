package org.signal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;

import org.apache.nifi.processor.exception.ProcessException;
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
import org.whispersystems.signalservice.api.messages.multidevice.VerifiedMessage.VerifiedState;

public class TestConsumeSignalMessage extends AbstractMultiNumberTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(TestConsumeSignalMessage.class);

	private static final String TEST_GROUP = System.getenv("nifi-signal-messenger.test.group");

    private TestRunner runner;

    @Before
    public void init() throws InitializationException {
    	if(isSettingsEmpty()) {
    		return;
    	}
    	
		runner = TestRunners.newTestRunner(ConsumeSignalMessage.class);
		setSignaleService(runner);
        runner.setProperty(ConsumeSignalMessage.SIGNAL_SERVICE, serviceIdentifierA);
        runner.enableControllerService(serviceA);
        runner.enableControllerService(serviceB);
    }
    
    @After
    public void deactivate() throws InitializationException {
    	if(runner == null)
    		return;
    	
    	if(runner.isControllerServiceEnabled(serviceA)) {
			runner.disableControllerService(serviceA);
		}
    	
    	if(runner.isControllerServiceEnabled(serviceB)) {
			runner.disableControllerService(serviceB);
		}
    }

    @Test
    public void consumeMessage() throws ProcessException, InvocationTargetException, IOException, InterruptedException {
    	if(isSettingsEmpty()) {
    		IllegalStateException exc = new IllegalStateException("No configuration set, skipping test");
    		LOGGER.warn(exc.getMessage(), exc);
			return;
		}

    	runner.clearTransferState();
    	String message = "Testing: " + Double.toString(Math.random());
    	serviceB.sendMessage(Arrays.asList(numberA), message, null);
    	
    	runner.setRunSchedule(500);
    	runner.run(10);
    	
    	runner.assertTransferCount(ConsumeSignalMessage.FAILURE, 0);

    	List<MockFlowFile> flowFiles = runner.getFlowFilesForRelationship(ConsumeSignalMessage.SUCCESS);
    	assertEquals(1, flowFiles.size());
    	MockFlowFile flowFile = flowFiles.get(0);
    	assertEquals(message, flowFile.getAttribute(ConsumeSignalMessage.ATTRIBUTE_MESSAGE));
    	assertEquals(numberB, flowFile.getAttribute(ConsumeSignalMessage.ATTRIBUTE_SENDER_NUMBER));
    }

    @Test
    public void consumeMessageVerified() throws ProcessException, InvocationTargetException, IOException, InterruptedException {
    	if(isSettingsEmpty()) {
    		IllegalStateException exc = new IllegalStateException("No configuration set, skipping test");
    		LOGGER.warn(exc.getMessage(), exc);
			return;
		}

    	runner.clearTransferState();
    	String message = "Testing: " + Double.toString(Math.random());
    	serviceB.sendMessage(Arrays.asList(numberA), message, null);
    	
    	runner.setProperty(ConsumeSignalMessage.IGNORE_UNVERIFIED_SENDER, Boolean.TRUE.toString());
    	
    	runner.setRunSchedule(500);
    	runner.run(10);
    	
    	runner.assertTransferCount(ConsumeSignalMessage.FAILURE, 0);

    	List<MockFlowFile> flowFiles = runner.getFlowFilesForRelationship(ConsumeSignalMessage.SUCCESS);
    	assertEquals(1, flowFiles.size());
    	MockFlowFile flowFile = flowFiles.get(0);
    	assertEquals(message, flowFile.getAttribute(ConsumeSignalMessage.ATTRIBUTE_MESSAGE));
    	assertEquals(numberB, flowFile.getAttribute(ConsumeSignalMessage.ATTRIBUTE_SENDER_NUMBER));
    	
    	assertEquals(VerifiedState.VERIFIED.toString(), flowFile.getAttribute(ConsumeSignalMessage.ATTRIBUTE_SENDER_VERIFIED));
    }
    
    @Test
    @Ignore("Make sure you are sending from an unverified number")
    public void consumeMessageUnverified() throws ProcessException, InvocationTargetException, IOException, InterruptedException {
    	if(isSettingsEmpty()) {
    		IllegalStateException exc = new IllegalStateException("No configuration set, skipping test");
    		LOGGER.warn(exc.getMessage(), exc);
			return;
		}

    	runner.clearTransferState();
    	String message = "Testing: " + Double.toString(Math.random());
    	serviceB.sendMessage(Arrays.asList(numberA), message, null);
    	
    	runner.setProperty(ConsumeSignalMessage.IGNORE_UNVERIFIED_SENDER, Boolean.TRUE.toString());
    	
    	runner.setRunSchedule(500);
    	runner.run(10);
    	
    	runner.assertTransferCount(ConsumeSignalMessage.SUCCESS, 0);
    	runner.assertTransferCount(ConsumeSignalMessage.FAILURE, 0);
    }
    
    @Test
    @Ignore("Make sure you are sending from an unverified number")
    public void consumeMessageUnverified2() throws ProcessException, InvocationTargetException, IOException, InterruptedException {
    	if(isSettingsEmpty()) {
    		IllegalStateException exc = new IllegalStateException("No configuration set, skipping test");
    		LOGGER.warn(exc.getMessage(), exc);
			return;
		}

    	runner.clearTransferState();
    	String message = "Testing: " + Double.toString(Math.random());
    	serviceB.sendMessage(Arrays.asList(numberA), message, null);
    	
    	runner.setProperty(ConsumeSignalMessage.IGNORE_UNVERIFIED_SENDER, Boolean.FALSE.toString());
    	
    	runner.setRunSchedule(500);
    	runner.run(10);
    	
    	runner.assertTransferCount(ConsumeSignalMessage.SUCCESS, 1);
    	runner.assertTransferCount(ConsumeSignalMessage.FAILURE, 0);

    	List<MockFlowFile> flowFiles = runner.getFlowFilesForRelationship(ConsumeSignalMessage.SUCCESS);
    	assertEquals(1, flowFiles.size());
    	MockFlowFile flowFile = flowFiles.get(0);
    	assertEquals(message, flowFile.getAttribute(ConsumeSignalMessage.ATTRIBUTE_MESSAGE));
    	assertEquals(numberB, flowFile.getAttribute(ConsumeSignalMessage.ATTRIBUTE_SENDER_NUMBER));
    	assertEquals(VerifiedState.DEFAULT.toString(), flowFile.getAttribute(ConsumeSignalMessage.ATTRIBUTE_SENDER_VERIFIED));
    }
    
    
    @Test
    public void consumeGroupMessage() throws ProcessException, InvocationTargetException, IOException, InterruptedException {
    	if(isSettingsEmpty() || TEST_GROUP == null || TEST_GROUP.isBlank()) {
    		IllegalStateException exc = new IllegalStateException("No configuration set, skipping test");
    		LOGGER.warn(exc.getMessage(), exc);
			return;
		}

    	runner.clearTransferState();
    	String message = "Testing: " + Double.toString(Math.random());
    	serviceB.sendGroupMessage(Arrays.asList(TEST_GROUP), message, null);
    	
    	runner.setRunSchedule(500);
    	runner.run(10);
    	
    	List<MockFlowFile> flowFiles = runner.getFlowFilesForRelationship(ConsumeSignalMessage.SUCCESS);
    	assertEquals(1, flowFiles.size());
    	MockFlowFile flowFile = flowFiles.get(0);
    	assertEquals(message, flowFile.getAttribute(ConsumeSignalMessage.ATTRIBUTE_MESSAGE));
    	assertEquals(numberB, flowFile.getAttribute(ConsumeSignalMessage.ATTRIBUTE_SENDER_NUMBER));
    	
    	assertNotNull(flowFile.getAttribute(ConsumeSignalMessage.ATTRIBUTE_MESSAGE_GROUP_ID));
    	assertNotEquals("", flowFile.getAttribute(ConsumeSignalMessage.ATTRIBUTE_MESSAGE_GROUP_ID));
    	assertEquals(TEST_GROUP, flowFile.getAttribute(ConsumeSignalMessage.ATTRIBUTE_MESSAGE_GROUP_TITLE));
    }

}
