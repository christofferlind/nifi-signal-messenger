package org.signal;

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

public class TestDeleteSignalMessage extends AbstractMultiNumberTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(TestDeleteSignalMessage.class);
	
	private TestRunner runner;

	@Before
    public void init() throws InitializationException {
		if(isSettingsEmpty()) {
			return;
		}
		
        runner = TestRunners.newTestRunner(DeleteSignalMessage.class);

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
    public void deleteMessageManual() throws InterruptedException, InitializationException {
    	if(isSettingsEmpty()) {
    		IllegalStateException exc = new IllegalStateException("No configuration set, skipping test");
    		LOGGER.warn(exc.getMessage(), exc);
			return;
		}
    	
    	String content = "THIS MESSAGE WILL BE DELETED " + TestPutSignalMessage.class.getSimpleName() + " " + Math.random();

    	TestRunner runnerPut = TestRunners.newTestRunner(PutSignalMessage.class);
        setSignaleService(runnerPut);
        runnerPut.setProperty(AbstractSignalSenderProcessor.PROP_SIGNAL_SERVICE, serviceIdentifierA);
        runnerPut.enableControllerService(serviceA);
    	
    	runnerPut.clearTransferState();
    	runnerPut.setProperty(AbstractSignalSenderProcessor.PROP_ACCOUNT, numberA);
    	runnerPut.setProperty(AbstractSignalSenderProcessor.PROP_RECIPIENTS, numberManual);
    	runnerPut.setProperty(PutSignalMessage.PROP_MESSAGE_CONTENT, content);
    	runnerPut.enqueue(new byte[0]);
    	runnerPut.run();

    	runnerPut.assertAllFlowFilesTransferred(AbstractSignalSenderProcessor.SUCCESS, 1);
    	
    	MockFlowFile flowFile = runnerPut.getFlowFilesForRelationship(AbstractSignalSenderProcessor.SUCCESS).get(0);
    	flowFile.assertAttributeNotEquals(Constants.ATTRIBUTE_TIMESTAMP, "");
    	
    	runner.setProperty(AbstractSignalSenderProcessor.PROP_ACCOUNT, numberA);
    	runner.setProperty(AbstractSignalSenderProcessor.PROP_RECIPIENTS, numberManual);
    	runner.setProperty(DeleteSignalMessage.PROP_TIMESTAMP, flowFile.getAttribute(Constants.ATTRIBUTE_TIMESTAMP));
    	runner.enqueue(flowFile.getData());
    	runner.run();
    	
    	runner.assertAllFlowFilesTransferred(AbstractSignalSenderProcessor.SUCCESS, 1);
    }


    @Test
    public void deleteMessage() throws InterruptedException, InitializationException {
    	if(isSettingsEmpty()) {
    		IllegalStateException exc = new IllegalStateException("No configuration set, skipping test");
    		LOGGER.warn(exc.getMessage(), exc);
    		return;
    	}

    	String content = "THIS MESSAGE WILL BE DELETED " + TestPutSignalMessage.class.getSimpleName() + " " + Math.random();

    	TestRunner runnerPut = TestRunners.newTestRunner(PutSignalMessage.class);
    	setSignaleService(runnerPut);
    	runnerPut.setProperty(AbstractSignalSenderProcessor.PROP_SIGNAL_SERVICE, serviceIdentifierA);
    	runnerPut.enableControllerService(serviceA);

    	runnerPut.clearTransferState();
    	runnerPut.setProperty(AbstractSignalSenderProcessor.PROP_ACCOUNT, numberA);
    	runnerPut.setProperty(AbstractSignalSenderProcessor.PROP_RECIPIENTS, numberB);
    	runnerPut.setProperty(PutSignalMessage.PROP_MESSAGE_CONTENT, content);
    	runnerPut.enqueue(new byte[0]);
    	runnerPut.run();

    	runnerPut.assertAllFlowFilesTransferred(AbstractSignalSenderProcessor.SUCCESS, 1);

    	MockFlowFile flowFile = runnerPut.getFlowFilesForRelationship(AbstractSignalSenderProcessor.SUCCESS).get(0);
    	flowFile.assertAttributeNotEquals(Constants.ATTRIBUTE_TIMESTAMP, "");

    	runner.setProperty(AbstractSignalSenderProcessor.PROP_ACCOUNT, numberA);
    	runner.setProperty(AbstractSignalSenderProcessor.PROP_RECIPIENTS, numberB);
    	runner.setProperty(DeleteSignalMessage.PROP_TIMESTAMP, flowFile.getAttribute(Constants.ATTRIBUTE_TIMESTAMP));
    	runner.enqueue(flowFile.getData());
    	runner.run();

    	runner.assertAllFlowFilesTransferred(AbstractSignalSenderProcessor.SUCCESS, 1);
    }

}
