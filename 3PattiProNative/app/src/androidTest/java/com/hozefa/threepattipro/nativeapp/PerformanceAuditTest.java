package com.hozefa.threepattipro.nativeapp;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

/** Controlled renderer/codec measurements. In-memory fixtures, no production writes. */
public class PerformanceAuditTest {
 private Object get(MainActivity a,String key){try{java.lang.reflect.Field f=MainActivity.class.getDeclaredField(key);f.setAccessible(true);return f.get(a);}catch(Exception e){throw new AssertionError(e);}}
 private void set(MainActivity a,String key,Object value){try{java.lang.reflect.Field f=MainActivity.class.getDeclaredField(key);f.setAccessible(true);f.set(a,value);}catch(Exception e){throw new AssertionError(e);}}
 private void invoke(MainActivity a,String name,Class<?>[] types,Object...args){try{java.lang.reflect.Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(a,args);}catch(Exception e){throw new AssertionError(e);}}
 private double ms(long start){return (System.nanoTime()-start)/1000000d;}
 private JSONObject percentiles(List<Double> source)throws Exception{Collections.sort(source);return new JSONObject().put("samples",source.size()).put("p50Ms",source.get((source.size()-1)/2)).put("p95Ms",source.get((int)Math.ceil(source.size()*.95)-1));}
 @Test public void measureRendererAndBackupCodecOnComplete3000RoundLedger()throws Exception{
  Assume.assumeTrue(BuildConfig.USE_EMULATORS);assertTrue(InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName().endsWith(".sandbox"));
  List<Map<String,Object>> players=new ArrayList<>(),rounds=new ArrayList<>(),sessions=new ArrayList<>();List<String> ids=Arrays.asList("a","b","c","d","e","f");long base=1760000000000L;
  for(String id:ids)players.add(FirebaseGateway.map("id",id,"name","Audit "+id,"active",true));
  for(int i=0;i<3000;i++){int b=155+5*(i%20);String h=ids.get(i%6),p=ids.get((i+1)%6),result=i%3==0?"loss":"win";List<String> opponents=new ArrayList<>(ids);opponents.remove(h);opponents.remove(p);rounds.add(FirebaseGateway.map("id","audit_"+i,"source","manual","sessionId","s"+(i/100),"type","hukum","timestamp",java.time.Instant.ofEpochMilli(base+i*1000L).toString(),"playerIds",ids,"hukumId",h,"hukumName","Audit "+h,"partnerIds",Arrays.asList(p),"nonPartnerIds",opponents,"bid",b,"result",result,"scoreChanges",ScoreRules.calculate(ids,b,h,Arrays.asList(p),result)));}
  Collections.reverse(rounds);
  for(int i=0;i<30;i++){List<Map<String,Object>> own=new ArrayList<>();for(Map<String,Object> r:rounds)if(("s"+i).equals(r.get("sessionId")))own.add(r);Map<String,Object> s=SessionSummary.compute(players,own);s.putAll(FirebaseGateway.map("id","s"+i,"source","manual","sessionName","Audit session "+i,"startTime",java.time.Instant.ofEpochMilli(base+i*100000L).toString(),"endTime",java.time.Instant.ofEpochMilli(base+(i+1)*100000L).toString()));sessions.add(s);}
  JSONObject report=new JSONObject().put("scope","Android emulator renderer CPU; animations off; codecs on instrumentation worker; not WAN or physical-phone latency").put("fixtureRounds",3000).put("api",android.os.Build.VERSION.SDK_INT);JSONObject views=new JSONObject();
  android.content.Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();String oldMotion=ctx.getSharedPreferences("preferences",0).getString("motion","full");ctx.getSharedPreferences("preferences",0).edit().putString("motion","off").commit();
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   AtomicBoolean initialized=new AtomicBoolean();long end=System.currentTimeMillis()+30000;while(System.currentTimeMillis()<end&&!initialized.get()){scenario.onActivity(a->initialized.set(get(a,"api")!=null));Thread.sleep(50);}assertTrue(initialized.get());
   scenario.onActivity(a->{((FirebaseGateway)get(a,"api")).stop();set(a,"loggedIn",true);set(a,"ranksSessions",false);set(a,"profileId","a");a.historyDeferred(false);a.syncState(true);a.dataUpdated(players,rounds,sessions);});
   String[] labels={"Home","Round and preview","History rounds","Lifetime ranks","Big Scoreboard","Player profile","Analytics","Settings"};int[] tabs={0,2,4,3,10,5,7,9};
   for(int j=0;j<tabs.length;j++){List<Double> times=new ArrayList<>();final int tab=tabs[j];for(int n=0;n<12;n++){scenario.onActivity(a->{set(a,"tab",tab);long t=System.nanoTime();invoke(a,"render",new Class<?>[]{boolean.class},true);times.add(ms(t));});Thread.sleep(20);}views.put(labels[j],percentiles(times));}
   List<Double> sessionTimes=new ArrayList<>();for(int n=0;n<12;n++){scenario.onActivity(a->{set(a,"tab",4);set(a,"historyTab",1);long t=System.nanoTime();invoke(a,"render",new Class<?>[]{boolean.class},true);sessionTimes.add(ms(t));});}views.put("Session history",percentiles(sessionTimes));
   JSONObject archive=new JSONObject().put("format","3patti-firestore-v1").put("project","demo-three-patti");JSONArray docs=new JSONArray();for(Map<String,Object> r:rounds){Map<String,Object> row=new HashMap<>(r);String id=(String)row.remove("id");docs.put(new JSONObject().put("name","projects/demo-three-patti/databases/(default)/documents/rounds/"+id).put("fields",SdkDocuments.fields(row)));}archive.put("documents",docs);List<Double> exports=new ArrayList<>(),imports=new ArrayList<>();for(int n=0;n<12;n++){long t=System.nanoTime();byte[] bytes=archive.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);exports.add(ms(t));t=System.nanoTime();assertEquals(3000,BackupArchive.validate(bytes,"demo-three-patti").size());imports.add(ms(t));}views.put("Backup serialize worker",percentiles(exports));views.put("Restore validation worker",percentiles(imports));
  }finally{ctx.getSharedPreferences("preferences",0).edit().putString("motion",oldMotion).commit();}
  report.put("operations",views);System.out.println("NATIVE_PERFORMANCE_AUDIT "+report);
 }
}
