package com.hozefa.threepattipro.nativeapp;
import android.app.Activity;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.function.Consumer;

/** Responsive, read-only score presentation. No sequence is played after saving. */
final class ScoreboardPage {
    private final Activity a;private final MotionEngine motion;private final LinearLayout parent;
    private final ScoreboardModel model;private final String context;private final Map<String,Integer> moves;
    private final boolean session,celebrate;private final Consumer<String> profile;
    ScoreboardPage(Activity a,MotionEngine motion,LinearLayout parent,ScoreboardModel model,String context,Map<String,Integer> moves,boolean session,boolean celebrate,Consumer<String> profile){this.a=a;this.motion=motion;this.parent=parent;this.model=model;this.context=context;this.moves=moves;this.session=session;this.celebrate=celebrate;this.profile=profile;}
    private TextView text(String s,int size,int color,boolean bold){return DialogUi.text(a,s,size,color,bold);}
    private void add(LinearLayout p,View v){p.addView(v,new LinearLayout.LayoutParams(-1,-2));}
    private int color(ScoreboardModel.Row r){return r.rank==1?DialogUi.GOLD:r.rank==2?0xFFD5DCE8:r.rank==3?0xFFD9A079:DialogUi.WHITE;}
    private String rank(ScoreboardModel.Row r){return (r.rank==1?"♛  ":"")+(r.tied>1?"Joint #":"#")+r.rank;}
    private LinearLayout card(){LinearLayout v=DialogUi.body(a);v.setPadding(DialogUi.dp(a,14),DialogUi.dp(a,16),DialogUi.dp(a,14),DialogUi.dp(a,16));v.setBackground(DialogUi.background(a,DialogUi.CARD,16));return v;}
    private void attachProfile(View view,String id){view.setClickable(true);view.setFocusable(true);view.setOnClickListener(v->profile.accept(id));}
    void podium(Runnable all){
        add(parent,text("ROYAL TOP 3",16,DialogUi.GOLD,true));DialogUi.gap(parent,10);
        if(model.rows.isEmpty()){add(parent,text("No players with rounds in this scope.",14,DialogUi.MUTED,false));return;}
        boolean horizontal=a.getResources().getConfiguration().screenWidthDp>=320&&a.getResources().getConfiguration().fontScale<=1.3f;
        LinearLayout row=DialogUi.column(a);row.setGravity(Gravity.BOTTOM);if(horizontal)row.setOrientation(LinearLayout.HORIZONTAL);
        int size=Math.min(3,model.rows.size());int[] order=horizontal&&size==3?new int[]{1,0,2}:size==3?new int[]{0,1,2}:size==2?new int[]{0,1}:new int[]{0};
        for(int index:order){ScoreboardModel.Row r=model.rows.get(index);LinearLayout c=card();c.setContentDescription("Podium "+r.name);c.setTag(new MotionEngine.Anchor("rank:podium:"+context+":"+r.id));
            TextView medal=text(rank(r),19,color(r),true);medal.setGravity(Gravity.CENTER);
            if(r.rank==1&&model.leaders.size()==1)medal.setTag(new MotionEngine.Anchor("crown:"+context));
            add(c,medal);DialogUi.gap(c,12);TextView name=text(r.name,15,DialogUi.WHITE,true);name.setGravity(Gravity.CENTER);add(c,name);DialogUi.gap(c,10);
            TextView value=text(ScoreboardModel.points(r.points),22,r.points<0?DialogUi.RED:color(r),true);value.setGravity(Gravity.CENTER);value.setTag(new MotionEngine.Anchor("podium-score:"+context+":"+r.id,r.points,true));add(c,value);
            add(c,text(r.played+" played",11,DialogUi.MUTED,false));attachProfile(c,r.id);
            if(horizontal){c.setMinimumHeight(DialogUi.dp(a,index==0?205:175));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(DialogUi.dp(a,3),0,DialogUi.dp(a,3),0);row.addView(c,p);}else{add(row,c);DialogUi.gap(row,8);}
            if(celebrate&&r.rank==1)motion.leader(c);
        }
        add(parent,row);DialogUi.gap(parent,10);
        if(model.rows.size()>3&&model.rows.get(2).tied>1&&model.rows.get(3).rank==model.rows.get(2).rank){
            int extra=0;for(int i=3;i<model.rows.size()&&model.rows.get(i).rank==model.rows.get(2).rank;i++)extra++;
            TextView more=text("+"+extra+" more tied at #"+model.rows.get(2).rank+" · View full ranking",13,DialogUi.GOLD,true);more.setMinHeight(DialogUi.dp(a,48));more.setOnClickListener(v->all.run());more.setFocusable(true);add(parent,more);
        }
    }
    void cards(boolean big,int limit){for(int i=0;i<Math.min(limit,model.rows.size());i++){
        ScoreboardModel.Row r=model.rows.get(i);LinearLayout c=card();c.setContentDescription("Scorecard "+r.name);c.setTag(new MotionEngine.Anchor("rank:"+context+":"+r.id));
        add(c,text(rank(r)+"   "+r.name,big?23:17,color(r),true));DialogUi.gap(c,8);
        TextView score=text(ScoreboardModel.points(r.points),big?38:28,r.points<0?DialogUi.RED:DialogUi.GREEN,true);score.setTag(new MotionEngine.Anchor("rank-score:"+context+":"+r.id,r.points,true));add(c,score);
        add(c,text(r.played+" played",12,DialogUi.MUTED,false));DialogUi.gap(c,10);
        add(c,text("Last played round  "+(r.hasLast?ScoreboardModel.points(r.last):"—"),14,DialogUi.WHITE,false));
        add(c,text("Current session  "+(session?ScoreboardModel.points(r.sessionPoints):"No active session"),13,DialogUi.MUTED,false));
        add(c,text(r.rank==1?(r.tied>1?"Joint leader":"Table leader"):model.gap(r)+" points behind leader",13,DialogUi.GOLD,false));
        int move=moves.getOrDefault(r.id,0);if(move!=0)add(c,text((move>0?"↑ ":"↓ ")+Math.abs(move)+" position"+(Math.abs(move)==1?"":"s"),14,move>0?DialogUi.GREEN:DialogUi.RED,true));
        attachProfile(c,r.id);add(parent,c);DialogUi.gap(parent,12);
    }}
}
