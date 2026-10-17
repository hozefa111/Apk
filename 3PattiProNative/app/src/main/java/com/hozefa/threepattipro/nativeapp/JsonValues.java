package com.hozefa.threepattipro.nativeapp;
import org.json.*;
import java.util.*;
/** Decode durable local drafts without changing Firestore number/array semantics. */
final class JsonValues {
    private JsonValues(){}
    static Map<String,Object> object(JSONObject json)throws Exception{
        Map<String,Object> map=new LinkedHashMap<>();Iterator<String> keys=json.keys();
        while(keys.hasNext()){String key=keys.next();map.put(key,value(json.get(key)));}return map;
    }
    private static Object value(Object raw)throws Exception{
        if(raw==JSONObject.NULL)return null;if(raw instanceof JSONObject)return object((JSONObject)raw);
        if(raw instanceof JSONArray){List<Object> list=new ArrayList<>();for(int i=0;i<((JSONArray)raw).length();i++)list.add(value(((JSONArray)raw).get(i)));return list;}return raw;
    }
}
