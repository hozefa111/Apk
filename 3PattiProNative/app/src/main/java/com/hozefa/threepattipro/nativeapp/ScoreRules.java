package com.hozefa.threepattipro.nativeapp;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Single scoring authority for Android preview and Firestore round writes.
 * Web reference: hozefa111/hh app.js calculateScoreChanges().
 */
public final class ScoreRules {
    private ScoreRules() {}
    public static Map<String,Integer> calculate(List<String> players,int bid,String hukum,
                                                 List<String> partners,String result) {
        if(players==null||hukum==null||partners==null||players.size()<3)
            throw new IllegalArgumentException("At least 3 players and a Hukum are required");
        if(!players.contains(hukum)||new HashSet<>(players).size()!=players.size())
            throw new IllegalArgumentException("Hukum missing or duplicate players");
        if(partners.isEmpty()||partners.contains(hukum)||!players.containsAll(partners)
                ||new HashSet<>(partners).size()!=partners.size()
                ||players.size()-partners.size()-1<1)
            throw new IllegalArgumentException("Need unique partners and at least one opponent");
        if(!"win".equals(result)&&!"loss".equals(result))
            throw new IllegalArgumentException("Invalid Hukum result");
        if(bid<155||bid>250||(bid-155)%5!=0)
            throw new IllegalArgumentException("Bid must be 155-250 in steps of 5");
        Map<String,Integer> changes=new LinkedHashMap<>();
        for(String pid:players) {
            if("win".equals(result)) {
                changes.put(pid,pid.equals(hukum)?bid*2:partners.contains(pid)?bid:0);
            }else {
                changes.put(pid,pid.equals(hukum)?-bid:
                        partners.contains(pid)?-(int)Math.round(bid/2.0):bid);
            }
        }
        return Collections.unmodifiableMap(changes);
    }
}
