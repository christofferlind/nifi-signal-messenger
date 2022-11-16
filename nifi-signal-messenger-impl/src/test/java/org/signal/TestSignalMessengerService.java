package org.signal;

import static org.junit.Assert.assertFalse;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.apache.nifi.reporting.InitializationException;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.Before;
import org.junit.Test;
import org.signal.model.SignalGroup;
import org.signal.model.SignalIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestSignalMessengerService {
	private static final Logger LOGGER = LoggerFactory.getLogger(TestSignalMessengerService.class);

	private String url = System.getenv("nifi-signal-messenger.test.url");
	
	protected String numberA = System.getenv("nifi-signal-messenger.test.number.a");
	
    private TestRunner runner;
	private SignalMessengerService service;

	@Before
    public void init() throws InitializationException {
        runner = TestRunners.newTestRunner(TestSignalMessengerServiceProcessor.class);
        service = new SignalMessengerService();
        runner.addControllerService("signalservice", service);
        runner.setProperty(service, SignalMessengerService.PROP_DAEMON_URL, url);
    }

    @Test
    public void enableDisable() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InterruptedException {
    	if(isSettingsEmpty()) {
    		IllegalStateException exc = new IllegalStateException("No configuration set, skipping test");
    		LOGGER.warn(exc.getMessage(), exc);
			return;
		}

    	resetRunner();

        runner.enableControllerService(service);

        runner.assertValid(service);
        
        Thread.sleep(500);
//        assertEquals(State.TIMED_WAITING, service.receiveMessagesThread.getState());
//
//        assertEquals(number, service.getSignalUsername());
//        assertNotNull(service.getMessageSender());
        runner.run();
        runner.disableControllerService(service);
        
        assertFalse(runner.isControllerServiceEnabled(service));
    }

    @Test
    public void testListIdentities() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InterruptedException, UnsupportedOperationException, IOException, ExecutionException {
    	if(isSettingsEmpty()) {
    		IllegalStateException exc = new IllegalStateException("No configuration set, skipping test");
    		LOGGER.warn(exc.getMessage(), exc);
			return;
		}
    	
    	if(numberA == null || numberA.isBlank()) {
    		IllegalStateException exc = new IllegalStateException("No configuration set, skipping test");
    		LOGGER.warn(exc.getMessage(), exc);
			return;
    	}

    	resetRunner();

        runner.enableControllerService(service);

        runner.assertValid(service);
        
        runner.run();
        
        Map<String, SignalIdentity> idents = service.getIdentities(numberA);
        assertFalse(idents.isEmpty());
        
        runner.disableControllerService(service);
        
        assertFalse(runner.isControllerServiceEnabled(service));
    }

    @Test
    public void testListGroups() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InterruptedException, UnsupportedOperationException, IOException, ExecutionException {
    	if(isSettingsEmpty()) {
    		IllegalStateException exc = new IllegalStateException("No configuration set, skipping test");
    		LOGGER.warn(exc.getMessage(), exc);
			return;
		}
    	
    	if(numberA == null || numberA.isBlank()) {
    		IllegalStateException exc = new IllegalStateException("No configuration set, skipping test");
    		LOGGER.warn(exc.getMessage(), exc);
			return;
    	}

    	resetRunner();

        runner.enableControllerService(service);

        runner.assertValid(service);
        
        runner.run();
        
        Map<String, SignalGroup> groups = service.getGroups(numberA);
        assertFalse(groups.isEmpty());
        
        runner.disableControllerService(service);
        
        assertFalse(runner.isControllerServiceEnabled(service));
    }

    private void resetRunner() {
		runner.clearProperties();
    	runner.clearTransferState();
        runner.setProperty(TestSignalMessengerServiceProcessor.CLIENT_SERVICE, "signalservice");
    	setServiceProperties();
	}

	private void setServiceProperties() {
        runner.setProperty(service, SignalMessengerService.PROP_DAEMON_URL, url);
	}

	public boolean isSettingsEmpty() {
		return url == null || "".equals(url);
	}
}
