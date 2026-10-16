package com.hozefa.threepattipro.nativeapp;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ScoreRules {
    private ScoreRules() {}
    public static Map<String,Integer> calculate(List<String> players,int bid,String hukum,List<String> partners,String result) {
        if(players==null||hukum==null||partners==null||!players.contains(hukum))
            throw new IllegalArgumentException("Missing players or Hukum");
        if(!"win".equals(result)&&!"loss".equals(result))throw new IllegalArgumentException("Invalid result");
        if(bid<155||bid>250||(bid-155)%5!=0)throw new IllegalArgumentException("Invalid bid");
        Map<String,Integer> changes=new LinkedHashMap<>();
        for(String pid:players) {
            if("win".equals(result)) {
                changes.put(pid,pid.equals(hukum)?bid*2:(partners.contains(pid)?bid:0));
            }else {
                changes.put(pid,pid.equals(hukum)?-bid:(partners.contains(pid)?-(int)Math.round(bid/2.0):bid));
            }
        }
        return Collections.unmodifiableMap(changes);
    }
}
