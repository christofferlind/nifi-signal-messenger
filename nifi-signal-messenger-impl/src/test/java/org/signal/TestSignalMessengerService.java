/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.signal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.lang.Thread.State;
import java.lang.reflect.InvocationTargetException;

import org.apache.nifi.reporting.InitializationException;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestSignalMessengerService {
	private static final Logger LOGGER = LoggerFactory.getLogger(TestSignalMessengerService.class);
	private String storePath = System.getenv("nifi-signal-messenger.test.data");
	private String number = System.getenv("nifi-signal-messenger.test.number");
	
    private TestRunner runner;
	private SignalMessengerService service;

	@Before
    public void init() throws InitializationException {
        runner = TestRunners.newTestRunner(TestSignalMessengerServiceProcessor.class);
        service = new SignalMessengerService();
        runner.addControllerService("signalservice", service);
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
        
        Thread.sleep(100);
        assertEquals(State.TIMED_WAITING, service.receiveMessagesThread.getState());

        assertEquals(number, service.getSignalUsername());
        assertNotNull(service.getMessageSender());
        runner.run();
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
		runner.setProperty(service, SignalMessengerService.PROP_STORE_PATH, storePath);
        runner.setProperty(service, SignalMessengerService.PROP_NUMBER, number);
	}

	public boolean isSettingsEmpty() {
		return storePath == null || "".equals(storePath) || number == null || "".equals(number);
	}
}
