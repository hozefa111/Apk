package com.hozefa.threepattipro.nativeapp;

import org.json.*;
import org.junit.Test;
import java.util.*;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class WebSessionParityTest {
    @SuppressWarnings("unchecked")
    @Test public void completeSessionAwardsMatchOriginalHH()throws Exception{
        String source;try(InputStream in=getClass().getResourceAsStream("/web-session-vectors.json")){assertNotNull(in);source=new String(in.readAllBytes(),StandardCharsets.UTF_8);}
        JSONArray vectors=new JSONObject(source).getJSONArray("vectors");assertEquals(64,vectors.length());
        for(int i=0;i<vectors.length();i++){
            JSONObject vector=vectors.getJSONObject(i);Map<String,Object> input=JsonValues.object(vector);
            Map<String,Object> actual=SessionSummary.compute((List<Map<String,Object>>)input.get("players"),(List<Map<String,Object>>)input.get("rounds"));
            assertTrue(vector.getString("name")+" expected="+vector.getJSONObject("expected")+" actual="+new JSONObject(actual),
                BulkWritePlan.sameJson(vector.getJSONObject("expected"),new JSONObject(actual)));
        }
    }
}
