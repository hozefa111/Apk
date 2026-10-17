package com.hozefa.threepattipro.nativeapp;
import org.json.*;
import java.util.*;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.io.IOException;

/** Explicit file import of HH's 3PattiProState. No browser storage is accessed. */
public final class LegacyImport {
    private LegacyImport(){}
    private static String stable(String value)throws Exception{
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex=new StringBuilder("legacy-");for(byte b:digest)hex.append(String.format(Locale.ROOT,"%02x",b&255));return hex.toString();
    }
    private static JSONObject value(Object raw)throws Exception{
        JSONObject out=new JSONObject();
        if(raw==null||raw==JSONObject.NULL)return out.put("nullValue",JSONObject.NULL);
        if(raw instanceof Boolean)return out.put("booleanValue",raw);
        if(raw instanceof Number)return out.put("integerValue",String.valueOf(((Number)raw).longValue()));
        if(raw instanceof JSONObject){JSONObject fields=new JSONObject();Iterator<String> keys=((JSONObject)raw).keys();while(keys.hasNext()){String key=keys.next();fields.put(key,value(((JSONObject)raw).get(key)));}return out.put("mapValue",new JSONObject().put("fields",fields));}
        if(raw instanceof JSONArray){JSONArray values=new JSONArray();for(int i=0;i<((JSONArray)raw).length();i++)values.put(value(((JSONArray)raw).get(i)));return out.put("arrayValue",new JSONObject().put("values",values));}
        return out.put("stringValue",String.valueOf(raw));
    }
    private static JSONObject document(String project,String path,JSONObject data)throws Exception{
        JSONObject fields=value(data).getJSONObject("mapValue").getJSONObject("fields");
        for(String key:Arrays.asList("createdAt","timestamp"))if(data.has(key))fields.put(key,new JSONObject().put("timestampValue",data.getString(key)));
        return new JSONObject().put("name","projects/"+project+"/databases/(default)/documents/"+path).put("fields",fields);
    }
    public static byte[] convert(byte[] bytes,String project)throws Exception{
        JSONObject old=new JSONObject(new String(bytes,StandardCharsets.UTF_8));JSONArray players=old.getJSONArray("totalPlayers"),history=old.optJSONArray("history"),active=old.optJSONArray("activePlayers");
        if(players.length()==0)throw new IOException("Legacy export has no players");
        Map<String,String> ids=new LinkedHashMap<>(),names=new HashMap<>();Set<String> activeIds=new HashSet<>();
        if(active!=null)for(int i=0;i<active.length();i++)activeIds.add(String.valueOf(active.get(i)));
        JSONArray docs=new JSONArray();String now=Instant.now().toString();
        for(int i=0;i<players.length();i++){
            JSONObject player=players.getJSONObject(i);String oldId=String.valueOf(player.get("id")),name=player.getString("name").trim();
            if(ids.containsKey(oldId)||name.isEmpty())throw new IOException("Duplicate player ID or empty name in legacy export");
            String id=stable("player:"+oldId);ids.put(oldId,id);names.put(oldId,name);
            docs.put(document(project,"players/"+id,new JSONObject().put("name",name).put("active",active==null||activeIds.contains(oldId)).put("createdAt",now)));
        }
        if(history!=null)for(int i=0;i<history.length();i++){
            JSONObject round=history.getJSONObject(i);String hukum=String.valueOf(round.get("hukum"));
            if(!ids.containsKey(hukum))throw new IOException("Round references an unknown Hukum");
            JSONArray played=round.getJSONArray("players"),partners=round.optJSONArray("partners"),pids=new JSONArray(),ps=new JSONArray(),pnames=new JSONArray(),others=new JSONArray(),onames=new JSONArray();
            Set<String> partnerOld=new LinkedHashSet<>(),playedOld=new LinkedHashSet<>();
            if(partners!=null)for(int j=0;j<partners.length();j++){String pid=String.valueOf(partners.get(j));if(!ids.containsKey(pid)||!partnerOld.add(pid))throw new IOException("Invalid partner IDs");ps.put(ids.get(pid));pnames.put(names.get(pid));}
            for(int j=0;j<played.length();j++){String pid=String.valueOf(played.get(j));if(!ids.containsKey(pid)||!playedOld.add(pid))throw new IOException("Invalid playing IDs");pids.put(ids.get(pid));if(!pid.equals(hukum)&&!partnerOld.contains(pid)){others.put(ids.get(pid));onames.put(names.get(pid));}}
            if(!playedOld.contains(hukum)||!playedOld.containsAll(partnerOld)||partnerOld.contains(hukum))throw new IOException("Invalid round roles");
            String result=Boolean.TRUE.equals(round.opt("result"))||"win".equals(round.optString("result"))?"win":"loss";
            List<String> playingList=new ArrayList<>(),partnerList=new ArrayList<>();for(String pid:playedOld)playingList.add(ids.get(pid));for(String pid:partnerOld)partnerList.add(ids.get(pid));
            Map<String,Integer> expected=ScoreRules.calculate(playingList,round.getInt("bid"),ids.get(hukum),partnerList,result);
            JSONObject deltas=round.optJSONObject("deltas"),scores=new JSONObject();
            if(deltas!=null){Iterator<String> keys=deltas.keys();while(keys.hasNext()){String pid=keys.next();if(!playedOld.contains(pid))throw new IOException("Score references a non-playing player");Object score=deltas.get(pid);if(!(score instanceof Number)||((Number)score).doubleValue()!=((Number)score).intValue())throw new IOException("Invalid legacy score");scores.put(ids.get(pid),score);}}
            else for(Map.Entry<String,Integer> e:expected.entrySet())scores.put(e.getKey(),e.getValue());
            String timestamp=now;Object rawDate=round.has("date")?round.get("date"):round.opt("ts");
            if(rawDate instanceof Number)timestamp=Instant.ofEpochMilli(((Number)rawDate).longValue()).toString();else if(rawDate instanceof String)timestamp=Instant.parse((String)rawDate).toString();
            JSONObject data=new JSONObject().put("type","hukum").put("timestamp",timestamp).put("bid",round.getInt("bid")).put("result",result).put("hukumId",ids.get(hukum)).put("hukumName",names.get(hukum)).put("partnerIds",ps).put("partnerNames",pnames).put("nonPartnerIds",others).put("nonPartnerNames",onames).put("playerIds",pids).put("scoreChanges",scores);
            docs.put(document(project,"rounds/"+stable("round:"+i+":"+round.toString()),data));
        }
        byte[] archive=new JSONObject().put("format","3patti-firestore-v1").put("project",project).put("exportedAt",now).put("documents",docs).toString().getBytes(StandardCharsets.UTF_8);
        BackupArchive.validate(archive,project);return archive;
    }
}
