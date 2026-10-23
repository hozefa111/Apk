package com.hozefa.threepattipro.nativeapp;

import org.json.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Content token checked inside an SDK transaction, never sent as a Firestore updateTime. */
final class DocumentVersion {
    static String of(JSONObject fields)throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(canonical(fields).getBytes(StandardCharsets.UTF_8));
        StringBuilder out=new StringBuilder("sdk1:");
        final char[] hex="0123456789abcdef".toCharArray();
        for(byte b:digest){int v=b&255;out.append(hex[v>>>4]).append(hex[v&15]);}
        return out.toString();
    }
    private static String canonical(Object value)throws Exception {
        if(value instanceof JSONObject){
            JSONObject object=(JSONObject)value;List<String> keys=new ArrayList<>();
            Iterator<String> iterator=object.keys();while(iterator.hasNext())keys.add(iterator.next());Collections.sort(keys);
            StringJoiner out=new StringJoiner(",","{","}");
            for(String key:keys)out.add(JSONObject.quote(key)+":"+canonical(object.get(key)));
            return out.toString();
        }
        if(value instanceof JSONArray){StringJoiner out=new StringJoiner(",","[","]");JSONArray array=(JSONArray)value;for(int i=0;i<array.length();i++)out.add(canonical(array.get(i)));return out.toString();}
        if(value==JSONObject.NULL||value==null)return "null";
        if(value instanceof String)return JSONObject.quote((String)value);
        return String.valueOf(value);
    }
}
