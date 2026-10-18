package com.hozefa.threepattipro.nativeapp;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.*;
import org.json.*;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

/** Lossless portable archive values, separate from the UI's display values. */
final class SdkDocuments {
    static JSONObject fields(Map<String,Object> data)throws Exception {
        JSONObject out=new JSONObject();for(Map.Entry<String,Object> entry:data.entrySet())out.put(entry.getKey(),value(entry.getValue()));return out;
    }
    private static JSONObject value(Object v)throws Exception {
        JSONObject out=new JSONObject();
        if(v==null)return out.put("nullValue",JSONObject.NULL);
        if(v instanceof Timestamp){Timestamp t=(Timestamp)v;return out.put("timestampValue",Instant.ofEpochSecond(t.getSeconds(),t.getNanoseconds()).toString());}
        if(v instanceof Date)return out.put("timestampValue",((Date)v).toInstant().toString());
        if(v instanceof String)return out.put("stringValue",v);
        if(v instanceof Boolean)return out.put("booleanValue",v);
        if(v instanceof Double||v instanceof Float){double d=((Number)v).doubleValue();return out.put("doubleValue",Double.isNaN(d)?"NaN":d==Double.POSITIVE_INFINITY?"Infinity":d==Double.NEGATIVE_INFINITY?"-Infinity":d);}
        if(v instanceof Number)return out.put("integerValue",String.valueOf(v));
        if(v instanceof Blob)return out.put("bytesValue",Base64.getEncoder().encodeToString(((Blob)v).toBytes()));
        if(v instanceof GeoPoint){GeoPoint p=(GeoPoint)v;return out.put("geoPointValue",new JSONObject().put("latitude",p.getLatitude()).put("longitude",p.getLongitude()));}
        if(v instanceof DocumentReference){DocumentReference r=(DocumentReference)v;return out.put("referenceValue","projects/"+r.getFirestore().getApp().getOptions().getProjectId()+"/databases/(default)/documents/"+r.getPath());}
        if(v instanceof Map){@SuppressWarnings("unchecked") Map<String,Object> map=(Map<String,Object>)v;return out.put("mapValue",new JSONObject().put("fields",fields(map)));}
        if(v instanceof List){JSONArray list=new JSONArray();for(Object item:(List<?>)v)list.put(value(item));return out.put("arrayValue",new JSONObject().put("values",list));}
        throw new IOException("Unsupported Firestore value: "+v.getClass().getSimpleName());
    }
    static Map<String,Object> decode(JSONObject fields,FirebaseFirestore db)throws Exception {
        Map<String,Object> out=new LinkedHashMap<>();Iterator<String> keys=fields.keys();while(keys.hasNext()){String key=keys.next();out.put(key,decodeValue(fields.getJSONObject(key),db));}return out;
    }
    private static Object decodeValue(JSONObject v,FirebaseFirestore db)throws Exception {
        if(v.has("nullValue"))return null;
        if(v.has("stringValue"))return v.getString("stringValue");
        if(v.has("integerValue"))return Long.parseLong(v.getString("integerValue"));
        if(v.has("doubleValue"))return Double.valueOf(v.get("doubleValue").toString());
        if(v.has("booleanValue"))return v.getBoolean("booleanValue");
        if(v.has("timestampValue")){Instant t=Instant.parse(v.getString("timestampValue"));return new Timestamp(t.getEpochSecond(),t.getNano());}
        if(v.has("bytesValue")){String s=v.getString("bytesValue");byte[] b;try{b=Base64.getDecoder().decode(s);}catch(IllegalArgumentException e){b=Base64.getUrlDecoder().decode(s);}return Blob.fromBytes(b);}
        if(v.has("geoPointValue")){JSONObject p=v.getJSONObject("geoPointValue");return new GeoPoint(p.optDouble("latitude",0),p.optDouble("longitude",0));}
        if(v.has("referenceValue")){String prefix="projects/"+db.getApp().getOptions().getProjectId()+"/databases/(default)/documents/";String path=v.getString("referenceValue");if(!path.startsWith(prefix))throw new IOException("Cross-project reference cannot be restored");return db.document(path.substring(prefix.length()));}
        if(v.has("mapValue"))return decode(v.getJSONObject("mapValue").optJSONObject("fields")==null?new JSONObject():v.getJSONObject("mapValue").getJSONObject("fields"),db);
        if(v.has("arrayValue")){List<Object> out=new ArrayList<>();JSONArray values=v.getJSONObject("arrayValue").optJSONArray("values");if(values!=null)for(int i=0;i<values.length();i++)out.add(decodeValue(values.getJSONObject(i),db));return out;}
        throw new IOException("Unsupported archive value");
    }
}
