package com.hozefa.threepattipro.nativeapp;
import org.junit.Test;
import org.json.*;
import java.util.*;
import static org.junit.Assert.*;
public class DataSafetyTest {
    private Map<String,Object> round(){Map<String,Object> r=new HashMap<>();r.put("type","hukum");r.put("bid",155);r.put("hukumId","a");r.put("partnerIds",Arrays.asList("b"));r.put("nonPartnerIds",Arrays.asList("c"));r.put("playerIds",Arrays.asList("a","b","c"));r.put("result","win");r.put("scoreChanges",new HashMap<>(Map.of("a",310,"b",155,"c",0)));return r;}
    @Test public void retryAcceptsFirestoreLongNumbers(){Map<String,Object> a=round(),b=round();b.put("bid",155L);b.put("scoreChanges",Map.of("a",310L,"b",155L,"c",0L));assertTrue(FirebaseGateway.sameRound(a,b));}
    @Test public void retryRejectsDifferentPayload(){Map<String,Object> a=round(),b=round();b.put("result","loss");assertFalse(FirebaseGateway.sameRound(a,b));b=round();b.put("scoreChanges",Map.of("a",999,"b",155,"c",0));assertFalse(FirebaseGateway.sameRound(a,b));}
    @Test public void retryIgnoresTimestampAndDisplayNames(){Map<String,Object> a=round(),b=round();a.put("timestamp","before");b.put("timestamp","after");a.put("hukumName","Old");b.put("hukumName","Renamed");assertTrue(FirebaseGateway.sameRound(a,b));}
    @Test public void zeroDoesNotBreakWebHotStreak(){List<Map<String,Object>> rounds=new ArrayList<>();for(int score:new int[]{155,0,155,155,-155,155}){Map<String,Object> r=round();r.put("scoreChanges",Map.of("a",score));rounds.add(r);}Collections.reverse(rounds);assertEquals(3,RoundAnalytics.longestPositiveStreak("a",rounds));}
    @Test public void settlementDoesNotExtendHotStreak(){Map<String,Object> r=round();r.put("type","settlement");assertEquals(0,RoundAnalytics.longestPositiveStreak("a",Arrays.asList(r,r,r)));}
    private byte[] archive(String project,JSONArray docs)throws Exception{return new JSONObject().put("format","3patti-firestore-v1").put("project",project).put("documents",docs).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);}
    private JSONObject doc(String path)throws Exception{return new JSONObject().put("name","projects/demo-test/databases/(default)/documents/"+path).put("fields",new JSONObject().put("name",new JSONObject().put("stringValue","Player")));}
    @Test public void archivePreservesTypedFields()throws Exception{JSONObject d=doc("players/a");d.getJSONObject("fields").put("createdAt",new JSONObject().put("timestampValue","2026-01-01T00:00:00Z"));assertEquals(1,BackupArchive.validate(archive("demo-test",new JSONArray().put(d)),"demo-test").size());}
    @Test public void foreignProjectRejected()throws Exception{try{BackupArchive.validate(archive("another",new JSONArray()),"demo-test");fail();}catch(java.io.IOException expected){}}
    @Test public void duplicateRecordsRejected()throws Exception{try{BackupArchive.validate(archive("demo-test",new JSONArray().put(doc("players/a")).put(doc("players/a"))),"demo-test");fail();}catch(java.io.IOException expected){}}
    @Test public void arbitraryCollectionRejected()throws Exception{try{BackupArchive.validate(archive("demo-test",new JSONArray().put(doc("secrets/a"))),"demo-test");fail();}catch(java.io.IOException expected){}}
    @Test public void largeArchiveIsValidatedForBatchedRestore()throws Exception{JSONArray docs=new JSONArray();for(int i=0;i<1201;i++)docs.put(doc("players/p"+i));assertEquals(1201,BackupArchive.validate(archive("demo-test",docs),"demo-test").size());}
    @Test public void durableDraftRetainsScoringPayload()throws Exception{Map<String,Object> original=round();Map<String,Object> restored=JsonValues.object(new JSONObject(new JSONObject(original).toString()));assertTrue(FirebaseGateway.sameRound(original,restored));}
    @Test public void backupFingerprintUsesVersionsInExportedBytes()throws Exception{JSONObject a=doc("players/a").put("updateTime","old");byte[] before=archive("demo-test",new JSONArray().put(a));String fingerprint=BackupArchive.fingerprint(before,"demo-test");a.put("updateTime","new");assertNotEquals(fingerprint,BackupArchive.fingerprint(archive("demo-test",new JSONArray().put(a)),"demo-test"));assertTrue(fingerprint.contains("old"));}
}
