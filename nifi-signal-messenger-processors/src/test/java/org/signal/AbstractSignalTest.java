package org.signal;

import org.apache.nifi.reporting.InitializationException;
import org.apache.nifi.util.TestRunner;

public abstract class AbstractSignalTest {

	protected String url = System.getenv("nifi-signal-messenger.test.url");
	protected String number = System.getenv("nifi-signal-messenger.test.number");
	
	protected SignalMessengerService service = new SignalMessengerService();
	protected String serviceIdentifier = "signalservice";

	public boolean isSettingsEmpty() {
		return url == null || "".equals(url) || number == null || "".equals(number);
	}

	protected void setSignaleService(TestRunner runner) throws InitializationException {
		runner.addControllerService(serviceIdentifier, service);
		runner.setProperty(service, SignalMessengerService.PROP_DAEMON_URL, url);
	}

}
