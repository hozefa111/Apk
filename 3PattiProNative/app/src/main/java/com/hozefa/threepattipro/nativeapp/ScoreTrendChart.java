package com.hozefa.threepattipro.nativeapp;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.*;
import java.util.function.IntConsumer;

/** Existing native Canvas trend styling, extended to all players. Cached paths keep scrubbing cheap. */
final class ScoreTrendChart extends View {
    static final int[] COLORS={0xFFF6D365,0xFF4FACFE,0xFF2ECD71,0xFFCA94FF,0xFFFF8C78,0xFF54DED6,0xFFFF8DC7,0xFFBEC76E};
    static final class State {final Set<String> hidden=new HashSet<>();int round=-1;boolean latest=true;}
    final ScoreTrendModel model;final State state;private final IntConsumer changed;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Path> paths=new ArrayList<>();private int cursor;private boolean dirty=true,dragging;private float downX,downY;private long low,high;
    ScoreTrendChart(Context c,ScoreTrendModel m,State s,IntConsumer changed){super(c);model=m;state=s;this.changed=changed;cursor=m.frames.size()-1;if(!s.latest){cursor=0;for(int i=0;i<m.frames.size();i++)if(m.frames.get(i).round<=s.round)cursor=i;}setFocusable(true);setClickable(true);setContentDescription("All players score chart. Left and right keys inspect rounds.");}
    int color(int i){int index=model.series.get(i).colorIndex;return index<COLORS.length?COLORS[index]:Color.HSVToColor(new float[]{(index*137.508f)%360,0.55f,1});}
    int cursor(){return cursor;}
    void select(int index){cursor=Math.max(0,Math.min(model.frames.size()-1,index));ScoreTrendModel.Frame f=model.frames.get(cursor);state.round=f.round;state.latest=cursor==model.frames.size()-1;StringBuilder description=new StringBuilder("Score chart. ").append(f.separate?"Latest totals, ":"").append("Round ").append(f.round);for(int i=0;i<model.series.size();i++)description.append(". ").append(model.series.get(i).name).append(" ").append(ScoreboardModel.points(f.values[i]));setContentDescription(description.toString());changed.accept(cursor);invalidate();}
    void move(int delta){select(cursor+delta);}
    void latest(){select(model.frames.size()-1);}
    void visibilityChanged(){dirty=true;invalidate();}
    private float dp(float n){return n*getResources().getDisplayMetrics().density;}
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){dirty=true;}
    private float left(){return dp(52);}private float width(){return Math.max(1,getWidth()-left()-dp(14));}private float height(){return Math.max(1,getHeight()-dp(58));}
    private float x(int round){return left()+width()*round/Math.max(1,model.totalRounds);}
    private float y(long value){return dp(20)+(float)(((double)high-value)/Math.max(1d,(double)high-low))*height();}
    private void prepare(){
        low=0;high=0;for(ScoreTrendModel.Frame f:model.frames)for(int i=0;i<model.series.size();i++)if(!state.hidden.contains(model.series.get(i).id)){low=Math.min(low,f.values[i]);high=Math.max(high,f.values[i]);}if(low==high){low--;high++;}
        paths.clear();int bucket=Math.max(1,model.frames.size()/Math.max(1,(int)(width()*2)));
        for(int s=0;s<model.series.size();s++){
            Path path=new Path();boolean started=false;
            // Keep each pixel bucket's extrema in time order so narrow spikes survive.
            for(int start=0;start<model.frames.size();start+=bucket){int end=Math.min(model.frames.size(),start+bucket),min=start,max=start;for(int j=start;j<end;j++){if(model.frames.get(j).values[s]<model.frames.get(min).values[s])min=j;if(model.frames.get(j).values[s]>model.frames.get(max).values[s])max=j;}
                int[] sample={start,Math.min(min,max),Math.max(min,max),end-1};int prior=-1;
                for(int j:sample){if(j==prior)continue;prior=j;ScoreTrendModel.Frame f=model.frames.get(j);if(f.separate)continue;float px=x(f.round),py=y(f.values[s]);if(!started){path.moveTo(px,py);started=true;}else path.lineTo(px,py);}
            }paths.add(path);
        }dirty=false;
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);if(dirty)prepare();paint.setTextSize(10*getResources().getDisplayMetrics().scaledDensity);paint.setStrokeWidth(dp(1));paint.setStyle(Paint.Style.FILL);
        for(int i=0;i<=4;i++){long value=Math.round(high-((double)high-low)*i/4);float py=y(value);paint.setColor(0xFF35343A);canvas.drawLine(left(),py,left()+width(),py,paint);paint.setColor(0xFFA5A7B0);canvas.drawText(axis(value),dp(1),py+dp(4),paint);}
        paint.setColor(0xFF666872);canvas.drawLine(left(),y(0),left()+width(),y(0),paint);
        paint.setColor(0xFFA5A7B0);canvas.drawText("0",left(),getHeight()-dp(8),paint);String last="Round "+model.totalRounds;canvas.drawText(last,getWidth()-dp(14)-paint.measureText(last),getHeight()-dp(8),paint);
        canvas.save();canvas.clipRect(left()-dp(3),dp(15),getWidth(),getHeight()-dp(30));
        for(int i=0;i<model.series.size();i++){if(state.hidden.contains(model.series.get(i).id))continue;paint.setColor(color(i));paint.setStrokeWidth(dp(2));paint.setStyle(Paint.Style.STROKE);canvas.drawPath(paths.get(i),paint);paint.setStyle(Paint.Style.FILL);ScoreTrendModel.Frame f=model.frames.get(cursor);canvas.drawCircle(x(f.round),y(f.values[i]),dp(3.5f),paint);if(model.pending){ScoreTrendModel.Frame end=model.frames.get(model.frames.size()-1);canvas.drawCircle(x(end.round),y(end.values[i]),dp(4),paint);}}
        paint.setColor(0xFFBFC2CB);paint.setStrokeWidth(dp(1));float cx=x(model.frames.get(cursor).round);canvas.drawLine(cx,dp(20),cx,dp(20)+height(),paint);canvas.restore();
    }
    private String axis(long n){double abs=Math.abs((double)n);if(abs>=1e9)return String.format(Locale.ROOT,"%.1fB",n/1e9);if(abs>=1e6)return String.format(Locale.ROOT,"%.1fM",n/1e6);if(abs>=1e3)return String.format(Locale.ROOT,"%.1fK",n/1e3);return String.valueOf(n);}
    private void inspect(float px){int round=Math.round((px-left())/width()*Math.max(1,model.totalRounds)),best=0;long distance=Long.MAX_VALUE;for(int i=0;i<model.frames.size();i++){long d=Math.abs((long)model.frames.get(i).round-round);if(d<distance){distance=d;best=i;}}select(best);}
    @Override public boolean onTouchEvent(MotionEvent e){switch(e.getActionMasked()){
        case MotionEvent.ACTION_DOWN:downX=e.getX();downY=e.getY();dragging=false;return true;
        case MotionEvent.ACTION_MOVE:if(!dragging&&Math.abs(e.getX()-downX)>dp(8)&&Math.abs(e.getX()-downX)>Math.abs(e.getY()-downY)){dragging=true;getParent().requestDisallowInterceptTouchEvent(true);}if(dragging)inspect(e.getX());return true;
        case MotionEvent.ACTION_UP:if(dragging||Math.hypot(e.getX()-downX,e.getY()-downY)<dp(14)){inspect(e.getX());performClick();}getParent().requestDisallowInterceptTouchEvent(false);return true;
        case MotionEvent.ACTION_CANCEL:getParent().requestDisallowInterceptTouchEvent(false);dragging=false;return true;
        default:return true;
    }}
    @Override public boolean performClick(){super.performClick();return true;}
    @Override public boolean onKeyDown(int code,KeyEvent event){if(code==KeyEvent.KEYCODE_DPAD_LEFT||code==KeyEvent.KEYCODE_DPAD_RIGHT){move(code==KeyEvent.KEYCODE_DPAD_LEFT?-1:1);return true;}return super.onKeyDown(code,event);}
}
