package org.signal;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;

public class TestUtilMethods {
    public static final String getAndWait(AtomicReference<String> refContent) throws InterruptedException {
    	Instant maxWait = Instant.now().plus(5, ChronoUnit.SECONDS);
    	while(!Thread.currentThread().isInterrupted()) {
    		String result = refContent.get();
    		if(result != null)
    			return result;
    		
    		Thread.sleep(101);
    		
    		if(Instant.now().isAfter(maxWait))
    			break;
    	}
    	
    	return null;
	}


    
//	@Test
//    public void testSplitComma() {
//		List<String> list = PutSignalMessage.getCommaSeparatedList("1,2,3,4");
//    	assertNotNull(list);
//    	assertEquals(4, list.size());
//    	assertArrayEquals(new String[] {"1","2","3","4"}, list.toArray());
//    }
//
//	@Test
//    public void testSplitCommaWithSpace() {
//		List<String> list = PutSignalMessage.getCommaSeparatedList("1 , 2 , 3 , 4 ");
//    	assertNotNull(list);
//    	assertEquals(4, list.size());
//    	assertArrayEquals(new String[] {"1","2","3","4"}, list.toArray());
//    }
//
//	@Test
//    public void testSplitCommaWithEmpty() {
//		List<String> list = PutSignalMessage.getCommaSeparatedList("1 ,, 2 , , , 3 , 4 ");
//    	assertNotNull(list);
//    	assertEquals(4, list.size());
//    	assertArrayEquals(new String[] {"1","2","3","4"}, list.toArray());
//    }
//
//	@Test
//    public void testSplitCommaEmpty() {
//		List<String> list = PutSignalMessage.getCommaSeparatedList("");
//    	assertNotNull(list);
//    	assertEquals(0, list.size());
//    }
//
//	@Test
//    public void testSplitCommaNull() {
//		List<String> list = PutSignalMessage.getCommaSeparatedList(null);
//    	assertNotNull(list);
//    	assertEquals(0, list.size());
//    }

}
