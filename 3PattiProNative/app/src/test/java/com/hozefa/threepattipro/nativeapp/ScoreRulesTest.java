package com.hozefa.threepattipro.nativeapp;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.*;

public final class ScoreRulesTest {
    private final List<String> all=Arrays.asList("a","b","c","d");

    @Test public void winMatchesWeb() {
        Map<String,Integer> s=ScoreRules.calculate(all,155,"a",Collections.singletonList("b"),"win");
        assertEquals(310,(int)s.get("a"));
        assertEquals(155,(int)s.get("b"));
        assertEquals(0,(int)s.get("c"));
        assertEquals(0,(int)s.get("d"));
    }
    @Test public void lossRoundsHalfUpLikeJavascript() {
        Map<String,Integer> s=ScoreRules.calculate(all,155,"a",Collections.singletonList("b"),"loss");
        assertEquals(-155,(int)s.get("a"));
        assertEquals(-78,(int)s.get("b"));
        assertEquals(155,(int)s.get("c"));
        assertEquals(155,(int)s.get("d"));
    }
    @Test public void severalPartnersAndMaxBid() {
        Map<String,Integer> s=ScoreRules.calculate(all,250,"a",Arrays.asList("b","c"),"loss");
        assertEquals(-250,(int)s.get("a"));
        assertEquals(-125,(int)s.get("b"));
        assertEquals(-125,(int)s.get("c"));
        assertEquals(250,(int)s.get("d"));
    }
    @Test public void allAllowedBidsAndBothResults() {
        for(int bid=155;bid<=250;bid+=5) {
            for(String result:Arrays.asList("win","loss")) {
                Map<String,Integer> s=ScoreRules.calculate(all,bid,"a",Arrays.asList("b","c"),result);
                assertEquals(4,s.size());
                assertEquals(result.equals("win")?2*bid:-bid,(int)s.get("a"));
                assertEquals(result.equals("win")?bid:-(int)Math.round(bid/2.0),(int)s.get("b"));
                assertEquals(result.equals("win")?bid:-(int)Math.round(bid/2.0),(int)s.get("c"));
                assertEquals(result.equals("win")?0:bid,(int)s.get("d"));
            }
        }
    }
    @Test public void twoPlayersNotAllowed() {
        reject(Arrays.asList("a","b"),155,"a",Arrays.asList("b"),"win");
    }
    @Test public void noPartnersNotAllowed() {
        reject(all,155,"a",Collections.emptyList(),"win");
    }
    @Test public void noOpponentsNotAllowed() {
        reject(Arrays.asList("a","b","c"),155,"a",Arrays.asList("b","c"),"win");
    }
    @Test public void hukumCannotBePartner() {
        reject(all,155,"a",Arrays.asList("a","b"),"win");
    }
    @Test public void duplicatePlayersRejected() {
        reject(Arrays.asList("a","a","c"),155,"a",Collections.singletonList("c"),"win");
    }
    @Test public void duplicatePartnersRejected() {
        reject(all,155,"a",Arrays.asList("b","b"),"win");
    }
    @Test public void nonplayingPartnerRejected() {
        reject(all,155,"a",Collections.singletonList("not-at-table"),"win");
    }
    @Test public void missingHukumRejected() {
        reject(all,155,"not-at-table",Collections.singletonList("b"),"loss");
    }
    @Test public void invalidResultRejected() {
        reject(all,155,"a",Collections.singletonList("b"),"draw");
    }
    @Test public void invalidBidRejected() {
        for(int bid:new int[]{0,154,156,249,251,1000})
            reject(all,bid,"a",Collections.singletonList("b"),"win");
    }
    @Test public void scoreResultIsImmutable() {
        Map<String,Integer> s=ScoreRules.calculate(all,155,"a",Collections.singletonList("b"),"win");
        try {s.put("a",99999);fail("Score changes must be immutable");}
        catch(UnsupportedOperationException expected) { }
    }
    @Test public void originalPlayerOrderingPreserved() {
        Map<String,Integer> s=ScoreRules.calculate(Arrays.asList("d","a","c","b"),
                160,"a",Collections.singletonList("b"),"loss");
        assertEquals(Arrays.asList("d","a","c","b"),new ArrayList<>(s.keySet()));
    }
    private void reject(List<String> players,int bid,String hukum,List<String> partners,String result) {
        try {ScoreRules.calculate(players,bid,hukum,partners,result);fail("Expected invalid input rejection");}
        catch(IllegalArgumentException expected) { }
    }
}
