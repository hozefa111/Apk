package com.hozefa.threepattipro.nativeapp;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class SessionCorrectionTest {
 private Map<String,Object> m(Object... values){return SessionLeaderboard.FirebaseMap.of(values);}
 private List<Map<String,Object>> players(){return Arrays.asList(m("id","a","name","Alice"),m("id","b","name","Bob"),m("id","c","name","Cara"));}
 private Map<String,Object> round(String id,String h,String partner,int bid,String outcome){List<String> ids=Arrays.asList("a","b","c");List<String> opponents=new ArrayList<>(ids);opponents.remove(h);opponents.remove(partner);return m("id",id,"sessionId","s","type","hukum","source","manual","hukumId",h,"hukumName",h,"partnerIds",Arrays.asList(partner),"nonPartnerIds",opponents,"playerIds",ids,"bid",bid,"result",outcome,"timestamp","2026-10-20T01:00:00Z","scoreChanges",ScoreRules.calculate(ids,bid,h,Arrays.asList(partner),outcome));}
 private Map<String,Object> saved(List<Map<String,Object>> rounds){Map<String,Object> s=SessionSummary.compute(players(),rounds);s.putAll(m("id","s","sessionName","Finished","startTime","2026-10-20T00:00:00Z","endTime","2026-10-20T02:00:00Z"));return s;}
 @Test public void deletingCompletedRoundCorrectsCountScoresMvpAndAwardsWithoutMutatingArchive(){
  Map<String,Object> win=round("win","a","b",180,"win"),loss=round("loss","c","a",155,"loss"),snapshot=saved(Arrays.asList(loss,win));assertEquals("Bob",((Map<?,?>)snapshot.get("mvp")).get("name"));
  Map<String,Object> corrected=SessionSummary.corrected(snapshot,players(),Arrays.asList(win),Arrays.asList(snapshot));assertEquals(1,corrected.get("totalRounds"));assertEquals("Alice",((Map<?,?>)corrected.get("mvp")).get("name"));assertEquals(360,((Map<?,?>)corrected.get("mvp")).get("score"));assertEquals(0,((Map<?,?>)corrected.get("biggestLoss")).get("score"));assertEquals(2,snapshot.get("totalRounds"));
  ScoreboardModel ranks=SessionLeaderboard.model(players(),Arrays.asList(win),Arrays.asList(snapshot),snapshot,null);assertEquals(360,ranks.rows.get(0).points);assertEquals("a",ranks.rows.get(0).id);
 }
 @Test public void deletingLastRoundClearsOldWinnersAndRetainsDeletedPlayerNamesForRemainingHistory(){
  Map<String,Object> win=round("win","a","b",180,"win"),snapshot=saved(Arrays.asList(win));Map<String,Object> corrected=SessionSummary.corrected(snapshot,Arrays.asList(players().get(0)),Arrays.asList(win),Arrays.asList(snapshot));assertEquals(3,((List<?>)corrected.get("leaderboard")).size());assertEquals("Bob",((Map<?,?>)((List<?>)corrected.get("leaderboard")).get(1)).get("name"));
  corrected=SessionSummary.corrected(snapshot,players(),Collections.emptyList(),Arrays.asList(snapshot));assertEquals(0,corrected.get("totalRounds"));assertNull(corrected.get("mvp"));assertTrue(((List<?>)corrected.get("leaderboard")).isEmpty());assertTrue(((List<?>)((Map<?,?>)corrected.get("badges")).get("mvp")).isEmpty());
 }
 @Test public void onlinePendingHistoryAndUnrelatedSessionsAreNeverRecalculatedAsManual(){
  Map<String,Object> win=round("win","a","b",180,"win"),snapshot=saved(Arrays.asList(win));Map<String,Object> unrelated=new HashMap<>(win);unrelated.put("sessionId","other");assertEquals(0,SessionSummary.corrected(snapshot,players(),Arrays.asList(unrelated),Arrays.asList(snapshot)).get("totalRounds"));
  snapshot.put("source","online");assertSame(snapshot,SessionSummary.corrected(snapshot,players(),Collections.emptyList(),Arrays.asList(snapshot)));snapshot.remove("source");snapshot.put("id","cf2_room");assertSame(snapshot,SessionSummary.corrected(snapshot,players(),Collections.emptyList(),Arrays.asList(snapshot)));
 }
}
