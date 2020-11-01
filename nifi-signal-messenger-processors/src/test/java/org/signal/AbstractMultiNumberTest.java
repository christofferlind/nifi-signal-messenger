package org.signal;

import org.apache.nifi.reporting.InitializationException;
import org.apache.nifi.util.TestRunner;

public abstract class AbstractMultiNumberTest {

	protected String storePath = System.getenv("nifi-signal-messenger.test.data");
	protected String numberA = System.getenv("nifi-signal-messenger.test.number.a");
	protected String numberB = System.getenv("nifi-signal-messenger.test.number.b");
	
	protected SignalMessengerService serviceA = new SignalMessengerService();
	protected String serviceIdentifierA = "signalserviceA";

	protected SignalMessengerService serviceB = new SignalMessengerService();
	protected String serviceIdentifierB = "signalserviceB";

	public boolean isSettingsEmpty() {
		return storePath == null || "".equals(storePath) || numberA == null || "".equals(numberA) || numberB == null || "".equals(numberB);
	}

	protected void setSignaleService(TestRunner runner) throws InitializationException {
		runner.addControllerService(serviceIdentifierA, serviceA);
		runner.setProperty(serviceA, SignalMessengerService.PROP_STORE_PATH, storePath);
	    runner.setProperty(serviceA, SignalMessengerService.PROP_NUMBER, numberA);

	    runner.addControllerService(serviceIdentifierB, serviceB);
		runner.setProperty(serviceB, SignalMessengerService.PROP_STORE_PATH, storePath);
	    runner.setProperty(serviceB, SignalMessengerService.PROP_NUMBER, numberB);
	}

}
