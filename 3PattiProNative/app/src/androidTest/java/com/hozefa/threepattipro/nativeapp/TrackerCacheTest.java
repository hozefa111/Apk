package com.hozefa.threepattipro.nativeapp;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.*;
import org.junit.*;
import static org.junit.Assert.*;

public class TrackerCacheTest {
    private Context context;private String name;private TrackerCache cache;
    @Before public void setup(){
        Assume.assumeTrue(BuildConfig.USE_EMULATORS);
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        name="tracker-test-"+UUID.randomUUID()+".db";cache=new TrackerCache(context,name);
    }
    @After public void cleanup(){if(cache!=null)cache.close();if(context!=null)context.deleteDatabase(name);}
    @Test public void oneChangedRowIsOneWriteAndColdReopenRetainsExactData(){
        Map<String,String> data=new LinkedHashMap<>();for(int i=0;i<500;i++)data.put("rounds/"+i,"{\"score\":"+i+"}");
        assertNull(cache.read());assertEquals(500,cache.write(data));assertEquals(0,cache.write(data));
        data.put("rounds/250","{\"score\":-78}");assertEquals(1,cache.write(data));
        data.remove("rounds/0");data.put("sessions/new","{\"endTime\":null}");assertEquals(2,cache.write(data));
        cache.close();cache=new TrackerCache(context,name);assertEquals(data,cache.read());
    }
    @Test public void failedTransactionKeepsPreviousCompleteSnapshot(){
        Map<String,String> old=new LinkedHashMap<>();old.put("rounds/a","{\"score\":310}");cache.write(old);
        Map<String,String> invalid=new LinkedHashMap<>();invalid.put("rounds/a","{\"score\":-155}");invalid.put("rounds/invalid",null);
        try{cache.write(invalid);fail("Invalid cache row must abort the transaction");}catch(android.database.sqlite.SQLiteException expected){}
        assertEquals(old,cache.read());
        Map<String,String> valid=new LinkedHashMap<>();valid.put("rounds/a","{\"score\":-155}");assertEquals(1,cache.write(valid));
    }
    @Test public void completedEmptyCacheDoesNotResurrectClearedHistory(){
        Map<String,String> data=new HashMap<>();data.put("rounds/old","{}");cache.write(data);
        assertEquals(1,cache.write(Collections.emptyMap()));cache.close();cache=new TrackerCache(context,name);
        assertNotNull(cache.read());assertTrue(cache.read().isEmpty());
    }
}
