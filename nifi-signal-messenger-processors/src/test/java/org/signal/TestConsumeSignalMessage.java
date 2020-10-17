package org.signal;

import org.apache.nifi.reporting.InitializationException;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestConsumeSignalMessage extends AbstractSignalTest{
	private static final Logger LOGGER = LoggerFactory.getLogger(TestConsumeSignalMessage.class);

    private TestRunner runner;

    @Before
    public void init() throws InitializationException {
    	if(isSettingsEmpty()) {
    		return;
    	}
    	
		runner = TestRunners.newTestRunner(ConsumeSignalMessage.class);
        String identifier = createSignaleService(runner);
        runner.setProperty(ConsumeSignalMessage.SIGNAL_SERVICE, identifier);
        runner.enableControllerService(service);
    }
    
    @After
    public void deactivate() throws InitializationException {
    	if(runner == null)
    		return;
    	
    	if(runner.isControllerServiceEnabled(service)) {
			runner.disableControllerService(service);
		}
    }

    @Test
    public void consumeMessage() {
    	if(isSettingsEmpty()) {
    		IllegalStateException exc = new IllegalStateException("No configuration set, skipping test");
    		LOGGER.warn(exc.getMessage(), exc);
			return;
		}

    	runner.clearTransferState();
    	runner.run(10);
    	runner.assertAllFlowFilesTransferred(PutSignalMessage.SUCCESS);
    }

}
