package com.hozefa.threepattipro.nativeapp;

import org.json.*;
import org.junit.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class ArchiveValidationTest {
    private byte[] archive(JSONObject value)throws Exception{
        JSONObject fields=new JSONObject().put("value",value);
        return new JSONObject().put("format","3patti-firestore-v1").put("project","demo-test").put("documents",new JSONArray().put(new JSONObject().put("name","projects/demo-test/databases/(default)/documents/rounds/a").put("fields",fields))).toString().getBytes(StandardCharsets.UTF_8);
    }
    private void rejects(JSONObject value)throws Exception{
        try{BackupArchive.validate(archive(value),"demo-test");fail("Must reject "+value);}catch(java.io.IOException expected){}
    }
    @Test public void malformedPrimitiveTypesRejected()throws Exception{
        rejects(new JSONObject().put("integerValue","not a number"));rejects(new JSONObject().put("integerValue","9223372036854775808"));
        rejects(new JSONObject().put("booleanValue","true"));rejects(new JSONObject().put("doubleValue","oops"));
        rejects(new JSONObject().put("stringValue",123));rejects(new JSONObject().put("nullValue","null"));
    }
    @Test public void invalidTimestampsAndLocationsRejected()throws Exception{
        rejects(new JSONObject().put("timestampValue","yesterday"));rejects(new JSONObject().put("timestampValue","0000-01-01T00:00:00Z"));
        rejects(new JSONObject().put("geoPointValue",new JSONObject().put("latitude",91)));
        rejects(new JSONObject().put("geoPointValue",new JSONObject().put("longitude",181)));
        rejects(new JSONObject().put("geoPointValue",new JSONObject().put("altitude",4)));
    }
    @Test public void invalidBytesAndReferencesRejected()throws Exception{
        rejects(new JSONObject().put("bytesValue","not base64!"));rejects(new JSONObject().put("referenceValue","players/a"));
    }
    @Test public void directNestedArraysRejectedButMapsInsideArraysAllowed()throws Exception{
        JSONObject emptyArray=new JSONObject().put("arrayValue",new JSONObject());
        rejects(new JSONObject().put("arrayValue",new JSONObject().put("values",new JSONArray().put(emptyArray))));
        JSONObject map=new JSONObject().put("mapValue",new JSONObject().put("fields",new JSONObject().put("nested",emptyArray)));
        assertEquals(1,BackupArchive.validate(archive(new JSONObject().put("arrayValue",new JSONObject().put("values",new JSONArray().put(map)))),"demo-test").size());
    }
    @Test public void oversizeFirestoreDocumentRejectedBeforeBatching()throws Exception{
        rejects(new JSONObject().put("stringValue","x".repeat(1_048_576)));
    }
    @Test public void containersCannotHideUnknownPayloadFields()throws Exception{
        rejects(new JSONObject().put("arrayValue",new JSONObject().put("unexpected",new JSONArray())));
        rejects(new JSONObject().put("mapValue",new JSONObject().put("unexpected",new JSONObject())));
        try{BackupArchive.parseJson(("{\"deep\":"+"[".repeat(200)+"0"+"]".repeat(200)+"}").getBytes(StandardCharsets.UTF_8));fail();}catch(java.io.IOException expected){}
        try{BackupArchive.parseJson(new byte[]{(byte)0xff});fail();}catch(java.io.IOException expected){}
        String invalidUnicode=new String(archive(new JSONObject().put("stringValue","MARKER")),StandardCharsets.UTF_8).replace("MARKER","\\uD800");
        try{BackupArchive.validate(invalidUnicode.getBytes(StandardCharsets.UTF_8),"demo-test");fail();}catch(java.io.IOException expected){}
        try{BackupArchive.parseJson("{}{}".getBytes(StandardCharsets.UTF_8));fail();}catch(java.io.IOException expected){}
    }
    @Test public void allPortableFirestoreTypesRemainSupported()throws Exception{
        JSONObject[] values={new JSONObject().put("integerValue","-9223372036854775808"),new JSONObject().put("doubleValue","NaN"),new JSONObject().put("nullValue",JSONObject.NULL),new JSONObject().put("booleanValue",false),new JSONObject().put("timestampValue","2026-10-17T03:00:00.123456789Z"),new JSONObject().put("bytesValue","YWJj"),new JSONObject().put("referenceValue","projects/demo-test/databases/(default)/documents/players/a"),new JSONObject().put("geoPointValue",new JSONObject().put("latitude",-90).put("longitude",180))};
        for(JSONObject value:values)assertEquals(1,BackupArchive.validate(archive(value),"demo-test").size());
    }
}
