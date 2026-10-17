package com.hozefa.threepattipro.nativeapp;
import org.junit.Test;
import org.json.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;
public class LegacyImportTest {
    private JSONObject source()throws Exception{return new JSONObject("{\"totalPlayers\":[{\"id\":1,\"name\":\"A\"},{\"id\":2,\"name\":\"B\"},{\"id\":3,\"name\":\"C\"}],\"activePlayers\":[\"1\",\"2\",\"3\"],\"history\":[{\"hukum\":1,\"partners\":[2],\"players\":[1,2,3],\"bid\":155,\"result\":true,\"ts\":1700000000000,\"deltas\":{\"1\":310,\"2\":155,\"3\":0}}]}");}
    @Test public void convertsPlayersAndPreservesHistoricalScores()throws Exception{byte[] raw=source().toString().getBytes(StandardCharsets.UTF_8);java.util.List<JSONObject> docs=BackupArchive.validate(LegacyImport.convert(raw,"demo-test"),"demo-test");assertEquals(4,docs.size());JSONObject scores=docs.get(3).getJSONObject("fields").getJSONObject("scoreChanges").getJSONObject("mapValue").getJSONObject("fields");assertEquals(3,scores.length());}
    @Test public void unknownPlayerRejected()throws Exception{JSONObject src=source();src.getJSONArray("history").getJSONObject(0).put("hukum",99);try{LegacyImport.convert(src.toString().getBytes(StandardCharsets.UTF_8),"demo-test");fail();}catch(java.io.IOException expected){}}
}
