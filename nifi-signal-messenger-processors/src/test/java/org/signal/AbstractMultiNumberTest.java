package org.signal;

import org.apache.nifi.reporting.InitializationException;
import org.apache.nifi.util.TestRunner;

public abstract class AbstractMultiNumberTest {

	protected String url = System.getenv("nifi-signal-messenger.test.url");
	protected String numberA = System.getenv("nifi-signal-messenger.test.number.a");
	protected String numberB = System.getenv("nifi-signal-messenger.test.number.b");

	protected String numberManual = System.getenv("nifi-signal-messenger.test.number.manual");
	
	protected SignalMessengerService serviceA = new SignalMessengerService();
	protected String serviceIdentifierA = "signalserviceA";

	public boolean isSettingsEmpty() {
		return url == null || "".equals(url) || numberA == null || "".equals(numberA) || numberB == null || "".equals(numberB);
	}
	
	public static final boolean isEnvironmentEmpty(String string) {
		if(string == null || string.isBlank())
			return true;
		
		String tmp= System.getenv(string);
		return tmp == null || tmp.isBlank();
	}


	protected void setSignaleService(TestRunner runner) throws InitializationException {
		runner.addControllerService(serviceIdentifierA, serviceA);
		runner.setProperty(serviceA, SignalMessengerService.PROP_DAEMON_URL, url);
	}

}
