package com.hozefa.threepattipro.nativeapp;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class ManualSessionGuardTest {
 private Map<String,Object> active(){return SessionLeaderboard.FirebaseMap.of("endTime",null);}
 @Test public void switchesRequireClosingPreviousSessionAndOrdinaryEditsKeepPointer(){
  Map<String,Map<String,Object>> updates=new LinkedHashMap<>();updates.put("two",active());try{ManualSessionGuard.next("one",updates);fail();}catch(IllegalStateException expected){}
  updates.put("one",SessionLeaderboard.FirebaseMap.of("endTime",new Date()));assertEquals("two",ManualSessionGuard.next("one",updates));updates.clear();assertEquals("one",ManualSessionGuard.next("one",updates));updates.put("one",null);assertNull(ManualSessionGuard.next("one",updates));
 }
 @Test public void duplicateActiveRestoreIsRejectedAndOnlineRoomsAreExcluded(){
  Map<String,Map<String,Object>> updates=new LinkedHashMap<>();updates.put("one",active());updates.put("two",active());try{ManualSessionGuard.next(null,updates);fail();}catch(IllegalStateException expected){}updates.remove("two");updates.put("cf2_room",active());updates.put("legacy",SessionLeaderboard.FirebaseMap.of("source","online","endTime",null));assertEquals("one",ManualSessionGuard.next(null,updates));
 }
}
