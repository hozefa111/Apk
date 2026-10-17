package com.hozefa.threepattipro.nativeapp;

import java.util.*;

/** Presentation selection only. Uses the existing gameplay-role and ordering rules. */
final class RoleHistory {
    static List<Map<String,Object>> select(String player,String role,List<Map<String,Object>> source){
        List<Map<String,Object>> result=new ArrayList<>();
        for(Map<String,Object> round:source){
            if(!RoundAnalytics.played(round,player))continue;
            String actual=player.equals(String.valueOf(round.get("hukumId")))?"hukum":RoundAnalytics.onHukumTeam(round,player)?"partner":"nonpartner";
            if(role.equals(actual))result.add(round);
        }
        result.sort(GameOrder.ROUNDS);return result;
    }
}
