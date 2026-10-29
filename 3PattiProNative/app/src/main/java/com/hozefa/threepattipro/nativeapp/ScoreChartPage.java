package com.hozefa.threepattipro.nativeapp;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** A single shared graph with per-player controls and an accessible round inspector. */
final class ScoreChartPage {
    private static TextView text(Context c,String value,int size,int color){return DialogUi.text(c,value,size,color,false);}
    private static Button button(Context c,String label){Button b=new Button(c);b.setText(label);b.setTextSize(12);b.setAllCaps(false);b.setTextColor(DialogUi.GOLD);b.setMinHeight(DialogUi.dp(c,48));b.setBackground(DialogUi.background(c,DialogUi.CARD,12));return b;}
    static void add(Context c,LinearLayout target,ScoreTrendModel model,ScoreTrendChart.State state){
        LinearLayout card=DialogUi.column(c);card.setPadding(DialogUi.dp(c,14),DialogUi.dp(c,16),DialogUi.dp(c,14),DialogUi.dp(c,16));card.setBackground(DialogUi.background(c,DialogUi.SURFACE,18));target.addView(card);
        card.addView(DialogUi.text(c,"All players score trend",17,DialogUi.WHITE,true));DialogUi.gap(card,6);
        card.addView(text(c,model.pending?"History syncing · "+model.savedRounds+" / "+model.totalRounds+" rounds. Latest totals are shown as dots; missing rounds are not connected.":model.totalRounds+" rounds · cumulative game points",12,DialogUi.MUTED));
        TextView selected=text(c,"",13,DialogUi.WHITE);selected.setMinHeight(DialogUi.dp(c,48));
        List<TextView> values=new ArrayList<>(),keys=new ArrayList<>();List<CheckBox> toggles=new ArrayList<>();Button[] controls=new Button[3];
        ScoreTrendChart chart=new ScoreTrendChart(c,model,state,index->{
            ScoreTrendModel.Frame f=model.frames.get(index);String label=f.round==0?"Starting scores · 0":f.separate?"Latest totals · Round "+f.round+" · history syncing":"Round "+f.round+" · "+date(f.date);
            selected.setText(label);for(int i=0;i<values.size();i++)values.get(i).setText(ScoreboardModel.points(f.values[i]));
            if(controls[0]!=null){controls[0].setEnabled(index>0);controls[1].setEnabled(index<model.frames.size()-1);controls[2].setEnabled(index<model.frames.size()-1);}
        });
        chart.setTag("all-player-score-chart");
        for(int start=0;start<model.series.size();start+=3){LinearLayout row=new LinearLayout(c);row.setOrientation(LinearLayout.HORIZONTAL);for(int i=start;i<Math.min(start+3,model.series.size());i++){final int index=i;TextView key=text(c,"● "+model.series.get(i).name,11,chart.color(i));key.setSingleLine(true);key.setEllipsize(android.text.TextUtils.TruncateAt.END);key.setMinHeight(DialogUi.dp(c,24));key.setPadding(0,0,DialogUi.dp(c,5),0);key.setFocusable(true);key.setContentDescription("Toggle line: "+model.series.get(i).name);key.setOnClickListener(v->toggles.get(index).performClick());key.setAlpha(state.hidden.contains(model.series.get(i).id)?.45f:1f);keys.add(key);row.addView(key,new LinearLayout.LayoutParams(0,-2,1));}card.addView(row);}
        int height=Math.max(210,Math.min(320,Math.round(c.getResources().getDisplayMetrics().heightPixels/c.getResources().getDisplayMetrics().density*.4f)));
        height=Math.max(190,height-24*((model.series.size()+2)/3));
        card.addView(chart,new LinearLayout.LayoutParams(-1,DialogUi.dp(c,height)));card.addView(selected);
        LinearLayout buttons=new LinearLayout(c);buttons.setOrientation(LinearLayout.HORIZONTAL);
        for(int i=0;i<3;i++){final int action=i;Button b=button(c,new String[]{"Previous","Next","Latest"}[i]);controls[i]=b;b.setContentDescription(new String[]{"Previous chart round","Next chart round","Latest chart round"}[i]);b.setOnClickListener(v->{if(action==2)chart.latest();else chart.move(action==0?-1:1);});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);if(i<2)lp.rightMargin=DialogUi.dp(c,6);buttons.addView(b,lp);}card.addView(buttons);DialogUi.gap(card,14);
        card.addView(text(c,"Tap a name to show or hide its line",12,DialogUi.MUTED));DialogUi.gap(card,6);
        for(int i=0;i<model.series.size();i++){
            final int index=i;ScoreTrendModel.Series s=model.series.get(i);LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);
            CheckBox toggle=new CheckBox(c);toggle.setText(s.name);toggle.setTextSize(14);toggle.setMaxLines(2);toggle.setTextColor(DialogUi.WHITE);toggle.setButtonTintList(ColorStateList.valueOf(chart.color(i)));toggle.setChecked(!state.hidden.contains(s.id));toggle.setContentDescription("Show line: "+s.name);toggle.setMinHeight(DialogUi.dp(c,48));row.addView(toggle,new LinearLayout.LayoutParams(0,-2,1));
            TextView score=DialogUi.text(c,"",15,chart.color(i),true);score.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);score.setPadding(DialogUi.dp(c,8),0,0,0);values.add(score);toggles.add(toggle);row.addView(score);card.addView(row);
            toggle.setOnCheckedChangeListener((view,checked)->{if(checked)state.hidden.remove(s.id);else state.hidden.add(s.id);chart.visibilityChanged();score.setAlpha(checked?1f:.45f);keys.get(index).setAlpha(checked?1f:.45f);});score.setAlpha(toggle.isChecked()?1f:.45f);
        }
        DialogUi.gap(card,10);card.addView(text(c,"Tap or drag horizontally to inspect a round. Scores carry forward when a player skips a round. Left/right keys and Previous/Next also work.",12,DialogUi.MUTED));
        chart.select(chart.cursor());
    }
    private static String date(String value){try{return DateTimeFormatter.ofPattern("dd MMM, HH:mm",Locale.ENGLISH).withZone(ZoneId.systemDefault()).format(Instant.parse(value));}catch(Exception unknown){return "Date unavailable";}}
}
