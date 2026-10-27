package com.hozefa.threepattipro.nativeapp;

import org.json.*;
import java.util.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/** Strict portable Firestore archive; never contains tokens or account credentials. */
public final class BackupArchive {
    private BackupArchive(){}
    public static JSONObject parseJson(byte[] bytes)throws Exception{
        if(bytes.length==0||bytes.length>16_000_000)throw new IOException("Backup must be between 1 byte and 16 MB");
        String json=StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        // Bound parser recursion before constructing untrusted JSON, including legacy imports.
        int depth=0;boolean quoted=false,escaped=false;
        for(int i=0;i<json.length();i++){char c=json.charAt(i);
            if(quoted){if(escaped)escaped=false;else if(c=='\\')escaped=true;else if(c=='\"')quoted=false;}
            else if(c=='\"')quoted=true;
            else if(c=='{'||c=='['){if(++depth>128)throw new IOException("Backup JSON nesting is too deep");}
            else if(c=='}'||c==']'){if(--depth<0)throw new IOException("Invalid backup JSON");}
        }
        if(quoted||depth!=0)throw new IOException("Incomplete backup JSON");
        JSONTokener tokener=new JSONTokener(json);Object parsed=tokener.nextValue();
        if(!(parsed instanceof JSONObject)||tokener.nextClean()!=0)throw new IOException("Choose one complete JSON backup object");
        return (JSONObject)parsed;
    }
    public static String path(JSONObject document,String project)throws Exception{
        String prefix="projects/"+project+"/databases/(default)/documents/";
        String name=document.getString("name");
        if(!name.startsWith(prefix))throw new IOException("Backup belongs to another Firebase project");
        String path=name.substring(prefix.length());String[] parts=path.split("/",-1);
        if(parts.length!=2 || !Arrays.asList("players","rounds","sessions").contains(parts[0]) || parts[1].isEmpty() || parts[1].equals(".") || parts[1].equals(".."))throw new IOException("Invalid backup document path");
        if(utf8(parts[1])>1500||RESERVED.matcher(parts[1]).matches())throw new IOException("Invalid Firestore document ID");
        return path;
    }
    public static String fingerprint(byte[] bytes,String project)throws Exception{
        JSONObject backup=parseJson(bytes);
        if(!project.equals(backup.getString("project")))throw new IOException("Wrong project");
        JSONArray documents=backup.getJSONArray("documents");Map<String,String> versions=new TreeMap<>();
        for(int i=0;i<documents.length();i++){JSONObject doc=documents.getJSONObject(i);versions.put(path(doc,project),doc.getString("updateTime"));}
        return versions.toString();
    }
    public static List<JSONObject> validate(byte[] bytes,String project)throws Exception{
        JSONObject backup=parseJson(bytes);
        if(!"3patti-firestore-v1".equals(backup.optString("format")) || !project.equals(backup.optString("project")))throw new IOException("Choose a 3 Patti PRO backup for this Firebase project");
        JSONArray documents=backup.getJSONArray("documents");
        List<JSONObject> result=new ArrayList<>();Set<String> paths=new HashSet<>();
        for(int i=0;i<documents.length();i++){
            JSONObject doc=documents.getJSONObject(i);String path=path(doc,project);
            if(!paths.add(path))throw new IOException("Duplicate backup record");
            JSONObject fields=doc.getJSONObject("fields");long size=validateFields(fields,0);
            // Firestore document size = name (segments + 16), fields, and 32-byte overhead.
            for(String segment:path.split("/"))size+=utf8(segment)+1;
            if(size+48>1_048_576)throw new IOException("Backup record exceeds Firestore's 1 MiB document limit: "+path);
            if(path.startsWith("players/")){
                String name=fields.getJSONObject("name").getString("stringValue");
                if(name.trim().isEmpty()||name.length()>200)throw new IOException("Invalid player name in backup");
            }
            result.add(doc);
        }
        return result;
    }
    private static final java.util.regex.Pattern RESERVED=java.util.regex.Pattern.compile("__.*__");
    private static final java.util.regex.Pattern INTEGER=java.util.regex.Pattern.compile("-?(0|[1-9][0-9]*)");
    private static int utf8(String value)throws IOException{
        int size=0;
        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);
            if(c<0x80)size++;else if(c<0x800)size+=2;
            else if(Character.isHighSurrogate(c)){if(i+1>=value.length()||!Character.isLowSurrogate(value.charAt(++i)))throw new IOException("Invalid Unicode in backup");size+=4;}
            else if(Character.isLowSurrogate(c))throw new IOException("Invalid Unicode in backup");else size+=3;
        }
        return size;
    }
    private static long validateFields(JSONObject fields,int depth)throws Exception{
        if(depth>20)throw new IOException("Backup nesting is too deep");
        Iterator<String> keys=fields.keys();long size=0;
        while(keys.hasNext()){
            String key=keys.next();if(key.isEmpty()||utf8(key)>1500||RESERVED.matcher(key).matches())throw new IOException("Invalid Firestore field name");
            size+=utf8(key)+1L+validateValue(fields.getJSONObject(key),depth,false);
        }
        return size;
    }
    private static long validateValue(JSONObject value,int depth,boolean arrayElement)throws Exception{
        if(depth>20||value.length()!=1)throw new IOException("Invalid Firestore value or nesting");
        String type=value.keys().next();Object raw=value.get(type);
        switch(type){
            case "nullValue":
                if(raw!=JSONObject.NULL&&!"NULL_VALUE".equals(raw)&&!Integer.valueOf(0).equals(raw))throw new IOException("Invalid null field");return 1;
            case "stringValue":
                if(!(raw instanceof String))throw new IOException("Invalid string field");return utf8((String)raw)+1L;
            case "integerValue":
                if(!(raw instanceof String)&&!(raw instanceof Number))throw new IOException("Invalid integer field");
                String integer=String.valueOf(raw);if(!INTEGER.matcher(integer).matches())throw new IOException("Invalid integer field");
                try{Long.parseLong(integer);}catch(NumberFormatException e){throw new IOException("Integer outside Firestore range",e);}return 8;
            case "doubleValue":
                if(!(raw instanceof Number)&&!Arrays.asList("NaN","Infinity","-Infinity").contains(raw))throw new IOException("Invalid floating-point field");return 8;
            case "booleanValue":if(!(raw instanceof Boolean))throw new IOException("Invalid boolean field");return 1;
            case "timestampValue":
                if(!(raw instanceof String))throw new IOException("Invalid timestamp field");
                try{Instant time=Instant.parse((String)raw);if(time.isBefore(Instant.parse("0001-01-01T00:00:00Z"))||time.isAfter(Instant.parse("9999-12-31T23:59:59.999999999Z")))throw new IOException("Timestamp outside Firestore range");}
                catch(java.time.format.DateTimeParseException e){throw new IOException("Invalid timestamp field",e);}return 8;
            case "bytesValue":
                if(!(raw instanceof String))throw new IOException("Invalid bytes field");
                try{return Base64.getDecoder().decode((String)raw).length;}catch(IllegalArgumentException first){
                    try{return Base64.getUrlDecoder().decode((String)raw).length;}catch(IllegalArgumentException e){throw new IOException("Invalid base64 bytes",e);}}
            case "referenceValue":
                if(!(raw instanceof String)||!((String)raw).matches("projects/[^/]+/databases/[^/]+/documents/[^/]+/[^/]+(/[^/]+/[^/]+)*"))throw new IOException("Invalid document reference");
                long reference=16;String suffix=((String)raw).split("/documents/",2)[1];for(String part:suffix.split("/"))reference+=utf8(part)+1L;return reference;
            case "geoPointValue":
                JSONObject point=value.getJSONObject(type);if(point.length()>2)throw new IOException("Invalid location field");
                Iterator<String> pointKeys=point.keys();while(pointKeys.hasNext()){String key=pointKeys.next();if(!Arrays.asList("latitude","longitude").contains(key)||!(point.get(key) instanceof Number))throw new IOException("Invalid location coordinate");}
                double latitude=point.optDouble("latitude",0),longitude=point.optDouble("longitude",0);
                if(!Double.isFinite(latitude)||!Double.isFinite(longitude)||Math.abs(latitude)>90||Math.abs(longitude)>180)throw new IOException("Location outside valid range");return 16;
            case "mapValue":
                JSONObject map=value.getJSONObject(type);if(map.length()>(map.has("fields")?1:0))throw new IOException("Invalid map field");
                return 32+validateFields(map.has("fields")?map.getJSONObject("fields"):new JSONObject(),depth+1);
            case "arrayValue":
                if(arrayElement)throw new IOException("Firestore does not support directly nested arrays");
                JSONObject array=value.getJSONObject(type);if(array.length()>(array.has("values")?1:0))throw new IOException("Invalid array field");
                JSONArray entries=array.has("values")?array.getJSONArray("values"):new JSONArray();long size=0;
                for(int i=0;i<entries.length();i++)size+=validateValue(entries.getJSONObject(i),depth+1,true);return size;
            default:throw new IOException("Unknown Firestore field type");
        }
    }
}
