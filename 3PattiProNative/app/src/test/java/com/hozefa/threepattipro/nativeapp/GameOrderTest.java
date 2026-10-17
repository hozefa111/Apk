package com.hozefa.threepattipro.nativeapp;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class GameOrderTest {
    @Test public void nanosecondRoundOrderingMatchesFirestore(){Map<String,Object> older=new HashMap<>(Map.of("id","z","timestamp","2026-10-17T00:00:00.000001Z")),newer=new HashMap<>(Map.of("id","a","timestamp","2026-10-17T00:00:00.000002Z"));List<Map<String,Object>> rows=new ArrayList<>(Arrays.asList(older,newer));rows.sort(GameOrder.ROUNDS);assertSame(newer,rows.get(0));}
    @Test public void exactRoundTimeTieUsesDescendingDocumentId(){Map<String,Object> a=Map.of("id","a","timestamp","2026-10-17T00:00:00Z"),b=Map.of("id","b","timestamp","2026-10-17T00:00:00Z");assertTrue(GameOrder.ROUNDS.compare(b,a)<0);}
    @Test public void activeSessionPrecedesCompletedThenNewestEnd(){Map<String,Object> active=new HashMap<>(Map.of("id","active","startTime","2026-10-15T00:00:00Z"));active.put("endTime",null);Map<String,Object> earlier=Map.of("id","earlier","startTime","2026-10-16T00:00:00Z","endTime","2026-10-17T01:00:00Z"),later=Map.of("id","later","startTime","2026-10-14T00:00:00Z","endTime","2026-10-17T02:00:00Z");List<Map<String,Object>> rows=new ArrayList<>(Arrays.asList(earlier,active,later));rows.sort(GameOrder.SESSIONS);assertSame(active,rows.get(0));assertSame(later,rows.get(1));}
}
