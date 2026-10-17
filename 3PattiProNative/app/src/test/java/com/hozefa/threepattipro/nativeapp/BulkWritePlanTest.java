package com.hozefa.threepattipro.nativeapp;

import org.json.*;
import org.junit.Test;
import java.util.*;
import java.io.IOException;
import static org.junit.Assert.*;

public class BulkWritePlanTest {
    private final String root="projects/demo-test/databases/(default)/documents/";
    private JSONObject doc(int i)throws Exception{return new JSONObject().put("name",root+"rounds/r"+i).put("updateTime","version-"+i).put("fields",new JSONObject().put("bid",new JSONObject().put("integerValue","155")));}
    private JSONArray deletes(int count)throws Exception{
        JSONArray writes=new JSONArray();for(int i=0;i<count;i++)writes.put(new JSONObject().put("delete",root+"rounds/r"+i).put("currentDocument",new JSONObject().put("updateTime","version-"+i)));return writes;
    }
    private JSONArray creates(int count)throws Exception{
        JSONArray writes=new JSONArray();for(int i=0;i<count;i++){JSONObject d=doc(i);d.remove("updateTime");writes.put(new JSONObject().put("update",d).put("currentDocument",new JSONObject().put("exists",false)));}return writes;
    }
    @Test public void largePlanRoundTripsAndAcknowledgesBatches()throws Exception{
        BulkWritePlan plan=BulkWritePlan.create("demo-test","user","clear",deletes(1201));
        plan=BulkWritePlan.parse(plan.bytes(),"demo-test","user");int batches=0,confirmed=0;
        while(plan.remaining.length()>0){int count=plan.batch().length();assertTrue(count<=400);confirmed+=count;plan=plan.acknowledged(count);batches++;}
        assertEquals(4,batches);assertEquals(1201,confirmed);assertEquals(1201,plan.total);
    }
    @Test public void timeoutAfterDeleteCommitSkipsAlreadyMissingRecords()throws Exception{
        BulkWritePlan plan=BulkWritePlan.create("demo-test","user","clear",deletes(801));
        Map<String,JSONObject> live=new HashMap<>();for(int i=400;i<801;i++)live.put("rounds/r"+i,doc(i));
        assertEquals(401,plan.reconcile(live).remaining.length());
    }
    @Test public void timeoutAfterRestoreCommitSkipsIdenticalExistingRecords()throws Exception{
        BulkWritePlan plan=BulkWritePlan.create("demo-test","user","restore",creates(801));
        Map<String,JSONObject> live=new HashMap<>();for(int i=0;i<400;i++)live.put("rounds/r"+i,doc(i));
        assertEquals(401,plan.reconcile(live).remaining.length());
    }
    @Test public void laterConflictRejectsEntirePreflightWithoutChangingPlan()throws Exception{
        BulkWritePlan plan=BulkWritePlan.create("demo-test","user","clear",deletes(801));
        Map<String,JSONObject> live=new HashMap<>();for(int i=0;i<801;i++)live.put("rounds/r"+i,doc(i));
        live.get("rounds/r800").put("updateTime","newer");
        try{plan.reconcile(live);fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("Newer record"));}
        assertEquals(801,plan.remaining.length());
    }
    @Test public void restoreNeverOverwritesDifferentExistingContents()throws Exception{
        BulkWritePlan plan=BulkWritePlan.create("demo-test","user","restore",creates(801));
        JSONObject newer=doc(800);newer.getJSONObject("fields").put("bid",new JSONObject().put("integerValue","215"));
        try{plan.reconcile(Map.of("rounds/r800",newer));fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("conflict"));}
    }
    @Test public void differentAccountCannotResume()throws Exception{
        BulkWritePlan plan=BulkWritePlan.create("demo-test","owner","clear",deletes(1));
        try{BulkWritePlan.parse(plan.bytes(),"demo-test","other");fail();}catch(IOException expected){}
    }
    @Test public void deletePlayerAndForeignTargetsAreRejected()throws Exception{
        JSONArray writes=deletes(1);writes.getJSONObject(0).put("delete",root+"players/a");
        try{BulkWritePlan.create("demo-test","user","clear",writes);fail();}catch(IOException expected){}
        writes.getJSONObject(0).put("delete",root.replace("demo-test","production")+"rounds/a");
        try{BulkWritePlan.create("demo-test","user","clear",writes);fail();}catch(IOException expected){}
    }
    @Test public void unsafeRestorePreconditionRejected()throws Exception{
        JSONArray writes=creates(1);writes.getJSONObject(0).put("currentDocument",new JSONObject().put("exists",true));
        try{BulkWritePlan.create("demo-test","user","restore",writes);fail();}catch(IOException expected){}
    }
    @Test public void nestedTypedFieldsCompareRegardlessOfKeyOrder()throws Exception{
        assertTrue(BulkWritePlan.sameJson(new JSONObject("{\"x\":{\"integerValue\":\"155\"},\"y\":[1,2]}"),new JSONObject("{\"y\":[1,2],\"x\":{\"integerValue\":\"155\"}}")));
        assertFalse(BulkWritePlan.sameJson(new JSONArray("[1,2]"),new JSONArray("[2,1]")));
    }
    private JSONObject rename()throws Exception{
        return new JSONObject().put("update",new JSONObject().put("name",root+"rounds/r0").put("fields",new JSONObject().put("hukumName",new JSONObject().put("stringValue","New name"))))
            .put("updateMask",new JSONObject().put("fieldPaths",new JSONArray().put("hukumName"))).put("currentDocument",new JSONObject().put("updateTime","version-0"));
    }
    @Test public void renameRetryAcceptsAppliedMaskedFieldsButProtectsNewerNames()throws Exception{
        BulkWritePlan plan=BulkWritePlan.create("demo-test","user","rename",new JSONArray().put(rename()));
        JSONObject existing=doc(0).put("updateTime","after-commit");existing.getJSONObject("fields").put("hukumName",new JSONObject().put("stringValue","New name"));
        assertEquals(0,plan.reconcile(Map.of("rounds/r0",existing)).remaining.length());
        existing.getJSONObject("fields").put("hukumName",new JSONObject().put("stringValue","Another client name"));
        try{plan.reconcile(Map.of("rounds/r0",existing));fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("conflict"));}
    }
    @Test public void renameCannotModifyScores()throws Exception{
        JSONObject write=rename();write.getJSONObject("update").getJSONObject("fields").put("scoreChanges",new JSONObject().put("integerValue","0"));
        write.getJSONObject("updateMask").getJSONArray("fieldPaths").put("scoreChanges");
        try{BulkWritePlan.create("demo-test","user","rename",new JSONArray().put(write));fail();}catch(IOException expected){}
    }
}
