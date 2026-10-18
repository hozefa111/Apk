package com.hozefa.threepattipro.nativeapp;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class DocumentVersionTest {
    @Test public void mapOrderDoesNotCauseFalseConflicts()throws Exception {
        assertEquals(DocumentVersion.of(new JSONObject("{\"b\":{\"z\":2,\"a\":1},\"a\":1}")),DocumentVersion.of(new JSONObject("{\"a\":1,\"b\":{\"a\":1,\"z\":2}}")));
    }
    @Test public void everyFieldTypeAndArrayOrderAffectsTheToken()throws Exception {
        for(String text:new String[]{"{\"x\":1}","{\"x\":\"1\"}","{\"x\":null}","{\"x\":false}","{\"x\":[1,2]}","{\"x\":[2,1]}"}){
            assertNotEquals(DocumentVersion.of(new JSONObject()),DocumentVersion.of(new JSONObject(text)));
        }
        assertNotEquals(DocumentVersion.of(new JSONObject("{\"x\":[1,2]}")),DocumentVersion.of(new JSONObject("{\"x\":[2,1]}")));
        assertNotEquals(DocumentVersion.of(new JSONObject("{\"x\":1}")),DocumentVersion.of(new JSONObject("{\"x\":\"1\"}")));
    }
    @Test public void oldArchiveTimestampFormattingRemainsCompatible()throws Exception {
        assertTrue(BulkWritePlan.sameJson(new JSONObject("{\"timestampValue\":\"2026-10-18T00:00:00.123000000Z\"}"),new JSONObject("{\"timestampValue\":\"2026-10-18T00:00:00.123Z\"}")));
        assertFalse(BulkWritePlan.sameJson(new JSONObject("{\"timestampValue\":\"2026-10-18T00:00:00.123000001Z\"}"),new JSONObject("{\"timestampValue\":\"2026-10-18T00:00:00.123Z\"}")));
    }
    @Test public void timestampPrecisionAndUnknownFieldsAreProtected()throws Exception {
        JSONObject first=new JSONObject("{\"t\":{\"timestampValue\":\"2026-10-18T00:00:00.000000001Z\"}}");
        JSONObject other=new JSONObject("{\"t\":{\"timestampValue\":\"2026-10-18T00:00:00.000000002Z\"}}");
        assertNotEquals(DocumentVersion.of(first),DocumentVersion.of(other));
        assertNotEquals(DocumentVersion.of(first),DocumentVersion.of(new JSONObject(first.toString()).put("extra",true)));
        assertTrue(DocumentVersion.of(first).matches("sdk1:[0-9a-f]{64}"));
    }
}
