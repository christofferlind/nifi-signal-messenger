package org.signal;

import org.apache.nifi.reporting.InitializationException;
import org.apache.nifi.util.TestRunner;

public abstract class AbstractSignalTest {

	protected String storePath = System.getenv("nifi-signal-messenger.test.data");
	protected String number = System.getenv("nifi-signal-messenger.test.number");
	
	protected SignalMessengerService service = new SignalMessengerService();
	protected String serviceIdentifier = "signalservice";

	public boolean isSettingsEmpty() {
		return storePath == null || "".equals(storePath) || number == null || "".equals(number);
	}

	protected void setSignaleService(TestRunner runner) throws InitializationException {
		runner.addControllerService(serviceIdentifier, service);
		runner.setProperty(service, SignalMessengerService.PROP_STORE_PATH, storePath);
	    runner.setProperty(service, SignalMessengerService.PROP_NUMBER, number);
	}

}
