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

import org.apache.nifi.reporting.InitializationException;
import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestPutSignalMessage extends AbstractSignalTest {
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
    public void putMessage() {
    	if(isSettingsEmpty()) {
    		IllegalStateException exc = new IllegalStateException("No configuration set, skipping test");
    		LOGGER.warn(exc.getMessage(), exc);
			return;
		}

    	runner.clearTransferState();
    	runner.setProperty(PutSignalMessage.RECIPIENTS, number);
    	runner.setProperty(PutSignalMessage.MESSAGE_CONTENT, "Testing " + PutSignalMessage.class.getSimpleName());
    	runner.enqueue(new byte[0]);
    	runner.run();
    	runner.assertAllFlowFilesTransferred(PutSignalMessage.SUCCESS);
    }

    @Test
    public void putMessageFail() {
    	if(isSettingsEmpty()) {
    		IllegalStateException exc = new IllegalStateException("No configuration set, skipping test");
    		LOGGER.warn(exc.getMessage(), exc);
			return;
		}

    	runner.clearTransferState();
    	runner.setProperty(PutSignalMessage.RECIPIENTS, number+"12332,"+number);
    	runner.setProperty(PutSignalMessage.MESSAGE_CONTENT, "Testing " + PutSignalMessage.class.getSimpleName());
    	runner.enqueue(new byte[0]);
    	runner.run();
    	runner.assertAllFlowFilesTransferred(PutSignalMessage.FAILURE, 1);
    	MockFlowFile ff = runner.getFlowFilesForRelationship(PutSignalMessage.FAILURE).get(0);
    	assertEquals(Boolean.toString(true), ff.getAttribute("signal.send.failed.unregistered"));
    }

}
