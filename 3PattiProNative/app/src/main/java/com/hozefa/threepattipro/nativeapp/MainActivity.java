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
    private static final int BG = 0xFF0A0A0C, PANEL = 0xFF18181C, PANEL2 = 0xFF222228;
    private static final int BORDER = 0xFF35343A, WHITE = 0xFFFFFFFF, MUTED = 0xFF8A8D93;
    private static final int GOLD = 0xFFF6D365, GREEN = 0xFF2ECD71, RED = 0xFFE74C3C;
    private static final int BLUE = 0xFF4FACFE;
    private FirebaseGateway api;
    private final List<Map<String,Object>> players = new ArrayList<>();
    private final List<Map<String,Object>> rounds = new ArrayList<>();
    private final List<Map<String,Object>> sessions = new ArrayList<>();
    private LinearLayout root, content, navigation, toolbar;
    private ScrollView scroll;
    private boolean loggedIn=false, busy=false, hasData=false;
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
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        buildShell();
        api=new FirebaseGateway(this,this,getString(R.string.firebase_web_api_key));
        api.start();
    }
    @Override protected void onDestroy() {
        if (api != null) api.stop();
        super.onDestroy();
    }
    @Override public void dataUpdated(List<Map<String,Object>> p,List<Map<String,Object>> r,List<Map<String,Object>> s) {
        p.sort(Comparator.comparing(a -> str(a,"name").toLowerCase(Locale.ROOT)));
        r.sort((a,b) -> Long.compare(time(b.get("timestamp")),time(a.get("timestamp"))));
        s.sort((a,b) -> Long.compare(time(b.get("startTime")),time(a.get("startTime"))));
        // Preserve every touch target on empty/no-op Firestore polls.
        if(hasData && players.equals(p) && rounds.equals(r) && sessions.equals(s)) return;
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
        if(playing!=null) playing.retainAll(activeIds());
        // New rounds and sessions can arrive while the form is open. Update only
        // its score preview unless the player roster itself changed.
        if(tab==2 && !rosterChanged && roundBidView!=null && roundPreviewSection!=null)
            updateRoundForm();
        else render(false);
    }
    @Override public void authChanged(boolean value) {
        if(loggedIn==value) return;
        loggedIn=value;
        updateToolbar();
        if(!loggedIn && tab==2) tab=0;
        render(false);
    }
    @Override public void notice(String msg) {
        Toast.makeText(this,msg,Toast.LENGTH_LONG).show();
        // Toast messages must not reset the current view or move the page.
    }
    @Override public void busy(boolean b) {
        busy=b;
        updateToolbar();
        if(tab==2&&roundSaveView!=null&&roundPreviewSection!=null&&roundHintView!=null)
            updateRoundForm();
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
            boolean isWin="win".equals(str(r,"result"));
            boolean side=idIsHukum(r,pid)||strings(r.get("partnerIds")).contains(pid);
            if(isWin==side)n++;
        }
        return n;
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
        b.setBackground(primary?gradient(0xFFF6D365,0xFFFDA085,14):shape(PANEL2,14,BORDER));
        b.setForeground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x40FFFFFF),null,null));
        b.setClickable(true);
        b.setFocusable(true);
        b.setOnClickListener(v->{if(action!=null) action.run();});
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
        t.setOnClickListener(v->{if(action!=null)action.run();});
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
    private void line(LinearLayout c,String left,String right,int rightColor) {
        LinearLayout r=row();
        TextView l=text(left,13,MUTED,false);
        r.addView(l,new LinearLayout.LayoutParams(0,dp(30),1));
        r.addView(text(right,14,rightColor,true));
        c.addView(r);
    }
    private void buildShell() {
        root=column();root.setBackgroundColor(BG);
        toolbar=row();pad(toolbar,18,15,18,15);toolbar.setBackgroundColor(BG);
        root.addView(toolbar,lp(-1,66));updateToolbar();
        View bar=new View(this);bar.setBackgroundColor(BORDER);root.addView(bar,lp(-1,1));
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
        scroll.setSmoothScrollingEnabled(false);
        content=column();pad(content,18,20,18,35);scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        navigation=row();pad(navigation,7,7,7,7);
        navigation.setMinimumHeight(dp(64));
        navigation.setBackgroundColor(0xFF121215);
        root.addView(navigation,lp(-1,66));
        setContentView(root);
        render(true);
    }
    private void updateToolbar() {
        if(toolbar==null)return;
        toolbar.removeAllViews();
        LinearLayout heading=column();
        TextView appName=text("♠  3 PATTI PRO",21,GOLD,true);
        heading.addView(appName);
        gap(heading,5);
        heading.addView(text("SCORE TRACKER",9,MUTED,true));
        toolbar.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        TextView status=text(busy?"SAVING…":loggedIn?"ADMIN":"LOGIN",11,loggedIn?GREEN:GOLD,true);
        status.setGravity(Gravity.CENTER);pad(status,12,10,12,10);
        status.setBackground(shape(PANEL2,12,BORDER));
        status.setOnClickListener(v->{if(busy)return;if(loggedIn)confirmLogout();else loginDialog();});
        toolbar.addView(status);
        TextView refresh=text("↻",23,GOLD,true);
        refresh.setGravity(Gravity.CENTER);
        refresh.setBackground(shape(PANEL2,12,BORDER));
        refresh.setMinWidth(dp(48));refresh.setMinHeight(dp(48));
        refresh.setOnClickListener(v->{
            if(api!=null) {
                api.refresh();
                Toast.makeText(this,"Refreshing scores...",Toast.LENGTH_SHORT).show();
            }
        });
        LinearLayout.LayoutParams rp=lp(48,48);rp.leftMargin=dp(8);
        toolbar.addView(refresh,rp);
    }
    private void render(boolean reset) {
        if(content==null||navigation==null)return;
        int prev=reset?0:scroll.getScrollY();
        content.removeAllViews();
        navigation.removeAllViews();
        if(!hasData) {
            gap(content,80);content.addView(text("♠  Loading your table…",20,GOLD,true));
            gap(content,16);content.addView(text("Connecting to your existing Firebase project",13,MUTED,false));
        } else {
            switch(tab) {
                case 0:home();break;
                case 1:playersPage();break;
                case 2:roundPage();break;
                case 3:ranksPage();break;
                case 4:historyPage();break;
                case 5:profilePage();break;
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
            item.addView(sym);gap(item,2);item.addView(text);
            if(tab==i)item.setBackground(shape(PANEL2,12,0));
            navigation.addView(item,new LinearLayout.LayoutParams(0,-1,1));
            item.setOnClickListener(v->{
                if(destination==2&&!loggedIn){loginDialog();return;}
                profileId=null;
                tab=destination;render(true);
            });
        }
        scroll.post(()->scroll.scrollTo(0,prev));
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
            sessionBar.addView(text(status,13,session==null?MUTED:GREEN,true));
            if(session!=null) {
                gap(sessionBar,7);
                sessionBar.addView(text("Started "+when(session.get("startTime")),12,MUTED,false));
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
        tile.addView(number);
        return tile;
    }
    private void empty(String message) {
        LinearLayout p=panel();
        p.addView(text(message,14,MUTED,false));
        content.addView(p);gap(content,12);
    }
    private void playersPage() {
        title(content,"Players","Active and benched members of your table");
        if(loggedIn){
            content.addView(button("＋  Add a Player",true,this::addPlayerDialog));gap(content,18);
        }
        if(players.isEmpty()){empty("No players yet. Add the first player as admin.");return;}
        for(Map<String,Object> p:players) {
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
            top.addView(text((score>=0?"+":"")+score,17,score>=0?GREEN:RED,true));
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
        new AlertDialog.Builder(this)
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
        long now=System.currentTimeMillis();
        java.util.Calendar start=java.util.Calendar.getInstance();
        start.set(java.util.Calendar.HOUR_OF_DAY,0);
        start.set(java.util.Calendar.MINUTE,0);
        start.set(java.util.Calendar.SECOND,0);
        start.set(java.util.Calendar.MILLISECOND,0);
        long min=0,max=Long.MAX_VALUE;
        switch(rankFilter) {
            case "today":min=start.getTimeInMillis();break;
            case "yesterday":max=start.getTimeInMillis();start.add(java.util.Calendar.DAY_OF_YEAR,-1);min=start.getTimeInMillis();break;
            case "week": {
                int day=start.get(java.util.Calendar.DAY_OF_WEEK);
                start.add(java.util.Calendar.DAY_OF_YEAR,-(day-java.util.Calendar.SUNDAY));
                min=start.getTimeInMillis();break;
            }
            case "month":start.set(java.util.Calendar.DAY_OF_MONTH,1);min=start.getTimeInMillis();break;
            case "year":start.set(java.util.Calendar.DAY_OF_YEAR,1);min=start.getTimeInMillis();break;
            case "custom":min=customStart;max=customEnd>0?customEnd+86400000L:Long.MAX_VALUE;break;
            default:return gameRounds();
        }
        final long from=min,to=max;
        List<Map<String,Object>> result=new ArrayList<>();
        for(Map<String,Object> r:gameRounds()) {
            long t=time(r.get("timestamp"));
            if(t>=from&&t<to)result.add(r);
        }
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
                rankFilter=opt[0];render(false);
            });
            LinearLayout.LayoutParams one=lp(-2,46);one.rightMargin=dp(8);
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
        android.app.DatePickerDialog dialog=new android.app.DatePickerDialog(this,(picker,year,month,day)->{
            java.util.Calendar picked=java.util.Calendar.getInstance();
            picked.clear();picked.set(year,month,day);
            if(isStart)customStart=picked.getTimeInMillis();
            else customEnd=picked.getTimeInMillis();
            render(false);
        },c.get(java.util.Calendar.YEAR),c.get(java.util.Calendar.MONTH),c.get(java.util.Calendar.DAY_OF_MONTH));
        dialog.show();
    }

    private void profilePage() {
        Map<String,Object> target=null;
        for(Map<String,Object> p:players)if(id(p).equals(profileId)){target=p;break;}
        if(target==null){title(content,"Profile unavailable","Player not found");return;}
        String pid=id(target);
        TextView back=button("←  Back to Ranks",false,()->{tab=3;render(true);});
        content.addView(back);gap(content,16);
        title(content,"♛ "+str(target,"name"),"Player profile · Detailed statistics");
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
            boolean win="win".equals(str(r,"result"))==(roleH||roleP);
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
        line(stats,"Rank",String.valueOf(Math.max(1,rankedFor(filtered).indexOf(target)+1)),GOLD);
        line(stats,"Total Score",(totalScore>=0?"+":"")+totalScore,totalScore>=0?GREEN:RED);
        line(stats,"Rounds",String.valueOf(individual.size()),WHITE);
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
            content.addView(button("Best round: +"+max+"  ·  Tap for details",false,()->roundDetails(bestR)));
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
        StringBuilder out=new StringBuilder();
        int total=0;
        for(Map<String,Object> r:source) {
            String actual=pid.equals(str(r,"hukumId"))?"hukum":
                    strings(r.get("partnerIds")).contains(pid)?"partner":"nonpartner";
            if(!role.equals(actual))continue;
            total++;
            if(total<=30)out.append(when(r.get("timestamp"))).append(" · ")
                    .append(str(r,"hukumName")).append(" · ")
                    .append(num(map(r.get("scoreChanges")).get(pid))).append("\n");
        }
        new AlertDialog.Builder(this).setTitle(role.toUpperCase(Locale.ROOT)+" · "+total+" rounds")
                .setMessage(total==0?"No rounds":out.toString())
                .setPositiveButton("Close",null).show();
    }
    private void roundDetails(Map<String,Object> round) {
        StringBuilder body=new StringBuilder("Hukum: "+str(round,"hukumName")+
                "\nBid: "+str(round,"bid")+"\nResult: "+str(round,"result")+
                "\nDate: "+when(round.get("timestamp"))+"\n\nSCORE CHANGES\n");
        for(Map.Entry<String,Object> e:map(round.get("scoreChanges")).entrySet()) {
            body.append(nameOf(e.getKey())).append("  ")
                    .append(num(e.getValue())>0?"+":"").append(e.getValue()).append("\n");
        }
        new AlertDialog.Builder(this).setTitle("Round details").setMessage(body.toString())
                .setPositiveButton("Close",null).show();
    }

    private void addRankCard(Map<String,Object> p,int position) {
        LinearLayout card=panel();
        LinearLayout r=row();
        TextView rank=text(position==0?"♛":(position+1)+".",22,position==0?GOLD:MUTED,true);
        rank.setGravity(Gravity.CENTER);r.addView(rank,lp(45,42));
        LinearLayout middle=column();
        middle.addView(text(str(p,"name"),16,WHITE,true));gap(middle,5);
        List<Map<String,Object>> source=(tab==3 && currentRankData!=null)?currentRankData:gameRounds();
        middle.addView(text(played(id(p),source)+" rounds played",12,MUTED,false));
        r.addView(middle,new LinearLayout.LayoutParams(0,-2,1));
        int score=scoreOf(id(p),source);
        r.addView(text((score>=0?"+":"")+score,19,score>=0?GREEN:RED,true));
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
        TextView roundTab=chip("Game Rounds",historyTab==0,true,()->{historyTab=0;render(false);});
        TextView sessionTab=chip("Sessions",historyTab==1,true,()->{historyTab=1;render(false);});
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
        new AlertDialog.Builder(this)
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
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        head.addView(title,new LinearLayout.LayoutParams(0,dp(36),1));
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
        card.setClickable(true);card.setFocusable(true);
        card.setForeground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x25FFFFFF),null,null));
        card.setOnClickListener(v->roundActions(r));
        content.addView(card);gap(content,10);
    }
    private void roundActions(Map<String,Object> round) {
        AlertDialog.Builder dialog=new AlertDialog.Builder(this)
                .setTitle("Round · "+when(round.get("timestamp")))
                .setMessage("Hukum: "+str(round,"hukumName")+
                        "\nBid: "+str(round,"bid")+
                        "\nResult: "+str(round,"result")+
                        "\nPartners: "+android.text.TextUtils.join(", ",strings(round.get("partnerNames")))+
                        "\nNon-partners: "+android.text.TextUtils.join(", ",strings(round.get("nonPartnerNames"))))
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
                if(roundSaving||busy)return;
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
            if(roundSaving||busy)return;
            bid=Math.max(155,bid-5);updateRoundForm();
        }),new LinearLayout.LayoutParams(0,dp(50),1));
        TextView label=text("155 – 250",13,MUTED,false);
        label.setGravity(Gravity.CENTER);
        stepper.addView(label,new LinearLayout.LayoutParams(0,dp(50),1));
        stepper.addView(button("+ 5",false,()->{
            if(roundSaving||busy)return;
            bid=Math.min(250,bid+5);updateRoundForm();
        }),new LinearLayout.LayoutParams(0,dp(50),1));
        bidding.addView(stepper);content.addView(bidding);gap(content,18);

        roundHukumSection=column();
        label(roundHukumSection,"03  •  Choose Hukum");
        LinearLayout hukumBox=panel();
        for(Map<String,Object> player:active){
            String pid=id(player);
            TextView pick=chip(str(player,"name"),false,true,()->{
                if(roundSaving||busy)return;
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
                if(roundSaving||busy)return;
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
            if(roundSaving||busy)return;
            outcome="win";updateRoundForm();
        });
        roundLoseView=chip("✕   Hukum Lose",false,false,()->{
            if(roundSaving||busy)return;
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
        view.setText(label);
        view.setTextColor(selected?(gold?GOLD:GREEN):WHITE);
        view.setTypeface(Typeface.create("sans-serif",selected?Typeface.BOLD:Typeface.NORMAL));
        view.setBackground(shape(selected?(gold?0xFF60492E:0xFF1A4B43):PANEL2,
                12,selected?(gold?GOLD:GREEN):BORDER));
    }
    private void updateRoundForm(){
        if(roundBidView==null||tab!=2)return;
        roundBidView.setText("Bid  "+bid+"   ▾");
        for(Map.Entry<String,TextView> item:roundPlayingButtons.entrySet()){
            String pid=item.getKey();
            updateRoundChip(item.getValue(),(playing.contains(pid)?"✓   ":"○   ")+nameOf(pid),
                    playing.contains(pid),false);
        }
        boolean enough=playing!=null && playing.size()>=3;
        if(!enough) {hukum=null;partners.clear();}
        roundHukumSection.setVisibility(enough?View.VISIBLE:View.GONE);
        boolean hasHukum=enough&&hukum!=null&&playing.contains(hukum);
        roundPartnerSection.setVisibility(hasHukum?View.VISIBLE:View.GONE);
        roundResultSection.setVisibility(hasHukum?View.VISIBLE:View.GONE);
        for(Map.Entry<String,TextView> item:roundHukumButtons.entrySet()){
            String pid=item.getKey();
            TextView control=item.getValue();
            control.setVisibility(playing.contains(pid)?View.VISIBLE:View.GONE);
            updateRoundChip(control,(pid.equals(hukum)?"♛   ":"○   ")+nameOf(pid),
                    pid.equals(hukum),true);
        }
        for(Map.Entry<String,TextView> item:roundPartnerButtons.entrySet()){
            String pid=item.getKey();
            TextView control=item.getValue();
            control.setVisibility(hasHukum&&playing.contains(pid)&&!pid.equals(hukum)?View.VISIBLE:View.GONE);
            updateRoundChip(control,(partners.contains(pid)?"✓   ":"○   ")+nameOf(pid),
                    partners.contains(pid),false);
        }
        updateRoundChip(roundWinView,"✓   Hukum Win","win".equals(outcome),false);
        updateRoundChip(roundLoseView,"✕   Hukum Lose","loss".equals(outcome),false);
        roundPreviewSection.removeViews(1,Math.max(0,roundPreviewSection.getChildCount()-1));
        boolean ready=validRound();
        roundPreviewSection.setVisibility(ready?View.VISIBLE:View.GONE);
        if(ready){
            LinearLayout card=panel();
            Map<String,Integer> preview=scoreChanges();
            for(Map<String,Object> player:activePlayers()){
                String pid=id(player);
                if(!playing.contains(pid))continue;
                int delta=preview.getOrDefault(pid,0);
                String role=pid.equals(hukum)?"♛ ":partners.contains(pid)?"◈ ":"○ ";
                line(card,role+str(player,"name"),delta==0?"—":(delta>0?"+":"")+delta,
                        delta>0?GREEN:delta<0?RED:MUTED);
            }
            roundPreviewSection.addView(card);
        }
        String hint=!enough?"Select at least 3 players.":!hasHukum?"Choose Hukum to continue.":
                partners.isEmpty()?"Choose at least one partner.":
                playing.size()-partners.size()-1<1?"At least one non-partner must be playing.":
                outcome==null?"Select Hukum Win or Lose.":"Preview verified. Check the scores before saving.";
        roundHintView.setText(roundSaving?"Saving round to Firebase…":hint);
        roundSaveView.setVisibility(ready?View.VISIBLE:View.GONE);
        roundSaveView.setEnabled(ready&&!roundSaving&&!busy);
        roundSaveView.setAlpha(roundSaving||busy?0.55f:1f);
        roundSaveView.setText(roundSaving?"Saving…":"✓  Save Round to Firebase");
    }
    private void chooseBid(){
        if(roundSaving||busy)return;
        String[] values=new String[20];
        for(int i=0;i<values.length;i++)values[i]=String.valueOf(155+i*5);
        new AlertDialog.Builder(this)
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
        if(!validRound()){Toast.makeText(this,"Complete the round details first",Toast.LENGTH_SHORT).show();return;}
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
        new AlertDialog.Builder(this)
                .setTitle("Save this round?")
                .setMessage("Bid "+bid+" · "+nameOf(hukum)+" · "+("win".equals(outcome)?"Hukum win":"Hukum lose")+
                        "\n\nThis will add one new round to your existing Firebase data.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Save Round",(d,w)->{
                    // Keep the user's form intact until the server confirms successful write.
                    final Map<String,Object> preview=new HashMap<>(body);
                    preview.put("timestamp",java.time.Instant.now().toString());
                    roundSaving=true;
                    updateRoundForm();
                    api.addDocument("rounds",body,"Round saved successfully",()->{
                        roundSaving=false;
                        resetRound();
                        tab=0;
                        render(true);
                        new AlertDialog.Builder(this)
                            .setTitle("Round saved successfully")
                            .setMessage("Your round is saved in your existing Firebase database. Share its premium image?")
                            .setNegativeButton("Not Now",null)
                            .setPositiveButton("Share Result",(dlg,index)->shareRound(preview))
                            .show();
                    },()->{
                        roundSaving=false;
                        updateRoundForm();
                    });
                }).show();
    }
    private void resetRound(){playing=null;partners.clear();hukum=null;outcome=null;bid=155;}
    private void addPlayerDialog() {
        EditText name=new EditText(this);
        name.setSingleLine(true);name.setHint("Player name");pad(name,20,10,20,10);
        new AlertDialog.Builder(this).setTitle("Add Player")
                .setView(name)
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Add",(dlg,which)->{
                    String value=name.getText().toString().trim();
                    if(value.isEmpty()){Toast.makeText(this,"Enter a player name",Toast.LENGTH_SHORT).show();return;}
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
        new AlertDialog.Builder(this).setTitle("Edit Player Name")
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
                    api.renamePlayer(id(player),newName,new ArrayList<>(rounds));
                }).show();
    }
    private void editSessionDialog(Map<String,Object> session) {
        EditText field=new EditText(this);
        field.setSingleLine(true);field.setText(str(session,"sessionName"));
        pad(field,18,10,18,10);
        new AlertDialog.Builder(this).setTitle("Edit Session Name")
                .setView(field).setNegativeButton("Cancel",null)
                .setPositiveButton("Save",(d,w)->{
                    String value=field.getText().toString().trim();
                    if(value.isEmpty())return;
                    api.patchDocument("sessions",id(session),FirebaseGateway.map("sessionName",value),"Session updated");
                }).show();
    }
    private void confirmRemove(String collection,Map<String,Object> record,String noun) {
        if(!loggedIn)return;
        // Deliberately NEVER deletes without an explicit, named confirmation.
        new AlertDialog.Builder(this)
                .setTitle("Permanently delete "+noun+"?")
                .setMessage("This will remove the selected "+noun+" from Firebase. It cannot be undone. Other existing records stay unchanged.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Continue",(d,w)->new AlertDialog.Builder(this)
                        .setTitle("Final confirmation")
                        .setMessage("Delete only this "+noun+"?\n"+("player".equals(noun)?str(record,"name"):
                                "session".equals(noun)?str(record,"sessionName"):when(record.get("timestamp"))))
                        .setNegativeButton("Keep",null)
                        .setPositiveButton("Delete",(dlg,which)->api.deleteDocument(collection,id(record),noun+" deleted")).show())
                .show();
    }
    private void showSessionSummary(Map<String,Object> session) {
        StringBuilder message=new StringBuilder();
        message.append("Date: ").append(when(session.get("startTime")))
                .append("\nEnded: ").append(session.get("endTime")==null?"Active":when(session.get("endTime")))
                .append("\nRounds: ").append(num(session.get("totalRounds")));
        long dur=session.get("duration") instanceof Number?
                ((Number)session.get("duration")).longValue():0L;
        if(dur>0)message.append("\nDuration: ").append(dur/3600000).append("h ").append((dur/60000)%60).append("m");
        Map<String,Object> mvp=map(session.get("mvp"));
        if(!mvp.isEmpty())message.append("\nMVP: ").append(str(mvp,"name")).append(" (").append(str(mvp,"score")).append(")");
        Map<String,Object> best=map(session.get("bestHukum"));
        if(!best.isEmpty())message.append("\nBest Hukum: ").append(str(best,"name")).append(" · ").append(num(best.get("winRate"))).append("%");
        Map<String,Object> biggest=map(session.get("biggestWin"));
        if(!biggest.isEmpty())message.append("\nBiggest Win: ").append(str(biggest,"name")).append(" +").append(str(biggest,"score"));
        Map<String,Object> smallest=map(session.get("biggestLoss"));
        if(!smallest.isEmpty())message.append("\nBiggest Loss: ").append(str(smallest,"name")).append(" ").append(str(smallest,"score"));
        message.append("\n\nLEADERBOARD\n");
        Object board=session.get("leaderboard");
        if(board instanceof List)for(Object item:(List<?>)board) {
            Map<String,Object> row=map(item);
            message.append(str(row,"rank")).append(". ").append(str(row,"name"))
                    .append("  ").append(str(row,"score")).append("\n");
        }
        Map<String,Object> badges=map(session.get("badges"));
        for(Map.Entry<String,Object> entry:badges.entrySet()){
            List<String> ids=strings(entry.getValue());
            if(ids.isEmpty())continue;
            message.append("\n").append(entry.getKey()).append(": ");
            for(String playerId:ids)message.append(nameOf(playerId)).append(" ");
        }
        String detail=message.toString();
        new AlertDialog.Builder(this).setTitle(str(session,"sessionName"))
                .setMessage(detail)
                .setNeutralButton("Share Image", (d,w)->shareSession(session))
                .setPositiveButton("Close",null).show();
    }
    private void confirmPlayerStatus(Map<String,Object> p,boolean activate) {
        new AlertDialog.Builder(this)
                .setTitle(activate?"Restore Player?":"Bench Player?")
                .setMessage(str(p,"name")+(activate?" will return to the active table.":" will be temporarily benched. Historic scores will be kept."))
                .setNegativeButton("Cancel",null)
                .setPositiveButton(activate?"Restore":"Bench",(d,w)->api.patchDocument("players",id(p),
                        FirebaseGateway.map("active",activate),"Player status updated"))
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
        new AlertDialog.Builder(this).setTitle("3 Patti PRO · Admin Login")
                .setMessage("Use the same email and password as your existing web app.")
                .setView(form)
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Login",(d,w)->api.signIn(email.getText().toString().trim(),password.getText().toString()))
                .show();
    }
    private void confirmLogout() {
        new AlertDialog.Builder(this).setTitle("Logout?")
                .setMessage("Only this device's login is removed. Your Firebase game data stays unchanged.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Logout",(d,w)->api.signOut()).show();
    }
    private void startSessionDialog() {
        if(activeSession()!=null){Toast.makeText(this,"A session is already active",Toast.LENGTH_SHORT).show();return;}
        EditText input=new EditText(this);
        input.setSingleLine(true);
        input.setText("Session "+DateTimeFormatter.ofPattern("dd MMM yyyy").withZone(ZoneId.systemDefault()).format(Instant.now()));
        pad(input,18,10,18,10);
        new AlertDialog.Builder(this).setTitle("Start Game Session")
                .setView(input).setNegativeButton("Cancel",null)
                .setPositiveButton("Start",(d,w)->{
                    String n=input.getText().toString().trim();
                    api.addDocument("sessions",FirebaseGateway.map(
                        "sessionName",n.isEmpty()?"Game Session":n,
                        "startTime",FirebaseGateway.now(),"endTime",null,
                        "totalRounds",0,"leaderboard",new ArrayList<>(),
                        "mvp",null,"bestHukum",null,
                        "biggestWin",FirebaseGateway.map("name","-","score",0),
                        "biggestLoss",FirebaseGateway.map("name","-","score",0),
                        "streaks",new ArrayList<>(),"badges",new HashMap<>()),"Session started");
                }).show();
    }
    private void endSessionDialog() {
        Map<String,Object> s=activeSession();if(s==null)return;
        new AlertDialog.Builder(this).setTitle("End Session?")
                .setMessage("Final scores will be saved to this session. No player or round data will be deleted.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("End Session",(d,w)->completeSession(s)).show();
    }
    @SuppressWarnings("unchecked")
    private List<String> stringsMutable(Map<String,Object> owner,String key) {
        return (List<String>)owner.get(key);
    }
    private void completeSession(Map<String,Object> s) {
        long start=time(s.get("startTime")), end=System.currentTimeMillis();
        if(start<=0){Toast.makeText(this,"Session start time missing",Toast.LENGTH_SHORT).show();return;}
        List<Map<String,Object>> r=new ArrayList<>();
        for(Map<String,Object> game:gameRounds()) {
            long t=time(game.get("timestamp"));
            if(t>=start&&t<=end)r.add(game);
        }
        List<Map<String,Object>> ranked=new ArrayList<>(players);
        ranked.removeIf(p->played(id(p),r)==0);
        ranked.sort((a,b)->Integer.compare(scoreOf(id(b),r),scoreOf(id(a),r)));
        List<Map<String,Object>> leaderboard=new ArrayList<>();
        int place=0;
        for(Map<String,Object> p:ranked){
            place++;
            leaderboard.add(FirebaseGateway.map("rank",place,"id",id(p),"name",str(p,"name"),
                    "score",scoreOf(id(p),r),"roundsPlayed",played(id(p),r)));
        }
        Map<String,Object> mvp=ranked.isEmpty()?null:FirebaseGateway.map(
                "name",str(ranked.get(0),"name"),"score",scoreOf(id(ranked.get(0)),r));
        String winningName="-",losingName="-";
        int best=0,worst=0;
        for(Map<String,Object> round:r)for(Map.Entry<String,Object> score:map(round.get("scoreChanges")).entrySet()){
            int v=num(score.getValue());
            if(v>best){best=v;winningName=nameOf(score.getKey());}
            if(v<worst){worst=v;losingName=nameOf(score.getKey());}
        }
        Map<String,Object> bestHukum=null;
        double topWinRate=-1;int topHukumRounds=-1;
        Map<String,Integer> hukumCount=new HashMap<>(),hukumWins=new HashMap<>(),playerLosses=new HashMap<>();
        int maxBid=0;
        for(Map<String,Object> round:r){
            String pid=str(round,"hukumId");
            hukumCount.merge(pid,1,Integer::sum);
            if("win".equals(str(round,"result")))hukumWins.merge(pid,1,Integer::sum);
            maxBid=Math.max(maxBid,num(round.get("bid")));
            for(String playerId:strings(round.get("playerIds"))) {
                boolean winsThisRound="win".equals(str(round,"result"))==
                        (pid.equals(playerId)||strings(round.get("partnerIds")).contains(playerId));
                if(!winsThisRound)playerLosses.merge(playerId,1,Integer::sum);
            }
        }
        for(Map<String,Object> p:players){
            String pid=id(p);int n=hukumCount.getOrDefault(pid,0),w=hukumWins.getOrDefault(pid,0);
            if(n==0)continue;
            double rate=(double)w/n;
            if(rate>topWinRate||(rate==topWinRate&&n>topHukumRounds)){
                topWinRate=rate;topHukumRounds=n;
                bestHukum=FirebaseGateway.map("name",str(p,"name"),"winRate",Math.round(rate*100),
                        "wins",w,"total",n);
            }
        }
        Map<String,Object> badges=new LinkedHashMap<>();
        for(String key:new String[]{"hukumKing","hotStreak","unlucky","sharpPartner","mvp","riskTaker"})
            badges.put(key,new ArrayList<String>());
        int maxHukum=0,maxLosses=0;
        for(String playerId:hukumCount.keySet())maxHukum=Math.max(maxHukum,hukumCount.get(playerId));
        for(String playerId:playerLosses.keySet())maxLosses=Math.max(maxLosses,playerLosses.get(playerId));
        for(Map<String,Object> p:players){
            String pid=id(p);
            if(maxHukum>0&&hukumCount.getOrDefault(pid,0)==maxHukum)stringsMutable(badges,"hukumKing").add(pid);
            if(maxLosses>0&&playerLosses.getOrDefault(pid,0)==maxLosses)stringsMutable(badges,"unlucky").add(pid);
            int partnerCount=0,partnerWins=0,currentStreak=0,topStreak=0;
            List<Map<String,Object>> chron=new ArrayList<>(r);
            Collections.reverse(chron);
            for(Map<String,Object> round:chron){
                boolean present=strings(round.get("playerIds")).contains(pid);
                if(!present)continue;
                boolean isPartner=strings(round.get("partnerIds")).contains(pid);
                if(isPartner){partnerCount++;if("win".equals(str(round,"result")))partnerWins++;}
                int score=num(map(round.get("scoreChanges")).get(pid));
                if(score>0){currentStreak++;topStreak=Math.max(topStreak,currentStreak);}
                else if(score<0)currentStreak=0;
            }
            if(partnerCount>=3&&(double)partnerWins/partnerCount>=0.8)stringsMutable(badges,"sharpPartner").add(pid);
            if(topStreak>=3)stringsMutable(badges,"hotStreak").add(pid);
        }
        if(!ranked.isEmpty()) {
            int highScore=scoreOf(id(ranked.get(0)),r);
            for(Map<String,Object> p:ranked)if(scoreOf(id(p),r)==highScore)stringsMutable(badges,"mvp").add(id(p));
        }
        for(Map<String,Object> round:r)if(maxBid>0&&num(round.get("bid"))==maxBid){
            String playerId=str(round,"hukumId");
            if(!stringsMutable(badges,"riskTaker").contains(playerId))stringsMutable(badges,"riskTaker").add(playerId);
        }
        List<Map<String,Object>> streaks=new ArrayList<>();
        for(Map<String,Object> player:players){
            String pid=id(player);
            int streak=0;
            for(Map<String,Object> round:r){
                if(!strings(round.get("playerIds")).contains(pid))continue;
                boolean onHukumSide=pid.equals(str(round,"hukumId"))||
                        strings(round.get("partnerIds")).contains(pid);
                boolean won="win".equals(str(round,"result"))==onHukumSide;
                if(won)streak++;else break;
            }
            if(streak>=2)streaks.add(FirebaseGateway.map("name",str(player,"name"),"streak",streak));
        }
        streaks.sort((a,b)->Integer.compare(num(b.get("streak")),num(a.get("streak"))));
        Map<String,Object> update=FirebaseGateway.map(
                "bestHukum",bestHukum,"badges",badges,"streaks",streaks,
                "endTime",FirebaseGateway.now(),
                "duration",end-start,"totalRounds",r.size(),
                "leaderboard",leaderboard,"mvp",mvp,
                "biggestWin",FirebaseGateway.map("name",winningName,"score",best),
                "biggestLoss",FirebaseGateway.map("name",losingName,"score",worst));
        api.patchDocument("sessions",id(s),update,"Session ended · Summary saved");
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
        String label="all".equals(rankFilter)?"All Time":
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
                when(session.get("startTime"))+"  ·  "+num(session.get("totalRounds"))+" rounds",awards,ranking);
    }
    @Override public void onBackPressed() {
        if(tab==5){tab=3;render(true);}
        else if(tab!=0){tab=0;render(true);}
        else new AlertDialog.Builder(this).setTitle("Exit 3 Patti PRO?")
                .setNegativeButton("Stay",null).setPositiveButton("Exit",(d,w)->finish()).show();
    }
}
