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
    public void consumeMessage() throws ProcessException, InvocationTargetException, IOException, InterruptedException {
    	if(isSettingsEmpty()) {
    		IllegalStateException exc = new IllegalStateException("No configuration set, skipping test");
    		LOGGER.warn(exc.getMessage(), exc);
			return;
		}

    	runner.clearTransferState();
    	String message = "Testing: " + Double.toString(Math.random());
    	serviceA.sendMessage(numberB, Arrays.asList(numberA), message, null);
    	
    	runner.setRunSchedule(250);
    	runner.run(10);
    	
    	runner.assertTransferCount(ConsumeSignalMessage.FAILURE, 0);

    	List<MockFlowFile> flowFiles = runner.getFlowFilesForRelationship(ConsumeSignalMessage.SUCCESS);
    	assertEquals(1, flowFiles.size());
    	MockFlowFile flowFile = flowFiles.get(0);
    	assertEquals(message, flowFile.getAttribute(Constants.ATTRIBUTE_MESSAGE));
    	assertEquals(numberB, flowFile.getAttribute(Constants.ATTRIBUTE_SENDER_NUMBER));
    }
    
    @Test
    @Ignore("Used for manual testing")
    public void consumeMessageManual() throws ProcessException, InvocationTargetException, IOException, InterruptedException {
    	if(isSettingsEmpty()) {
    		IllegalStateException exc = new IllegalStateException("No configuration set, skipping test");
    		LOGGER.warn(exc.getMessage(), exc);
			return;
		}

    	runner.clearTransferState();
    	
    	runner.setRunSchedule(2_000);
    	runner.run(10);
    	
    	runner.assertTransferCount(ConsumeSignalMessage.FAILURE, 0);

    	List<MockFlowFile> flowFiles = runner.getFlowFilesForRelationship(ConsumeSignalMessage.SUCCESS);
    	assertEquals(1, flowFiles.size());
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
    	serviceA.sendGroupMessage(numberB, Arrays.asList(TEST_GROUP), message, null);
    	
    	runner.setRunSchedule(500);
    	runner.run(10);
    	
    	List<MockFlowFile> flowFiles = runner.getFlowFilesForRelationship(ConsumeSignalMessage.SUCCESS);
    	assertEquals(1, flowFiles.size());
    	MockFlowFile flowFile = flowFiles.get(0);
    	assertEquals(message, flowFile.getAttribute(Constants.ATTRIBUTE_MESSAGE));
    	assertEquals(numberB, flowFile.getAttribute(Constants.ATTRIBUTE_SENDER_NUMBER));
    	
    	assertNotNull(flowFile.getAttribute(Constants.ATTRIBUTE_MESSAGE_GROUP_ID));
    	assertNotEquals("", flowFile.getAttribute(Constants.ATTRIBUTE_MESSAGE_GROUP_ID));
    	assertEquals(TEST_GROUP, flowFile.getAttribute(Constants.ATTRIBUTE_MESSAGE_GROUP_TITLE));
    }

}
