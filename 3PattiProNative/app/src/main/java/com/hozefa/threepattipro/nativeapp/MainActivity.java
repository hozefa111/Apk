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
    private static final int BG = 0xFF0B0F19, PANEL = 0xFF151C2C, PANEL2 = 0xFF1C2639;
    private static final int BORDER = 0xFF2A3448, WHITE = 0xFFF9FAFE, MUTED = 0xFF96A4BC;
    private static final int GOLD = 0xFFF4CD83, GREEN = 0xFF48D5A6, RED = 0xFFFF7188;
    private static final int BLUE = 0xFF76ACFF;
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
    private int visibleRoundCount=25, visibleSessionCount=20, visibleRankCount=35;
    private List<Map<String,Object>> cachedGames = Collections.emptyList();
    private final Map<String,Integer> cachedScores=new HashMap<>(),cachedGameScores=new HashMap<>(),cachedPlays=new HashMap<>(),cachedWins=new HashMap<>();
    private Set<String> playing=null;
    private final LinkedHashSet<String> partners=new LinkedHashSet<>();
    private String hukum=null, outcome=null;
    private int bid=155;
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
        // No UI re-creation on a no-op Firebase poll. Preserves scrolling and taps.
        if(hasData && players.equals(p) && rounds.equals(r) && sessions.equals(s)) return;
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
        render(false);
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
        b.setBackground(primary?gradient(0xFFF4D391,0xFFE2AE60,14):shape(PANEL2,14,BORDER));
        b.setClickable(true);
        b.setFocusable(true);
        b.setOnClickListener(v->{
            if(busy){Toast.makeText(this,"Saving in progress. Please try again.",Toast.LENGTH_SHORT).show();return;}
            if(action!=null) action.run();
        });
        return b;
    }
    private TextView chip(String name,boolean selected,boolean gold,Runnable action) {
        int fill=selected?(gold?0xFF60492E:0xFF1A4B43):PANEL2;
        int fg=selected?(gold?GOLD:GREEN):WHITE;
        TextView t=text(name,13,fg,selected);
        t.setGravity(Gravity.CENTER);pad(t,15,10,15,10);
        t.setBackground(shape(fill,12,selected?(gold?GOLD:GREEN):BORDER));
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
        navigation.setBackgroundColor(PANEL);
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
        heading.addView(text("NATIVE SCORE TRACKER",9,MUTED,true));
        toolbar.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        TextView status=text(busy?"SAVING…":loggedIn?"ADMIN":"LOGIN",11,loggedIn?GREEN:GOLD,true);
        status.setGravity(Gravity.CENTER);pad(status,12,10,12,10);
        status.setBackground(shape(PANEL2,12,BORDER));
        status.setOnClickListener(v->{if(busy)return;if(loggedIn)confirmLogout();else loginDialog();});
        toolbar.addView(status);
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
        title(content,"Your table, at a glance","Scores synced with the original 3 Patti PRO");
        LinearLayout hero=column();
        hero.setBackground(gradient(0xFF2B344D,0xFF1B2637,22));
        pad(hero,20,22,20,22);
        hero.addView(text("♠    TABLE STATUS",12,GOLD,true));
        gap(hero,12);
        hero.addView(text(activePlayers().size()+" Players  ·  "+gameRounds().size()+" Rounds",24,WHITE,true));
        gap(hero,10);
        Map<String,Object> session=activeSession();
        hero.addView(text(session==null?"●  No active session":"●  LIVE · "+str(session,"sessionName"),
                13,session==null?MUTED:GREEN,true));
        gap(hero,16);
        if(loggedIn) {
            hero.addView(button(session==null?"▶  Start Session":"■  End Session",
                    session==null,session==null?this::startSessionDialog:this::endSessionDialog));
        } else {
            hero.addView(text("Viewing scores · Log in to manage the game",12,MUTED,false));
        }
        content.addView(hero);gap(content,18);

        List<Map<String,Object>> games=gameRounds();
        int total=0;
        for(Map<String,Object> r:games)total+=num(r.get("bid"));
        LinearLayout stats=row();
        LinearLayout left=panel(),right=panel();
        left.addView(text("ACTIVE PLAYERS",10,MUTED,true));
        gap(left,9);left.addView(text(String.valueOf(activePlayers().size()),27,GOLD,true));
        right.addView(text("TOTAL BIDS",10,MUTED,true));
        gap(right,9);right.addView(text(String.valueOf(total),27,WHITE,true));
        LinearLayout.LayoutParams half=new LinearLayout.LayoutParams(0,-2,1);
        half.rightMargin=dp(9);stats.addView(left,half);
        stats.addView(right,new LinearLayout.LayoutParams(0,-2,1));
        content.addView(stats);gap(content,22);

        content.addView(text("TOP PLAYERS",15,WHITE,true));gap(content,12);
        List<Map<String,Object>> ranked=rankedPlayers();
        if(ranked.isEmpty())empty("No rounds have been played yet.");
        else for(int i=0;i<Math.min(3,ranked.size());i++)addRankCard(ranked.get(i),i);
        gap(content,18);
        if(loggedIn) {content.addView(button("＋  Create New Round",true,()->{tab=2;render(true);}));gap(content,24);}
        content.addView(text("RECENT ROUNDS",15,WHITE,true));gap(content,12);
        if(games.isEmpty())empty("Your completed rounds will appear here.");
        else for(int i=0;i<Math.min(4,games.size());i++)addRoundCard(games.get(i));
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
            if(loggedIn) {
                gap(card,12);
                card.addView(button("Edit Name",false,()->editPlayerDialog(p)));
                gap(card,8);
                card.addView(button(active?"Bench Player":"Restore Player",false,()->confirmPlayerStatus(p,!active)));
                gap(card,8);
                card.addView(button("Remove Player",false,()->confirmRemove("players",p,"player")));
            }
            content.addView(card);gap(content,12);
        }
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
        if(!"all".equals(rankFilter)&&games!=gameRounds()) {
            result.removeIf(p->played(id(p),games)==0);
        }
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
                if(otherSide==(roleH||roleP))h[0]++;else h[1]++;
                if(win)h[2]++;else h[3]++;
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
        middle.addView(text(played(id(p),gameRounds())+" rounds played",12,MUTED,false));
        r.addView(middle,new LinearLayout.LayoutParams(0,-2,1));
        int score=scoreOf(id(p),gameRounds());
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
        content.addView(button("Share Leaderboard",false,this::shareLeaderboard));
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
            if(rounds.isEmpty())empty("No rounds recorded yet.");
            int count=Math.min(visibleRoundCount,rounds.size());
            for(int i=0;i<count;i++)addRoundCard(rounds.get(i));
            if(count<rounds.size()) {
                content.addView(button("Show "+Math.min(25,rounds.size()-count)+" More Rounds",false,()->{
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
                gap(card,8);
                card.addView(button("View Session Summary",false,()->showSessionSummary(s)));
                if(loggedIn) {
                    gap(card,8);
                    card.addView(button("Edit Session Name",false,()->editSessionDialog(s)));
                    gap(card,8);
                    card.addView(button("Delete Session",false,()->confirmRemove("sessions",s,"session")));
                }
                content.addView(card);gap(content,12);
            }
            if(count<sessions.size()) {
                content.addView(button("Show "+Math.min(20,sessions.size()-count)+" More Sessions",false,()->{
                    visibleSessionCount+=20;render(false);
                }));
            }
        }
    }
    private void addRoundCard(Map<String,Object> r) {
        LinearLayout card=panel();
        String outcome=str(r,"result");
        if("settlement".equals(str(r,"type"))) {
            card.addView(text("↔  Score settlement",16,BLUE,true));
        } else {
            LinearLayout row=row();
            TextView main=text("♛  "+str(r,"hukumName"),16,WHITE,true);
            row.addView(main,new LinearLayout.LayoutParams(0,-2,1));
            TextView badge=text("win".equals(outcome)?"WIN":"LOSE",12,"win".equals(outcome)?GREEN:RED,true);
            badge.setGravity(Gravity.CENTER);pad(badge,10,7,10,7);
            badge.setBackground(shape("win".equals(outcome)?0xFF1C443B:0xFF492633,10,0));
            row.addView(badge);card.addView(row);gap(card,10);
            line(card,"Bid",String.valueOf(num(r.get("bid"))),GOLD);
            String party=strings(r.get("partnerNames")).toString().replace("[","").replace("]","");
            line(card,"Partner",party.isEmpty()?"—":party,WHITE);
        }
        line(card,"Played on",when(r.get("timestamp")),MUTED);
        gap(card,8);
        card.addView(button("Round Details",false,()->roundDetails(r)));
        gap(card,8);
        card.addView(button("Share Round",false,()->shareRound(r)));
        if(loggedIn) {
            gap(card,8);
            card.addView(button("Delete Round",false,()->confirmRemove("rounds",r,"round")));
        }
        content.addView(card);gap(content,12);
    }
    private void roundPage() {
        if(!loggedIn){empty("Admin login is required to add rounds.");return;}
        title(content,"New Hukum Round","Select players and preview scores before saving");
        List<Map<String,Object>> active=activePlayers();
        if(playing==null)playing=activeIds();
        if(active.size()<3){empty("At least 3 active players are required.");return;}
        label(content,"01  •  Who's playing");
        LinearLayout selection=panel();
        for(Map<String,Object> p:active) {
            String pid=id(p);
            boolean on=playing.contains(pid);
            LinearLayout r=row();
            TextView name=chip((on?"✓   ":"○   ")+str(p,"name"),on,false,()->{
                if(playing.contains(pid)) {
                    playing.remove(pid);if(pid.equals(hukum))hukum=null;partners.remove(pid);
                }else playing.add(pid);
                render(false);
            });
            r.addView(name,new LinearLayout.LayoutParams(-1,dp(46)));
            selection.addView(r);gap(selection,7);
        }
        content.addView(selection);gap(content,20);
        if(playing.size()<3){empty("Select at least 3 players to continue.");return;}

        label(content,"02  •  Bid amount");
        LinearLayout bidding=panel();
        TextView current=text("₹  "+bid,26,GOLD,true);bidding.addView(current);gap(bidding,12);
        LinearLayout bidRow=row();
        bidRow.addView(button("− 5",false,()->{bid=Math.max(155,bid-5);render(false);}),new LinearLayout.LayoutParams(0,dp(50),1));
        TextView interval=text("155 – 250",13,MUTED,false);
        interval.setGravity(Gravity.CENTER);bidRow.addView(interval,new LinearLayout.LayoutParams(0,dp(50),1));
        bidRow.addView(button("+ 5",false,()->{bid=Math.min(250,bid+5);render(false);}),new LinearLayout.LayoutParams(0,dp(50),1));
        bidding.addView(bidRow);content.addView(bidding);gap(content,20);

        label(content,"03  •  Choose Hukum");
        LinearLayout hukumBox=panel();
        for(Map<String,Object> p:active)if(playing.contains(id(p))){
            String pid=id(p);
            hukumBox.addView(chip((pid.equals(hukum)?"♛   ":"○   ")+str(p,"name"),
                    pid.equals(hukum),true,()->{
                        hukum=pid;partners.remove(pid);render(false);
                    }),lp(-1,46));
            gap(hukumBox,8);
        }
        content.addView(hukumBox);gap(content,20);
        if(hukum==null){empty("Select Hukum to choose partners.");return;}
        label(content,"04  •  Choose Partner(s)");
        LinearLayout partnerBox=panel();
        for(Map<String,Object> p:active)if(playing.contains(id(p))&&!id(p).equals(hukum)){
            String pid=id(p);
            partnerBox.addView(chip((partners.contains(pid)?"✓   ":"○   ")+str(p,"name"),
                    partners.contains(pid),false,()->{
                        if(!partners.add(pid))partners.remove(pid);
                        render(false);
                    }),lp(-1,46));
            gap(partnerBox,8);
        }
        content.addView(partnerBox);gap(content,20);
        label(content,"05  •  Hukum result");
        LinearLayout results=row();
        TextView win=chip("✓   Hukum Win","win".equals(outcome),false,()->{outcome="win";render(false);});
        TextView lose=chip("✕   Hukum Lose","loss".equals(outcome),false,()->{outcome="loss";render(false);});
        LinearLayout.LayoutParams half=new LinearLayout.LayoutParams(0,dp(50),1);
        half.rightMargin=dp(10);results.addView(win,half);
        results.addView(lose,new LinearLayout.LayoutParams(0,dp(50),1));
        content.addView(results);gap(content,22);
        if(!validRound()){empty("Choose at least one partner, one opponent, and the round result.");return;}
        label(content,"SCORE PREVIEW");
        LinearLayout preview=panel();
        Map<String,Integer> score=scoreChanges();
        for(Map<String,Object> p:active)if(playing.contains(id(p))) {
            String pid=id(p),role=pid.equals(hukum)?"♛ ":partners.contains(pid)?"◈ ":"○ ";
            int change=score.getOrDefault(pid,0);
            line(preview,role+str(p,"name"),(change>=0?"+":"")+change,change>=0?GREEN:RED);
        }
        content.addView(preview);gap(content,16);
        content.addView(button(busy?"Saving...":"✓  Save Round to Firebase",true,this::saveRound));
        gap(content,14);
        content.addView(text("Uses your existing Firebase project. No old records are deleted.",11,MUTED,false));
    }
    private boolean validRound() {
        if(!loggedIn||hukum==null||outcome==null||playing==null||playing.size()<3||partners.isEmpty())return false;
        if(!playing.contains(hukum)||partners.contains(hukum))return false;
        for(String p:partners)if(!playing.contains(p))return false;
        return playing.size()-partners.size()-1>=1;
    }
    private Map<String,Integer> scoreChanges() {
        Map<String,Integer> changes=new LinkedHashMap<>();
        for(String pid:playing) {
            if("win".equals(outcome)) {
                changes.put(pid,pid.equals(hukum)?bid*2:(partners.contains(pid)?bid:0));
            } else {
                changes.put(pid,pid.equals(hukum)?-bid:(partners.contains(pid)?-(int)Math.round(bid/2.0):bid));
            }
        }
        return changes;
    }
    private void saveRound() {
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
                    api.addDocument("rounds",body,"Round saved successfully");
                    resetRound();tab=0;render(true);
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
        long dur=num(session.get("duration"));
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
                .setNeutralButton("Share", (d,w)->share(str(session,"sessionName")+"\n\n"+detail))
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
        Map<String,Object> update=FirebaseGateway.map(
                "bestHukum",bestHukum,"badges",badges,"streaks",new ArrayList<>(),
                "endTime",FirebaseGateway.now(),
                "duration",end-start,"totalRounds",r.size(),
                "leaderboard",leaderboard,"mvp",mvp,
                "biggestWin",FirebaseGateway.map("name",winningName,"score",best),
                "biggestLoss",FirebaseGateway.map("name",losingName,"score",worst));
        api.patchDocument("sessions",id(s),update,"Session ended · Summary saved");
    }
    private void shareLeaderboard() {
        StringBuilder out=new StringBuilder("♠  3 Patti PRO · Leaderboard\n\n");
        int rank=0;
        for(Map<String,Object> p:rankedPlayers()){
            out.append(++rank).append(". ").append(str(p,"name")).append("  ");
            int score=scoreOf(id(p),gameRounds());if(score>=0)out.append("+");out.append(score).append("\n");
        }
        NativeImageShare.share(this,"Leaderboard",out.toString());
    }
    private void shareRound(Map<String,Object> round) {
        StringBuilder out=new StringBuilder("3 Patti PRO · Round\n");
        out.append("Hukum: ").append(str(round,"hukumName")).append("\n");
        out.append("Bid: ").append(str(round,"bid")).append("\n");
        out.append("Result: ").append(str(round,"result")).append("\n");
        for(Map.Entry<String,Object> e:map(round.get("scoreChanges")).entrySet())
            out.append(nameOf(e.getKey())).append(": ").append(e.getValue()).append("\n");
        NativeImageShare.share(this,"Round Result",out.toString());
    }
    private void share(String body) {NativeImageShare.share(this,"Session Summary",body);}
    @Override public void onBackPressed() {
        if(tab!=0){tab=0;render(true);}
        else new AlertDialog.Builder(this).setTitle("Exit 3 Patti PRO?")
                .setNegativeButton("Stay",null).setPositiveButton("Exit",(d,w)->finish()).show();
    }
}
