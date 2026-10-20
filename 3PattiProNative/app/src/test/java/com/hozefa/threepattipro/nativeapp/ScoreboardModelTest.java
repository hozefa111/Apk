package com.hozefa.threepattipro.nativeapp;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class ScoreboardModelTest {
    private Map<String,Object> row(Object... pairs){Map<String,Object> out=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)out.put((String)pairs[i],pairs[i+1]);return out;}
    private List<Map<String,Object>> roster(){return Arrays.asList(row("id","a","name","Alpha"),row("id","b","name","Beta"),row("id","c","name","Gamma"),row("id","d","name","Delta"));}
    private Map<String,Object> game(int n,long a,long b,long c,long d){return row("id","r"+n,"type","hukum","timestamp","2026-10-20T10:00:0"+n+"Z","playerIds",Arrays.asList("a","b","c","d"),"scoreChanges",row("a",a,"b",b,"c",c,"d",d));}
    private ScoreboardModel model(Map<String,Object>... games){return new ScoreboardModel(roster(),Arrays.asList(games),Collections.emptyList());}
    @Test public void competitionRanksGiveSharedPlaces(){ScoreboardModel m=model(game(1,20,20,10,0));assertEquals(1,m.byId.get("a").rank);assertEquals(1,m.byId.get("b").rank);assertEquals(3,m.byId.get("c").rank);assertEquals(4,m.byId.get("d").rank);assertEquals(2,m.leaders.size());}
    @Test public void allFourTiedRemainVisibleWithStableRosterOrder(){ScoreboardModel m=model(game(1,0,0,0,0));assertEquals(4,m.rows.size());for(ScoreboardModel.Row r:m.rows){assertEquals(1,r.rank);assertEquals(4,r.tied);}assertEquals("a",m.rows.get(0).id);}
    @Test public void negativeLeaderHasZeroGap(){ScoreboardModel m=model(game(1,-10,-20,-30,-40));assertEquals("a",m.rows.get(0).id);assertEquals(0,m.gap(m.rows.get(0)));assertEquals(10,m.gap(m.byId.get("b")));}
    @Test public void totalsDoNotOverflow32Bit(){ScoreboardModel m=model(game(1,2_000_000_000L,0,0,0),game(2,2_000_000_000L,0,0,0));assertEquals(4_000_000_000L,m.byId.get("a").points);}
    @Test public void latestPersonalRoundSkipsAbsences(){Map<String,Object> later=game(2,0,10,20,30);later.put("playerIds",Arrays.asList("b","c","d"));ScoreboardModel m=model(later,game(1,170,0,0,0));assertEquals(170,m.byId.get("a").last);assertEquals(1,m.byId.get("a").played);}
    @Test public void explicitZeroIsARealLastRound(){ScoreboardModel m=model(game(1,50,0,0,0),game(2,0,0,0,0));assertTrue(m.byId.get("a").hasLast);assertEquals(0,m.byId.get("a").last);}
    @Test public void chronologyDoesNotDependOnInputOrder(){ScoreboardModel m=model(game(2,25,0,0,0),game(1,10,0,0,0));assertEquals(25,m.byId.get("a").last);assertEquals(35,m.byId.get("a").points);}
    @Test public void sessionPointsAreSeparateFromSelectedPeriod(){Map<String,Object> first=game(1,10,20,0,0),second=game(2,30,10,0,0);ScoreboardModel m=new ScoreboardModel(roster(),Arrays.asList(first,second),Arrays.asList(second));assertEquals(40,m.byId.get("a").points);assertEquals(30,m.byId.get("a").sessionPoints);}
    @Test public void missingHistoryDoesNotInventLeaders(){ScoreboardModel m=model();assertTrue(m.rows.isEmpty());assertTrue(m.leaders.isEmpty());}
    @Test public void missingDeltaStillCountsParticipation(){Map<String,Object> g=game(1,0,0,0,0);g.put("scoreChanges",Collections.emptyMap());ScoreboardModel m=model(g);assertEquals(1,m.byId.get("a").played);assertEquals(0,m.byId.get("a").points);}
    @Test public void practiceAndSettlementsAreExcluded(){Map<String,Object> p=game(2,999,0,0,0),s=game(3,999,0,0,0);p.put("mode","practice");s.put("type","settlement");assertEquals(10,model(game(1,10,0,0,0),p,s).byId.get("a").points);}
    @Test public void duplicateParticipantIdsCountOnce(){Map<String,Object> g=game(1,10,0,0,0);g.put("playerIds",Arrays.asList("a","a","b","c"));assertEquals(1,model(g).byId.get("a").played);}
    @Test public void unplayedRosterPlayersDoNotBecomeLeaders(){Map<String,Object> g=game(1,-10,-20,-30,0);g.put("playerIds",Arrays.asList("a","b","c"));ScoreboardModel m=model(g);assertEquals(3,m.rows.size());assertEquals("a",m.rows.get(0).id);}
    @Test public void tieBreakingAfterNewRoundChangesRanksCorrectly(){ScoreboardModel before=model(game(1,20,20,10,0)),after=model(game(1,20,20,10,0),game(2,0,10,20,0));assertEquals(1,before.byId.get("a").rank);assertEquals(3,after.byId.get("a").rank);assertEquals(1,after.byId.get("b").rank);assertEquals(1,after.byId.get("c").rank);}
}
