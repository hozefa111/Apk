package com.hozefa.threepattipro.nativeapp;
import android.app.Activity;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
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
    private GradientDrawable surface(int top,int bottom,int edge,int radius){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{top,bottom});
        d.setCornerRadius(DialogUi.dp(a,radius));d.setStroke(DialogUi.dp(a,1),edge);return d;
    }
    private void center(LinearLayout c,String label,int size,int color,boolean bold){TextView t=text(label,size,color,bold);t.setGravity(Gravity.CENTER);add(c,t);}
    /** Drawn crown: consistent on every Android version, independent of emoji fonts. */
    private final class Crown extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Shader gold=new LinearGradient(0,3,0,45,0xFFFFF1AF,0xFFD79B32,Shader.TileMode.CLAMP);
        private final Path silhouette=new Path();
        Crown(){super(a);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            silhouette.moveTo(10,15);silhouette.lineTo(22,25);silhouette.lineTo(32,7);silhouette.lineTo(42,25);silhouette.lineTo(54,15);silhouette.lineTo(48,37);silhouette.lineTo(16,37);silhouette.close();
        }
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.save();canvas.scale(getWidth()/64f,getHeight()/48f);
            paint.setShader(gold);canvas.drawPath(silhouette,paint);
            canvas.drawRoundRect(16,40,48,45,2,2,paint);canvas.drawCircle(9,12,3,paint);canvas.drawCircle(32,4,3,paint);canvas.drawCircle(55,12,3,paint);
            paint.setShader(null);paint.setColor(0xFF77511D);canvas.drawCircle(32,30,2.5f,paint);canvas.restore();
        }
    }
    void podium(Runnable all){
        add(parent,text("ROYAL TOP 3",16,DialogUi.GOLD,true));DialogUi.gap(parent,10);
        if(model.rows.isEmpty()){add(parent,text("No players with rounds in this scope.",14,DialogUi.MUTED,false));return;}
        boolean horizontal=a.getResources().getConfiguration().screenWidthDp>=340&&a.getResources().getConfiguration().fontScale<=1.3f;
        LinearLayout stage=DialogUi.column(a);stage.setPadding(DialogUi.dp(a,7),DialogUi.dp(a,14),DialogUi.dp(a,7),DialogUi.dp(a,8));
        stage.setBackground(surface(0xFF17191F,0xFF101115,0xFF303038,22));
        LinearLayout row=DialogUi.column(a);row.setGravity(Gravity.BOTTOM);if(horizontal)row.setOrientation(LinearLayout.HORIZONTAL);
        int size=Math.min(3,model.rows.size());int[] order=horizontal&&size==3?new int[]{1,0,2}:size==3?new int[]{0,1,2}:size==2?new int[]{0,1}:new int[]{0};
        for(int index:order){ScoreboardModel.Row r=model.rows.get(index);boolean winner=r.rank==1;int metal=color(r);
            LinearLayout c=DialogUi.column(a);c.setGravity(Gravity.CENTER_HORIZONTAL);c.setPadding(DialogUi.dp(a,5),DialogUi.dp(a,12),DialogUi.dp(a,5),0);
            c.setBackground(surface(winner?0xFF493A21:r.rank==3?0xFF352B29:0xFF2C323E,0xFF1B1E26,winner?0xFFB68E40:0xFF42434D,16));
            c.setContentDescription("Podium "+r.name);c.setTag(new MotionEngine.Anchor("rank:podium:"+context+":"+r.id));
            TextView avatar=text(r.name.isEmpty()?"?":r.name.substring(0,r.name.offsetByCodePoints(0,1)).toUpperCase(Locale.ROOT),20,metal,true);
            avatar.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP,20);avatar.setGravity(Gravity.CENTER);avatar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            avatar.setBackground(surface(winner?0xFF6A532E:0xFF3A404C,0xFF222630,metal,50));
            if(winner){FrameLayout badge=new FrameLayout(a);FrameLayout.LayoutParams face=new FrameLayout.LayoutParams(DialogUi.dp(a,44),DialogUi.dp(a,44),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);badge.addView(avatar,face);
                Crown crown=new Crown();if(model.leaders.size()==1)crown.setTag(new MotionEngine.Anchor("crown:"+context));badge.addView(crown,new FrameLayout.LayoutParams(DialogUi.dp(a,42),DialogUi.dp(a,30),Gravity.TOP|Gravity.CENTER_HORIZONTAL));
                c.addView(badge,new LinearLayout.LayoutParams(DialogUi.dp(a,64),DialogUi.dp(a,64)));
            }else c.addView(avatar,new LinearLayout.LayoutParams(DialogUi.dp(a,44),DialogUi.dp(a,44)));DialogUi.gap(c,8);
            center(c,r.name,14,DialogUi.WHITE,true);DialogUi.gap(c,7);
            TextView value=text(ScoreboardModel.points(r.points),22,r.points<0?DialogUi.RED:metal,true);value.setGravity(Gravity.CENTER);
            // Full long values remain visible on narrow phones; the accessible text stays exact.
            if(horizontal){value.setMaxLines(1);value.setAutoSizeTextTypeUniformWithConfiguration(10,22,1,android.util.TypedValue.COMPLEX_UNIT_SP);}
            value.setTag(new MotionEngine.Anchor("podium-score:"+context+":"+r.id,r.points,true));
            c.addView(value,new LinearLayout.LayoutParams(-1,horizontal?DialogUi.dp(a,30):-2));DialogUi.gap(c,3);
            center(c,r.played+" played",11,DialogUi.MUTED,false);DialogUi.gap(c,12);
            TextView medal=text((r.tied>1?"Joint #":"#")+r.rank, winner?18:16,metal,true);medal.setGravity(Gravity.CENTER);
            medal.setBackground(surface(winner?0xFF5A4425:0xFF343844,0xFF22252D,winner?0xFF9B7836:0xFF42434D,10));
            medal.setMinHeight(DialogUi.dp(a,winner?32:28));c.addView(medal,new LinearLayout.LayoutParams(-1,-2));attachProfile(c,r.id);
            if(horizontal){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(DialogUi.dp(a,3),0,DialogUi.dp(a,3),0);row.addView(c,p);}else{add(row,c);DialogUi.gap(row,8);}
            if(celebrate&&winner)motion.leader(c);
        }
        add(stage,row);add(parent,stage);DialogUi.gap(parent,10);
        if(model.rows.size()>3&&model.rows.get(2).tied>1&&model.rows.get(3).rank==model.rows.get(2).rank){
            int extra=0;for(int i=3;i<model.rows.size()&&model.rows.get(i).rank==model.rows.get(2).rank;i++)extra++;
            TextView more=text("+"+extra+" more tied at #"+model.rows.get(2).rank+" · View full ranking",13,DialogUi.GOLD,true);more.setMinHeight(DialogUi.dp(a,48));more.setOnClickListener(v->all.run());more.setFocusable(true);add(parent,more);
        }
    }
    void cards(boolean big,int limit){
        if(!big&&!model.rows.isEmpty()){DialogUi.gap(parent,6);add(parent,text("SCORE BREAKDOWN",12,DialogUi.MUTED,true));DialogUi.gap(parent,10);}
        for(int i=0;i<Math.min(limit,model.rows.size());i++){
            ScoreboardModel.Row r=model.rows.get(i);LinearLayout c=card();c.setPadding(DialogUi.dp(a,14),DialogUi.dp(a,12),DialogUi.dp(a,14),DialogUi.dp(a,12));
            c.setBackground(surface(0xFF22262E,0xFF191C23,r.rank==1?0xFF685334:0xFF343842,16));
            c.setContentDescription("Scorecard "+r.name);c.setTag(new MotionEngine.Anchor("rank:"+context+":"+r.id));
            boolean stacked=big||a.getResources().getConfiguration().fontScale>1.3f||a.getResources().getConfiguration().screenWidthDp<340;
            LinearLayout head=DialogUi.column(a);if(!stacked){head.setOrientation(LinearLayout.HORIZONTAL);head.setGravity(Gravity.CENTER_VERTICAL);}
            TextView who=text(rank(r)+"   "+r.name,big?23:15,color(r),true);
            head.addView(who,stacked?new LinearLayout.LayoutParams(-1,-2):new LinearLayout.LayoutParams(0,-2,1));
            TextView score=text(ScoreboardModel.points(r.points),big?38:23,r.points<0?DialogUi.RED:DialogUi.GREEN,true);
            score.setTag(new MotionEngine.Anchor("rank-score:"+context+":"+r.id,r.points,true));
            if(!stacked){score.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);score.setMaxLines(1);score.setAutoSizeTextTypeUniformWithConfiguration(12,23,1,android.util.TypedValue.COMPLEX_UNIT_SP);head.addView(score,new LinearLayout.LayoutParams(0,DialogUi.dp(a,36),1));}else{DialogUi.gap(head,8);add(head,score);}
            add(c,head);DialogUi.gap(c,4);add(c,text(r.played+" played",12,DialogUi.MUTED,false));DialogUi.gap(c,10);
            View rule=new View(a);rule.setBackgroundColor(0xFF363943);c.addView(rule,new LinearLayout.LayoutParams(-1,DialogUi.dp(a,1)));DialogUi.gap(c,9);
            add(c,text("Last played round  "+(r.hasLast?ScoreboardModel.points(r.last):"—"),13,DialogUi.WHITE,false));DialogUi.gap(c,4);
            add(c,text("Current session  "+(session?ScoreboardModel.points(r.sessionPoints):"No active session"),12,DialogUi.MUTED,false));DialogUi.gap(c,7);
            add(c,text(r.rank==1?(r.tied>1?"Joint leader":"Table leader"):model.gap(r)+" points behind leader",12,color(r),false));
            int move=moves.getOrDefault(r.id,0);if(move!=0)add(c,text((move>0?"↑ ":"↓ ")+Math.abs(move)+" position"+(Math.abs(move)==1?"":"s"),14,move>0?DialogUi.GREEN:DialogUi.RED,true));
            attachProfile(c,r.id);add(parent,c);DialogUi.gap(parent,9);
        }
    }
}
