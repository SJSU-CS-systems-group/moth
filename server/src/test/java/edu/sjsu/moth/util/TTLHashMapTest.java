package edu.sjsu.moth.util;

import edu.sjsu.moth.server.util.Util;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

public class TTLHashMapTest {
    @Test
    public void testTTL() throws InterruptedException {
        var ttlMap = new Util.TTLHashMap<Integer, Integer>(10, TimeUnit.MILLISECONDS);
        // timing is terrible in unit tests! trying to make it course. we will run for 50ms
        // and just check that the first put is gone and the last is there.
        int last = 0;
        for (int i = 0; i < 50; i++) {
            ttlMap.put(i, i);
            Thread.sleep(1);
            last = i;
        }
        Assertions.assertNull(ttlMap.get(0));
        Assertions.assertEquals(last, ttlMap.get(last));
        var future = ttlMap.scheduled;
        ttlMap = null;

        // flail at garbage collector
        System.gc();
        Runtime.getRuntime().gc();

        // give task time to cancel
        Thread.sleep(20);
        Assertions.assertTrue(future.isDone());
    }
}
