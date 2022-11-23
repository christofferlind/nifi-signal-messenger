package org.signal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

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

}
