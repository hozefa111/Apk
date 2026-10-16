package com.hozefa.threepattipro.nativeapp;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public final class RoundAnalyticsTest {
    private Map<String,Object> round(String result,String hukum,List<String> partners){
        Map<String,Object> map=new HashMap<>();
        map.put("type","hukum");
        map.put("playerIds",Arrays.asList("a","b","c","d"));
        map.put("hukumId",hukum);
        map.put("partnerIds",partners);
        map.put("result",result);
        return map;
    }
    @Test public void hukumAndPartnerWinTogether(){
        Map<String,Object> r=round("win","a",Arrays.asList("b"));
        assertTrue(RoundAnalytics.won(r,"a"));
        assertTrue(RoundAnalytics.won(r,"b"));
        assertFalse(RoundAnalytics.won(r,"c"));
    }
    @Test public void opponentsWinWhenHukumLoses(){
        Map<String,Object> r=round("loss","a",Arrays.asList("b"));
        assertFalse(RoundAnalytics.won(r,"a"));
        assertFalse(RoundAnalytics.won(r,"b"));
        assertTrue(RoundAnalytics.won(r,"c"));
    }
    @Test public void outsidersNeverPlay(){
        Map<String,Object> r=round("loss","a",Arrays.asList("b"));
        assertFalse(RoundAnalytics.played(r,"outside"));
        assertFalse(RoundAnalytics.won(r,"outside"));
    }
    @Test public void scoreZeroDoesNotImplyWin(){
        Map<String,Object> r=round("win","a",Arrays.asList("b"));
        // Non-partner receives zero score, but lost the round by game rules.
        assertFalse(RoundAnalytics.won(r,"c"));
    }
    @Test public void consecutiveRoundStreakNewestFirst(){
        Map<String,Object> lost=round("win","a",Arrays.asList("b"));
        Map<String,Object> won=round("loss","a",Arrays.asList("b"));
        assertEquals(2,RoundAnalytics.winningStreak("c",Arrays.asList(won,won,lost,won)));
    }
    @Test public void skippedRoundsDoNotInterruptStreak(){
        Map<String,Object> skipped=round("win","a",Arrays.asList("b"));
        skipped.put("playerIds",Arrays.asList("a","b","d"));
        Map<String,Object> won=round("loss","a",Arrays.asList("b"));
        assertEquals(2,RoundAnalytics.winningStreak("c",Arrays.asList(won,skipped,won)));
    }
    @Test public void settlementNeverCounts(){
        Map<String,Object> change=round("win","a",Arrays.asList("b"));
        change.put("type","settlement");
        assertFalse(RoundAnalytics.played(change,"a"));
        assertEquals(0,RoundAnalytics.winningStreak("a",Collections.singletonList(change)));
    }
    @Test public void headToHeadTeammatesAndOpponents(){
        Map<String,Object> teammates=round("win","a",Arrays.asList("b"));
        Map<String,Object> opponents=round("loss","a",Arrays.asList("b"));
        int[] withPartner=RoundAnalytics.headToHead("a","b",Arrays.asList(teammates,opponents));
        assertArrayEquals(new int[]{2,0,0,0},withPartner);
        int[] withOpponent=RoundAnalytics.headToHead("a","c",Arrays.asList(teammates,opponents));
        assertArrayEquals(new int[]{0,2,1,1},withOpponent);
    }
    @Test public void roleSwitchOpponentWinsAreCounted(){
        Map<String,Object> first=round("loss","a",Arrays.asList("b"));
        Map<String,Object> second=round("loss","c",Arrays.asList("d"));
        assertArrayEquals(new int[]{0,2,1,1},
                RoundAnalytics.headToHead("a","c",Arrays.asList(first,second)));
    }
}
