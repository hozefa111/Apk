package com.hozefa.threepattipro.nativeapp;

import java.util.*;

/** HH endSession statistics, independent of Android views and Firebase writes. */
public final class SessionSummary {
    private SessionSummary(){}
    private static String text(Map<String,Object> row,String key){Object value=row.get(key);return value==null?"":String.valueOf(value);}
    private static int number(Object value){return value instanceof Number?((Number)value).intValue():0;}
    private static List<?> list(Object value){return value instanceof List?(List<?>)value:Collections.emptyList();}
    private static Map<?,?> scores(Map<String,Object> round){Object value=round.get("scoreChanges");return value instanceof Map?(Map<?,?>)value:Collections.emptyMap();}
    private static Map<String,Object> row(Object... pairs){Map<String,Object> out=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)out.put((String)pairs[i],pairs[i+1]);return out;}
    private static final class Stats {int score,played,hukum,hukumWins,partner,partnerWins,losses;}
    public static Map<String,Object> compute(List<Map<String,Object>> players,List<Map<String,Object>> newestFirst){
        List<Map<String,Object>> rounds=new ArrayList<>();for(Map<String,Object> r:newestFirst)if(!"settlement".equals(text(r,"type")))rounds.add(r);
        Map<String,Stats> stats=new LinkedHashMap<>();Map<String,String> names=new HashMap<>();
        for(Map<String,Object> player:players){String id=text(player,"id");stats.put(id,new Stats());names.put(id,text(player,"name"));}
        for(Map<String,Object> round:rounds){
            String hukum=text(round,"hukumId");List<?> partners=list(round.get("partnerIds")),opponents=list(round.get("nonPartnerIds"));
            List<?> ids;
            if(round.get("playerIds") instanceof List)ids=list(round.get("playerIds"));
            else{List<Object> fallback=new ArrayList<>();fallback.add(hukum);fallback.addAll(partners);fallback.addAll(opponents);ids=fallback;}
            boolean win="win".equals(text(round,"result"));
            for(Object raw:ids){String id=String.valueOf(raw);Stats s=stats.get(id);if(s==null)continue;
                s.played++;s.score+=number(scores(round).get(id));
                if(id.equals(hukum)){s.hukum++;if(win)s.hukumWins++;else s.losses++;}
                else if(partners.contains(id)){s.partner++;if(win)s.partnerWins++;else s.losses++;}
                else if(opponents.contains(id)&&win)s.losses++;
            }
        }
        List<Map<String,Object>> leaderboard=new ArrayList<>();
        for(Map<String,Object> player:players){String id=text(player,"id");Stats s=stats.get(id);if(s.played>0)leaderboard.add(row("id",id,"name",names.get(id),"score",s.score,"roundsPlayed",s.played));}
        leaderboard.sort((a,b)->Integer.compare(number(b.get("score")),number(a.get("score"))));
        for(int i=0;i<leaderboard.size();i++)leaderboard.get(i).put("rank",i+1);
        Map<String,Object> mvp=leaderboard.isEmpty()?null:row("name",leaderboard.get(0).get("name"),"score",leaderboard.get(0).get("score"));
        Map<String,Object> bestHukum=null;double bestRate=-1;int bestTotal=0,maxHukum=0,maxLosses=0;
        for(Map<String,Object> player:players){String id=text(player,"id");Stats s=stats.get(id);maxHukum=Math.max(maxHukum,s.hukum);maxLosses=Math.max(maxLosses,s.losses);
            if(s.hukum==0)continue;double rate=(double)s.hukumWins/s.hukum;
            if(rate>bestRate||(rate==bestRate&&s.hukum>bestTotal)){bestRate=rate;bestTotal=s.hukum;bestHukum=row("name",names.get(id),"winRate",Math.round(rate*100),"wins",s.hukumWins,"total",s.hukum);}
        }
        Map<String,Object> biggestWin=row("name","-","score",0),biggestLoss=row("name","-","score",0);int maxBid=-1;
        for(Map<String,Object> round:rounds){maxBid=Math.max(maxBid,number(round.get("bid")));
            for(Map.Entry<?,?> score:scores(round).entrySet()){int change=number(score.getValue());String name=names.getOrDefault(String.valueOf(score.getKey()),"Unknown");
                if(change>number(biggestWin.get("score")))biggestWin=row("name",name,"score",change);
                if(change<number(biggestLoss.get("score")))biggestLoss=row("name",name,"score",change);
            }
        }
        Map<String,Object> badges=new LinkedHashMap<>();for(String key:new String[]{"hukumKing","hotStreak","unlucky","sharpPartner","mvp","riskTaker"})badges.put(key,new ArrayList<String>());
        for(Map<String,Object> player:players){String id=text(player,"id");Stats s=stats.get(id);
            if(maxHukum>0&&s.hukum==maxHukum)add(badges,"hukumKing",id);
            if(maxLosses>0&&s.losses==maxLosses)add(badges,"unlucky",id);
            if(s.partner>=3&&(double)s.partnerWins/s.partner>=0.8)add(badges,"sharpPartner",id);
            if(RoundAnalytics.longestPositiveStreak(id,rounds)>=3)add(badges,"hotStreak",id);
        }
        if(!leaderboard.isEmpty()){int high=number(leaderboard.get(0).get("score"));for(Map<String,Object> player:leaderboard)if(number(player.get("score"))==high)add(badges,"mvp",text(player,"id"));}
        if(maxBid>0)for(Map<String,Object> round:rounds)if(number(round.get("bid"))==maxBid){String id=text(round,"hukumId");if(!list(badges.get("riskTaker")).contains(id))add(badges,"riskTaker",id);}
        List<Map<String,Object>> streaks=new ArrayList<>();
        for(Map<String,Object> player:players){String id=text(player,"id");int streak=RoundAnalytics.winningStreak(id,rounds);if(streak>=2)streaks.add(row("name",names.get(id),"streak",streak));}
        streaks.sort((a,b)->Integer.compare(number(b.get("streak")),number(a.get("streak"))));
        return row("totalRounds",rounds.size(),"leaderboard",leaderboard,"mvp",mvp,"bestHukum",bestHukum,"biggestWin",biggestWin,"biggestLoss",biggestLoss,"streaks",streaks,"badges",badges);
    }
    @SuppressWarnings("unchecked") private static void add(Map<String,Object> badges,String key,String id){((List<String>)badges.get(key)).add(id);}
}
