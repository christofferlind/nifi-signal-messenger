package org.signal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import org.apache.nifi.util.MockFlowFile;
import org.junit.Test;

public class TestUtilMethods {
	
    @Test
    public void testSplitComma() {
		List<String> list = PutSignalMessage.getCommaSeparatedList("1,2,3,4");
    	assertNotNull(list);
    	assertEquals(4, list.size());
    	assertArrayEquals(new String[] {"1","2","3","4"}, list.toArray());
    }

	@Test
    public void testSplitCommaWithSpace() {
		List<String> list = PutSignalMessage.getCommaSeparatedList("1 , 2 , 3 , 4 ");
    	assertNotNull(list);
    	assertEquals(4, list.size());
    	assertArrayEquals(new String[] {"1","2","3","4"}, list.toArray());
    }

	@Test
    public void testSplitCommaWithEmpty() {
		List<String> list = PutSignalMessage.getCommaSeparatedList("1 ,, 2 , , , 3 , 4 ");
    	assertNotNull(list);
    	assertEquals(4, list.size());
    	assertArrayEquals(new String[] {"1","2","3","4"}, list.toArray());
    }

	@Test
    public void testSplitCommaEmpty() {
		List<String> list = PutSignalMessage.getCommaSeparatedList("");
    	assertNotNull(list);
    	assertEquals(0, list.size());
    }

	@Test
    public void testSplitCommaNull() {
		List<String> list = PutSignalMessage.getCommaSeparatedList(null);
    	assertNotNull(list);
    	assertEquals(0, list.size());
    }

	public static final void assertBasicAttributes(MockFlowFile flowFile, String numberA, String numberB) {
		flowFile.assertAttributeEquals(Constants.ATTRIBUTE_SENDER_NUMBER, numberB);
		flowFile.assertAttributeExists(Constants.ATTRIBUTE_TIMESTAMP);
		flowFile.assertAttributeExists(Constants.ATTRIBUTE_TIMESTAMP_STRING);
		flowFile.assertAttributeEquals(Constants.ATTRIBUTE_ACCOUNT_NUMBER, numberA);
		flowFile.assertAttributeEquals(Constants.ATTRIBUTE_RECEIVING_NUMBER, numberA);
		flowFile.assertAttributeExists(Constants.ATTRIBUTE_SENDER_VERIFIED);
		
		flowFile.assertAttributeNotEquals(Constants.ATTRIBUTE_TIMESTAMP_STRING, "");
	}

}
