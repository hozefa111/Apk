package com.hozefa.threepattipro.nativeapp;
import org.junit.Test;import static org.junit.Assert.*;import java.util.*;
public class TrackerCacheIntegrityTest {
 @Test public void deterministicAcrossMapOrderAndCounts(){Map<String,String>a=new LinkedHashMap<>();a.put("players/p","{\"name\":\"नाम\"}");a.put("rounds/r","{\"score\":360}");Map<String,String>b=new LinkedHashMap<>();b.put("rounds/r",a.get("rounds/r"));b.put("players/p",a.get("players/p"));TrackerCacheIntegrity x=new TrackerCacheIntegrity(a,7,12),y=new TrackerCacheIntegrity(b,7,12);assertEquals(x.sha256,y.sha256);assertEquals(2,x.total);assertEquals(Long.valueOf(1),x.counts.get("rounds"));assertEquals(Long.valueOf(0),x.counts.get("sessions"));}
 @Test public void rowsAndCheckpointAreBoundTogether(){Map<String,String>m=new HashMap<>();m.put("rounds/r","{}");String base=new TrackerCacheIntegrity(m,7,12).sha256;assertNotEquals(base,new TrackerCacheIntegrity(m,8,12).sha256);assertNotEquals(base,new TrackerCacheIntegrity(m,7,13).sha256);m.put("rounds/r","{\"score\":-999}");assertNotEquals(base,new TrackerCacheIntegrity(m,7,12).sha256);m.clear();assertNotEquals(base,new TrackerCacheIntegrity(m,7,12).sha256);}
 @Test(expected=IllegalArgumentException.class) public void privateGameplayNeverEntersTrackerCache(){new TrackerCacheIntegrity(Collections.singletonMap("onlineRooms/room/private/player","{}"),7,12);}
}
