package com.hozefa.threepattipro.nativeapp;
import java.util.*;

/** Read-only presentation of saved game deltas, never a scoring authority. */
final class ScoreboardModel {
    static final class Row {
        final String id,name; long points,sessionPoints,last; boolean hasLast; int played,rank,tied,wins,losses,asHukum,asPartner;
        Row(String id,String name){this.id=id;this.name=name;}
    }
    boolean sessionView;
    final List<Row> rows=new ArrayList<>();
    final Map<String,Row> byId=new LinkedHashMap<>();
    final Set<String> leaders=new LinkedHashSet<>();
    ScoreboardModel(List<Map<String,Object>> players,List<Map<String,Object>> source,List<Map<String,Object>> session){
        for(Map<String,Object> p:players){String id=AdvancedAnalytics.text(p,"id");if(!id.isEmpty())byId.put(id,new Row(id,AdvancedAnalytics.text(p,"name")));}
        accumulate(source,false);accumulate(session,true);
        for(Row row:byId.values())if(row.played>0||row.points!=0)rows.add(row);
        rankRows();
    }
    private void rankRows(){
        rows.sort((a,b)->Long.compare(b.points,a.points));
        Map<Long,Integer> counts=new HashMap<>();for(Row row:rows)counts.merge(row.points,1,Integer::sum);
        long prior=0;int rank=0;for(int i=0;i<rows.size();i++){Row r=rows.get(i);if(i==0||r.points!=prior)rank=i+1;r.rank=rank;r.tied=counts.get(r.points);prior=r.points;if(rank==1)leaders.add(r.id);}
    }
    static ScoreboardModel standings(List<Map<String,Object>> board){
        ScoreboardModel model=new ScoreboardModel(Collections.emptyList(),Collections.emptyList(),Collections.emptyList());model.sessionView=true;
        for(Map<String,Object> p:board){String id=AdvancedAnalytics.text(p,"id");if(id.isEmpty()||model.byId.containsKey(id))continue;Row row=new Row(id,AdvancedAnalytics.text(p,"name"));row.points=AdvancedAnalytics.number(p.get("score"));row.sessionPoints=row.points;row.played=(int)AdvancedAnalytics.number(p.get("roundsPlayed"));row.wins=(int)AdvancedAnalytics.number(p.get("wins"));row.losses=(int)AdvancedAnalytics.number(p.get("losses"));row.asHukum=(int)AdvancedAnalytics.number(p.get("asHukum"));row.asPartner=(int)AdvancedAnalytics.number(p.get("asPartner"));row.hasLast=p.get("last") instanceof Number;row.last=AdvancedAnalytics.number(p.get("last"));model.byId.put(id,row);if(row.played>0||row.points!=0)model.rows.add(row);}model.rankRows();return model;
    }
    private void accumulate(List<Map<String,Object>> source,boolean session){
        for(Map<String,Object> round:AdvancedAnalytics.games(source)){
            Set<String> participants=new LinkedHashSet<>(AdvancedAnalytics.ids(round.get("playerIds")));
            Map<?,?> scores=round.get("scoreChanges") instanceof Map?(Map<?,?>)round.get("scoreChanges"):Collections.emptyMap();
            for(Row row:byId.values()){
                Object delta=scores.get(row.id);long value=delta instanceof Number?((Number)delta).longValue():0;
                if(session){row.sessionPoints+=value;continue;}
                row.points+=value;
                if(participants.contains(row.id)){row.played++;row.hasLast=true;row.last=value;int result=AdvancedAnalytics.result(round,row.id);if(result>0)row.wins++;if(result<0)row.losses++;if(row.id.equals(round.get("hukumId")))row.asHukum++;if(AdvancedAnalytics.ids(round.get("partnerIds")).contains(row.id))row.asPartner++;}
            }
        }
    }
    long gap(Row row){return rows.isEmpty()?0:rows.get(0).points-row.points;}
    Map<String,Integer> ranks(){Map<String,Integer> out=new HashMap<>();for(Row r:rows)out.put(r.id,r.rank);return out;}
    static String points(long n){return n>0?"+"+n:String.valueOf(n);}
}
