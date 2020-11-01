package org.signal;

import static org.junit.Assert.assertEquals;

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
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestConsumeSignalMessage extends AbstractMultiNumberTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(TestConsumeSignalMessage.class);

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
    	runner.run(3);
    	
    	List<MockFlowFile> flowFiles = runner.getFlowFilesForRelationship(ConsumeSignalMessage.SUCCESS);
    	assertEquals(1, flowFiles.size());
    	MockFlowFile flowFile = flowFiles.get(0);
    	assertEquals(message, flowFile.getAttribute("signal.message"));
    	assertEquals(numberB, flowFile.getAttribute("signal.sender.number"));
    }

}
