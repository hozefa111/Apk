package com.hozefa.threepattipro.nativeapp;

import java.util.*;

/** HH endSession statistics, independent of Android views and Firebase writes. */
public final class SessionSummary {
    private SessionSummary(){}
    /** Saved metadata is an archive; corrected manual views follow the same rounds as Ranks. */
    public static Map<String,Object> corrected(Map<String,Object> session,List<Map<String,Object>> players,List<Map<String,Object>> all,List<Map<String,Object>> catalog){
        if(SessionLeaderboard.id(session).isEmpty()||session.get("endTime")==null||SessionLeaderboard.online(session))return session;
        List<Map<String,Object>> sessions=new ArrayList<>();for(Map<String,Object> s:catalog)if(!SessionLeaderboard.id(s).equals(SessionLeaderboard.id(session)))sessions.add(s);sessions.add(session);
        List<Map<String,Object>> selected=SessionLeaderboard.rounds(all,sessions,SessionLeaderboard.id(session));Collections.reverse(selected);
        return fromSelected(session,players,selected);
    }
    static Map<String,List<Map<String,Object>>> indexNewest(List<Map<String,Object>> newest,List<Map<String,Object>> sessions){
        Map<String,long[]> bounds=new LinkedHashMap<>();for(Map<String,Object> session:sessions)if(!SessionLeaderboard.online(session)){bounds.put(SessionLeaderboard.id(session),new long[]{SessionLeaderboard.time(session.get("startTime")),session.get("endTime")==null?Long.MAX_VALUE:SessionLeaderboard.time(session.get("endTime"))});}
        Map<String,List<Map<String,Object>>> groups=new HashMap<>();Map<String,Set<String>> seen=new HashMap<>();int anonymous=0;
        for(Map<String,Object> r:newest){if(!AdvancedAnalytics.eligible(r)||"online".equals(r.get("source")))continue;String target=text(r,"sessionId");
            if(target.isEmpty()){long t=SessionLeaderboard.time(r.get("timestamp"));int count=0;for(Map.Entry<String,long[]> b:bounds.entrySet())if(t!=Long.MIN_VALUE&&b.getValue()[0]!=Long.MIN_VALUE&&t>=b.getValue()[0]&&t<b.getValue()[1]){target=b.getKey();count++;}if(count!=1)continue;}
            String key=text(r,"id");if(key.isEmpty())key="anonymous:"+(anonymous++);if(seen.computeIfAbsent(target,k->new HashSet<>()).add(key))groups.computeIfAbsent(target,k->new ArrayList<>()).add(r);
        }
        return groups;
    }
    static Map<String,Object> fromSelected(Map<String,Object> session,List<Map<String,Object>> players,List<Map<String,Object>> selected){
        Map<String,Map<String,Object>> roster=new LinkedHashMap<>();for(Map<String,Object> p:players)roster.put(text(p,"id"),p);
        for(Map<String,Object> p:SessionLeaderboard.maps(session.get("leaderboard"))){String id=text(p,"id");if(id.isEmpty())id=text(p,"playerId");if(!id.isEmpty())roster.putIfAbsent(id,row("id",id,"name",p.get("name")));}
        for(Map<String,Object> r:selected){
            String h=text(r,"hukumId");if(!h.isEmpty())roster.putIfAbsent(h,row("id",h,"name",r.getOrDefault("hukumName",h)));
            for(String role:new String[]{"partner","nonPartner"}){List<?> ids=list(r.get(role+"Ids")),names=list(r.get(role+"Names"));for(int i=0;i<ids.size();i++){String id=String.valueOf(ids.get(i));roster.putIfAbsent(id,row("id",id,"name",i<names.size()?names.get(i):id));}}
            for(Object raw:scores(r).keySet()){String id=String.valueOf(raw);roster.putIfAbsent(id,row("id",id,"name",id));}
        }
        Map<String,Object> result=new LinkedHashMap<>(session);result.putAll(compute(new ArrayList<>(roster.values()),selected));return result;
    }
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
