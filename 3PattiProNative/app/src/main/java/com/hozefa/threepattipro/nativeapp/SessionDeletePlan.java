package com.hozefa.threepattipro.nativeapp;

import org.json.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Durable serial cascade intents. A lost response retains the original command and preview. */
final class SessionDeletePlan {
    final String project,owner;
    final int total;
    final JSONArray remaining;
    private SessionDeletePlan(String project,String owner,int total,JSONArray remaining)throws Exception{
        this.project=project;this.owner=owner;this.total=total;this.remaining=new JSONArray(remaining.toString());
        if(owner.isEmpty()||total<remaining.length()||total<1)throw new IOException("Invalid session deletion plan");
        Set<String> seen=new HashSet<>();
        for(int i=0;i<this.remaining.length();i++){
            JSONObject row=this.remaining.getJSONObject(i);String id=row.getJSONObject("session").getString("id");
            if(!id.matches("[a-zA-Z0-9_-]{1,240}")||!seen.add(id)||!row.getString("commandId").matches("[a-zA-Z0-9_-]{8,100}")
                    ||row.getJSONObject("preview").optString("previewToken").isEmpty())throw new IOException("Invalid session deletion target");
        }
    }
    static SessionDeletePlan create(String project,String owner,List<Map<String,Object>> sessions,List<JSONObject> previews)throws Exception{
        if(sessions.size()!=previews.size())throw new IOException("Review every selected session before deleting");
        JSONArray rows=new JSONArray();
        for(int i=0;i<sessions.size();i++){
            Map<String,Object> session=sessions.get(i);
            JSONObject target=new JSONObject().put("id",session.get("id")).put("sessionName",session.get("sessionName"));
            rows.put(new JSONObject().put("session",target).put("preview",previews.get(i)).put("commandId",UUID.randomUUID().toString()));
        }
        return new SessionDeletePlan(project,owner,rows.length(),rows);
    }
    static SessionDeletePlan parse(byte[] bytes,String project,String owner)throws Exception{
        JSONObject value=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
        if(!"3patti-session-delete-v1".equals(value.getString("format"))||!project.equals(value.getString("project"))||!owner.equals(value.getString("owner")))
            throw new IOException("Use the account that started this operation");
        return new SessionDeletePlan(project,owner,value.getInt("total"),value.getJSONArray("remaining"));
    }
    byte[] bytes()throws Exception{return new JSONObject().put("format","3patti-session-delete-v1").put("project",project).put("owner",owner).put("total",total).put("remaining",remaining).toString().getBytes(StandardCharsets.UTF_8);}
    SessionDeletePlan acknowledged()throws Exception{
        if(remaining.length()==0)throw new IOException("No pending session deletion");
        JSONArray rows=new JSONArray();for(int i=1;i<remaining.length();i++)rows.put(remaining.get(i));
        return new SessionDeletePlan(project,owner,total,rows);
    }
}
