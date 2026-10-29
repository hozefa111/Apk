package com.hozefa.threepattipro.nativeapp;

import java.util.*;

/** Read-only chart data from the same rounds/standings as Ranks. No database work. */
final class ScoreTrendModel {
    static final class Series {
        final String id,name;final int colorIndex;
        Series(String id,String name,int colorIndex){this.id=id;this.name=name;this.colorIndex=colorIndex;}
    }
    static final class Frame {
        final int round;final String key,date;final long[] values;final boolean separate;
        Frame(int round,String key,String date,long[] values,boolean separate){this.round=round;this.key=key;this.date=date;this.values=values;this.separate=separate;}
    }
    final List<Series> series=new ArrayList<>();final List<Frame> frames=new ArrayList<>();
    final int savedRounds,totalRounds;final boolean pending;
    ScoreTrendModel(List<Map<String,Object>> source,ScoreboardModel board,List<Map<String,Object>> roster,Map<String,Object> remote){
        List<Map<String,Object>> rounds=AdvancedAnalytics.games(source);savedRounds=rounds.size();
        Set<String> ids=new TreeSet<>();for(Map<String,Object> p:roster)ids.add(AdvancedAnalytics.text(p,"id"));for(ScoreboardModel.Row row:board.rows)ids.add(row.id);
        Map<String,Integer> colors=new HashMap<>();int next=0;for(String id:ids)colors.put(id,next++);
        List<ScoreboardModel.Row> rows=new ArrayList<>(board.rows);rows.sort(Comparator.comparing(r->r.id));
        for(ScoreboardModel.Row r:rows)series.add(new Series(r.id,r.name,colors.get(r.id)));
        long[] totals=new long[series.size()];frames.add(new Frame(0,"baseline","",totals.clone(),false));
        int n=0;for(Map<String,Object> round:rounds){for(int i=0;i<series.size();i++)totals[i]=Math.addExact(totals[i],AdvancedAnalytics.delta(round,series.get(i).id));frames.add(new Frame(++n,AdvancedAnalytics.text(round,"id"),AdvancedAnalytics.text(round,"timestamp"),totals.clone(),false));}
        long remoteCount=remote==null?0:AdvancedAnalytics.number(remote.get("totalRounds"));
        boolean live=remote!=null&&remoteCount>=savedRounds&&(SessionLeaderboard.active(remote)||AdvancedAnalytics.number(remote.get("pendingExports"))>0||savedRounds<remoteCount);
        totalRounds=live?(int)Math.min(Integer.MAX_VALUE,remoteCount):savedRounds;
        long[] latest=new long[series.size()];boolean mismatch=false;for(int i=0;i<series.size();i++){latest[i]=board.byId.get(series.get(i).id).points;mismatch|=latest[i]!=totals[i];}
        pending=live&&(savedRounds<totalRounds||mismatch);
        if(pending){
            // Equal counts but different totals cannot prove any intermediate history.
            if(savedRounds==totalRounds)frames.subList(1,frames.size()).clear();
            frames.add(new Frame(totalRounds,"latest","",latest,true));
        }
    }
}
