package com.hozefa.threepattipro.nativeapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.text.InputType;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class MainActivity extends Activity implements FirebaseGateway.Listener {
    private int BG = 0xFF0A0A0C, PANEL = 0xFF18181C, PANEL2 = 0xFF222228;
    private static final int BORDER = 0xFF35343A, WHITE = 0xFFFFFFFF, MUTED = 0xFF8A8D93;
    private int GOLD = 0xFFF6D365, GREEN = 0xFF2ECD71, RED = 0xFFE74C3C;
    private static final int BLUE = 0xFF4FACFE;
    private FirebaseGateway api;
    private MotionEngine motion;
    private final MotionEvents motionEvents=new MotionEvents();
    private boolean resumed, launchReveal, screenEntry;
    private int displayedTab=-1;
    private Map<String,MotionEngine.Before> scoreMotion;
    private String previousLeader="";
    private long sessionStartRevealUntil;
    private Map<String,Object> deferredRoundResult,deferredSessionSummary;

    private AlertDialog authDialog;
    private android.content.SharedPreferences settings;
    private String rosterFilter="all",searchQuery="";
    private Runnable refreshSearch;
    private String pendingRoundTimestamp=null,pendingRoundJson=null;
    private final android.os.Handler clock=new android.os.Handler(android.os.Looper.getMainLooper());
    private TextView sessionTimer;
    private final Runnable tick=new Runnable(){public void run(){
        Map<String,Object> session=activeSession();
        if(sessionTimer!=null&&session!=null)sessionTimer.setText("Duration  "+duration(System.currentTimeMillis()-time(session.get("startTime"))));
        clock.postDelayed(this,1000);
    }};
    private String duration(long ms){long n=Math.max(0,ms/1000);return String.format(Locale.ROOT,"%02d:%02d:%02d",n/3600,n/60%60,n%60);}

    private final List<Map<String,Object>> players = new ArrayList<>();
    private final List<Map<String,Object>> rounds = new ArrayList<>();
    private final List<Map<String,Object>> sessions = new ArrayList<>();
    private LinearLayout root, content, navigation, toolbar;
    private ScrollView scroll;
    private boolean loggedIn=false, busy=false, hasData=false, liveConnected=false;
    private String syncProblem;
    private long syncRetryAt;
    private boolean quotaPaused;
    private int tab=0, historyTab=0;
    private String profileId=null, rankFilter="all";
    private long customStart=0L, customEnd=0L;
    private int profileRoundVisible=20;
    private List<Map<String,Object>> currentRankData=null;
    private int visibleRoundCount=25, visibleSessionCount=20, visibleRankCount=35;
    private List<Map<String,Object>> cachedGames = Collections.emptyList();
    private final Map<String,Integer> cachedScores=new HashMap<>(),cachedGameScores=new HashMap<>(),cachedPlays=new HashMap<>(),cachedWins=new HashMap<>();
    private Set<String> playing=null;
    private final LinkedHashSet<String> partners=new LinkedHashSet<>();
    private String hukum=null, outcome=null;
    private int bid=155;
    private String pendingRoundId=null,pendingRoundKey=null;

    // Round form views live across taps; data changes update these controls in place.
    // Rebuilding the entire Activity on every bid/partner tap caused UI jank.
    private final Map<String,TextView> roundPlayingButtons=new LinkedHashMap<>();
    private final Map<String,TextView> roundHukumButtons=new LinkedHashMap<>();
    private final Map<String,TextView> roundPartnerButtons=new LinkedHashMap<>();
    private LinearLayout roundHukumSection,roundPartnerSection,roundResultSection,roundPreviewSection;
    private TextView roundBidView,roundWinView,roundLoseView,roundSaveView,roundHintView;
    private boolean roundSaving=false;
    private final DateTimeFormatter dateFormat=DateTimeFormatter.ofPattern("dd MMM, hh:mm a").withZone(ZoneId.systemDefault());

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        settings=getSharedPreferences("preferences",MODE_PRIVATE);
        motion=new MotionEngine(this);launchReveal=state==null;
        applyAppearance();
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        restoreDraft();
        if(state!=null){tab=state.getInt("tab",0);historyTab=state.getInt("historyTab",0);rankFilter=state.getString("rankFilter","all");profileId=state.getString("profileId");customStart=state.getLong("customStart");customEnd=state.getLong("customEnd");searchQuery=state.getString("searchQuery","");}
        buildShell();
        clock.post(tick);
        api=new FirebaseGateway(this,this,getString(R.string.firebase_web_api_key));
        api.start();
    }
    @Override protected void onDestroy() {
        persistDraft();
        if(authDialog!=null){authDialog.dismiss();authDialog=null;}
        if(motion!=null)motion.close();
        clock.removeCallbacksAndMessages(null);
        if (api != null) api.stop();
        super.onDestroy();
    }
    @Override public void dataUpdated(List<Map<String,Object>> p,List<Map<String,Object>> r,List<Map<String,Object>> s) {
        p.sort(GameOrder.PLAYERS);
        r.sort(GameOrder.ROUNDS);
        s.sort(GameOrder.SESSIONS);
        Map<String,Long> timestamps=new HashMap<>();
        for(Map<String,Object> round:r)if(!"settlement".equals(str(round,"type")))timestamps.put(id(round),time(round.get("timestamp")));
        boolean freshRound=motionEvents.update(timestamps,liveConnected&&resumed,System.currentTimeMillis());
        // A no-op coherent refresh still seeds motion after resume/reconnect.
        if(hasData && players.equals(p) && rounds.equals(r) && sessions.equals(s)) return;
        motion.cancelTree(content);
        scoreMotion=freshRound?motion.capture(content):null;
        List<Map<String,Object>> oldRanks=rankedFor(filteredRounds());
        previousLeader=oldRanks.isEmpty()?"":id(oldRanks.get(0));
        boolean firstData=!hasData;
        boolean rosterChanged=!players.equals(p);
        players.clear();players.addAll(p);
        rounds.clear();rounds.addAll(r);
        sessions.clear();sessions.addAll(s);
        cachedGames=new ArrayList<>();
        cachedScores.clear();cachedGameScores.clear();cachedPlays.clear();cachedWins.clear();
        for(Map<String,Object> round:rounds) {
            if(!"settlement".equals(str(round,"type"))) cachedGames.add(round);
            Map<String,Object> scores=map(round.get("scoreChanges"));
            for(Map.Entry<String,Object> entry:scores.entrySet()) {
                cachedScores.merge(entry.getKey(),num(entry.getValue()),Integer::sum);
            }
            if("settlement".equals(str(round,"type"))) continue;
            for(Map.Entry<String,Object> entry:scores.entrySet())cachedGameScores.merge(entry.getKey(),num(entry.getValue()),Integer::sum);
            boolean hukumWin="win".equals(str(round,"result"));
            String leader=str(round,"hukumId");
            List<String> partners=strings(round.get("partnerIds"));
            for(String pid:strings(round.get("playerIds"))) {
                cachedPlays.merge(pid,1,Integer::sum);
                if(hukumWin==(pid.equals(leader)||partners.contains(pid))) {
                    cachedWins.merge(pid,1,Integer::sum);
                }
            }
        }
        hasData=true;
        if(playing!=null && pendingRoundId==null) playing.retainAll(activeIds());
        // New rounds and sessions can arrive while the form is open. Update only
        // its score preview unless the player roster itself changed.
        if(tab==6 && refreshSearch!=null)refreshSearch.run();
        else if(tab==2 && !rosterChanged && roundBidView!=null && roundPreviewSection!=null)
            updateRoundForm();
        else render(false);
        scoreMotion=null;
        if(firstData)motion.enter(content,0);
    }
    @Override public void authChanged(boolean value) {
        if(loggedIn==value) return;
        loggedIn=value;
        if(value&&authDialog!=null){authDialog.dismiss();authDialog=null;}
        updateToolbar();
        if(!loggedIn && tab==2) tab=0;
        render(false);
    }
    @Override public void snapshotContinuity(boolean continuous){if(!continuous)motionEvents.invalidate();}
    @Override public void syncState(boolean online) {
        if(liveConnected==online)return;
        liveConnected=online;

        updateToolbar();
        if(tab==2&&roundSaveView!=null&&roundHintView!=null&&roundPreviewSection!=null)updateRoundForm();
    }
    @Override public void syncProblem(String message,long retryAt,boolean quota){
        boolean changed=!Objects.equals(syncProblem,message)||syncRetryAt!=retryAt||quotaPaused!=quota;
        syncProblem=message;syncRetryAt=retryAt;quotaPaused=quota;
        if(changed){updateToolbar();render(false);}
    }
    @Override public void notice(String msg) {
        if("Session started".equals(msg))sessionStartRevealUntil=resumed?android.os.SystemClock.uptimeMillis()+10000:0;
        Toast.makeText(this,msg,Toast.LENGTH_LONG).show();
        if(motion!=null&&toolbar!=null){String lower=msg.toLowerCase(Locale.ROOT);if(lower.contains("failed")||lower.contains("error")||lower.contains("cannot")||lower.contains("not saved"))motion.error(toolbar);}
        // Toast messages must not reset the current view or move the page.
    }
    @Override public void busy(boolean b) {
        busy=b;
        updateToolbar();
        if(!b&&tab==9&&api!=null&&shownPendingBulk!=api.hasPendingBulkOperation())render(false);
        if(tab==2&&roundSaveView!=null&&roundPreviewSection!=null&&roundHintView!=null)
            updateRoundForm();
    }
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);persistDraft();state.putInt("tab",tab);state.putInt("historyTab",historyTab);state.putString("rankFilter",rankFilter);state.putString("profileId",profileId);state.putLong("customStart",customStart);state.putLong("customEnd",customEnd);state.putString("searchQuery",searchQuery);}
    @Override protected void onResume(){
        super.onResume();resumed=true;motion.resume();motionEvents.invalidate();if(api!=null)api.setForeground(true);
        if(launchReveal){launchReveal=false;motion.enter(root,0);}
        if(deferredRoundResult!=null){Map<String,Object> result=deferredRoundResult;deferredRoundResult=null;showRoundSaved(result,false);}
        if(deferredSessionSummary!=null){Map<String,Object> result=deferredSessionSummary;deferredSessionSummary=null;showSessionSummary(result,false);}
    }
    @Override protected void onPause(){if(api!=null)api.setForeground(false);resumed=false;sessionStartRevealUntil=0;motionEvents.invalidate();if(motion!=null)motion.pause();persistDraft();super.onPause();}
    MotionEngine motionEngine(){return motion;}

    private boolean persistDraft(){
        if(settings==null)return false;
        try{
            org.json.JSONObject draft=new org.json.JSONObject();
            if(playing!=null)draft.put("playing",new org.json.JSONArray(playing));
            draft.put("partners",new org.json.JSONArray(partners));draft.put("hukum",hukum);
            draft.put("outcome",outcome);draft.put("bid",bid);
            draft.put("pendingId",pendingRoundId);draft.put("pendingKey",pendingRoundKey);draft.put("pendingTimestamp",pendingRoundTimestamp);draft.put("pendingPayload",pendingRoundJson);
            return settings.edit().putString("draft",draft.toString()).commit();
        }catch(Exception ignored){return false;}
    }
    private void restoreDraft(){
        try{
            org.json.JSONObject draft=new org.json.JSONObject(settings.getString("draft","{}"));
            if(draft.has("playing")){playing=new LinkedHashSet<>();org.json.JSONArray a=draft.getJSONArray("playing");for(int i=0;i<a.length();i++)playing.add(a.getString(i));}
            org.json.JSONArray a=draft.optJSONArray("partners");if(a!=null)for(int i=0;i<a.length();i++)partners.add(a.getString(i));
            hukum=draft.optString("hukum",null);outcome=draft.optString("outcome",null);bid=draft.optInt("bid",155);
            pendingRoundId=draft.optString("pendingId",null);pendingRoundKey=draft.optString("pendingKey",null);pendingRoundTimestamp=draft.optString("pendingTimestamp",null);pendingRoundJson=draft.optString("pendingPayload",null);
        }catch(Exception ignored){settings.edit().remove("draft").apply();}
    }
    private int dp(int n) {return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int fill,int radius,int stroke) {
        GradientDrawable d=new GradientDrawable();
        d.setColor(fill);d.setCornerRadius(dp(radius));
        if(stroke!=0) d.setStroke(dp(1),stroke);
        return d;
    }
    private GradientDrawable gradient(int start,int end,int radius) {
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{start,end});
        d.setCornerRadius(dp(radius));return d;
    }
    private String str(Map<String,Object> map,String k) {Object v=map.get(k);return v==null?"":String.valueOf(v);}
    private String id(Map<String,Object> m) {return str(m,"id");}
    private int num(Object v) {
        if(v instanceof Number)return ((Number)v).intValue();
        try{return Integer.parseInt(String.valueOf(v));}catch(Exception e){return 0;}
    }
    private double val(Object v) {
        if(v instanceof Number)return ((Number)v).doubleValue();
        try{return Double.parseDouble(String.valueOf(v));}catch(Exception e){return 0;}
    }
    private long time(Object v) {
        if(v==null)return 0;
        try{return Instant.parse(String.valueOf(v)).toEpochMilli();}
        catch(Exception e){return 0;}
    }
    private String when(Object v) {long t=time(v);return t==0?"—":dateFormat.format(Instant.ofEpochMilli(t));}
    @SuppressWarnings("unchecked") private Map<String,Object> map(Object data) {
        return data instanceof Map?(Map<String,Object>)data:Collections.emptyMap();
    }
    private List<String> strings(Object obj) {
        ArrayList<String> out=new ArrayList<>();
        if(obj instanceof List) for(Object o:(List<?>)obj)if(o!=null)out.add(String.valueOf(o));
        return out;
    }
    private String nameOf(String pid) {
        for(Map<String,Object> p:players)if(id(p).equals(pid))return str(p,"name");
        return "Unknown player";
    }
    private List<Map<String,Object>> activePlayers() {
        List<Map<String,Object>> out=new ArrayList<>();
        for(Map<String,Object> p:players)if(!Boolean.FALSE.equals(p.get("active")))out.add(p);
        return out;
    }
    private Set<String> activeIds() {
        Set<String> set=new LinkedHashSet<>();
        for(Map<String,Object> p:activePlayers())set.add(id(p));
        return set;
    }
    private List<Map<String,Object>> gameRounds() { return cachedGames; }
    private Map<String,Object> activeSession() {
        for(Map<String,Object> s:sessions)if(s.get("endTime")==null)return s;
        return null;
    }
    private int scoreOf(String pid,List<Map<String,Object>> source) {
        if(source==rounds) return cachedScores.getOrDefault(pid,0);
        if(source==cachedGames) return cachedGameScores.getOrDefault(pid,0);
        int score=0;
        for(Map<String,Object> r:source)score+=num(map(r.get("scoreChanges")).get(pid));
        return score;
    }
    private int played(String pid,List<Map<String,Object>> source) {
        if(source==cachedGames) return cachedPlays.getOrDefault(pid,0);
        int n=0; for(Map<String,Object> r:source)if(strings(r.get("playerIds")).contains(pid))n++; return n;
    }
    private int wins(String pid,List<Map<String,Object>> source) {
        if(source==cachedGames) return cachedWins.getOrDefault(pid,0);
        int n=0;
        for(Map<String,Object> r:source) {
            if(!strings(r.get("playerIds")).contains(pid))continue;
            if(RoundAnalytics.won(r,pid))n++;
        }
        return n;
    }
    private int winningStreak(String pid,List<Map<String,Object>> source){
        return RoundAnalytics.winningStreak(pid,source);
    }
    private boolean idIsHukum(Map<String,Object> r,String pid) {return str(r,"hukumId").equals(pid);}
    private TextView text(String label,int size,int color,boolean bold) {
        TextView t=new TextView(this);
        t.setText(label);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        if(bold)t.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
        t.setIncludeFontPadding(false);
        return t;
    }
    private LinearLayout column() {LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);return c;}
    private LinearLayout row() {LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);return r;}
    private LinearLayout.LayoutParams lp(int w,int h) {return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
    private void pad(View v,int l,int top,int r,int bottom){v.setPadding(dp(l),dp(top),dp(r),dp(bottom));}
    private void gap(LinearLayout c,int height) {View v=new View(this);c.addView(v,lp(1,height));}
    private LinearLayout panel() {
        LinearLayout c=column();
        c.setBackground(shape(PANEL,20,BORDER));
        pad(c,17,17,17,17);
        return c;
    }
    private TextView button(String label,boolean primary,Runnable action) {
        TextView b=text(label,14,primary?BG:WHITE,true);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(48));
        pad(b,18,12,18,12);
        b.setBackground(primary?gradient(GOLD,settings.getString("appearance","gold").equals("gold")?0xFFFDA085:GOLD,14):shape(PANEL2,14,BORDER));
        b.setForeground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x40FFFFFF),null,null));
        b.setClickable(true);
        b.setFocusable(true);
        b.setOnClickListener(v->{if(settings.getBoolean("haptics",false))v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);motion.tap(v);if(action!=null) action.run();});
        return b;
    }
    private TextView chip(String name,boolean selected,boolean gold,Runnable action) {
        int fill=selected?(gold?0xFF60492E:0xFF1A4B43):PANEL2;
        int fg=selected?(gold?GOLD:GREEN):WHITE;
        TextView t=text(name,13,fg,selected);
        t.setGravity(Gravity.CENTER);pad(t,15,10,15,10);
        t.setBackground(shape(fill,12,selected?(gold?GOLD:GREEN):BORDER));
        t.setForeground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x35FFFFFF),null,null));
        t.setClickable(true);
        t.setFocusable(true);
        t.setMinHeight(dp(48));
        t.setOnClickListener(v->{if(settings.getBoolean("haptics",false))v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);motion.tap(v);if(action!=null)action.run();});
        return t;
    }
    private void title(LinearLayout c,String head,String sub) {
        c.addView(text(head,23,WHITE,true));
        if(sub!=null&&!sub.isEmpty()){gap(c,6);c.addView(text(sub,12,MUTED,false));}
        gap(c,18);
    }
    private void label(LinearLayout c,String heading) {
        c.addView(text(heading.toUpperCase(Locale.ROOT),11,GOLD,true));gap(c,12);
    }
    private TextView line(LinearLayout c,String left,String right,int rightColor) {
        LinearLayout r=row();
        TextView l=text(left,13,MUTED,false);
        if(getResources().getConfiguration().fontScale>1.3f){
            r.setOrientation(LinearLayout.VERTICAL);r.setGravity(Gravity.START);
            r.addView(l,lp(-1,-2));gap(r,4);TextView value=text(right,14,rightColor,true);r.addView(value,lp(-1,-2));gap(r,12);c.addView(r);return value;
        }
        l.setMinHeight(dp(30));r.addView(l,new LinearLayout.LayoutParams(0,-2,1));
        TextView value=text(right,14,rightColor,true);value.setGravity(Gravity.END);
        r.addView(value,new LinearLayout.LayoutParams(0,-2,1));
        c.addView(r);return value;
    }
    private void buildShell() {
        motion.cancelAll();
        root=column();root.setBackgroundColor(BG);
        toolbar=row();pad(toolbar,18,15,18,15);toolbar.setBackgroundColor(BG);
        toolbar.setMinimumHeight(dp(66));root.addView(toolbar,lp(-1,-2));updateToolbar();
        View bar=new View(this);bar.setBackgroundColor(BORDER);root.addView(bar,lp(-1,1));
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
        scroll.setSmoothScrollingEnabled(false);
        content=column();pad(content,18,20,18,35);scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        navigation=row();pad(navigation,7,7,7,7);
        navigation.setMinimumHeight(dp(64));
        navigation.setBackgroundColor(0xFF121215);
        root.addView(navigation,lp(-1,-2));
        setContentView(root);
        render(true);
    }
    private void updateToolbar() {
        if(toolbar==null)return;
        toolbar.removeAllViews();
        boolean largeText=getResources().getConfiguration().fontScale>1.3f;
        toolbar.setOrientation(largeText?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);
        toolbar.setGravity(largeText?Gravity.START:Gravity.CENTER_VERTICAL);
        LinearLayout heading=column();
        TextView appName=text("♠  3 PATTI PRO",21,GOLD,true);
        if(largeText){appName.setMaxLines(1);appName.setAutoSizeTextTypeUniformWithConfiguration(16,21,1,android.util.TypedValue.COMPLEX_UNIT_SP);}
        heading.addView(appName);
        gap(heading,5);
        heading.addView(text(liveConnected?"●  LIVE SYNC":quotaPaused?"○  SYNC PAUSED · LIMIT REACHED":hasData?"○  OFFLINE · SAVED TABLE":"○  OFFLINE / CONNECTING",9,
                liveConnected?GREEN:MUTED,true));
        toolbar.addView(heading,largeText?lp(-1,-2):new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout actions=largeText?row():toolbar;
        if(largeText){gap(toolbar,8);toolbar.addView(actions,lp(-1,-2));}
        TextView status=text(busy?"SAVING…":loggedIn?"ADMIN":"LOGIN",11,loggedIn?GREEN:GOLD,true);
        status.setGravity(Gravity.CENTER);pad(status,12,10,12,10);
        status.setBackground(shape(PANEL2,12,BORDER));
        status.setOnClickListener(v->{if(busy)return;if(loggedIn)confirmLogout();else loginDialog();});
        actions.addView(status);
        if(busy&&motion.full()){android.widget.ProgressBar saving=new android.widget.ProgressBar(this);saving.setContentDescription("Saving progress");actions.addView(saving,lp(22,22));}
        if(largeText)actions.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        TextView refresh=text("↻",23,GOLD,true);
        refresh.setGravity(Gravity.CENTER);
        refresh.setBackground(shape(PANEL2,12,BORDER));
        refresh.setMinWidth(dp(48));refresh.setMinHeight(dp(48));
        refresh.setOnClickListener(v->{
            if(api!=null) {
                if(api.refresh()){motion.refresh(refresh);Toast.makeText(this,"Refreshing scores...",Toast.LENGTH_SHORT).show();}
                else if(syncProblem!=null)new MotionDialogBuilder(this,motion).setTitle(quotaPaused?"Sync paused":"Connection unavailable").setMessage(syncProblem+retryDescription()).setPositiveButton("Close",null).show();
            }
        });
        LinearLayout.LayoutParams rp=lp(48,48);rp.leftMargin=dp(8);
        actions.addView(refresh,rp);
        TextView more=text("⋮",24,GOLD,true);more.setGravity(Gravity.CENTER);more.setContentDescription("More options");
        more.setMinWidth(dp(40));more.setMinHeight(dp(48));
        more.setOnClickListener(v->new MotionDialogBuilder(this,motion).setTitle("Your table")
            .setItems(new String[]{"Search","Analytics","Share Studio","Settings"},(d,which)->{tab=6+which;render(true);})
            .setNegativeButton("Close",null).show());actions.addView(more);

    }
    private String retryDescription(){
        return syncRetryAt>0?"\nNext retry: "+DateTimeFormatter.ofPattern("dd MMM, hh:mm a").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(syncRetryAt))+". Refresh does not bypass this wait.":"";
    }
    private void render(boolean reset) {
        if(content==null||navigation==null)return;
        int oldTab=displayedTab;screenEntry=reset||oldTab!=tab;displayedTab=tab;
        motion.cancelTree(content);
        sessionTimer=null;refreshSearch=null;
        if(settings.getBoolean("keep_awake",false)&&activeSession()!=null)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        int prev=reset?0:scroll.getScrollY();
        content.removeAllViews();
        navigation.removeAllViews();
        if(syncProblem!=null){
            LinearLayout banner=panel();
            banner.addView(text(quotaPaused?"Sync paused · Firebase limit":"Connection unavailable",16,GOLD,true));gap(banner,10);
            banner.addView(text(syncProblem+retryDescription(),13,MUTED,false));
            if(hasData){gap(banner,8);banner.addView(text("Showing the last saved table. Changes are disabled until live sync returns.",13,WHITE,false));}
            content.addView(banner);gap(content,14);
        }
        if(!hasData && tab<6 && syncProblem!=null){
            gap(content,24);content.addView(text("Your table has not loaded yet",20,GOLD,true));gap(content,12);
            content.addView(text("This connection error does not delete your points. A fresh install needs one successful connection before an offline table is available. You can leave this screen; the app will retry while open.",14,MUTED,false));
        }else if(!hasData && tab<6) {
            gap(content,80);content.addView(text("♠  Loading your table…",20,GOLD,true));
            gap(content,16);content.addView(text("Connecting to your shared table",13,MUTED,false));
            for(int i=0;i<3;i++){gap(content,16);View skeleton=new View(this);skeleton.setBackground(shape(PANEL2,16,0));content.addView(skeleton,lp(-1,64));motion.loading(skeleton);}
        } else {
            switch(tab) {
                case 0:home();break;
                case 1:playersPage();break;
                case 2:roundPage();break;
                case 3:ranksPage();break;
                case 4:historyPage();break;
                case 5:profilePage();break;
                case 6:searchPage();break;
                case 7:analyticsPage();break;
                case 8:shareStudioPage();break;
                case 9:settingsPage();break;
                default:home();
            }
        }
        String[] navNames={"Home","Players","Round","Ranks","History"};
        String[] symbols={"⌂","♣","＋","★","◷"};
        for(int i=0;i<navNames.length;i++){
            final int destination=i;
            LinearLayout item=column();item.setGravity(Gravity.CENTER);
            TextView sym=text(symbols[i],24,tab==i?GOLD:MUTED,true);sym.setGravity(Gravity.CENTER);
            TextView text=text(navNames[i],10,tab==i?GOLD:MUTED,tab==i);text.setGravity(Gravity.CENTER);
            text.setMaxLines(1);text.setAutoSizeTextTypeUniformWithConfiguration(8,10,1,android.util.TypedValue.COMPLEX_UNIT_SP);
            item.addView(sym);gap(item,2);item.addView(text);
            if(tab==i)item.setBackground(shape(PANEL2,12,0));
            item.setMinimumHeight(dp(52));item.setSelected(tab==i);item.setContentDescription(navNames[i]);
            navigation.addView(item,new LinearLayout.LayoutParams(0,-2,1));
            item.setOnClickListener(v->{
                if(destination==2&&!loggedIn){loginDialog();return;}
                motion.tap(item);profileId=null;
                tab=destination;render(true);
            });
        }
        scroll.post(()->scroll.scrollTo(0,prev));
        if(screenEntry){motion.enter(content,Integer.compare(tab,oldTab));motion.staged(content);}
        else if(scoreMotion!=null)motion.reconcile(content,scoreMotion);
    }
    private void home() {
        title(content,"Table Status","3 Patti PRO · Live Game Dashboard");
        List<Map<String,Object>> games=gameRounds();
        List<Map<String,Object>> active=activePlayers();
        String winner="—";
        int maxBalance=0,money=0;
        for(Map<String,Object> p:active){
            int balance=scoreOf(id(p),rounds);
            if(balance>0)money+=balance;
            if(balance>maxBalance){maxBalance=balance;winner=str(p,"name")+" (+"+balance+")";}
        }
        LinearLayout hero=column();
        hero.setBackground(gradient(0xFF28221C,0xFF151519,20));
        pad(hero,16,18,16,18);
        hero.addView(text("♛  3 PATTI PRO",12,GOLD,true));gap(hero,15);
        LinearLayout top=row();
        LinearLayout p1=statTile("PLAYERS",String.valueOf(active.size()),GOLD);
        LinearLayout p2=statTile("ROUNDS",String.valueOf(games.size()),WHITE);
        LinearLayout.LayoutParams h1=new LinearLayout.LayoutParams(0,-2,1);
        h1.rightMargin=dp(9);top.addView(p1,h1);
        top.addView(p2,new LinearLayout.LayoutParams(0,-2,1));
        hero.addView(top);gap(hero,10);
        LinearLayout bottom=row();
        LinearLayout p3=statTile("TOP WINNER",winner,GREEN);
        LinearLayout p4=statTile("ON TABLE",String.valueOf(money),GOLD);
        LinearLayout.LayoutParams h3=new LinearLayout.LayoutParams(0,-2,1);
        h3.rightMargin=dp(9);bottom.addView(p3,h3);
        bottom.addView(p4,new LinearLayout.LayoutParams(0,-2,1));
        hero.addView(bottom);
        content.addView(hero);
        gap(content,15);

        Map<String,Object> session=activeSession();
        if(loggedIn){
            LinearLayout sessionBar=panel();
            String status=session==null?"○  No active session":
                    "●  SESSION ACTIVE · "+str(session,"sessionName");
            TextView sessionStatus=text(status,13,session==null?MUTED:GREEN,true);
            sessionBar.addView(sessionStatus);
            boolean revealStart=session!=null&&android.os.SystemClock.uptimeMillis()<sessionStartRevealUntil;
            if(session!=null&&(screenEntry||revealStart))motion.pulse(sessionStatus);
            if(revealStart){motion.enter(sessionBar,0);sessionStartRevealUntil=0;}
            if(session!=null) {
                gap(sessionBar,7);
                sessionBar.addView(text("Started "+when(session.get("startTime")),12,MUTED,false));
                sessionTimer=text("Duration  "+duration(System.currentTimeMillis()-time(session.get("startTime"))),13,GOLD,true);
                gap(sessionBar,8);sessionBar.addView(sessionTimer);
            }
            gap(sessionBar,12);
            sessionBar.addView(button(session==null?"▶  Start Session":"■  End Session",
                    session==null,session==null?this::startSessionDialog:this::endSessionDialog));
            // Clear-all is intentionally not exposed in this build: user forbids data deletion.
            content.addView(sessionBar);
            gap(content,16);
        }else {
            LinearLayout viewOnly=panel();
            viewOnly.addView(text("Viewing scores · Log in to add rounds and manage sessions",12,MUTED,false));
            content.addView(viewOnly);gap(content,16);
        }
        label(content,"Quick Actions");
        quickActions("＋  New Round",()->{if(loggedIn){tab=2;render(true);}else loginDialog();},
                "♣  Players",()->{tab=1;render(true);});
        quickActions("★  Rankings",()->{tab=3;render(true);},
                "◷  History",()->{tab=4;render(true);});
        quickActions("⌕  Search",()->{tab=6;render(true);},"▥  Analytics",()->{tab=7;render(true);});
        gap(content,13);
        content.addView(text("RECENT ACTIVITY",15,WHITE,true));gap(content,12);
        if(games.isEmpty())empty("No rounds yet. Add players and start playing!");
        else for(int i=0;i<Math.min(8,games.size());i++){
            Map<String,Object> round=games.get(i);
            LinearLayout card=panel();
            String result=str(round,"result");
            card.addView(text("Bid "+num(round.get("bid"))+" · "+("win".equals(result)?"Hukum Win":"Hukum Lose"),
                    15,"win".equals(result)?GREEN:RED,true));
            gap(card,7);
            card.addView(text("Hukum: "+str(round,"hukumName")+" · "+when(round.get("timestamp")),12,MUTED,false));
            card.setOnClickListener(v->roundDetails(round));
            content.addView(card);gap(content,9);
        }
    }
    private void quickActions(String a,Runnable left,String b,Runnable right) {
        LinearLayout row=row();
        LinearLayout.LayoutParams lp1=new LinearLayout.LayoutParams(0,dp(72),1);
        lp1.rightMargin=dp(10);
        row.addView(button(a,false,left),lp1);
        row.addView(button(b,false,right),new LinearLayout.LayoutParams(0,dp(72),1));
        content.addView(row);
        gap(content,10);
    }
    private LinearLayout statTile(String label,String value,int valueColor){
        LinearLayout tile=column();
        tile.setBackground(shape(0xFF202024,13,0));
        pad(tile,12,12,12,12);
        tile.addView(text(label,10,MUTED,true));
        gap(tile,8);
        TextView number=text(value, label.equals("TOP WINNER")?13:22,valueColor,true);
        number.setSingleLine(false);
        number.setMaxLines(2);
        if(!label.equals("TOP WINNER"))try{number.setTag(new MotionEngine.Anchor("home:"+label,Integer.parseInt(value),false));}catch(NumberFormatException ignored){}
        tile.addView(number);
        return tile;
    }
    private void empty(String message) {
        LinearLayout p=panel();
        p.addView(text(message,14,MUTED,false));
        content.addView(p);gap(content,12);motion.enter(p,0);
    }
    private void playersPage() {
        title(content,"Players","Active and benched members of your table");
        if(loggedIn){
            content.addView(button("＋  Add a Player",true,this::addPlayerDialog));gap(content,18);
        }
        if(players.isEmpty()){empty("No players yet. Add the first player as admin.");return;}
        LinearLayout filters=row();
        for(String f:new String[]{"all","active","benched"}){TextView c=chip(f.toUpperCase(Locale.ROOT),rosterFilter.equals(f),true,()->{rosterFilter=f;render(false);});filters.addView(c,new LinearLayout.LayoutParams(0,dp(48),1));}
        content.addView(filters);gap(content,16);
        List<Map<String,Object>> roster=new ArrayList<>(players);
        roster.sort(Comparator.comparing(p->Boolean.FALSE.equals(p.get("active"))));
        int rosterCount=0;
        for(Map<String,Object> p:roster) {
            boolean isActive=!Boolean.FALSE.equals(p.get("active"));
            if("active".equals(rosterFilter)&&!isActive || "benched".equals(rosterFilter)&&isActive)continue;
            rosterCount++;
            String id=id(p),name=str(p,"name");boolean active=!Boolean.FALSE.equals(p.get("active"));
            LinearLayout card=panel();
            LinearLayout top=row();
            TextView avatar=text(initials(name),16,GOLD,true);
            avatar.setGravity(Gravity.CENTER);
            avatar.setBackground(shape(0xFF35405C,100,0));
            top.addView(avatar,lp(48,48));
            LinearLayout names=column();pad(names,12,0,5,0);
            names.addView(text(name,16,WHITE,true));gap(names,5);
            names.addView(text(active?"● Active player":"● Benched",12,active?GREEN:MUTED,false));
            top.addView(names,new LinearLayout.LayoutParams(0,-2,1));
            int score=scoreOf(id,rounds);
            TextView points=text((score>=0?"+":"")+score,17,score>=0?GREEN:RED,true);
            points.setTag(new MotionEngine.Anchor("player-score:"+id,score,true));top.addView(points);
            card.addView(top);
            card.setClickable(true);
            card.setFocusable(true);
            card.setOnClickListener(v->playerActions(p));
            if(loggedIn) {
                gap(card,9);
                card.addView(text("Tap for player options  ›",12,GOLD,false));
            }
            content.addView(card);gap(content,12);
        }
    }
    private void playerActions(Map<String,Object> player) {
        if(!loggedIn) {
            profileId=id(player);tab=5;render(true);
            return;
        }
        boolean active=!Boolean.FALSE.equals(player.get("active"));
        String[] options={"View Profile","Edit Name",active?"Bench Player":"Restore Player","Remove Player"};
        new MotionDialogBuilder(this,motion)
                .setTitle(str(player,"name"))
                .setItems(options,(dialog,choice)->{
                    switch(choice) {
                        case 0:profileId=id(player);tab=5;render(true);break;
                        case 1:editPlayerDialog(player);break;
                        case 2:confirmPlayerStatus(player,!active);break;
                        case 3:confirmRemove("players",player,"player");break;
                    }
                }).setNegativeButton("Close",null).show();
    }
    private String initials(String name) {
        String[] parts=name.trim().split("\\s+");
        if(parts.length==0||name.isEmpty())return "?";
        return (parts[0].substring(0,1)+(parts.length>1?parts[parts.length-1].substring(0,1):"")).toUpperCase(Locale.ROOT);
    }
    private List<Map<String,Object>> rankedPlayers() {
        return rankedFor(gameRounds());
    }

    private List<Map<String,Object>> rankedFor(List<Map<String,Object>> games) {
        List<Map<String,Object>> result=new ArrayList<>(players);
        result.sort((a,b)->Integer.compare(scoreOf(id(b),games),scoreOf(id(a),games)));
        result.removeIf(p->played(id(p),games)==0 && scoreOf(id(p),games)==0);
        return result;
    }

    private List<Map<String,Object>> filteredRounds() {
        if("all".equals(rankFilter))return gameRounds();
        DateRange range=DateRange.of(rankFilter,Instant.now(),ZoneId.systemDefault(),customStart,customEnd);
        List<Map<String,Object>> result=new ArrayList<>();
        for(Map<String,Object> round:gameRounds())if(range.contains(time(round.get("timestamp"))))result.add(round);
        return result;
    }

    private void dateFilterBar(LinearLayout target) {
        HorizontalScrollView hsv=new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout items=row();
        String[][] options={{"all","All Time"},{"today","Today"},{"yesterday","Yesterday"},
                {"week","This Week"},{"month","This Month"},{"year","This Year"},{"custom","Custom"}};
        for(String[] opt:options) {
            TextView item=chip(opt[1],rankFilter.equals(opt[0]),true,()->{
                rankFilter=opt[0];render(false);motion.enter(content,0);
            });
            LinearLayout.LayoutParams one=lp(-2,-2);one.rightMargin=dp(8);
            items.addView(item,one);
        }
        hsv.addView(items);
        target.addView(hsv);gap(target,12);
        if("custom".equals(rankFilter)) {
            LinearLayout dates=row();
            TextView first=button(customStart==0?"Start Date":dateOnly(customStart),false,()->selectDate(true));
            TextView last=button(customEnd==0?"End Date":dateOnly(customEnd),false,()->selectDate(false));
            LinearLayout.LayoutParams part=new LinearLayout.LayoutParams(0,dp(48),1);
            part.rightMargin=dp(8);dates.addView(first,part);
            dates.addView(last,new LinearLayout.LayoutParams(0,dp(48),1));
            target.addView(dates);gap(target,14);
        }
    }
    private String dateOnly(long ms) {
        return DateTimeFormatter.ofPattern("dd MMM yyyy").withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(ms));
    }
    private void selectDate(boolean isStart) {
        java.util.Calendar c=java.util.Calendar.getInstance();
        long selected=isStart?customStart:customEnd;
        if(selected>0)c.setTimeInMillis(selected);
        android.app.DatePickerDialog dialog=new android.app.DatePickerDialog(this,R.style.Theme_ThreePatti_Dialog,(picker,year,month,day)->{
            java.util.Calendar picked=java.util.Calendar.getInstance();
            picked.clear();picked.set(year,month,day);
            long value=picked.getTimeInMillis();
            if(isStart&&customEnd>0&&value>customEnd){
                Toast.makeText(this,"Start date must be before end date",Toast.LENGTH_LONG).show();return;
            }
            if(!isStart&&customStart>0&&value<customStart){
                Toast.makeText(this,"End date must be after start date",Toast.LENGTH_LONG).show();return;
            }
            if(isStart)customStart=value;
            else customEnd=value;
            render(false);
        },c.get(java.util.Calendar.YEAR),c.get(java.util.Calendar.MONTH),c.get(java.util.Calendar.DAY_OF_MONTH));
        motion.dialog(dialog);DialogUi.install(dialog);dialog.show();
    }

    private void profilePage() {
        Map<String,Object> target=null;
        for(Map<String,Object> p:players)if(id(p).equals(profileId)){target=p;break;}
        if(target==null){title(content,"Profile unavailable","Player not found");return;}
        String pid=id(target);
        TextView back=button("←  Back to Ranks",false,()->{tab=3;render(true);});
        content.addView(back);gap(content,16);
        title(content,"♛ "+str(target,"name"),"Player profile · Detailed statistics");
        int streak=winningStreak(pid,filteredRounds());
        if(streak>=2){content.addView(text("🔥  "+streak+"-round winning streak",14,GOLD,true));gap(content,12);}
        dateFilterBar(content);
        List<Map<String,Object>> filtered=filteredRounds();
        List<Map<String,Object>> individual=new ArrayList<>();
        for(Map<String,Object> r:filtered)if(strings(r.get("playerIds")).contains(pid))individual.add(r);
        int totalScore=scoreOf(pid,individual),wins=0,losses=0,hukumWins=0,hukumLosses=0;
        int partnerWins=0,partnerLosses=0,otherWins=0,otherLosses=0;
        Map<String,Integer> alongside=new HashMap<>(),against=new HashMap<>();
        Map<String,int[]> head=new HashMap<>();
        Map<String,Object> best=null,worst=null;
        int max=Integer.MIN_VALUE,min=Integer.MAX_VALUE;
        for(Map<String,Object> r:individual) {
            boolean roleH=pid.equals(str(r,"hukumId"));
            boolean roleP=strings(r.get("partnerIds")).contains(pid);
            boolean win=RoundAnalytics.won(r,pid);
            if(win)wins++;else losses++;
            if(roleH){if(win)hukumWins++;else hukumLosses++;}
            else if(roleP){if(win)partnerWins++;else partnerLosses++;}
            else{if(win)otherWins++;else otherLosses++;}
            int score=num(map(r.get("scoreChanges")).get(pid));
            if(score>max){max=score;best=r;}
            if(score<min){min=score;worst=r;}
            for(String other:strings(r.get("playerIds"))) {
                if(other.equals(pid))continue;
                boolean otherSide=other.equals(str(r,"hukumId"))||strings(r.get("partnerIds")).contains(other);
                int[] h=head.computeIfAbsent(other,k->new int[4]);
                if(otherSide==(roleH||roleP))h[0]++;
                else {h[1]++;if(win)h[2]++;else h[3]++;}
            }
        }
        LinearLayout stats=panel();
        int rankIndex=rankedFor(filtered).indexOf(target);
        line(stats,"Rank",rankIndex<0?"—":"#"+(rankIndex+1),GOLD);
        line(stats,"Total Score",(totalScore>=0?"+":"")+totalScore,totalScore>=0?GREEN:RED).setTag(new MotionEngine.Anchor("profile-score:"+pid+":"+rankFilter+":"+customStart+":"+customEnd,totalScore,true));
        line(stats,"Rounds",String.valueOf(individual.size()),WHITE);
        line(stats,"Win Rate",individual.isEmpty()?"0%":Math.round(100.0*wins/individual.size())+"%",GOLD);
        line(stats,"Wins / Losses",wins+"W / "+losses+"L",WHITE);
        content.addView(stats);gap(content,16);
        label(content,"PLAYING ROLES");
        final int hw=hukumWins,hl=hukumLosses,pw=partnerWins,pl=partnerLosses,ow=otherWins,ol=otherLosses;
        roleCard("♛  Hukum",hw,hl,()->showRoleRounds(pid,"hukum",individual));
        roleCard("◈  Partner",pw,pl,()->showRoleRounds(pid,"partner",individual));
        roleCard("○  Non-partner",ow,ol,()->showRoleRounds(pid,"nonpartner",individual));
        gap(content,10);
        label(content,"BEST & WORST ROUND");
        if(best!=null) {
            final Map<String,Object> bestR=best;
            content.addView(button("Best round: "+(max>=0?"+":"")+max+"  ·  Tap for details",false,()->roundDetails(bestR)));
            gap(content,8);
            final Map<String,Object> worstR=worst;
            content.addView(button("Worst round: "+min+"  ·  Tap for details",false,()->roundDetails(worstR)));
        }else empty("No completed rounds in this period.");
        gap(content,18);
        label(content,"HEAD TO HEAD");
        if(head.isEmpty())empty("No head-to-head data.");
        else {
            List<String> rivals=new ArrayList<>(head.keySet());
            rivals.sort((a,b)->Integer.compare(head.get(b)[1],head.get(a)[1]));
            for(String rival:rivals) {
                int[] h=head.get(rival);
                LinearLayout panel=panel();
                panel.addView(text("vs  "+nameOf(rival),15,WHITE,true));gap(panel,7);
                line(panel,h[0]+" together · "+h[1]+" against",h[2]+"W / "+h[3]+"L",GOLD);
                content.addView(panel);gap(content,9);
            }
        }
        gap(content,12);
        label(content,"PLAYER ROUND HISTORY");
        int maxRows=Math.min(profileRoundVisible,individual.size());
        for(int i=0;i<maxRows;i++) {
            Map<String,Object> game=individual.get(i);
            content.addView(button(when(game.get("timestamp"))+"  ·  "+str(game,"hukumName")+
                    "  ·  "+num(map(game.get("scoreChanges")).get(pid)),false,()->roundDetails(game)));
            gap(content,7);
        }
        if(maxRows<individual.size())content.addView(button("Show More Rounds",false,()->{profileRoundVisible+=20;render(false);}));
    }
    private void roleCard(String title,int wins,int losses,Runnable click) {
        LinearLayout panel=panel();
        panel.addView(text(title,17,GOLD,true));gap(panel,8);
        line(panel,"Rounds",String.valueOf(wins+losses),WHITE);
        line(panel,"Results",wins+"W / "+losses+"L",GREEN);
        panel.setOnClickListener(v->click.run());
        content.addView(panel);gap(content,10);
    }
    private void showRoleRounds(String pid,String role,List<Map<String,Object>> source) {
        List<Map<String,Object>> selected=RoleHistory.select(pid,role,source);
        String heading="hukum".equals(role)?"Hukum":"partner".equals(role)?"Partner":"Non-partner";
        MotionDialogBuilder builder=new MotionDialogBuilder(this,motion);
        if(selected.isEmpty()){
            LinearLayout body=DialogUi.body(this);DialogUi.fact(body,"Player",nameOf(pid),WHITE);
            body.addView(DialogUi.text(this,"No rounds in this role for the selected period.",15,MUTED,false));
            builder.setView(DialogUi.scroll(this,body));
        }else{
            ListView list=new DialogUi.BoundedList(this);list.setContentDescription("Role round history");list.setFastScrollEnabled(selected.size()>30);
            LinearLayout header=DialogUi.body(this);DialogUi.fact(header,"Player",nameOf(pid),WHITE);
            long points=0;for(Map<String,Object> round:selected)points+=num(map(round.get("scoreChanges")).get(pid));
            header.addView(DialogUi.text(this,selected.size()+" rounds · Total points "+(points>0?"+":"")+points,13,MUTED,false));DialogUi.gap(header,8);
            list.addHeaderView(header,null,false);list.setAdapter(new RoleHistoryAdapter(this,selected,r->when(r.get("timestamp")),r->num(map(r.get("scoreChanges")).get(pid))));
            list.setOnItemClickListener((parent,view,position,itemId)->{Object item=parent.getItemAtPosition(position);if(item instanceof Map)roundDetails(map(item));});
            builder.setView(list);
        }
        builder.setTitle(heading+" · "+selected.size()+" rounds").setPositiveButton("Close",null).show();
    }
    private View roundDetailBody(Map<String,Object> round,boolean includeScores){
        LinearLayout body=DialogUi.body(this);
        DialogUi.fact(body,"Date",when(round.get("timestamp")),WHITE);
        DialogUi.fact(body,"Hukum",str(round,"hukumName"),GOLD);
        DialogUi.fact(body,"Bid",str(round,"bid"),WHITE);
        boolean win="win".equals(str(round,"result"));
        DialogUi.fact(body,"Result",win?"Hukum win":"Hukum loss",win?GREEN:RED);
        DialogUi.fact(body,"Partners",android.text.TextUtils.join(", ",strings(round.get("partnerNames"))),WHITE);
        DialogUi.fact(body,"Non-partners",android.text.TextUtils.join(", ",strings(round.get("nonPartnerNames"))),WHITE);
        if(includeScores){
            DialogUi.heading(body,"SCORE CHANGES");Map<String,Object> changes=map(round.get("scoreChanges"));
            LinkedHashSet<String> order=new LinkedHashSet<>(strings(round.get("playerIds")));order.addAll(changes.keySet());
            for(String pid:order)if(changes.containsKey(pid)){
                String role=pid.equals(str(round,"hukumId"))?"Hukum":strings(round.get("partnerIds")).contains(pid)?"Partner":"Non-partner";
                DialogUi.score(body,nameOf(pid),role,num(changes.get(pid)));
            }
        }
        return DialogUi.scroll(this,body);
    }
    private void roundDetails(Map<String,Object> round) {
        new MotionDialogBuilder(this,motion).setTitle("Round details").setView(roundDetailBody(round,true))
            .setPositiveButton("Close",null).show();
    }

    private void addRankCard(Map<String,Object> p,int position) {
        LinearLayout card=panel();
        LinearLayout r=row();
        TextView rank=text(position==0?"🥇":position==1?"🥈":position==2?"🥉":(position+1)+".",22,position==0?GOLD:MUTED,true);
        rank.setGravity(Gravity.CENTER);r.addView(rank,lp(45,42));
        LinearLayout middle=column();
        middle.addView(text(str(p,"name"),16,WHITE,true));gap(middle,5);
        List<Map<String,Object>> source=(tab==3 && currentRankData!=null)?currentRankData:gameRounds();
        int streak=winningStreak(id(p),source);
        middle.addView(text(played(id(p),source)+" played"+
                (streak>=2?"  ·  🔥 "+streak+" streak":""),12,MUTED,false));
        r.addView(middle,new LinearLayout.LayoutParams(0,-2,1));
        int score=scoreOf(id(p),source);
        String context=rankFilter+":"+customStart+":"+customEnd;
        TextView points=text((score>=0?"+":"")+score,19,score>=0?GREEN:RED,true);
        points.setTag(new MotionEngine.Anchor("rank-score:"+context+":"+id(p),score,true));r.addView(points);
        card.setTag(new MotionEngine.Anchor("rank:"+context+":"+id(p)));
        if(position==0&&scoreMotion!=null&&!previousLeader.isEmpty()&&!previousLeader.equals(id(p)))motion.leader(card);
        card.addView(r);
        card.setClickable(true);
        card.setOnClickListener(v->{profileId=id(p);tab=5;profileRoundVisible=20;render(true);});
        content.addView(card);gap(content,11);
    }
    private void ranksPage() {
        title(content,"Leaderboard","Lifetime scores · Hukum, Partner & Non-partner");
        dateFilterBar(content);
        List<Map<String,Object>> games=filteredRounds(), ranked=rankedFor(games);
        currentRankData=games;
        if(ranked.isEmpty())empty("No players with rounds in this date range.");
        int maximum=Math.min(visibleRankCount,ranked.size());
        for(int i=0;i<maximum;i++) {
            Map<String,Object> p=ranked.get(i);
            addRankCard(p,i);
            if(played(id(p),games)>0) {
                LinearLayout detail=row();
                detail.addView(text("     "+wins(id(p),games)+" Wins  ·  "+played(id(p),games)+" Played",11,MUTED,false));
                content.addView(detail);gap(content,13);
            }
        }
        if(maximum<ranked.size()) {
            content.addView(button("Show "+Math.min(35,ranked.size()-maximum)+" More Players",false,()->{
                visibleRankCount+=35;render(false);
            }));
        }
        gap(content,10);
        content.addView(button("Share Rankings Image",false,this::shareLeaderboard));
    }
    private void historyPage() {
        title(content,"History","Every saved round and previous session");
        LinearLayout toggle=row();
        TextView roundTab=chip("Game Rounds",historyTab==0,true,()->{historyTab=0;render(false);motion.enter(content,0);});
        TextView sessionTab=chip("Sessions",historyTab==1,true,()->{historyTab=1;render(false);motion.enter(content,0);});
        LinearLayout.LayoutParams l1=new LinearLayout.LayoutParams(0,dp(48),1);l1.rightMargin=dp(10);
        toggle.addView(roundTab,l1);toggle.addView(sessionTab,new LinearLayout.LayoutParams(0,dp(48),1));
        content.addView(toggle);gap(content,18);
        if(historyTab==0) {
            List<Map<String,Object>> history=gameRounds();
            if(history.isEmpty())empty("No rounds recorded yet.");
            int count=Math.min(visibleRoundCount,history.size());
            for(int i=0;i<count;i++)addRoundCard(history.get(i));
            if(count<history.size()) {
                content.addView(button("Show "+Math.min(25,history.size()-count)+" More Rounds",false,()->{
                    visibleRoundCount+=25;render(false);
                }));
            }
        } else {
            if(sessions.isEmpty())empty("No previous sessions yet.");
            int count=Math.min(visibleSessionCount,sessions.size());
            for(int i=0;i<count;i++) {
                Map<String,Object> s=sessions.get(i);
                LinearLayout card=panel();
                card.addView(text(str(s,"sessionName").isEmpty()?"Game Session":str(s,"sessionName"),17,WHITE,true));
                gap(card,10);
                line(card,"Started",when(s.get("startTime")),MUTED);
                line(card,"Ended",s.get("endTime")==null?"Active":when(s.get("endTime")),s.get("endTime")==null?GREEN:WHITE);
                line(card,"Rounds",String.valueOf(num(s.get("totalRounds"))),GOLD);
                if(s.get("endTime")!=null)line(card,"Duration",duration(s.get("duration") instanceof Number?((Number)s.get("duration")).longValue():time(s.get("endTime"))-time(s.get("startTime"))),MUTED);
                Map<String,Object> mvp=map(s.get("mvp"));
                if(!mvp.isEmpty())line(card,"MVP",str(mvp,"name"),GREEN);
                gap(card,9);
                card.addView(text(loggedIn?"Tap for session options  ›":"Tap to view summary  ›",12,GOLD,false));
                card.setClickable(true);
                card.setOnClickListener(v->sessionActions(s));
                content.addView(card);gap(content,12);
            }
            if(count<sessions.size()) {
                content.addView(button("Show "+Math.min(20,sessions.size()-count)+" More Sessions",false,()->{
                    visibleSessionCount+=20;render(false);
                }));
            }
        }
    }
    private void sessionActions(Map<String,Object> session) {
        if(!loggedIn){showSessionSummary(session);return;}
        new MotionDialogBuilder(this,motion)
                .setTitle(str(session,"sessionName"))
                .setItems(new String[]{"View Summary","Share Summary Image","Edit Session Name","Delete Session"},
                        (dialog,which)->{
                            switch(which){
                                case 0:showSessionSummary(session);break;
                                case 1:shareSession(session);break;
                                case 2:editSessionDialog(session);break;
                                case 3:confirmRemove("sessions",session,"session");break;
                            }
                        }).setNegativeButton("Close",null).show();
    }
    private void addRoundCard(Map<String,Object> r) {
        LinearLayout card=panel();
        LinearLayout head=row();
        boolean victory="win".equals(str(r,"result"));
        TextView title=text("♛  "+str(r,"hukumName"),15,WHITE,true);
        title.setMaxLines(3);
        head.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView flag=text(victory?"WIN":"LOSE",12,victory?GREEN:RED,true);
        flag.setGravity(Gravity.CENTER);
        flag.setBackground(shape(victory?0xFF1C443B:0xFF492633,9,0));
        pad(flag,10,7,10,7);
        head.addView(flag);
        card.addView(head);gap(card,5);
        card.addView(text("Bid "+str(r,"bid")+"  •  "+when(r.get("timestamp")),12,MUTED,false));
        gap(card,8);
        String team=android.text.TextUtils.join(", ",strings(r.get("partnerNames")));
        card.addView(text("Partners: "+(team.isEmpty()?"—":team),12,MUTED,false));
        card.addView(text("Opponents: "+android.text.TextUtils.join(", ",strings(r.get("nonPartnerNames"))),12,MUTED,false));
        if(!settings.getBoolean("compact",false)){
            gap(card,8);
            for(Map.Entry<String,Object> entry:map(r.get("scoreChanges")).entrySet()){
                int change=num(entry.getValue());
                line(card,nameOf(entry.getKey()),(change>0?"+":"")+change,change>0?GREEN:change<0?RED:MUTED);
            }
        }
        card.setClickable(true);card.setFocusable(true);
        card.setForeground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x25FFFFFF),null,null));
        card.setOnClickListener(v->roundActions(r));
        content.addView(card);gap(content,10);
    }
    private void roundActions(Map<String,Object> round) {
        AlertDialog.Builder dialog=new MotionDialogBuilder(this,motion)
                .setTitle("Round · "+when(round.get("timestamp")))
                .setView(roundDetailBody(round,false))
                .setPositiveButton("Details",(d,w)->roundDetails(round))
                .setNeutralButton("Share Image",(d,w)->shareRound(round));
        if(loggedIn)dialog.setNegativeButton("Delete",(d,w)->confirmRemove("rounds",round,"round"));
        else dialog.setNegativeButton("Close",null);
        dialog.show();
    }
    private void roundPage() {
        if(!loggedIn){empty("Admin login is required to add rounds.");return;}
        title(content,"New Hukum Round","Select players and preview scores before saving");
        final List<Map<String,Object>> active=activePlayers();
        if(playing==null)playing=activeIds();
        if(active.size()<3){empty("At least 3 active players are required.");return;}
        roundPlayingButtons.clear();roundHukumButtons.clear();roundPartnerButtons.clear();

        label(content,"01  •  Who's playing");
        LinearLayout participants=panel();
        for(Map<String,Object> player:active){
            final String pid=id(player);
            TextView pick=chip(str(player,"name"),playing.contains(pid),false,()->{
                if(roundSaving||busy||pendingRoundId!=null)return;
                if(!playing.add(pid)){
                    playing.remove(pid);
                    if(pid.equals(hukum))hukum=null;
                    partners.remove(pid);
                }
                updateRoundForm();
            });
            participants.addView(pick,spacedRow(8));
            roundPlayingButtons.put(pid,pick);
        }
        content.addView(participants);gap(content,18);

        label(content,"02  •  Bid amount");
        LinearLayout bidding=panel();
        roundBidView=text("",27,GOLD,true);
        roundBidView.setClickable(true);
        roundBidView.setFocusable(true);
        roundBidView.setContentDescription("Choose bid amount");
        roundBidView.setOnClickListener(v->chooseBid());
        bidding.addView(roundBidView);
        gap(bidding,14);
        LinearLayout stepper=row();
        stepper.addView(button("− 5",false,()->{
            if(roundSaving||busy||pendingRoundId!=null)return;
            bid=Math.max(155,bid-5);updateRoundForm();
        }),new LinearLayout.LayoutParams(0,dp(50),1));
        TextView label=text("155 – 250",13,MUTED,false);
        label.setGravity(Gravity.CENTER);
        stepper.addView(label,new LinearLayout.LayoutParams(0,dp(50),1));
        stepper.addView(button("+ 5",false,()->{
            if(roundSaving||busy||pendingRoundId!=null)return;
            bid=Math.min(250,bid+5);updateRoundForm();
        }),new LinearLayout.LayoutParams(0,dp(50),1));
        bidding.addView(stepper);content.addView(bidding);gap(content,18);

        roundHukumSection=column();
        label(roundHukumSection,"03  •  Choose Hukum");
        LinearLayout hukumBox=panel();
        for(Map<String,Object> player:active){
            String pid=id(player);
            TextView pick=chip(str(player,"name"),false,true,()->{
                if(roundSaving||busy||pendingRoundId!=null)return;
                hukum=pid;partners.remove(pid);updateRoundForm();
            });
            hukumBox.addView(pick,spacedRow(8));
            roundHukumButtons.put(pid,pick);
        }
        roundHukumSection.addView(hukumBox);gap(roundHukumSection,18);
        content.addView(roundHukumSection);

        roundPartnerSection=column();
        label(roundPartnerSection,"04  •  Choose Partner(s)");
        LinearLayout partnerBox=panel();
        for(Map<String,Object> player:active){
            String pid=id(player);
            TextView pick=chip(str(player,"name"),false,false,()->{
                if(roundSaving||busy||pendingRoundId!=null)return;
                if(!partners.add(pid))partners.remove(pid);
                updateRoundForm();
            });
            partnerBox.addView(pick,spacedRow(8));
            roundPartnerButtons.put(pid,pick);
        }
        roundPartnerSection.addView(partnerBox);gap(roundPartnerSection,18);
        content.addView(roundPartnerSection);

        roundResultSection=column();
        label(roundResultSection,"05  •  Hukum result");
        LinearLayout results=row();
        roundWinView=chip("✓   Hukum Win",false,false,()->{
            if(roundSaving||busy||pendingRoundId!=null)return;
            outcome="win";updateRoundForm();
        });
        roundLoseView=chip("✕   Hukum Lose",false,false,()->{
            if(roundSaving||busy||pendingRoundId!=null)return;
            outcome="loss";updateRoundForm();
        });
        LinearLayout.LayoutParams half=new LinearLayout.LayoutParams(0,dp(50),1);
        half.rightMargin=dp(10);
        results.addView(roundWinView,half);
        results.addView(roundLoseView,new LinearLayout.LayoutParams(0,dp(50),1));
        roundResultSection.addView(results);gap(roundResultSection,18);
        content.addView(roundResultSection);

        roundPreviewSection=column();
        label(roundPreviewSection,"Score preview");
        content.addView(roundPreviewSection);
        roundHintView=text("",12,MUTED,false);
        content.addView(roundHintView);gap(content,14);
        roundSaveView=button("✓  Save Round to Firebase",true,this::saveRound);
        content.addView(roundSaveView);gap(content,12);
        content.addView(text("Uses your existing Firebase data. Saving happens only after confirmation.",11,MUTED,false));
        updateRoundForm();
    }
    private LinearLayout.LayoutParams spacedRow(int spacing){
        LinearLayout.LayoutParams params=lp(-1,48);
        params.bottomMargin=dp(spacing);
        return params;
    }
    private void updateRoundChip(TextView view,String label,boolean selected,boolean gold){
        if(view==null)return;
        boolean loss=view==roundLoseView;
        motion.selection(view,selected,loss?RED:gold?GOLD:GREEN);
        view.setText(label);
        view.setTextColor(selected?(loss?RED:gold?GOLD:GREEN):WHITE);
        view.setTypeface(Typeface.create("sans-serif",selected?Typeface.BOLD:Typeface.NORMAL));
        view.setBackground(shape(selected?(loss?0xFF4B2426:gold?0xFF60492E:0xFF1A4B43):PANEL2,
                12,selected?(loss?RED:gold?GOLD:GREEN):BORDER));
    }
    private void updateRoundForm(){
        if(roundBidView==null||tab!=2)return;
        motion.bid(roundBidView,bid);
        for(Map.Entry<String,TextView> item:roundPlayingButtons.entrySet()){
            String pid=item.getKey();
            item.getValue().setContentDescription("Playing "+nameOf(pid));
            updateRoundChip(item.getValue(),(playing.contains(pid)?"✓   ":"○   ")+nameOf(pid),
                    playing.contains(pid),false);
        }
        boolean enough=playing!=null && playing.size()>=3;
        if(!enough) {hukum=null;partners.clear();}
        motion.show(roundHukumSection,enough);
        boolean hasHukum=enough&&hukum!=null&&playing.contains(hukum);
        motion.show(roundPartnerSection,hasHukum);
        motion.show(roundResultSection,hasHukum);
        for(Map.Entry<String,TextView> item:roundHukumButtons.entrySet()){
            String pid=item.getKey();
            TextView control=item.getValue();control.setContentDescription("Hukum "+nameOf(pid));
            control.setVisibility(playing.contains(pid)?View.VISIBLE:View.GONE);
            updateRoundChip(control,(pid.equals(hukum)?"♛   ":"○   ")+nameOf(pid),
                    pid.equals(hukum),true);
        }
        for(Map.Entry<String,TextView> item:roundPartnerButtons.entrySet()){
            String pid=item.getKey();
            TextView control=item.getValue();control.setContentDescription("Partner "+nameOf(pid));
            control.setVisibility(hasHukum&&playing.contains(pid)&&!pid.equals(hukum)?View.VISIBLE:View.GONE);
            updateRoundChip(control,(partners.contains(pid)?"✓   ":"○   ")+nameOf(pid),
                    partners.contains(pid),false);
        }
        updateRoundChip(roundWinView,"✓   Hukum Win","win".equals(outcome),false);
        updateRoundChip(roundLoseView,"✕   Hukum Lose","loss".equals(outcome),false);
        roundPreviewSection.removeViews(1,Math.max(0,roundPreviewSection.getChildCount()-1));
        boolean ready=validRound();
        motion.show(roundPreviewSection,ready);
        if(ready){
            LinearLayout card=panel();
            Map<String,Integer> preview=scoreChanges();
            for(Map<String,Object> player:activePlayers()){
                String pid=id(player);
                if(!playing.contains(pid))continue;
                int delta=preview.getOrDefault(pid,0);
                String role=pid.equals(hukum)?"♛ ":partners.contains(pid)?"◈ ":"○ ";
                line(card,role+str(player,"name"),delta==0?"0":(delta>0?"+":"")+delta,
                        delta>0?GREEN:delta<0?RED:MUTED);
            }
            roundPreviewSection.addView(card);
        }
        String hint=!enough?"Select at least 3 players.":!hasHukum?"Choose Hukum to continue.":
                partners.isEmpty()?"Choose at least one partner.":
                playing.size()-partners.size()-1<1?"At least one non-partner must be playing.":
                outcome==null?"Select Hukum Win or Lose.":"Preview verified. Check the scores before saving.";
        roundHintView.setText(roundSaving?"Saving round to Firebase…":pendingRoundId!=null?"Unconfirmed save: selections locked. Retry to confirm the same round.":hint);
        roundSaveView.setVisibility(ready?View.VISIBLE:View.GONE);
        roundSaveView.setEnabled(ready&&liveConnected&&!roundSaving&&!busy);
        roundSaveView.setAlpha(roundSaving||busy?0.55f:1f);
        roundSaveView.setText(roundSaving?"Saving…":"✓  Save Round to Firebase");
    }
    private void chooseBid(){
        if(roundSaving||busy||pendingRoundId!=null)return;
        String[] values=new String[20];
        for(int i=0;i<values.length;i++)values[i]=String.valueOf(155+i*5);
        new MotionDialogBuilder(this,motion)
                .setTitle("Select Bid")
                .setSingleChoiceItems(values,(bid-155)/5,(dialog,selected)->{
                    bid=155+selected*5;
                    dialog.dismiss();
                    updateRoundForm();
                }).setNegativeButton("Cancel",null).show();
    }
    private boolean validRound() {
        if(!loggedIn||hukum==null||outcome==null||playing==null||playing.size()<3||partners.isEmpty())return false;
        if(!playing.contains(hukum)||partners.contains(hukum))return false;
        for(String p:partners)if(!playing.contains(p))return false;
        return playing.size()-partners.size()-1>=1;
    }
    private Map<String,Integer> scoreChanges() {
        return ScoreRules.calculate(new ArrayList<>(playing),bid,hukum,new ArrayList<>(partners),outcome);
    }
    private void saveRound() {
        if(roundSaving||busy){Toast.makeText(this,"A save is already in progress",Toast.LENGTH_SHORT).show();return;}
        if(!validRound()){motion.error(roundHintView);Toast.makeText(this,"Complete the round details first",Toast.LENGTH_SHORT).show();return;}
        List<String> pIds=new ArrayList<>(playing), partnerIds=new ArrayList<>(partners), opponentIds=new ArrayList<>();
        List<String> partnerNames=new ArrayList<>(),opponentNames=new ArrayList<>();
        for(String pid:partnerIds)partnerNames.add(nameOf(pid));
        for(String pid:pIds)if(!pid.equals(hukum)&&!partners.contains(pid)){opponentIds.add(pid);opponentNames.add(nameOf(pid));}
        Map<String,Object> scores=new HashMap<>();scores.putAll(scoreChanges());
        Map<String,Object> body=FirebaseGateway.map(
                "type","hukum","timestamp",FirebaseGateway.now(),"bid",bid,
                "hukumId",hukum,"hukumName",nameOf(hukum),
                "partnerIds",partnerIds,"partnerNames",partnerNames,
                "nonPartnerIds",opponentIds,"nonPartnerNames",opponentNames,
                "playerIds",pIds,"result",outcome,"scoreChanges",scores);
        new MotionDialogBuilder(this,motion)
                .setTitle("Save this round?")
                .setMessage("Bid "+bid+" · "+nameOf(hukum)+" · "+("win".equals(outcome)?"Hukum win":"Hukum lose")+
                        "\n\nThis will add one new round to your existing Firebase data.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Save Round",(d,w)->{
                    String key=bid+"|"+hukum+"|"+outcome+"|"+
                            new TreeSet<>(playing)+"|"+new TreeSet<>(partners);
                    if(pendingRoundKey!=null&&!key.equals(pendingRoundKey)){
                        notice("An earlier round is unconfirmed. Restore that selection and retry before changing it.");return;
                    }
                    if(pendingRoundId==null){pendingRoundKey=key;pendingRoundId=UUID.randomUUID().toString();pendingRoundTimestamp=Instant.now().toString();}
                    if(pendingRoundJson==null){Map<String,Object> durable=new LinkedHashMap<>(body);durable.put("timestamp",pendingRoundTimestamp);pendingRoundJson=new org.json.JSONObject(durable).toString();}
                    if(!persistDraft()){notice("Could not persist this round safely. Free storage and retry.");return;}
                    sendPendingRound();
                }).show();
    }
    private void retryPendingRound(){
        if(pendingRoundId==null||pendingRoundJson==null){notice("No unconfirmed round");return;}
        new MotionDialogBuilder(this,motion).setTitle("Retry unconfirmed round?").setMessage("Verify or save the original round using its existing ID. Its scores and selections are preserved.").setNegativeButton("Cancel",null).setPositiveButton("Retry",(d,w)->sendPendingRound()).show();
    }
    private void sendPendingRound(){
        if(roundSaving||busy)return;
        try{
            Map<String,Object> preview=JsonValues.object(new org.json.JSONObject(pendingRoundJson));
            Map<String,Object> body=new LinkedHashMap<>(preview);body.put("timestamp",new FirebaseGateway.Stamp(pendingRoundTimestamp));
            roundSaving=true;if(tab==2)updateRoundForm();
            final String savedId=pendingRoundId;
            api.addRoundWithId(savedId,body,()->{
                roundSaving=false;resetRound();tab=0;render(true);
                Set<String> acknowledged=new LinkedHashSet<>(settings.getStringSet("motion_acknowledged_rounds",Collections.emptySet()));
                boolean first=acknowledged.add(savedId);while(acknowledged.size()>128)acknowledged.remove(acknowledged.iterator().next());
                settings.edit().putStringSet("motion_acknowledged_rounds",acknowledged).apply();
                if(resumed&&!isFinishing())showRoundSaved(preview,first);else deferredRoundResult=preview;
            },()->{roundSaving=false;if(tab==2)updateRoundForm();});
        }catch(Exception error){roundSaving=false;notice("Cannot recover draft: "+error.getMessage());}
    }
    private void showRoundSaved(Map<String,Object> preview,boolean celebrate){
        LinearLayout display=column();pad(display,22,16,22,16);
        TextView check=text("✓  SAVED",25,"win".equals(str(preview,"result"))?GREEN:GOLD,true);check.setGravity(Gravity.CENTER);display.addView(check);
        gap(display,10);TextView result=text("win".equals(str(preview,"result"))?"Hukum Win":"Hukum Lose",16,"win".equals(str(preview,"result"))?GREEN:RED,true);result.setGravity(Gravity.CENTER);display.addView(result);
        if(celebrate)motion.staged(display);
        new MotionDialogBuilder(this,motion).setTitle("Round saved successfully").setMessage("Your round is saved. Share its image?").setView(display)
            .setNegativeButton("Not Now",null).setPositiveButton("Share Result",(d,w)->shareRound(preview)).show();
    }
    private void resetRound(){playing=null;partners.clear();hukum=null;outcome=null;bid=155;
        pendingRoundId=null;pendingRoundKey=null;pendingRoundTimestamp=null;pendingRoundJson=null;persistDraft();}
    private void addPlayerDialog() {
        EditText name=new EditText(this);
        name.setSingleLine(true);name.setHint("Player name");pad(name,20,10,20,10);
        new MotionDialogBuilder(this,motion).setTitle("Add Player")
                .setView(name)
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Add",(dlg,which)->{
                    String value=name.getText().toString().trim();
                    if(value.isEmpty()||value.length()>40){Toast.makeText(this,"Enter a player name (1–40 characters)",Toast.LENGTH_SHORT).show();return;}
                    for(Map<String,Object> p:players)if(str(p,"name").equalsIgnoreCase(value)){
                        Toast.makeText(this,"Player already exists",Toast.LENGTH_SHORT).show();return;
                    }
                    api.addDocument("players",FirebaseGateway.map("name",value,"active",true,"createdAt",FirebaseGateway.now()),"Player added");
                }).show();
    }
    private void editPlayerDialog(Map<String,Object> player) {
        EditText name=new EditText(this);
        name.setSingleLine(true);
        name.setText(str(player,"name"));
        name.setSelectAllOnFocus(true);
        pad(name,20,10,20,10);
        new MotionDialogBuilder(this,motion).setTitle("Edit Player Name")
                .setMessage("Names in past rounds will also change. Large histories use recoverable batches; retry interrupted changes from Settings.")
                .setView(name).setNegativeButton("Cancel",null)
                .setPositiveButton("Save",(d,w)->{
                    String newName=name.getText().toString().trim();
                    if(newName.isEmpty()||newName.equals(str(player,"name")))return;
                    if(newName.length()>40) {
                        Toast.makeText(this,"Name must be at most 40 characters",Toast.LENGTH_SHORT).show();return;
                    }
                    for(Map<String,Object> other:players) {
                        if(!id(other).equals(id(player))&&str(other,"name").equalsIgnoreCase(newName)) {
                            Toast.makeText(this,"Duplicate name",Toast.LENGTH_SHORT).show();return;
                        }
                    }
                    api.renamePlayer(id(player),newName,new ArrayList<>(rounds),str(player,"_updateTime"));
                }).show();
    }
    private void editSessionDialog(Map<String,Object> session) {
        EditText field=new EditText(this);
        field.setSingleLine(true);field.setText(str(session,"sessionName"));
        pad(field,18,10,18,10);
        new MotionDialogBuilder(this,motion).setTitle("Edit Session Name")
                .setView(field).setNegativeButton("Cancel",null)
                .setPositiveButton("Save",(d,w)->{
                    String value=field.getText().toString().trim();
                    if(value.isEmpty())return;
                    api.patchDocument("sessions",id(session),FirebaseGateway.map("sessionName",value),"Session updated",str(session,"_updateTime"),null);
                }).show();
    }
    private void confirmRemove(String collection,Map<String,Object> record,String noun) {
        if(!loggedIn)return;
        // Deliberately NEVER deletes without an explicit, named confirmation.
        new MotionDialogBuilder(this,motion)
                .setTitle("Permanently delete "+noun+"?")
                .setMessage("This will remove the selected "+noun+" from Firebase. A recovery backup is saved on this device before deletion. Export it from Settings.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Continue",(d,w)->new MotionDialogBuilder(this,motion)
                        .setTitle("Final confirmation")
                        .setMessage("Delete only this "+noun+"?\n"+("player".equals(noun)?str(record,"name"):
                                "session".equals(noun)?str(record,"sessionName"):when(record.get("timestamp"))))
                        .setNegativeButton("Keep",null)
                        .setPositiveButton("Delete",(dlg,which)->api.deleteDocument(collection,id(record),noun+" deleted",str(record,"_updateTime"))).show())
                .show();
    }
    private void showSessionSummary(Map<String,Object> session) {showSessionSummary(session,false);}
    private void showSessionSummary(Map<String,Object> session,boolean celebrate) {
        ScrollView scroller=new DialogUi.BoundedScroll(this);
        scroller.setFillViewport(false);
        LinearLayout display=column();pad(display,18,14,18,18);
        scroller.addView(display);
        display.addView(text("♛  "+str(session,"sessionName"),23,GOLD,true));
        gap(display,8);
        display.addView(text("Complete Session Overview",12,MUTED,true));
        gap(display,20);

        LinearLayout overview=panel();
        overview.addView(text("OVERVIEW",12,GOLD,true));gap(overview,12);
        line(overview,"Started",when(session.get("startTime")),WHITE);
        line(overview,"Ended",session.get("endTime")==null?"Active":when(session.get("endTime")),WHITE);
        line(overview,"Rounds",String.valueOf(num(session.get("totalRounds"))),GOLD);
        long length=session.get("duration") instanceof Number?
                ((Number)session.get("duration")).longValue():Math.max(0,time(session.get("endTime"))-time(session.get("startTime")));
        if(length>0)
            line(overview,"Duration",(length/3600000)+"h "+(length/60000)%60+"m",WHITE);
        display.addView(overview);gap(display,14);

        LinearLayout awards=panel();
        awards.addView(text("🏆  SESSION AWARDS",12,GOLD,true));gap(awards,12);
        Map<String,Object> mvp=map(session.get("mvp"));
        Map<String,Object> bestHukum=map(session.get("bestHukum"));
        Map<String,Object> top=map(session.get("biggestWin"));
        Map<String,Object> bottom=map(session.get("biggestLoss"));
        if(!mvp.isEmpty())line(awards,"★ MVP",str(mvp,"name")+"  "+(num(mvp.get("score"))>=0?"+":"")+str(mvp,"score"),num(mvp.get("score"))>=0?GREEN:RED);
        if(!bestHukum.isEmpty())line(awards,"♛ Best Hukum",
                str(bestHukum,"name")+" · "+str(bestHukum,"winRate")+"%",GOLD);
        if(!top.isEmpty()&&!"-".equals(str(top,"name")))
            line(awards,"↑ Biggest Win",str(top,"name")+" +"+str(top,"score"),GREEN);
        if(!bottom.isEmpty()&&!"-".equals(str(bottom,"name")))
            line(awards,"↓ Biggest Loss",str(bottom,"name")+" "+str(bottom,"score"),RED);
        String[][] labels={{"hukumKing","Hukum King"},{"hotStreak","Hot Streak"},
                {"unlucky","Unlucky"},{"sharpPartner","Sharp Partner"},
                {"mvp","Session MVP"},{"riskTaker","Risk Taker"}};
        Map<String,Object> badges=map(session.get("badges"));
        for(String[] label:labels){
            List<String> ids=strings(badges.get(label[0]));
            if(ids.isEmpty())continue;
            List<String> names=new ArrayList<>();
            for(String pid:ids)names.add(nameOf(pid));
            line(awards,label[1],android.text.TextUtils.join(", ",names),GOLD);
        }
        display.addView(awards);gap(display,14);
        LinearLayout leaderboard=panel();
        leaderboard.addView(text("🏅  SESSION LEADERBOARD",12,GOLD,true));gap(leaderboard,12);
        Object items=session.get("leaderboard");
        int place=0;
        if(items instanceof List)for(Object item:(List<?>)items) {
            Map<String,Object> record=map(item);
            int points=num(record.get("score"));
            place++;
            String name=str(record,"name");
            line(leaderboard,(place<=3?new String[]{"🥇","🥈","🥉"}[place-1]:"#"+place)+"  "+name,
                    (points>=0?"+":"")+points,points>0?GREEN:points<0?RED:MUTED);
        }
        if(place==0)leaderboard.addView(text("No rounds recorded",14,MUTED,false));
        display.addView(leaderboard);
        if(celebrate){
            Object rows=session.get("leaderboard");
            if(rows instanceof List&&!((List<?>)rows).isEmpty()){
                LinearLayout podium=panel();podium.addView(text("🏆  SESSION WINNER",13,GOLD,true));gap(podium,12);
                int count=Math.min(3,((List<?>)rows).size());
                for(int i=0;i<count;i++){
                    Map<String,Object> row=map(((List<?>)rows).get(i));
                    TextView winner=text((i==0?"♛  ":"#"+(i+1)+"  ")+str(row,"name")+"  "+MotionPolicy.points(num(row.get("score")),true),i==0?23:16,i==0?GOLD:WHITE,true);
                    pad(winner,0,8,0,8);podium.addView(winner);
                }
                display.addView(podium,0);motion.staged(podium);
            }
            if(motion.full())display.addView(button("Skip animation",false,()->motion.cancelAll()),1);
            motion.staged(display);motion.revealAwards(awards);
        }
        AlertDialog summaryDialog=new MotionDialogBuilder(this,motion).setTitle("SESSION SUMMARY")
                .setView(scroller)
                .setNeutralButton("Share Image",(dialog,which)->shareSession(session))
                .setPositiveButton("Close",null).create();
        summaryDialog.setOnShowListener(d->{if(celebrate&&resumed){motion.confetti((ViewGroup)summaryDialog.getWindow().getDecorView());}});
        summaryDialog.show();
    }
    private void confirmPlayerStatus(Map<String,Object> p,boolean activate) {
        new MotionDialogBuilder(this,motion)
                .setTitle(activate?"Restore Player?":"Bench Player?")
                .setMessage(str(p,"name")+(activate?" will return to the active table.":" will be temporarily benched. Historic scores will be kept."))
                .setNegativeButton("Cancel",null)
                .setPositiveButton(activate?"Restore":"Bench",(d,w)->api.patchDocument("players",id(p),
                        FirebaseGateway.map("active",activate),"Player status updated",str(p,"_updateTime"),null))
                .show();
    }
    private void loginDialog() {
        LinearLayout form=column();pad(form,22,6,22,6);
        EditText email=new EditText(this);email.setHint("Admin email");email.setSingleLine(true);
        email.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        form.addView(email,lp(-1,54));gap(form,12);
        EditText password=new EditText(this);password.setHint("Password");password.setSingleLine(true);
        password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        form.addView(password,lp(-1,54));
        final AlertDialog dialog=new MotionDialogBuilder(this,motion).setTitle("3 Patti PRO · Admin Login")
                .setMessage("Use the same email and password as your existing web app.")
                .setView(form).setNegativeButton("Cancel",null).setPositiveButton("Login",null).create();
        authDialog=dialog;
        dialog.setOnDismissListener(v->{if(authDialog==dialog)authDialog=null;});
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn->{
            String value=email.getText().toString().trim();
            if(!android.util.Patterns.EMAIL_ADDRESS.matcher(value).matches()){email.setError("Enter a valid email");motion.error(email);return;}
            if(password.length()==0){password.setError("Enter your password");motion.error(password);return;}
            if(!busy)api.signIn(value,password.getText().toString());
        }));dialog.show();
    }
    private void confirmLogout() {
        new MotionDialogBuilder(this,motion).setTitle("Logout?")
                .setMessage("Only this device's login is removed. Your Firebase game data stays unchanged.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Logout",(d,w)->api.signOut()).show();
    }
    private void startSessionDialog() {
        if(activeSession()!=null){Toast.makeText(this,"A session is already active",Toast.LENGTH_SHORT).show();return;}
        EditText input=new EditText(this);
        input.setSingleLine(true);
        input.setText("Session "+DateTimeFormatter.ofPattern("d MMM yyyy",Locale.ENGLISH).withZone(ZoneId.systemDefault()).format(Instant.now()));
        pad(input,18,10,18,10);
        new MotionDialogBuilder(this,motion).setTitle("Start Game Session")
                .setView(input).setNegativeButton("Cancel",null)
                .setPositiveButton("Start",(d,w)->{
                    String n=input.getText().toString().trim();
                    api.addDocument("sessions",FirebaseGateway.map(
                        "sessionName",n.isEmpty()?"Session "+DateTimeFormatter.ofPattern("d MMM yyyy",Locale.ENGLISH).withZone(ZoneId.systemDefault()).format(Instant.now()):n,
                        "startTime",FirebaseGateway.now(),"endTime",null,
                        "totalRounds",0,"leaderboard",new ArrayList<>(),
                        "mvp",null,"bestHukum",null,
                        "biggestWin",FirebaseGateway.map("name","-","score",0),
                        "biggestLoss",FirebaseGateway.map("name","-","score",0),
                        "streaks",new ArrayList<>(),"badges",new HashMap<>()),"Session started");
                }).show();
    }
    private void endSessionDialog() {
        Map<String,Object> active=activeSession();
        if(active==null||busy)return;
        long since=time(active.get("startTime"));
        int count=0;
        for(Map<String,Object> round:gameRounds()){
            long t=time(round.get("timestamp"));
            if(t>=since&&t<=System.currentTimeMillis())count++;
        }
        if(count==0){
            new MotionDialogBuilder(this,motion).setTitle("End empty session?")
                    .setMessage("No rounds were played in this session. End anyway? Your existing rounds remain unchanged.")
                    .setNegativeButton("Keep Active",null)
                    .setPositiveButton("End Session",(d,w)->api.patchDocument(
                            "sessions",id(active),FirebaseGateway.map("endTime",FirebaseGateway.now()),
                            "Empty session ended",str(active,"_updateTime"),null)).show();
            return;
        }
        new MotionDialogBuilder(this,motion).setTitle("End Session?")
                .setMessage(count+" rounds will be summarized. No player or round data will be deleted.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("End Session",(d,w)->completeSession(active)).show();
    }
    private void completeSession(Map<String,Object> s) {
        long start=time(s.get("startTime")), end=System.currentTimeMillis();
        if(start<=0){Toast.makeText(this,"Session start time missing",Toast.LENGTH_SHORT).show();return;}
        List<Map<String,Object>> r=new ArrayList<>();
        for(Map<String,Object> game:gameRounds()) {
            long t=time(game.get("timestamp"));
            if(t>=start&&t<=end)r.add(game);
        }
        Map<String,Object> update=SessionSummary.compute(players,r);
        update.put("endTime",FirebaseGateway.now());
        update.put("duration",end-start);
        api.patchDocument("sessions",id(s),update,"Session ended · Summary saved",str(s,"_updateTime"),()->{Map<String,Object> summary=new HashMap<>(s);summary.putAll(update);summary.put("endTime",Instant.ofEpochMilli(end).toString());if(resumed&&!isFinishing())showSessionSummary(summary,true);else deferredSessionSummary=summary;});
    }
    private List<NativeImageShare.RankRow> shareRankRows(List<Map<String,Object>> games) {
        List<NativeImageShare.RankRow> out=new ArrayList<>();
        for(Map<String,Object> p:rankedFor(games)){
            String pid=id(p);int w=0,h=0,part=0;
            for(Map<String,Object> round:games){
                if(!strings(round.get("playerIds")).contains(pid))continue;
                boolean isHukum=pid.equals(str(round,"hukumId"));
                boolean isPartner=strings(round.get("partnerIds")).contains(pid);
                if(isHukum)h++;
                if(isPartner)part++;
                if("win".equals(str(round,"result"))==(isHukum||isPartner))w++;
            }
            int n=played(pid,games);
            out.add(new NativeImageShare.RankRow(str(p,"name"),scoreOf(pid,games),n,w,n-w,h,part));
        }
        return out;
    }
    private void shareLeaderboard() {
        List<Map<String,Object>> selected=filteredRounds();
        if(selected.isEmpty()){
            Toast.makeText(this,"No ranks in this period",Toast.LENGTH_SHORT).show();
            return;
        }
        String label="custom".equals(rankFilter)?(customStart==0?"Any start":dateOnly(customStart))+" – "+(customEnd==0?"Any end":dateOnly(customEnd)):"all".equals(rankFilter)?"All Time":
                rankFilter.substring(0,1).toUpperCase(Locale.ROOT)+rankFilter.substring(1);
        NativeImageShare.leaderboard(this,label+"  ·  "+dateOnly(System.currentTimeMillis()),shareRankRows(selected));
    }
    private void shareRound(Map<String,Object> round) {
        List<NativeImageShare.ScoreRow> rows=new ArrayList<>();
        String hukumId=str(round,"hukumId");
        List<String> ids=strings(round.get("playerIds")),partners=strings(round.get("partnerIds"));
        Map<String,Object> changes=map(round.get("scoreChanges"));
        if(ids.isEmpty())ids=new ArrayList<>(changes.keySet());
        for(String pid:ids)if(changes.containsKey(pid)){
            String role=pid.equals(hukumId)?"hukum":partners.contains(pid)?"partner":"opponent";
            rows.add(new NativeImageShare.ScoreRow(nameOf(pid),role,num(changes.get(pid))));
        }
        NativeImageShare.round(this,when(round.get("timestamp")),str(round,"hukumName"),
                str(round,"result"),num(round.get("bid")),rows);
    }
    private void shareSession(Map<String,Object> session) {
        List<NativeImageShare.AwardRow> awards=new ArrayList<>();
        Map<String,Object> mvp=map(session.get("mvp"));
        if(!mvp.isEmpty())awards.add(new NativeImageShare.AwardRow("🏅 MVP",str(mvp,"name")+"  "+str(mvp,"score")));
        Map<String,Object> best=map(session.get("bestHukum"));
        if(!best.isEmpty())awards.add(new NativeImageShare.AwardRow("♛ Best Hukum",str(best,"name")+" "+str(best,"winRate")+"%"));
        Map<String,Object> biggest=map(session.get("biggestWin"));
        if(!biggest.isEmpty())awards.add(new NativeImageShare.AwardRow("📈 Biggest Win",str(biggest,"name")+"  +"+str(biggest,"score")));
        Map<String,Object> smallest=map(session.get("biggestLoss"));
        if(!smallest.isEmpty())awards.add(new NativeImageShare.AwardRow("📉 Biggest Loss",str(smallest,"name")+"  "+str(smallest,"score")));
        String[][] types={{"hukumKing","♛ Hukum King"},{"hotStreak","🔥 Hot Streak"},{"unlucky","💀 Unlucky"},
                {"sharpPartner","🎯 Sharp Partner"},{"mvp","🏆 Session MVP"},{"riskTaker","💰 Risk Taker"}};
        Map<String,Object> badges=map(session.get("badges"));
        for(String[] pair:types){
            List<String> names=new ArrayList<>();
            for(String pid:strings(badges.get(pair[0])))names.add(nameOf(pid));
            if(!names.isEmpty())awards.add(new NativeImageShare.AwardRow(pair[1],
                    android.text.TextUtils.join(", ",names)));
        }
        List<NativeImageShare.RankRow> ranking=new ArrayList<>();
        Object list=session.get("leaderboard");
        if(list instanceof List)for(Object item:(List<?>)list){
            Map<String,Object> p=map(item);
            ranking.add(new NativeImageShare.RankRow(str(p,"name"),num(p.get("score")),num(p.get("roundsPlayed")),0,0,0,0));
        }
        NativeImageShare.session(this,str(session,"sessionName"),
                when(session.get("startTime"))+" · "+duration(session.get("duration") instanceof Number?((Number)session.get("duration")).longValue():time(session.get("endTime"))-time(session.get("startTime")))+" · "+num(session.get("totalRounds"))+" rounds",awards,ranking);
    }
    @Override public void onBackPressed() {
        if(tab==5){tab=3;render(true);}
        else if(tab!=0){tab=0;render(true);}
        else new MotionDialogBuilder(this,motion).setTitle("Exit 3 Patti PRO?")
                .setNegativeButton("Stay",null).setPositiveButton("Exit",(d,w)->finish()).show();
    }
    private void searchPage(){
        title(content,"Search","Players, rounds and sessions across your saved table");
        EditText input=new EditText(this);input.setSingleLine(true);input.setHint("Name, bid, result or session");
        input.setTextColor(WHITE);input.setHintTextColor(MUTED);pad(input,14,10,14,10);input.setBackground(shape(PANEL,14,BORDER));
        content.addView(input,lp(-1,54));gap(content,16);
        LinearLayout results=column();content.addView(results);
        Runnable find=()->{
            results.animate().cancel();results.setAlpha(1);results.removeAllViews();String query=input.getText().toString().trim().toLowerCase(Locale.ROOT);
            if(query.isEmpty()){results.addView(text("Type to search your table",14,MUTED,false));return;}
            int count=0;
            for(Map<String,Object> player:players)if(str(player,"name").toLowerCase(Locale.ROOT).contains(query)){
                String pid=id(player);results.addView(button("♣  "+str(player,"name"),false,()->{profileId=pid;tab=5;render(true);}));gap(results,8);count++;
            }
            for(Map<String,Object> round:gameRounds()){
                String haystack=(str(round,"hukumName")+" "+strings(round.get("partnerNames"))+" "+strings(round.get("nonPartnerNames"))+" "+str(round,"bid")+" "+str(round,"result")+" "+when(round.get("timestamp"))).toLowerCase(Locale.ROOT);
                if(haystack.contains(query)){results.addView(button("♛  "+str(round,"hukumName")+" · "+str(round,"bid")+" · "+when(round.get("timestamp")),false,()->roundActions(round)));gap(results,8);count++;}
            }
            for(Map<String,Object> session:sessions)if(str(session,"sessionName").toLowerCase(Locale.ROOT).contains(query)){
                results.addView(button("◷  "+str(session,"sessionName"),false,()->showSessionSummary(session)));gap(results,8);count++;
            }
            results.addView(text(count+" results",12,MUTED,false));motion.enter(results,0);
        };
        refreshSearch=find;
        input.setText(searchQuery);input.setSelection(input.length());
        input.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){} public void onTextChanged(CharSequence s,int a,int b,int c){searchQuery=s.toString();find.run();}public void afterTextChanged(android.text.Editable e){}});
        find.run();
    }
    private void analyticsPage(){
        title(content,"Table Analytics","Scores, roles and trends from saved game rounds");dateFilterBar(content);
        List<Map<String,Object>> games=filteredRounds();
        if(games.isEmpty()){empty("No rounds in this date range.");return;}
        int hukumWins=0,bids=0,highest=0;
        for(Map<String,Object> round:games){if("win".equals(str(round,"result")))hukumWins++;int value=num(round.get("bid"));bids+=value;highest=Math.max(highest,value);}
        LinearLayout overview=panel();line(overview,"Rounds",String.valueOf(games.size()),WHITE);
        line(overview,"Average Bid",String.format(Locale.ROOT,"%.1f",(double)bids/games.size()),GOLD);
        line(overview,"Highest Bid",String.valueOf(highest),GOLD);
        line(overview,"Hukum Win Rate",Math.round(100.0*hukumWins/games.size())+"%",GREEN);
        content.addView(overview);gap(content,18);label(content,"PLAYER PERFORMANCE");
        List<Map<String,Object>> ranked=rankedFor(games);int maxScore=1;
        for(Map<String,Object> p:ranked)maxScore=Math.max(maxScore,Math.abs(scoreOf(id(p),games)));
        for(Map<String,Object> p:ranked){String pid=id(p);int score=scoreOf(pid,games),played=played(pid,games),won=wins(pid,games);
            LinearLayout card=panel();line(card,str(p,"name"),(score>0?"+":"")+score,score>=0?GREEN:RED).setTag(new MotionEngine.Anchor("analytics-score:"+pid+":"+rankFilter+":"+customStart+":"+customEnd,score,score>0));
            android.widget.ProgressBar bar=new android.widget.ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
            bar.setMax(maxScore);motion.progress(bar,Math.abs(score),screenEntry);bar.setProgressTintList(android.content.res.ColorStateList.valueOf(score>=0?GREEN:RED));
            card.addView(bar,lp(-1,12));gap(card,8);
            line(card,"Won / Played",won+" / "+played+" · "+(played==0?0:Math.round(100.0*won/played))+"%",MUTED);
            card.addView(text("Current streak "+winningStreak(pid,games)+" · Best positive streak "+RoundAnalytics.longestPositiveStreak(pid,games),12,MUTED,false));
            card.addView(new ScoreTrendView(this,pid,games),lp(-1,100));
            card.setOnClickListener(v->{profileId=pid;tab=5;render(true);});content.addView(card);gap(content,12);
        }
        content.addView(button("Share this leaderboard",true,this::shareLeaderboard));
    }
    private final class ScoreTrendView extends View{
        private float reveal=1f;
        final List<Integer> values=new ArrayList<>();final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        ScoreTrendView(android.content.Context c,String pid,List<Map<String,Object>> games){super(c);int total=0;values.add(total);
            for(int i=games.size()-1;i>=0;i--){Map<String,Object> round=games.get(i);if(!RoundAnalytics.played(round,pid))continue;total+=num(map(round.get("scoreChanges")).get(pid));values.add(total);}
            setContentDescription("Cumulative score trend, "+(values.size()-1)+" rounds, ending at "+total);
            motion.draw(this,screenEntry,value->{reveal=value;invalidate();});
        }
        @Override protected void onDraw(android.graphics.Canvas canvas){super.onDraw(canvas);int low=Collections.min(values),high=Collections.max(values);double range=Math.max(1,high-low);float left=dp(8),top=dp(12),w=getWidth()-dp(16),h=getHeight()-dp(30);
            paint.setColor(BORDER);paint.setStrokeWidth(dp(1));float zero=top+(float)(high/range)*h;canvas.drawLine(left,zero,left+w,zero,paint);
            paint.setColor(GOLD);paint.setStrokeWidth(dp(2));
            canvas.save();canvas.clipRect(left,top,left+w*reveal,top+h+dp(3));
            for(int i=1;i<values.size();i++){float x1=left+w*(i-1)/Math.max(1,values.size()-1),x2=left+w*i/Math.max(1,values.size()-1);float y1=top+(float)((high-values.get(i-1))/range)*h,y2=top+(float)((high-values.get(i))/range)*h;canvas.drawLine(x1,y1,x2,y2,paint);}
            canvas.restore();paint.setColor(MUTED);paint.setTextSize(dp(10));canvas.drawText("Cumulative score • oldest → newest",left,getHeight()-dp(2),paint);
        }
    }
    private int studioRoundVisible=20,studioSessionVisible=20;
    private void shareStudioPage(){
        title(content,"Share Studio","Preview, save and share your game cards");
        label(content,"CARD STYLE");
        String selected=settings.getString("share_style","classic");
        for(String style:new String[]{"classic","emerald","midnight"}){
            content.addView(button((style.equals(selected)?"●  ":"○  ")+style.toUpperCase(Locale.ROOT),style.equals(selected),()->{settings.edit().putString("share_style",style).apply();render(false);motion.enter(content,0);}));gap(content,8);
        }
        gap(content,12);dateFilterBar(content);
        content.addView(button("Preview Rankings",true,this::shareLeaderboard));gap(content,12);
        label(content,"ROUNDS IN SELECTED PERIOD");
        List<Map<String,Object>> shareRounds=filteredRounds();int roundCount=Math.min(studioRoundVisible,shareRounds.size());
        if(shareRounds.isEmpty())content.addView(text("No rounds in this period",13,MUTED,false));
        for(int i=0;i<roundCount;i++){Map<String,Object> round=shareRounds.get(i);content.addView(button(str(round,"hukumName")+" · Bid "+str(round,"bid")+" · "+when(round.get("timestamp")),false,()->shareRound(round)));gap(content,8);}
        if(roundCount<shareRounds.size()){content.addView(button("Show more rounds",false,()->{studioRoundVisible+=20;render(false);}));gap(content,8);}
        label(content,"SESSIONS");
        List<Map<String,Object>> ended=new ArrayList<>();for(Map<String,Object> session:sessions)if(session.get("endTime")!=null)ended.add(session);
        if(ended.isEmpty())content.addView(text("No completed sessions yet",13,MUTED,false));
        int sessionCount=Math.min(studioSessionVisible,ended.size());
        for(int i=0;i<sessionCount;i++){Map<String,Object> session=ended.get(i);content.addView(button(str(session,"sessionName"),false,()->shareSession(session)));gap(content,8);}
        if(sessionCount<ended.size())content.addView(button("Show more sessions",false,()->{studioSessionVisible+=20;render(false);}));
    }
    private byte[] pendingExport;
    private String exportedFingerprint=null,pendingExportFingerprint=null;
    private boolean shownPendingBulk;
    private void settingsPage(){
        shownPendingBulk=api!=null&&api.hasPendingBulkOperation();
        title(content,"Settings","Your preferences and data safety");
        LinearLayout info=panel();line(info,"Account",loggedIn?"Admin":"Visitor",loggedIn?GREEN:MUTED);line(info,"Connection",liveConnected?"Live":"Offline",liveConnected?GREEN:RED);content.addView(info);gap(content,14);
        content.addView(button(loggedIn?"Log out":"Admin login",false,loggedIn?this::confirmLogout:this::loginDialog));gap(content,14);
        android.widget.Switch compact=new android.widget.Switch(this);compact.setText("Compact round history");compact.setTextColor(WHITE);compact.setMinHeight(dp(56));compact.setChecked(settings.getBoolean("compact",false));compact.setOnCheckedChangeListener((v,b)->settings.edit().putBoolean("compact",b).apply());content.addView(compact);
        content.addView(button("Appearance · "+settings.getString("appearance","gold"),false,()->new MotionDialogBuilder(this,motion).setTitle("Appearance").setItems(new String[]{"Gold","Emerald","Midnight"},(d,w)->{int previous=BG;settings.edit().putString("appearance",new String[]{"gold","emerald","midnight"}[w]).apply();applyAppearance();buildShell();motion.theme(root,previous,BG);}).show()));gap(content,12);
        String motionLabel=settings.getString("motion","full");
        content.addView(button("Animations · "+motionLabel,false,()->new MotionDialogBuilder(this,motion).setTitle("Animations")
            .setSingleChoiceItems(new String[]{"Full","Reduced","Off"},"reduced".equals(motionLabel)?1:"off".equals(motionLabel)?2:0,(d,w)->{
                settings.edit().putString("motion",new String[]{"full","reduced","off"}[w]).apply();motion.cancelAll();d.dismiss();render(false);
            }).setNegativeButton("Cancel",null).show()));gap(content,8);
        content.addView(text("Follows Android animation settings. Reduced uses gentle fades; Off keeps changes immediate.",12,MUTED,false));gap(content,12);
        for(String key:new String[]{"haptics","keep_awake"}){
            android.widget.Switch toggle=new android.widget.Switch(this);toggle.setText(key.equals("haptics")?"Haptic feedback":"Keep screen awake during sessions");toggle.setTextColor(WHITE);toggle.setMinHeight(dp(56));toggle.setChecked(settings.getBoolean(key,false));toggle.setOnCheckedChangeListener((view,value)->settings.edit().putBoolean(key,value).apply());content.addView(toggle);
        }
        content.addView(button("Share Studio",false,()->{tab=8;render(true);}));gap(content,12);
        label(content,"DATA SAFETY");
        content.addView(button("Export JSON backup",true,this::exportBackup));gap(content,10);
        content.addView(text("Backups include player names, scores and sessions. Keep exported files private.",12,MUTED,false));gap(content,12);
        content.addView(button("Export last recovery backup",false,()->api.exportRecoveryBackup(bytes->{pendingExport=bytes;pendingExportFingerprint=null;Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("application/json");intent.putExtra(Intent.EXTRA_TITLE,"3patti-recovery.json");startActivityForResult(intent,700);})));gap(content,12);
        content.addView(button("Refresh live data",false,()->api.refresh()));gap(content,12);
        if(pendingRoundId!=null){content.addView(button("Retry unconfirmed round",true,this::retryPendingRound));gap(content,12);}
        content.addView(button("Discard local round draft",false,()->new MotionDialogBuilder(this,motion).setTitle("Discard draft?").setMessage(pendingRoundId==null?"Remove your unsaved form selections?":"This round has an unconfirmed save. Refresh and check History before discarding; it may already be saved.").setNegativeButton("Keep",null).setPositiveButton("Discard draft",(d,w)->{resetRound();notice("Local draft discarded");}).show()));
        if(loggedIn){
            if(shownPendingBulk){
                gap(content,12);content.addView(text("An interrupted data operation needs attention. Completed batches stay saved. Other changes are paused until you retry or stop it.",13,GOLD,false));
                gap(content,12);content.addView(button("Retry interrupted data operation",true,api::resumeBulkOperation));
                gap(content,12);content.addView(button("Stop interrupted operation",false,()->new MotionDialogBuilder(this,motion).setTitle("Stop remaining batches?").setMessage("Already completed batches stay in Firebase. Export the recovery backup before resolving a partial clear or restore. Stopping does not undo completed changes.").setNegativeButton("Keep pending",null).setPositiveButton("Stop remaining",(d,w)->api.abandonBulkOperation()).show()));
            }
            gap(content,12);content.addView(button("Restore backup / import HH legacy JSON",false,()->{Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("application/json");startActivityForResult(intent,702);}));
            gap(content,12);content.addView(button("Clear all rounds and sessions",false,this::clearHistoryDialog));
        }
        gap(content,20);content.addView(text("3 Patti PRO\nYour players, scores and game sessions",12,MUTED,false));
    }
    private void exportBackup(){
        api.exportBackup(bytes->{pendingExport=bytes;try{pendingExportFingerprint=BackupArchive.fingerprint(bytes,BuildConfig.USE_EMULATORS?"demo-three-patti":"patti-pro-a61d8");}catch(Exception error){notice("Backup validation failed");return;}Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("application/json");intent.putExtra(Intent.EXTRA_TITLE,"3patti-backup-"+java.time.LocalDate.now()+".json");startActivityForResult(intent,700);});
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==700&&result==RESULT_OK&&data!=null&&data.getData()!=null&&pendingExport!=null){
            final byte[] bytes=pendingExport;final android.net.Uri uri=data.getData();pendingExport=null;
            new Thread(()->{try(java.io.OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new java.io.IOException("Cannot open file");out.write(bytes);runOnUiThread(()->{exportedFingerprint=pendingExportFingerprint;notice("Backup saved");});}catch(Exception error){runOnUiThread(()->notice("Backup failed: "+error.getMessage()));}}).start();
        }
        if(request==702&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            final android.net.Uri uri=data.getData();new Thread(()->{
                try(java.io.InputStream in=getContentResolver().openInputStream(uri);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
                    if(in==null)throw new java.io.IOException("Cannot read backup");byte[] buffer=new byte[8192];int n;
                    while((n=in.read(buffer))!=-1){if(out.size()+n>16_000_000)throw new java.io.IOException("Backup exceeds 16 MB");out.write(buffer,0,n);}
                    byte[] raw=out.toByteArray();boolean legacy=BackupArchive.parseJson(raw).has("totalPlayers");
                    String project=BuildConfig.USE_EMULATORS?"demo-three-patti":"patti-pro-a61d8";
                    byte[] bytes=legacy?LegacyImport.convert(raw,project):raw;
                    List<org.json.JSONObject> docs=BackupArchive.validate(bytes,project);
                    runOnUiThread(()->new MotionDialogBuilder(this,motion).setTitle("Restore backup?").setMessage(docs.size()+" records. Only missing records will be created. Conflicts found before starting cancel the restore. Large restores use recoverable batches; if interrupted, retry from Settings. Existing records are never overwritten.").setNegativeButton("Cancel",null).setPositiveButton("Restore",(d,w)->api.restoreBackup(bytes,legacy)).show());
                }catch(Exception error){runOnUiThread(()->notice("Cannot restore: "+error.getMessage()));}
            }).start();
        }
        NativeImageShare.onActivityResult(this,request,result,data);
    }
    private void clearHistoryDialog(){
        if(exportedFingerprint==null){notice("Export and save a current JSON backup before clearing history.");return;}
        new MotionDialogBuilder(this,motion).setTitle("Clear all game data?").setMessage("This deletes all rounds and sessions. Players stay. Your exported backup can restore missing records. Large clears run in recoverable batches; if interrupted, completed batches stay deleted and Settings offers retry.")
            .setNegativeButton("Cancel",null).setPositiveButton("Continue",(d,w)->{
                EditText confirm=new EditText(this);confirm.setSingleLine(true);confirm.setHint("Type CONFIRM");
                AlertDialog dialog=new MotionDialogBuilder(this,motion).setTitle("Final confirmation").setView(confirm).setNegativeButton("Cancel",null).setPositiveButton("Clear All",null).create();
                dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn->{if(!"CONFIRM".equals(confirm.getText().toString().trim())){confirm.setError("Type CONFIRM exactly");motion.error(confirm);return;}dialog.dismiss();api.clearHistory(exportedFingerprint);exportedFingerprint=null;}));dialog.show();
            }).show();
    }
    private void applyAppearance(){
        String theme=settings.getString("appearance","gold");
        GOLD="emerald".equals(theme)?0xFF57D9AC:"midnight".equals(theme)?0xFFADB4FF:0xFFF6D365;
        BG="emerald".equals(theme)?0xFF071712:"midnight".equals(theme)?0xFF0B1025:0xFF0A0A0C;
        PANEL="emerald".equals(theme)?0xFF142820:"midnight".equals(theme)?0xFF171F3B:0xFF18181C;
        PANEL2="emerald".equals(theme)?0xFF203C30:"midnight".equals(theme)?0xFF232F50:0xFF222228;
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
    }
}
