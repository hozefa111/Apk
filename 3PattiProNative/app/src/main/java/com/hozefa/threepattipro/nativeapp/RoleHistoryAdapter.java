package com.hozefa.threepattipro.nativeapp;

import android.content.Context;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.function.Function;

/** Recycled, wrapping history cards; no TextView contains the full history dump. */
final class RoleHistoryAdapter extends BaseAdapter {
    private final Context context;
    private final String player;
    private final List<Map<String,Object>> rows;
    private final Function<Map<String,Object>,String> date;
    RoleHistoryAdapter(Context context,String player,List<Map<String,Object>> rows,Function<Map<String,Object>,String> date){this.context=context;this.player=player;this.rows=rows;this.date=date;}
    @Override public int getCount(){return rows.size();}
    @Override public Map<String,Object> getItem(int position){return rows.get(position);}
    @Override public long getItemId(int position){return position;}
    private static String text(Map<String,Object> row,String key){Object v=row.get(key);return v==null?"":String.valueOf(v);}
    @Override public View getView(int position,View recycled,ViewGroup parent){
        Cell cell;
        if(recycled==null){cell=new Cell(context);recycled=cell.root;recycled.setTag(cell);}else cell=(Cell)recycled.getTag();
        Map<String,Object> round=getItem(position);Object values=round.get("scoreChanges");Object raw=values instanceof Map?((Map<?,?>)values).get(player):null;
        int points=raw instanceof Number?((Number)raw).intValue():0;
        String signed=points>0?"+"+points:String.valueOf(points);
        String dateLabel=date.apply(round),hukum=text(round,"hukumName");
        cell.date.setText(dateLabel);cell.name.setText("Hukum · "+(hukum.isEmpty()?"—":hukum));
        cell.meta.setText("Bid "+text(round,"bid")+" · "+("win".equals(text(round,"result"))?"Hukum win":"Hukum loss"));
        cell.points.setText(signed);cell.points.setTextColor(points>0?DialogUi.GREEN:points<0?DialogUi.RED:DialogUi.MUTED);
        recycled.setContentDescription(dateLabel+", Hukum "+hukum+", "+signed+" points. View round details");
        return recycled;
    }
    private static final class Cell {
        final LinearLayout root;final TextView date,name,meta,points;
        Cell(Context c){
            root=DialogUi.column(c);root.setPadding(DialogUi.dp(c,10),DialogUi.dp(c,4),DialogUi.dp(c,10),DialogUi.dp(c,6));
            LinearLayout card=DialogUi.column(c);card.setPadding(DialogUi.dp(c,14),DialogUi.dp(c,14),DialogUi.dp(c,14),DialogUi.dp(c,14));card.setBackground(DialogUi.background(c,DialogUi.CARD,14));root.addView(card,new LinearLayout.LayoutParams(-1,-2));
            date=DialogUi.text(c,"",12,DialogUi.MUTED,false);card.addView(date);DialogUi.gap(card,7);
            name=DialogUi.text(c,"",15,DialogUi.WHITE,true);card.addView(name);DialogUi.gap(card,7);
            meta=DialogUi.text(c,"",12,DialogUi.MUTED,false);card.addView(meta);DialogUi.gap(card,10);
            LinearLayout score=DialogUi.column(c);score.addView(DialogUi.text(c,"POINTS",10,DialogUi.MUTED,false));DialogUi.gap(score,5);
            points=DialogUi.text(c,"",22,DialogUi.GREEN,true);score.addView(points);card.addView(score);
            root.setMinimumHeight(DialogUi.dp(c,48));
        }
    }
}
