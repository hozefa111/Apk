package com.hozefa.threepattipro.nativeapp;

import java.time.*;
import java.util.*;

/** Read-only, deterministic statistics. All lists use chronological round order. */
public final class AdvancedAnalytics {
    private AdvancedAnalytics(){}
    public static String text(Map<String,Object> row,String key){Object v=row.get(key);return v==null?"":String.valueOf(v);}
    public static long number(Object v){try{if(v instanceof Number){double d=((Number)v).doubleValue();return Double.isFinite(d)?((Number)v).longValue():0;}return Long.parseLong(String.valueOf(v));}catch(Exception e){return 0;}}
    public static long time(Object value){try{return Instant.parse(String.valueOf(value)).toEpochMilli();}catch(Exception e){return Long.MIN_VALUE;}}
    public static Set<String> ids(Object value){Set<String> out=new LinkedHashSet<>();if(value instanceof List)for(Object v:(List<?>)value)if(v!=null&&!String.valueOf(v).isEmpty())out.add(String.valueOf(v));return out;}
    public static boolean eligible(Map<String,Object> r){String type=text(r,"type"),mode=text(r,"mode");return !"settlement".equals(type)&&!"practice".equals(type)&&!"practice".equals(mode)&&!"practice".equals(text(r,"source"))&&!Boolean.TRUE.equals(r.get("isPractice"))&&!Boolean.TRUE.equals(r.get("practice"));}
    public static long delta(Map<String,Object> r,String pid){Object scores=r.get("scoreChanges");return scores instanceof Map?number(((Map<?,?>)scores).get(pid)):0;}
    public static String role(Map<String,Object> r,String pid){if(!ids(r.get("playerIds")).contains(pid))return "";if(pid.equals(text(r,"hukumId")))return "Hukum";return ids(r.get("partnerIds")).contains(pid)?"Partner":"Opponent";}
    /** 1 win, -1 loss, 0 unknown result. Points alone never identify a win. */
    public static int result(Map<String,Object> r,String pid){if(role(r,pid).isEmpty()||!ids(r.get("playerIds")).contains(text(r,"hukumId")))return 0;String result=text(r,"result");if(!"win".equals(result)&&!"loss".equals(result))return 0;boolean team=!"Opponent".equals(role(r,pid));return ("win".equals(result)==team)?1:-1;}
    public static List<Map<String,Object>> games(List<Map<String,Object>> source){List<Map<String,Object>> out=new ArrayList<>();for(Map<String,Object> r:source)if(eligible(r))out.add(r);out.sort(GameOrder.ROUNDS.reversed());return out;}
    public static List<Map<String,Object>> dates(List<Map<String,Object>> source,String filter,Instant now,ZoneId zone,long customStart,long customEnd){
        LocalDate today=now.atZone(zone).toLocalDate(),start=null,end=null;
        switch(filter){
            case "today":start=today;end=today.plusDays(1);break;
            case "yesterday":start=today.minusDays(1);end=today;break;
            case "week":start=today.minusDays(today.getDayOfWeek().getValue()%7);end=start.plusWeeks(1);break;
            case "month":start=today.withDayOfMonth(1);end=start.plusMonths(1);break;
            case "year":start=today.withDayOfYear(1);end=start.plusYears(1);break;
            case "custom":if(customStart!=0)start=Instant.ofEpochMilli(customStart).atZone(zone).toLocalDate();if(customEnd!=0)end=Instant.ofEpochMilli(customEnd).atZone(zone).toLocalDate().plusDays(1);break;
            default:break;
        }
        long min=start==null?Long.MIN_VALUE:start.atStartOfDay(zone).toInstant().toEpochMilli(),max=end==null?Long.MAX_VALUE:end.atStartOfDay(zone).toInstant().toEpochMilli();
        List<Map<String,Object>> out=new ArrayList<>();for(Map<String,Object> r:games(source)){long t=time(r.get("timestamp"));if(start==null&&end==null||t!=Long.MIN_VALUE&&t>=min&&t<max)out.add(r);}return out;
    }
    public static List<Map<String,Object>> scope(List<Map<String,Object>> rounds,List<Map<String,Object>> metadata,String scope){
        if("all".equals(scope))return games(rounds);
        String target=scope.startsWith("session:")?scope.substring(8):"";
        if("unassigned".equals(scope))target="@local/unassigned";
        if("current".equals(scope)||"previous".equals(scope)){
            long latest=Long.MIN_VALUE;for(Map<String,Object> s:metadata){boolean active=s.get("endTime")==null;if(active!="current".equals(scope))continue;long t=time(s.get(active?"startTime":"endTime"));if(t>latest&&!text(s,"id").isEmpty()){latest=t;target=text(s,"id");}}
        }
        for(Session session:groupSessions(rounds,metadata))if(session.id.equals(target))return new ArrayList<>(session.rounds);
        return new ArrayList<>();
    }
    public static final class Stats {
        public int played,wins,losses,unknown,currentWins,currentLosses,longestWins,longestLosses;
        public long points,gained,lost,bestRound,worstRound;
        public double average,variation;
        public final List<Long> cumulative=new ArrayList<>();
        public boolean limited(){return played<10;}
        public int decided(){return wins+losses;}
        public double rate(){return decided()==0?Double.NaN:100d*wins/decided();}
    }
    public static Stats stats(String pid,List<Map<String,Object>> chronological){
        Stats s=new Stats();s.cumulative.add(0L);double mean=0,m2=0;
        for(Map<String,Object> r:chronological){if(!eligible(r)||role(r,pid).isEmpty())continue;
            long d=delta(r,pid);s.played++;s.points+=d;if(d>0)s.gained+=d;else s.lost-=d;
            if(s.played==1){s.bestRound=d;s.worstRound=d;}else{s.bestRound=Math.max(s.bestRound,d);s.worstRound=Math.min(s.worstRound,d);}
            double diff=d-mean;mean+=diff/s.played;m2+=diff*(d-mean);
            int outcome=result(r,pid);if(outcome>0){s.wins++;s.currentWins++;s.currentLosses=0;}else if(outcome<0){s.losses++;s.currentLosses++;s.currentWins=0;}else{s.unknown++;s.currentWins=0;s.currentLosses=0;}
            s.longestWins=Math.max(s.longestWins,s.currentWins);s.longestLosses=Math.max(s.longestLosses,s.currentLosses);s.cumulative.add(s.points);
        }
        s.average=s.played==0?0:(double)s.points/s.played;s.variation=s.played==0?0:Math.sqrt(Math.max(0,m2/s.played));return s;
    }
    public static final class Form {
        public final List<Map<String,Object>> recent,previous;
        public final Stats now,before;
        Form(String pid,List<Map<String,Object>> source,int window){List<Map<String,Object>> played=new ArrayList<>();for(Map<String,Object> r:source)if(!role(r,pid).isEmpty())played.add(r);
            int end=played.size(),start=Math.max(0,end-window);recent=new ArrayList<>(played.subList(start,end));previous=new ArrayList<>(played.subList(Math.max(0,start-window),start));now=stats(pid,recent);before=stats(pid,previous);}
        public boolean comparable(int window){return now.played==window&&before.played==window;}
    }
    public static final class Pair {
        public final String peer;public final int rounds,wins,decided;public final long points;
        Pair(String peer,List<Map<String,Object>> source,String pid){this.peer=peer;int n=0,w=0,k=0;long total=0;for(Map<String,Object> r:source){if(role(r,pid).isEmpty()||role(r,peer).isEmpty()||"Opponent".equals(role(r,pid))||"Opponent".equals(role(r,peer)))continue;n++;int result=result(r,pid);if(result!=0)k++;if(result>0)w++;total+=delta(r,pid)+delta(r,peer);}rounds=n;wins=w;decided=k;points=total;}
        public double rate(){return decided==0?Double.NaN:100d*wins/decided;}
        public double average(){return rounds==0?0:(double)points/rounds;}
    }
    public static final class Frame {
        public final Map<String,Object> round;public final Map<String,Long> scores;public final Map<String,Integer> ranks,before;
        public final Set<String> leaders;public final int moved,movement;public final long swing,margin;
        Frame(Map<String,Object> r,Map<String,Long> scores,Map<String,Integer> before){round=r;this.scores=Collections.unmodifiableMap(new LinkedHashMap<>(scores));this.before=Collections.unmodifiableMap(new HashMap<>(before));ranks=rank(scores);leaders=new TreeSet<>();for(String id:ranks.keySet())if(ranks.get(id)==1)leaders.add(id);
            int count=0,change=0;for(String id:before.keySet()){int d=Math.abs(before.get(id)-ranks.get(id));if(d>0)count++;change+=d;}moved=count;movement=change;
            long abs=0;for(String id:ids(r.get("playerIds")))abs+=Math.abs(delta(r,id));swing=abs;
            List<Long> ordered=new ArrayList<>(scores.values());ordered.sort(Collections.reverseOrder());margin=ordered.size()<2?0:ordered.get(0)-ordered.get(1);
        }
    }
    private static Map<String,Integer> rank(Map<String,Long> scores){List<Long> values=new ArrayList<>(scores.values());values.sort(Collections.reverseOrder());Map<Long,Integer> places=new HashMap<>();for(int i=0;i<values.size();i++)places.putIfAbsent(values.get(i),i+1);Map<String,Integer> ranks=new LinkedHashMap<>();for(String id:scores.keySet())ranks.put(id,places.get(scores.get(id)));return ranks;}
    public static final class Session {
        public final String id,name;public final boolean active;public final List<Map<String,Object>> rounds=new ArrayList<>();
        Session(String id,String name,boolean active){this.id=id;this.name=name;this.active=active;}
    }
    public static final class Report {
        public final List<Map<String,Object>> rounds;
        public final Map<String,String> names=new LinkedHashMap<>();
        public final Set<String> rosterIds=new HashSet<>();
        public final Map<String,Stats> players=new LinkedHashMap<>();
        public final List<Frame> timeline=new ArrayList<>();
        public final List<Session> sessions;
        public final Map<String,Long> comeback=new HashMap<>(),highestLead=new HashMap<>();
        public int leaderChanges;
        public Report(List<Map<String,Object>> roster,List<Map<String,Object>> source,List<Map<String,Object>> metadata){
            rounds=games(source);for(Map<String,Object> p:roster){String id=text(p,"id");if(!id.isEmpty()){rosterIds.add(id);names.put(id,text(p,"name").isEmpty()?"Unnamed player":text(p,"name"));}}
            for(Map<String,Object> r:rounds)for(String id:ids(r.get("playerIds")))names.putIfAbsent(id,historicalName(r,id));
            for(String id:names.keySet())players.put(id,stats(id,rounds));
            Map<String,Long> totals=new LinkedHashMap<>(),deficits=new HashMap<>();Set<String> previous=Collections.emptySet();
            for(Map<String,Object> r:rounds){Map<String,Integer> before=rank(totals);for(String id:ids(r.get("playerIds")))totals.put(id,totals.getOrDefault(id,0L)+delta(r,id));
                if(totals.isEmpty())continue;Frame f=new Frame(r,totals,before);timeline.add(f);
                if(!previous.isEmpty()&&!previous.equals(f.leaders))leaderChanges++;previous=f.leaders;
                long top=Collections.max(totals.values());for(String id:totals.keySet()){
                    if(f.leaders.contains(id)){comeback.put(id,Math.max(comeback.getOrDefault(id,0L),deficits.getOrDefault(id,0L)));deficits.put(id,0L);highestLead.put(id,Math.max(highestLead.getOrDefault(id,0L),f.margin));}
                    else deficits.put(id,Math.max(deficits.getOrDefault(id,0L),top-totals.get(id)));
                }
            }
            sessions=groupSessions(rounds,metadata);
        }
        public Form form(String pid,int window){return new Form(pid,rounds,window);}
        public Stats roleStats(String pid,String role){List<Map<String,Object>> subset=new ArrayList<>();for(Map<String,Object> r:rounds)if(role.equals(role(r,pid)))subset.add(r);return stats(pid,subset);}
        public Map<Long,Stats> bids(String pid){Map<Long,List<Map<String,Object>>> grouped=new TreeMap<>();for(Map<String,Object> r:rounds)if(pid.equals(text(r,"hukumId"))&&!role(r,pid).isEmpty()){long bid=number(r.get("bid"));grouped.computeIfAbsent(bid,k->new ArrayList<>()).add(r);}Map<Long,Stats> out=new LinkedHashMap<>();for(Long bid:grouped.keySet())out.put(bid,stats(pid,grouped.get(bid)));return out;}
        public List<Pair> pairs(String pid){List<Pair> out=new ArrayList<>();for(String peer:names.keySet())if(!pid.equals(peer)){Pair pair=new Pair(peer,rounds,pid);if(pair.rounds>0)out.add(pair);}out.sort((a,b)->{int c=Boolean.compare(b.decided>=10,a.decided>=10);if(c==0)c=Double.compare(Double.isNaN(b.rate())?-1:b.rate(),Double.isNaN(a.rate())?-1:a.rate());if(c==0)c=Integer.compare(b.rounds,a.rounds);return c==0?a.peer.compareTo(b.peer):c;});return out;}
        public List<Frame> impacts(){List<Frame> out=new ArrayList<>(timeline);out.sort((a,b)->{int c=Integer.compare(b.movement,a.movement);if(c==0)c=Long.compare(b.swing,a.swing);return c==0?GameOrder.ROUNDS.compare(a.round,b.round):c;});return out;}
        public List<String> insights(String pid,int window){List<String> out=new ArrayList<>();Stats s=players.get(pid);if(s==null||s.played==0){out.add("Play saved table rounds to build your insights.");return out;}
            Form form=form(pid,window);if(form.comparable(window)){double diff=form.now.average-form.before.average;out.add(String.format(Locale.ROOT,"Last %d rounds: %.1f points/round; previous %d: %.1f (%+.1f change).",window,form.now.average,window,form.before.average,diff));}else out.add("Need "+(window*2)+" played rounds for two complete "+window+"-round form windows; currently "+s.played+".");
            if(s.currentWins>=2)out.add("Current winning streak: "+s.currentWins+" played rounds.");if(s.currentLosses>=2)out.add("Current losing streak: "+s.currentLosses+" played rounds.");
            String best="";Stats bestRole=null;for(String role:new String[]{"Hukum","Partner","Opponent"}){Stats r=roleStats(pid,role);if(r.decided()>=10&&(bestRole==null||r.rate()>bestRole.rate())){bestRole=r;best=role;}}
            if(bestRole!=null)out.add(String.format(Locale.ROOT,"Highest observed role win rate: %s, %.0f%% across %d decided rounds.",best,bestRole.rate(),bestRole.decided()));else out.add("Role comparisons have limited data until a role has 10 decided rounds.");
            List<Pair> pairs=pairs(pid);if(!pairs.isEmpty()&&pairs.get(0).decided>=10){Pair p=pairs.get(0);out.add(String.format(Locale.ROOT,"Highest observed partnership win rate: with %s, %.0f%% across %d decided rounds together.",names.get(p.peer),p.rate(),p.decided));}
            long recovered=comeback.getOrDefault(pid,0L);if(recovered>0)out.add("Largest deficit recovered to a tied or outright lead: "+recovered+" points.");
            if(s.unknown>0)out.add(s.unknown+" rounds have an unknown result and are excluded from win rates.");
            out.add("Past results describe these saved rounds; they do not predict the next game.");return out;
        }
    }
    private static String historicalName(Map<String,Object> r,String pid){if(pid.equals(text(r,"hukumId"))&&!text(r,"hukumName").isEmpty())return text(r,"hukumName");for(String key:new String[]{"partner","nonPartner"}){Object ids=r.get(key+"Ids"),names=r.get(key+"Names");if(ids instanceof List&&names instanceof List){int i=((List<?>)ids).indexOf(pid);if(i>=0&&i<((List<?>)names).size()&&((List<?>)names).get(i)!=null)return String.valueOf(((List<?>)names).get(i));}}return "Former player · "+pid;}
    public static List<Session> groupSessions(List<Map<String,Object>> rounds,List<Map<String,Object>> metadata){
        List<Map<String,Object>> sorted=new ArrayList<>(metadata);sorted.sort((a,b)->Long.compare(time(b.get("startTime")),time(a.get("startTime"))));Map<String,Session> groups=new LinkedHashMap<>();
        for(Map<String,Object> s:sorted){String id=text(s,"id");if(!id.isEmpty())groups.put(id,new Session(id,text(s,"sessionName").isEmpty()?"Game Session":text(s,"sessionName"),s.get("endTime")==null));}
        for(Map<String,Object> r:games(rounds)){String id=text(r,"sessionId");if(id.isEmpty()){
            long t=time(r.get("timestamp"));for(Map<String,Object> s:sorted){if(text(s,"id").isEmpty())continue;long start=time(s.get("startTime")),end=s.get("endTime")==null?Long.MAX_VALUE:time(s.get("endTime"));if(t!=Long.MIN_VALUE&&start!=Long.MIN_VALUE&&t>=start&&t<end){id=text(s,"id");break;}}
        }
            if(id.isEmpty())id="@local/unassigned";Session session=groups.get(id);if(session==null){session=new Session(id,"@local/unassigned".equals(id)?"Unassigned rounds":"Session · "+id,false);groups.put(id,session);}session.rounds.add(r);
        }
        List<Session> out=new ArrayList<>(groups.values());out.removeIf(s->s.rounds.isEmpty());return out;
    }
}
