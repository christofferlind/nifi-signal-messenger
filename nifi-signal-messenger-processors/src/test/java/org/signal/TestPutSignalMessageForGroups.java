package org.signal;

import org.apache.nifi.reporting.InitializationException;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestPutSignalMessageForGroups extends AbstractSignalTest {
	private static final String TEST_GROUP = System.getenv("nifi-signal-messenger.test.group");
	
	private static final Logger LOGGER = LoggerFactory.getLogger(TestPutSignalMessage.class);

	private TestRunner runner;

	@Before
	public void init() throws InitializationException {
		if(isSettingsEmpty()) {
			return;
		}

		runner = TestRunners.newTestRunner(PutSignalMessage.class);

		setSignaleService(runner);
		runner.setProperty(PutSignalMessage.SIGNAL_SERVICE, serviceIdentifier);
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
	public void putGroupMessage() {
		if(isSettingsEmpty()) {
			IllegalStateException exc = new IllegalStateException("No configuration set, skipping test");
			LOGGER.warn(exc.getMessage(), exc);
			return;
		}

		runner.clearTransferState();
		runner.setProperty(PutSignalMessage.GROUPS, TEST_GROUP);
		runner.setProperty(PutSignalMessage.MESSAGE_CONTENT, "Testing Group Message");
		runner.enqueue(new byte[0]);
		runner.run();
		runner.assertAllFlowFilesTransferred(PutSignalMessage.SUCCESS);
	}

	@Test
	public void putGroupMessageMissingGroup() {
		if(isSettingsEmpty()) {
			IllegalStateException exc = new IllegalStateException("No configuration set, skipping test");
			LOGGER.warn(exc.getMessage(), exc);
			return;
		}

		runner.clearTransferState();
		runner.setProperty(PutSignalMessage.GROUPS, TEST_GROUP + " missing" + Math.random()*10_000);
		runner.setProperty(PutSignalMessage.MESSAGE_CONTENT, "Testing Group Message");
		runner.enqueue(new byte[0]);
		runner.run();
		runner.assertAllFlowFilesTransferred(PutSignalMessage.FAILURE);
	}
}
