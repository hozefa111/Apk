package com.hozefa.threepattipro.nativeapp;

import org.json.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.time.Instant;

/** Durable, replayable create/delete/rename batches. Every remote write has a precondition. */
final class BulkWritePlan {
    static final int BATCH_RECORDS=400, BATCH_BYTES=6_000_000;
    final String project, owner, kind;
    final int total;
    final JSONArray remaining;
    private BulkWritePlan(String project,String owner,String kind,int total,JSONArray remaining){
        this.project=project;this.owner=owner;this.kind=kind;this.total=total;this.remaining=remaining;
    }
    static BulkWritePlan create(String project,String owner,String kind,JSONArray writes)throws Exception{
        BulkWritePlan plan=new BulkWritePlan(project,owner,kind,writes.length(),new JSONArray(writes.toString()));
        plan.validate();return plan;
    }
    static BulkWritePlan parse(byte[] bytes,String project,String owner)throws Exception{
        JSONObject json=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
        if(!"3patti-bulk-v1".equals(json.getString("format"))||!project.equals(json.getString("project"))||!owner.equals(json.getString("owner")))
            throw new IOException("Use the account that started this operation");
        BulkWritePlan plan=new BulkWritePlan(project,owner,json.getString("kind"),json.getInt("total"),json.getJSONArray("remaining"));
        plan.validate();return plan;
    }
    byte[] bytes()throws Exception{
        return new JSONObject().put("format","3patti-bulk-v1").put("project",project).put("owner",owner)
            .put("kind",kind).put("total",total).put("remaining",remaining).toString().getBytes(StandardCharsets.UTF_8);
    }
    private String path(JSONObject write)throws Exception{
        String name=isDelete()?write.getString("delete"):write.getJSONObject("update").getString("name");
        return BackupArchive.path(new JSONObject().put("name",name),project);
    }
    private void validate()throws Exception{
        if(!Arrays.asList("clear","delete","restore","rename").contains(kind)||owner.isEmpty()||total<remaining.length()||total<0)
            throw new IOException("Invalid recovery plan");
        Set<String> paths=new HashSet<>();
        for(int i=0;i<remaining.length();i++){
            JSONObject write=remaining.getJSONObject(i);String path=path(write);
            if(!paths.add(path))throw new IOException("Duplicate recovery target");
            JSONObject condition=write.getJSONObject("currentDocument");
            if(isDelete()){
                if(("clear".equals(kind)?path.startsWith("players/"):!path.startsWith("players/")&&!path.startsWith("rounds/"))||condition.optString("updateTime").isEmpty()||write.length()!=2)
                    throw new IOException("Unsafe recovery delete");
            }else if("restore".equals(kind)){
                if(condition.optBoolean("exists",true)||write.length()!=2||!write.getJSONObject("update").has("fields"))throw new IOException("Unsafe recovery restore");
            }else{
                if(condition.optString("updateTime").isEmpty()||write.length()!=3)throw new IOException("Unsafe recovery rename");
                JSONObject fields=write.getJSONObject("update").getJSONObject("fields");JSONArray mask=write.getJSONObject("updateMask").getJSONArray("fieldPaths");
                Set<String> allowed=path.startsWith("players/")?Collections.singleton("name"):
                    path.startsWith("rounds/")?new HashSet<>(Arrays.asList("hukumName","partnerNames","nonPartnerNames")):Collections.emptySet();
                Set<String> seen=new HashSet<>();
                for(int j=0;j<mask.length();j++){String field=mask.getString(j);if(!allowed.contains(field)||!fields.has(field)||!seen.add(field))throw new IOException("Unsafe rename field");}
                if(mask.length()==0||fields.length()!=mask.length())throw new IOException("Invalid rename mask");
            }
            if(write.toString().getBytes(StandardCharsets.UTF_8).length>BATCH_BYTES)throw new IOException("Record exceeds safe batch size");
        }
    }
    /** Full preflight before any resumed write; ambiguous successful commits are skipped. */
    BulkWritePlan reconcile(Map<String,JSONObject> live)throws Exception{
        JSONArray pending=new JSONArray();
        for(int i=0;i<remaining.length();i++){
            JSONObject write=remaining.getJSONObject(i);String path=path(write);JSONObject existing=live.get(path);
            if(isDelete()){
                if(existing==null)continue;
                JSONObject fields=existing.optJSONObject("fields"),source=fields==null?null:fields.optJSONObject("source");
                if("delete".equals(kind)&&path.startsWith("rounds/")&&source!=null&&"online".equals(source.optString("stringValue")))
                    throw new IOException("Completed online hands are immutable. Delete their stopped session instead.");
                if(!write.getJSONObject("currentDocument").getString("updateTime").equals(existing.optString("updateTime")))
                    throw new IOException("Newer record at "+path+". Kept unchanged; export recovery backup before resolving.");
            }else if("rename".equals(kind)){
                if(existing==null)throw new IOException("Rename target removed: "+path);
                if(!write.getJSONObject("currentDocument").getString("updateTime").equals(existing.optString("updateTime"))){
                    JSONObject intended=write.getJSONObject("update").getJSONObject("fields"),actual=existing.optJSONObject("fields");
                    Iterator<String> keys=intended.keys();
                    while(keys.hasNext()){String key=keys.next();if(actual==null||!actual.has(key)||!sameJson(actual.get(key),intended.get(key)))
                        throw new IOException("Rename conflict at "+path+". Newer record kept unchanged.");}
                    continue;
                }
            }else if(existing!=null){
                if(!sameJson(existing.getJSONObject("fields"),write.getJSONObject("update").getJSONObject("fields")))
                    throw new IOException("Restore conflict at "+path+". Kept unchanged.");
                continue;
            }
            pending.put(write);
        }
        return new BulkWritePlan(project,owner,kind,total,pending);
    }
    private boolean isDelete(){return "clear".equals(kind)||"delete".equals(kind);}
    JSONArray batch()throws Exception{
        JSONArray batch=new JSONArray();int bytes=32;
        for(int i=0;i<remaining.length()&&batch.length()<BATCH_RECORDS;i++){
            JSONObject write=remaining.getJSONObject(i);int size=write.toString().getBytes(StandardCharsets.UTF_8).length+1;
            if(bytes+size>BATCH_BYTES&&batch.length()>0)break;
            batch.put(write);bytes+=size;
        }
        return batch;
    }
    BulkWritePlan acknowledged(int count)throws Exception{
        if(count<0||count>remaining.length())throw new IOException("Invalid batch acknowledgement");
        JSONArray pending=new JSONArray();for(int i=count;i<remaining.length();i++)pending.put(remaining.get(i));
        return new BulkWritePlan(project,owner,kind,total,pending);
    }
    static boolean sameJson(Object left,Object right)throws Exception{
        if(left instanceof JSONObject&&right instanceof JSONObject){
            JSONObject a=(JSONObject)left,b=(JSONObject)right;if(a.length()!=b.length())return false;
            if(a.length()==1&&a.has("timestampValue")&&b.has("timestampValue"))
                return Instant.parse(a.getString("timestampValue")).equals(Instant.parse(b.getString("timestampValue")));
            Iterator<String> keys=a.keys();while(keys.hasNext()){String key=keys.next();if(!b.has(key)||!sameJson(a.get(key),b.get(key)))return false;}return true;
        }
        if(left instanceof JSONArray&&right instanceof JSONArray){
            JSONArray a=(JSONArray)left,b=(JSONArray)right;if(a.length()!=b.length())return false;
            for(int i=0;i<a.length();i++)if(!sameJson(a.get(i),b.get(i)))return false;return true;
        }
        if(left instanceof Number&&right instanceof Number)return Double.compare(((Number)left).doubleValue(),((Number)right).doubleValue())==0;
        return Objects.equals(left,right);
    }
}
