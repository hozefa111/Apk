package com.hozefa.threepattipro.nativeapp;
import org.junit.Test;
import org.json.*;
import java.util.*;
import java.io.IOException;
import static org.junit.Assert.*;

public class SessionDeletePlanTest {
    private SessionDeletePlan plan()throws Exception{return SessionDeletePlan.create("demo-test","admin",Arrays.asList(Map.of("id","one","sessionName","First"),Map.of("id","two","sessionName","Second")),Arrays.asList(new JSONObject().put("previewToken","hash-one"),new JSONObject().put("previewToken","hash-two")));}
    @Test public void crashAndRetryKeepOriginalCommandsAndConfirmOnlyAcknowledgedSessions()throws Exception{
        SessionDeletePlan p=plan(),restored=SessionDeletePlan.parse(p.bytes(),"demo-test","admin");
        assertEquals(p.remaining.toString(),restored.remaining.toString());
        String second=p.remaining.getJSONObject(1).getString("commandId");
        restored=SessionDeletePlan.parse(restored.acknowledged().bytes(),"demo-test","admin");
        assertEquals(1,restored.remaining.length());assertEquals(2,restored.total);assertEquals(second,restored.remaining.getJSONObject(0).getString("commandId"));
        assertEquals(0,SessionDeletePlan.parse(restored.acknowledged().bytes(),"demo-test","admin").remaining.length());
    }
    @Test public void wrongAccountOrProjectCannotResume()throws Exception{
        for(String[] pair:new String[][]{{"other","admin"},{"demo-test","other"}})try{SessionDeletePlan.parse(plan().bytes(),pair[0],pair[1]);fail();}catch(IOException expected){}
    }
    @Test public void duplicatesOrMissingPreviewsRejectedBeforeAnyDeletion()throws Exception{
        try{SessionDeletePlan.create("demo","admin",Arrays.asList(Map.of("id","one"),Map.of("id","one")),Arrays.asList(new JSONObject().put("previewToken","x"),new JSONObject().put("previewToken","y")));fail();}catch(IOException expected){}
        try{SessionDeletePlan.create("demo","admin",List.of(Map.of("id","one")),Collections.emptyList());fail();}catch(IOException expected){}
    }
    @Test public void selectedProfileAndRoundDeletesUseExistingPreconditionsAndProtectOnlineHands()throws Exception{
        String root="projects/demo-test/databases/(default)/documents/";
        JSONArray ops=new JSONArray().put(new JSONObject().put("delete",root+"players/a").put("currentDocument",new JSONObject().put("updateTime","v")));
        BulkWritePlan p=BulkWritePlan.create("demo-test","admin","delete",ops);
        assertEquals(1,p.reconcile(Map.of("players/a",new JSONObject().put("updateTime","v").put("fields",new JSONObject()))).remaining.length());
        assertEquals(0,p.reconcile(Collections.emptyMap()).remaining.length());
        try{p.reconcile(Map.of("players/a",new JSONObject().put("updateTime","newer")));fail();}catch(IOException expected){}
        ops.getJSONObject(0).put("delete",root+"sessions/s");try{BulkWritePlan.create("demo-test","admin","delete",ops);fail();}catch(IOException expected){}
        ops.getJSONObject(0).put("delete",root+"rounds/r");p=BulkWritePlan.create("demo-test","admin","delete",ops);
        try{p.reconcile(Map.of("rounds/r",new JSONObject().put("updateTime","v").put("fields",new JSONObject().put("source",new JSONObject().put("stringValue","online")))));fail();}catch(IOException expected){}
    }
}
