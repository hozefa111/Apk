package com.hozefa.threepattipro.nativeapp;

import com.google.firebase.firestore.*;
import com.google.android.gms.tasks.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Pulls a complete public ledger or missing invalidations; never publishes partial totals. */
final class TrackerJournalSync {
 static final List<String> COLLECTIONS=Arrays.asList("players","rounds","sessions");
 static boolean path(String p){return p.matches("^(players|rounds|sessions)/[a-zA-Z0-9_-]{1,240}$");}
 static long number(Map<String,Object> h,String key){Object n=h.get(key);if(!(n instanceof Number))throw new IllegalStateException("Invalid tracker "+key);return ((Number)n).longValue();}
 static Map<String,Object> values(Object... pairs){Map<String,Object> out=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)out.put((String)pairs[i],pairs[i+1]);return out;}
 static Map<String,Object> head(DocumentSnapshot d){return d.exists()?d.getData():values("epoch",1L,"revision",0L,"mode","prepare","enforced",false);}
 static final class Result { final Map<String,Map<String,Object>> documents;final long epoch,revision;Result(Map<String,Map<String,Object>> docs,Map<String,Object> h){documents=docs;epoch=number(h,"epoch");revision=number(h,"revision");} }
 final FirebaseFirestore db;final int pageSize,eventPageSize;long queries,documentReads;
 TrackerJournalSync(FirebaseFirestore db,int pageSize,int eventPageSize){if(pageSize<2||pageSize>500||eventPageSize<1||eventPageSize>100)throw new IllegalArgumentException("Invalid sync page size");this.db=db;this.pageSize=pageSize;this.eventPageSize=eventPageSize;}
 private <T>T await(Task<T> task)throws Exception{return Tasks.await(task,30,TimeUnit.SECONDS);}
 private Map<String,Object> readHead()throws Exception{documentReads++;return head(await(db.document("trackerSync/state").get(Source.SERVER)));}
 Result pull(Map<String,Map<String,Object>> cached,long epoch,long revision,Map<String,Object> suppliedHead,BooleanSupplier cancelled)throws Exception{
  Map<String,Object> h=suppliedHead!=null?suppliedHead:readHead();if(!"incremental".equals(h.get("mode"))||!Boolean.TRUE.equals(h.get("enforced")))return null;final long expectedEpoch=number(h,"epoch");Map<String,Map<String,Object>> rows=new LinkedHashMap<>(cached);long cursor=revision;
  if(epoch!=expectedEpoch||cursor<0||cursor>number(h,"revision")){
   rows.clear();cursor=number(h,"revision");
   for(String name:COLLECTIONS){String after=null;for(;;){if(cancelled.getAsBoolean())throw new CancellationException();Query q=db.collection(name).orderBy(FieldPath.documentId()).limit(pageSize);if(after!=null)q=q.startAfter(after);queries++;QuerySnapshot page=await(q.get(Source.SERVER));documentReads+=page.size();for(DocumentSnapshot d:page)rows.put(name+"/"+d.getId(),d.getData());if(page.size()<pageSize)break;after=page.getDocuments().get(page.size()-1).getId();}}
   h=readHead();
  }
  for(int pass=0;pass<8;pass++){
   if(cancelled.getAsBoolean())throw new CancellationException();if(!"incremental".equals(h.get("mode"))||!Boolean.TRUE.equals(h.get("enforced"))||number(h,"epoch")!=expectedEpoch)throw new IllegalStateException("Tracker epoch changed; verification will restart");
   Set<String> changed=new LinkedHashSet<>();while(cursor<number(h,"revision")){
    queries++;QuerySnapshot events=await(db.collection("trackerChanges").whereGreaterThan("revision",cursor).whereLessThanOrEqualTo("revision",number(h,"revision")).orderBy("revision").limit(eventPageSize).get(Source.SERVER));documentReads+=events.size();if(events.isEmpty())throw new IllegalStateException("Tracker journal gap");
    for(DocumentSnapshot d:events){Map<String,Object> e=d.getData();if(number(e,"epoch")!=expectedEpoch||number(e,"revision")!=cursor+1)throw new IllegalStateException("Tracker journal gap");Object paths=e.get("paths");if(!(paths instanceof List))throw new IllegalStateException("Invalid tracker paths");for(Object p:(List<?>)paths){if(!(p instanceof String)||!path((String)p))throw new IllegalStateException("Invalid tracker path");changed.add((String)p);}cursor++;}
   }
   List<String> paths=new ArrayList<>(changed);for(int i=0;i<paths.size();i+=20){if(cancelled.getAsBoolean())throw new CancellationException();List<Task<DocumentSnapshot>> tasks=new ArrayList<>();for(String p:paths.subList(i,Math.min(paths.size(),i+20)))tasks.add(db.document(p).get(Source.SERVER));await(Tasks.whenAll(tasks));documentReads+=tasks.size();for(Task<DocumentSnapshot> task:tasks){DocumentSnapshot d=task.getResult();if(d.exists())rows.put(d.getReference().getPath(),d.getData());else rows.remove(d.getReference().getPath());}}
   Map<String,Object> latest=changed.isEmpty()?h:readHead();if(number(latest,"epoch")!=expectedEpoch||!"incremental".equals(latest.get("mode"))||!Boolean.TRUE.equals(latest.get("enforced")))throw new IllegalStateException("Tracker epoch changed; verification will restart");if(number(latest,"revision")!=cursor){h=latest;continue;}return new Result(rows,latest);
  }
  throw new IllegalStateException("Tracker changed during synchronization; retry is automatic");
 }
}
