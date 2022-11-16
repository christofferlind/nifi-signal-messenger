package org.signal;

import java.util.ArrayList;
import java.util.List;

import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.exception.ProcessException;

public class TestSignalMessengerServiceProcessor extends AbstractProcessor {
    static final PropertyDescriptor CLIENT_SERVICE = new PropertyDescriptor.Builder()
            .name("signalservice")
            .description("Signal Messenger Service")
            .identifiesControllerService(SignalMessengerService.class)
            .required(true)
            .build();


    @Override
    public void onTrigger(ProcessContext context, ProcessSession session) throws ProcessException {
    }

    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        List<PropertyDescriptor> propDescs = new ArrayList<>();
        propDescs.add(CLIENT_SERVICE);
        return propDescs;
    }
}
