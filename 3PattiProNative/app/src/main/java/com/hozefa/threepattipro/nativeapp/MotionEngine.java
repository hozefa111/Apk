package com.hozefa.threepattipro.nativeapp;

import android.animation.*;
import android.app.Activity;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import java.util.*;

/** Activity-owned, bounded animations. Cancellation always restores final readable UI. */
final class MotionEngine {
    static final class Anchor {
        final String key;
        final Long number;
        final boolean signed;
        Anchor(String key) { this(key, null, false); }
        Anchor(String key, Number number, boolean signed) { this.key=key;this.number=number==null?null:number.longValue();this.signed=signed; }
    }
    static final class Before {
        final int x,y;
        final Long number;
        Before(int x,int y,Long number) { this.x=x;this.y=y;this.number=number; }
    }
    private final Activity activity;
    private final SharedPreferences preferences;
    private final Map<Animator,Runnable> running = new LinkedHashMap<>();
    private final List<Runnable> pending = new ArrayList<>();
    private final Map<Runnable,View> pendingOwners = new IdentityHashMap<>();
    private final Map<Animator,View> owners = new IdentityHashMap<>();
    private boolean foreground, closed;
    private int generation;
    private int deltaCount;

    MotionEngine(Activity activity) {
        this.activity=activity;
        preferences=activity.getSharedPreferences("preferences",Activity.MODE_PRIVATE);
    }
    MotionPolicy.Mode mode() {
        return MotionPolicy.resolve(preferences.getString("motion","full"), scale(Settings.Global.ANIMATOR_DURATION_SCALE),
                scale(Settings.Global.TRANSITION_ANIMATION_SCALE),scale(Settings.Global.WINDOW_ANIMATION_SCALE));
    }
    private float scale(String key) {
        try { return Settings.Global.getFloat(activity.getContentResolver(),key,1f); }
        catch (RuntimeException ignored) { return 1f; }
    }
    boolean full() { return mode()==MotionPolicy.Mode.FULL; }
    private boolean enabled() { return foreground&&!closed&&mode()!=MotionPolicy.Mode.OFF; }
    private float dp(float value) { return value*activity.getResources().getDisplayMetrics().density; }
    void resume() { if(!closed) foreground=true; }
    void pause() { foreground=false;cancelAll(); }
    void close() { closed=true;pause(); }
    void cancelAll() {
        generation++;
        for(Runnable remove:new ArrayList<>(pending)) remove.run();
        pending.clear();pendingOwners.clear();
        for(Animator animator:new ArrayList<>(running.keySet())) animator.cancel();
    }
    void cancelTree(View root) {
        // Reconciliation of a screen must not cancel a confirmed result dialog above it.
        for(Runnable remove:new ArrayList<>(pending))if(inside(pendingOwners.get(remove),root)) {
            remove.run();pending.remove(remove);pendingOwners.remove(remove);
        }
        for(Animator animator:new ArrayList<>(owners.keySet()))if(inside(owners.get(animator),root))animator.cancel();
    }
    private boolean inside(View view,View root) {
        if(view==root)return true;
        for(android.view.ViewParent parent=view==null?null:view.getParent();parent!=null;parent=parent.getParent())if(parent==root)return true;
        return false;
    }
    int activeCount() { return running.size(); }
    int pendingCount() { return pending.size(); }
    int deltaCount() { return deltaCount; }
    private boolean visible(View view) {
        return view.isAttachedToWindow()&&view.isShown()&&view.getGlobalVisibleRect(new Rect());
    }
    private void afterLayout(View view, Runnable action) {
        if(closed||mode()==MotionPolicy.Mode.OFF)return;
        final int expected=generation;
        final ViewTreeObserver original=view.getViewTreeObserver();
        final ViewTreeObserver.OnPreDrawListener[] listener={null};
        final View.OnAttachStateChangeListener[] detach={null};
        Runnable remove=()->{
            if(original.isAlive())original.removeOnPreDrawListener(listener[0]);
            if(view.getViewTreeObserver().isAlive()) view.getViewTreeObserver().removeOnPreDrawListener(listener[0]);
            if(detach[0]!=null)view.removeOnAttachStateChangeListener(detach[0]);
        };
        listener[0]=()->{
            remove.run();pending.remove(remove);pendingOwners.remove(remove);
            if(expected==generation&&enabled()&&visible(view)) action.run();
            return true;
        };
        detach[0]=new View.OnAttachStateChangeListener(){
            public void onViewAttachedToWindow(View v) {}
            public void onViewDetachedFromWindow(View v) {remove.run();pending.remove(remove);pendingOwners.remove(remove);}
        };
        pending.add(remove);pendingOwners.put(remove,view);
        view.addOnAttachStateChangeListener(detach[0]);
        view.getViewTreeObserver().addOnPreDrawListener(listener[0]);
        view.invalidate();
    }
    private void start(View view, Animator animator, Runnable finish) {
        if(!enabled()||!view.isAttachedToWindow()||running.size()>=64) { finish.run();return; }
        final boolean[] done={false};
        View.OnAttachStateChangeListener detach=new View.OnAttachStateChangeListener(){
            public void onViewAttachedToWindow(View v) {}
            public void onViewDetachedFromWindow(View v) { animator.cancel(); }
        };
        animator.addListener(new AnimatorListenerAdapter(){
            @Override public void onAnimationEnd(Animator a) {
                if(done[0])return;done[0]=true;
                running.remove(a);owners.remove(a);view.removeOnAttachStateChangeListener(detach);finish.run();
            }
        });
        animator.setInterpolator(new DecelerateInterpolator());
        view.addOnAttachStateChangeListener(detach);
        running.put(animator,finish);
        owners.put(animator,view);
        animator.start();
    }
    private void cancelFor(View view) {
        for(Animator animator:new ArrayList<>(owners.keySet()))if(owners.get(animator)==view)animator.cancel();
    }
    void enter(View view, int direction) {
        afterLayout(view,()->{
            cancelFor(view);
            boolean moving=full();
            view.setAlpha(0.1f);view.setTranslationY(moving?dp(10):0);view.setTranslationX(moving?dp(direction*9):0);
            AnimatorSet set=new AnimatorSet();
            set.playTogether(ObjectAnimator.ofFloat(view,View.ALPHA,1f),ObjectAnimator.ofFloat(view,View.TRANSLATION_Y,0),ObjectAnimator.ofFloat(view,View.TRANSLATION_X,0));
            set.setDuration(MotionPolicy.duration(mode(),220));
            start(view,set,()->{view.setAlpha(1);view.setTranslationX(0);view.setTranslationY(0);});
        });
    }
    void staged(LinearLayout parent) {
        afterLayout(parent,()->{
            int visible=0;
            for(int i=0;i<parent.getChildCount()&&visible<8;i++) {
                View child=parent.getChildAt(i);
                if(child.getVisibility()!=View.VISIBLE||child.getHeight()<dp(25)||!visible(child))continue;
                child.setAlpha(.2f);child.setTranslationY(full()?dp(8):0);
                AnimatorSet set=new AnimatorSet();set.playTogether(ObjectAnimator.ofFloat(child,View.ALPHA,1),ObjectAnimator.ofFloat(child,View.TRANSLATION_Y,0));
                set.setStartDelay(full()?visible*35L:0);set.setDuration(MotionPolicy.duration(mode(),200));
                start(child,set,()->{child.setAlpha(1);child.setTranslationY(0);});visible++;
            }
        });
    }
    void revealAwards(LinearLayout group) {
        if(closed||!full())return;
        final ViewTreeObserver original=group.getViewTreeObserver();
        final ViewTreeObserver.OnPreDrawListener[] listener={null};
        final View.OnAttachStateChangeListener[] detach={null};
        Runnable remove=()->{
            if(original.isAlive())original.removeOnPreDrawListener(listener[0]);
            if(group.getViewTreeObserver().isAlive())group.getViewTreeObserver().removeOnPreDrawListener(listener[0]);
            if(detach[0]!=null)group.removeOnAttachStateChangeListener(detach[0]);
        };
        Runnable complete=()->{remove.run();pending.remove(remove);pendingOwners.remove(remove);};
        listener[0]=()->{
            if(!enabled()||!full()){complete.run();return true;}
            Rect area=new Rect();
            if(group.isShown()&&group.getGlobalVisibleRect(area)&&area.height()>=Math.min(group.getHeight(),dp(120))){complete.run();staged(group);}
            return true;
        };
        detach[0]=new View.OnAttachStateChangeListener(){
            public void onViewAttachedToWindow(View v){}
            public void onViewDetachedFromWindow(View v){complete.run();}
        };
        pending.add(remove);pendingOwners.put(remove,group);group.addOnAttachStateChangeListener(detach[0]);group.getViewTreeObserver().addOnPreDrawListener(listener[0]);
    }
    void tap(View view) {
        if(!enabled()||!full())return;
        cancelFor(view);
        // The action runs immediately; feedback never delays or replays a click.
        ObjectAnimator scale=ObjectAnimator.ofPropertyValuesHolder(view,PropertyValuesHolder.ofFloat(View.SCALE_X,0.97f,1f),PropertyValuesHolder.ofFloat(View.SCALE_Y,0.97f,1f));
        scale.setDuration(160);start(view,scale,()->{view.setScaleX(1);view.setScaleY(1);});
    }
    void selection(View view, boolean selected, int color) {
        boolean changed=view.isSelected()!=selected;
        view.setSelected(selected);
        if(changed&&selected&&view.isAttachedToWindow()) { tap(view);highlight(view,color); }
    }
    void show(View view, boolean shown) {
        boolean reveal=shown&&view.getVisibility()!=View.VISIBLE;
        view.setVisibility(shown?View.VISIBLE:View.GONE);
        if(reveal)enter(view,0);
    }
    void text(TextView view, String value) {
        if(value.contentEquals(view.getText()))return;
        boolean had=view.length()>0;view.setText(value);
        if(had)tap(view);
    }
    void bid(TextView view,int target) {
        Long old=view.getTag() instanceof Anchor?((Anchor)view.getTag()).number:null;
        if(old!=null&&old==target)return;
        cancelFor(view);view.setTag(new Anchor("bid",target,false));
        String end="Bid  "+target+"   ▾";view.setText(end);
        if(old==null||!enabled()||!full())return;
        ValueAnimator value=ValueAnimator.ofFloat(0,1);value.setDuration(180);
        value.addUpdateListener(a->view.setText("Bid  "+MotionPolicy.interpolate(old.intValue(),target,(float)a.getAnimatedValue())+"   ▾"));
        start(view,value,()->view.setText(end));
    }
    void number(TextView view,long from,long to,boolean signed) {
        if(!full()||from==to) { view.setText(signed?ScoreboardModel.points(to):String.valueOf(to));return; }
        ValueAnimator value=ValueAnimator.ofFloat(0,1);value.setDuration(420);
        value.addUpdateListener(a->view.setText(signed?ScoreboardModel.points(Math.round(from+(to-(double)from)*(float)a.getAnimatedValue())):String.valueOf(Math.round(from+(to-(double)from)*(float)a.getAnimatedValue()))));
        start(view,value,()->view.setText(signed?ScoreboardModel.points(to):String.valueOf(to)));
    }
    Map<String,Before> capture(View root) {
        Map<String,Before> result=new HashMap<>();capture(root,result);return result;
    }
    private void capture(View view,Map<String,Before> result) {
        if(view.getTag() instanceof Anchor&&visible(view)) {
            Anchor anchor=(Anchor)view.getTag();int[] xy=new int[2];view.getLocationInWindow(xy);
            result.put(anchor.key,new Before(xy[0],xy[1],anchor.number));
        }
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)capture(((ViewGroup)view).getChildAt(i),result);
    }
    void reconcile(View root, Map<String,Before> before) { afterLayout(root,()->reconcileChildren(root,before)); }
    private void reconcileChildren(View view,Map<String,Before> before) {
        if(view.getTag() instanceof Anchor&&visible(view)) {
            Anchor anchor=(Anchor)view.getTag();Before old=before.get(anchor.key);
            if(old!=null) {
                if(anchor.number!=null&&old.number!=null&&view instanceof TextView&&anchor.number.longValue()!=old.number.longValue()) {
                    number((TextView)view,old.number,anchor.number,anchor.signed);
                    if(mode()==MotionPolicy.Mode.REDUCED){view.setAlpha(.65f);ObjectAnimator fade=ObjectAnimator.ofFloat(view,View.ALPHA,1f);fade.setDuration(120);start(view,fade,()->view.setAlpha(1));}
                    highlight(view,anchor.number>old.number?0xFF2ECD71:0xFFE74C3C);
                    delta(view,(long)anchor.number-old.number,anchor.number>old.number?0xFF2ECD71:0xFFE74C3C);
                } else if((anchor.key.startsWith("rank:")||anchor.key.startsWith("crown:"))&&full()) {
                    int[] xy=new int[2];view.getLocationInWindow(xy);
                    float dx=old.x-xy[0];float offset=old.y-xy[1];float limit=activity.getResources().getDisplayMetrics().heightPixels*.6f;
                    if((Math.abs(offset)>1||Math.abs(dx)>1)&&Math.abs(offset)<=limit) {
                        view.setTranslationY(offset);view.setTranslationX(dx);
                        AnimatorSet move=new AnimatorSet();move.playTogether(ObjectAnimator.ofFloat(view,View.TRANSLATION_Y,0),ObjectAnimator.ofFloat(view,View.TRANSLATION_X,0));move.setDuration(300);
                        start(view,move,()->{view.setTranslationY(0);view.setTranslationX(0);});
                    }
                }
            }
        }
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)reconcileChildren(((ViewGroup)view).getChildAt(i),before);
    }
    void highlight(View view,int color) {
        if(!enabled()||!full()||!visible(view))return;
        GradientDrawable glow=new GradientDrawable();glow.setColor(color);glow.setCornerRadius(dp(12));glow.setBounds(0,0,view.getWidth(),view.getHeight());glow.setAlpha(42);
        view.getOverlay().add(glow);
        ValueAnimator fade=ValueAnimator.ofInt(42,0);fade.setDuration(480);fade.addUpdateListener(a->glow.setAlpha((int)a.getAnimatedValue()));
        start(view,fade,()->view.getOverlay().remove(glow));
    }
    void leader(View view) { afterLayout(view,()->{tap(view);highlight(view,0xFFF6D365);}); }
    private void delta(View anchor,long difference,int color) {
        if(!enabled()||!full()||difference==0||!(anchor.getParent() instanceof ViewGroup))return;
        ViewGroup parent=(ViewGroup)anchor.getParent();
        for(android.view.ViewParent p=parent;p instanceof ViewGroup;p=p.getParent()) {
            ViewGroup candidate=(ViewGroup)p;
            if(candidate.getTag() instanceof Anchor&&((Anchor)candidate.getTag()).key.startsWith("rank:")){parent=candidate;break;}
        }
        final ViewGroup overlay=parent;
        TextView text=new TextView(activity);text.setText((difference>0?"+":"")+difference);text.setTextSize(13);text.setTextColor(color);text.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        text.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);text.setClickable(false);
        text.measure(View.MeasureSpec.makeMeasureSpec(parent.getWidth(),View.MeasureSpec.AT_MOST),View.MeasureSpec.makeMeasureSpec((int)dp(26),View.MeasureSpec.AT_MOST));
        int[] childXY=new int[2],parentXY=new int[2];anchor.getLocationInWindow(childXY);parent.getLocationInWindow(parentXY);
        int scoreTop=childXY[1]-parentXY[1],scoreBottom=scoreTop+anchor.getHeight();
        int x=Math.max(0,childXY[0]-parentXY[0]+anchor.getWidth()-text.getMeasuredWidth());
        int y=MotionPolicy.deltaTop(scoreTop,scoreBottom,parent.getHeight(),text.getMeasuredHeight(),(int)dp(4));
        if(y<0)return;
        text.layout(x,y,x+text.getMeasuredWidth(),y+text.getMeasuredHeight());parent.getOverlay().add(text);deltaCount++;
        float travel=y<scoreTop?-Math.min(dp(12),y-dp(2)):Math.min(dp(12),parent.getHeight()-y-text.getMeasuredHeight()-dp(2));
        AnimatorSet set=new AnimatorSet();set.playTogether(ObjectAnimator.ofFloat(text,View.TRANSLATION_Y,0,travel),ObjectAnimator.ofFloat(text,View.ALPHA,1,0));set.setDuration(650);
        start(parent,set,()->overlay.getOverlay().remove(text));
    }
    void progress(ProgressBar bar,int target,boolean reveal) {
        bar.setProgress(target);
        if(!reveal)return;
        afterLayout(bar,()->{
            if(!full())return;
            ValueAnimator value=ValueAnimator.ofInt(0,target);value.setDuration(450);
            value.addUpdateListener(a->bar.setProgress((int)a.getAnimatedValue()));start(bar,value,()->bar.setProgress(target));
        });
    }
    void draw(View view,boolean reveal,java.util.function.Consumer<Float> frame) {
        frame.accept(1f);
        if(reveal)afterLayout(view,()->{
            if(!full())return;
            ValueAnimator value=ValueAnimator.ofFloat(0,1);value.setDuration(500);
            value.addUpdateListener(a->frame.accept((float)a.getAnimatedValue()));start(view,value,()->frame.accept(1f));
        });
    }
    void pulse(View view) {
        afterLayout(view,()->{
            if(!full())return;
            ObjectAnimator pulse=ObjectAnimator.ofFloat(view,View.ALPHA,1f,.55f,1f);pulse.setDuration(900);pulse.setRepeatCount(1);
            start(view,pulse,()->view.setAlpha(1));
        });
    }
    void loading(View view) {
        afterLayout(view,()->{
            if(!full())return;
            ObjectAnimator shimmer=ObjectAnimator.ofFloat(view,View.ALPHA,.35f,.8f,.35f);shimmer.setDuration(700);shimmer.setRepeatCount(2);
            start(view,shimmer,()->view.setAlpha(1));
        });
    }
    void refresh(View view) {
        if(!enabled()||!full())return;
        ObjectAnimator turn=ObjectAnimator.ofFloat(view,View.ROTATION,0,360);turn.setDuration(500);
        start(view,turn,()->view.setRotation(0));
    }
    void error(View view) {
        if(!enabled())return;
        highlight(view,0xFFE74C3C);
        if(full()) {
            ObjectAnimator shake=ObjectAnimator.ofFloat(view,View.TRANSLATION_X,0,-dp(4),dp(4),-dp(2),0);shake.setDuration(220);
            start(view,shake,()->view.setTranslationX(0));
        }
    }
    void theme(View view,int from,int to) {
        if(!enabled()||!full())return;
        afterLayout(view,()->{
            ValueAnimator color=ValueAnimator.ofObject(new ArgbEvaluator(),from,to);color.setDuration(260);
            color.addUpdateListener(a->view.setBackgroundColor((int)a.getAnimatedValue()));start(view,color,()->view.setBackgroundColor(to));
        });
    }
    void dialog(Dialog dialog) {
        if(dialog.getWindow()==null)return;
        MotionPolicy.Mode mode=mode();
        dialog.getWindow().setWindowAnimations(mode==MotionPolicy.Mode.FULL?R.style.MotionDialogFull:mode==MotionPolicy.Mode.REDUCED?R.style.MotionDialogReduced:0);
    }
    void confetti(ViewGroup parent) {
        afterLayout(parent,()->{
            if(!full())return;
            Confetti view=new Confetti(activity);view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);view.setClickable(false);
            parent.getOverlay().add(view);view.layout(0,0,parent.getWidth(),parent.getHeight());
            ValueAnimator fall=ValueAnimator.ofFloat(0,1);fall.setDuration(1400);
            fall.addUpdateListener(a->{view.fraction=(float)a.getAnimatedValue();view.invalidate();});
            start(parent,fall,()->parent.getOverlay().remove(view));
        });
    }
    private static final class Confetti extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);float fraction;
        final float[] x=new float[42],speed=new float[42],phase=new float[42];
        final int[] colors={0xFFF6D365,0xFF2ECD71,0xFF4FACFE,0xFFFDA085};
        Confetti(Activity activity) {super(activity);Random random=new Random(73);for(int i=0;i<x.length;i++){x[i]=random.nextFloat();speed[i]=.6f+random.nextFloat()*.5f;phase[i]=random.nextFloat()*6.28f;}}
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);float density=getResources().getDisplayMetrics().density;
            for(int i=0;i<x.length;i++) {
                paint.setColor(colors[i%colors.length]);paint.setAlpha((int)(255*(1-fraction)));
                float px=x[i]*getWidth()+(float)Math.sin(phase[i]+fraction*5)*18*density;
                float py=(-.1f+fraction*speed[i])*getHeight();
                canvas.save();canvas.rotate(fraction*240+i*13,px,py);canvas.drawRect(px,py,px+4*density,py+7*density,paint);canvas.restore();
            }
        }
    }
}
