package com.hozefa.threepattipro.nativeapp;

import java.util.List;
import java.util.Map;

/** Native statistics rules matched to the existing HH game for saved Hukum rounds. */
public final class RoundAnalytics {
    private RoundAnalytics(){}
    private static String text(Map<String,Object> row,String key){
        Object v=row.get(key);return v==null?"":String.valueOf(v);
    }
    private static boolean includes(Object source,String playerId){
        if(!(source instanceof List))return false;
        for(Object id:(List<?>)source)if(playerId.equals(String.valueOf(id)))return true;
        return false;
    }
    public static boolean played(Map<String,Object> round,String playerId){
        return !"settlement".equals(text(round,"type"))&&includes(round.get("playerIds"),playerId);
    }
    public static boolean onHukumTeam(Map<String,Object> round,String playerId){
        return playerId.equals(text(round,"hukumId"))||includes(round.get("partnerIds"),playerId);
    }
    public static boolean won(Map<String,Object> round,String playerId){
        if(!played(round,playerId))return false;
        return ("win".equals(text(round,"result")))==onHukumTeam(round,playerId);
    }
    /** Input is ordered newest-first, as the native Firestore activity's gameRounds(). */
    public static int winningStreak(String playerId,List<Map<String,Object>> newestFirst){
        int count=0;
        for(Map<String,Object> round:newestFirst){
            if(!played(round,playerId))continue;
            if(won(round,playerId))count++;else break;
        }
        return count;
    }
    /** {together, against, winsAgainst, lossesAgainst}. */
    public static int[] headToHead(String playerId,String rival,List<Map<String,Object>> games){
        int[] counts=new int[4];
        for(Map<String,Object> round:games){
            if(!played(round,playerId)||!played(round,rival))continue;
            if(onHukumTeam(round,playerId)==onHukumTeam(round,rival))counts[0]++;
            else{
                counts[1]++;
                if(won(round,playerId))counts[2]++;else counts[3]++;
            }
        }
        return counts;
    }
}
