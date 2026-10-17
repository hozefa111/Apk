package com.hozefa.threepattipro.nativeapp;

import org.json.*;
import java.util.*;
import java.io.IOException;

/** Strict portable Firestore archive; never contains tokens or account credentials. */
public final class BackupArchive {
    private BackupArchive(){}
    public static final int MAX_DOCUMENTS=500;
    public static String path(JSONObject document,String project)throws Exception{
        String prefix="projects/"+project+"/databases/(default)/documents/";
        String name=document.getString("name");
        if(!name.startsWith(prefix))throw new IOException("Backup belongs to another Firebase project");
        String path=name.substring(prefix.length());String[] parts=path.split("/",-1);
        if(parts.length!=2 || !Arrays.asList("players","rounds","sessions").contains(parts[0]) || parts[1].isEmpty() || parts[1].equals(".") || parts[1].equals(".."))throw new IOException("Invalid backup document path");
        return path;
    }
    public static String fingerprint(byte[] bytes,String project)throws Exception{
        JSONObject backup=new JSONObject(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));
        if(!project.equals(backup.getString("project")))throw new IOException("Wrong project");
        JSONArray documents=backup.getJSONArray("documents");Map<String,String> versions=new TreeMap<>();
        for(int i=0;i<documents.length();i++){JSONObject doc=documents.getJSONObject(i);versions.put(path(doc,project),doc.getString("updateTime"));}
        return versions.toString();
    }
    public static List<JSONObject> validate(byte[] bytes,String project)throws Exception{
        if(bytes.length==0 || bytes.length>16_000_000)throw new IOException("Backup must be between 1 byte and 16 MB");
        JSONObject backup=new JSONObject(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));
        if(!"3patti-firestore-v1".equals(backup.optString("format")) || !project.equals(backup.optString("project")))throw new IOException("Choose a 3 Patti PRO backup for this Firebase project");
        JSONArray documents=backup.getJSONArray("documents");
        if(documents.length()>MAX_DOCUMENTS)throw new IOException("Atomic restore supports up to 500 records. No data changed.");
        List<JSONObject> result=new ArrayList<>();Set<String> paths=new HashSet<>();
        for(int i=0;i<documents.length();i++){
            JSONObject doc=documents.getJSONObject(i);String path=path(doc,project);
            if(!paths.add(path))throw new IOException("Duplicate backup record");
            JSONObject fields=doc.getJSONObject("fields");validateFields(fields,0);
            if(path.startsWith("players/")){
                String name=fields.getJSONObject("name").getString("stringValue");
                if(name.trim().isEmpty()||name.length()>200)throw new IOException("Invalid player name in backup");
            }
            result.add(doc);
        }
        return result;
    }
    private static void validateFields(JSONObject fields,int depth)throws Exception{
        if(depth>20)throw new IOException("Backup nesting is too deep");
        Iterator<String> keys=fields.keys();
        while(keys.hasNext()){
            String key=keys.next();JSONObject value=fields.getJSONObject(key);
            if(value.length()!=1)throw new IOException("Invalid Firestore field");
            String type=value.keys().next();
            switch(type){
                case "nullValue":case "stringValue":case "integerValue":case "doubleValue":case "booleanValue":case "timestampValue":case "bytesValue":case "referenceValue":case "geoPointValue":break;
                case "mapValue":validateFields(value.getJSONObject(type).optJSONObject("fields")==null?new JSONObject():value.getJSONObject(type).getJSONObject("fields"),depth+1);break;
                case "arrayValue":
                    JSONArray entries=value.getJSONObject(type).optJSONArray("values");
                    if(entries!=null)for(int i=0;i<entries.length();i++)validateFields(new JSONObject().put("value",entries.getJSONObject(i)),depth+1);
                    break;
                default:throw new IOException("Unknown Firestore field type");
            }
        }
    }
}
