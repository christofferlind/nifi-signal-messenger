package org.signal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.apache.nifi.util.MockFlowFile;
import org.junit.Test;

public class TestUtilMethods {
	
    @Test
    public void testSplitComma() {
		Optional<List<String>> list = Constants.getCommaSeparatedList("1,2,3,4");
    	assertNotNull(list);
    	assertTrue(list.isPresent());
    	assertEquals(4, list.get().size());
    	assertArrayEquals(new String[] {"1","2","3","4"}, list.get().toArray());
    }

	@Test
    public void testSplitCommaWithSpace() {
		Optional<List<String>> list = Constants.getCommaSeparatedList("1 , 2 , 3 , 4 ");
    	assertNotNull(list);
    	assertTrue(list.isPresent());
    	assertEquals(4, list.get().size());
    	assertArrayEquals(new String[] {"1","2","3","4"}, list.get().toArray());
    }

	@Test
    public void testSplitCommaWithEmpty() {
		Optional<List<String>> list = Constants.getCommaSeparatedList("1 ,, 2 , , , 3 , 4 ");
    	assertNotNull(list);
    	assertTrue(list.isPresent());
    	assertEquals(4, list.get().size());
    	assertArrayEquals(new String[] {"1","2","3","4"}, list.get().toArray());
    }

	@Test
    public void testSplitCommaEmpty() {
		Optional<List<String>> list = Constants.getCommaSeparatedList("");
    	assertNotNull(list);
    	assertTrue(list.isEmpty());
    }

	@Test
    public void testSplitCommaNull() {
		Optional<List<String>> list = Constants.getCommaSeparatedList(null);
    	assertNotNull(list);
    	assertTrue(list.isEmpty());
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
