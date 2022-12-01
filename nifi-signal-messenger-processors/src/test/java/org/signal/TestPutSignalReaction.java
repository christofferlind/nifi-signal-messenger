package org.signal;

import java.util.Map;

import org.apache.nifi.reporting.InitializationException;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestPutSignalReaction extends AbstractMultiNumberTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(TestPutSignalReaction.class);
	
	private TestRunner runner;

	@Before
    public void init() throws InitializationException {
		if(isSettingsEmpty()) {
			return;
		}
		
        runner = TestRunners.newTestRunner(PutSignalReaction.class);

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
    @Ignore("Manual test")
    public void putReaction() throws InterruptedException {
    	if(isSettingsEmpty()) {
    		IllegalStateException exc = new IllegalStateException("No configuration set, skipping test");
    		LOGGER.warn(exc.getMessage(), exc);
			return;
		}
    	
    	String envName = "nifi-signal-messenger.test.reaction.timestamp";
    	
		if(AbstractMultiNumberTest.isEnvironmentEmpty(envName)) {
    		IllegalStateException exc = new IllegalStateException("No configuration set, skipping test");
    		LOGGER.warn(exc.getMessage(), exc);
    		return;
    	}
    	
    	
    	Map<String, String> attributes = Map.of(
    			Constants.ATTRIBUTE_SENDER_NUMBER, numberManual,
    			Constants.ATTRIBUTE_TIMESTAMP, System.getenv(envName)
    			);

    	runner.clearTransferState();
    	runner.setProperty(AbstractSignalSenderProcessor.PROP_SIGNAL_SERVICE, serviceIdentifierA);
    	runner.setProperty(AbstractSignalSenderProcessor.PROP_ACCOUNT, numberA);
    	runner.setProperty(PutSignalReaction.PROP_REACTION_EMOJI, "0x1F44D");
		runner.enqueue(new byte[0], attributes);
    	runner.run();

    	runner.assertAllFlowFilesTransferred(AbstractSignalSenderProcessor.SUCCESS, 1);
    }

    @Test
    @Ignore("Manual test")
    public void putReactionRemove() throws InterruptedException {
    	if(isSettingsEmpty()) {
    		IllegalStateException exc = new IllegalStateException("No configuration set, skipping test");
    		LOGGER.warn(exc.getMessage(), exc);
    		return;
    	}

    	String envName = "nifi-signal-messenger.test.reaction.timestamp";
    	
    	if(AbstractMultiNumberTest.isEnvironmentEmpty(envName)) {
    		IllegalStateException exc = new IllegalStateException("No configuration set, skipping test");
    		LOGGER.warn(exc.getMessage(), exc);
    		return;
    	}

    	Map<String, String> attributes = Map.of(
    			Constants.ATTRIBUTE_SENDER_NUMBER, numberManual,
    			Constants.ATTRIBUTE_TIMESTAMP, System.getenv(envName)
    			);

    	runner.clearTransferState();
    	runner.setProperty(AbstractSignalSenderProcessor.PROP_SIGNAL_SERVICE, serviceIdentifierA);
    	runner.setProperty(AbstractSignalSenderProcessor.PROP_ACCOUNT, numberA);
    	runner.setProperty(PutSignalReaction.PROP_REACTION_EMOJI, "0x1F44D");
    	runner.setProperty(PutSignalReaction.PROP_REMOVE_REACTION, Boolean.toString(Boolean.TRUE));
    	
    	runner.enqueue(new byte[0], attributes);
    	runner.run();

    	runner.assertAllFlowFilesTransferred(AbstractSignalSenderProcessor.SUCCESS, 1);
    }
}
