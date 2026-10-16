package com.hozefa.threepattipro.nativeapp;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.*;
import android.net.Uri;
import androidx.core.content.FileProvider;
import android.text.TextPaint;
import java.io.File;
import java.io.FileOutputStream;
import java.util.*;
import java.util.List;

/**
 * Native Canvas artwork styled to match the original 3 Patti PRO HTML share-card:
 * orange-gold brand, centered header, #1a1a2e section cards, role icons,
 * green/red results, detailed rank statistics and award-filled session summary.
 * Does not use WebView/HTML/CSS, or modify Firebase in any way.
 */
public final class NativeImageShare {
    private static final int W=960, PAD=55, RIGHT=W-PAD;
    private static final int GOLD=0xFFF5A623, WHITE=Color.WHITE, MUTED=0xFF9EA4BA;
    private static final int GREEN=0xFF4CAF50, RED=0xFFF44336, PANEL=0xFF1A1A2E;
    private static final int BACKGROUND=0xFF0F0F1A, LINE=0xFF343446;
    private NativeImageShare() {}

    public static final class ScoreRow {
        public final String name, role;
        public final int delta;
        public ScoreRow(String name,String role,int delta) {
            this.name=name;this.role=role;this.delta=delta;
        }
    }
    public static final class RankRow {
        public final String name;
        public final int score,rounds,wins,losses,hukum,partners;
        public RankRow(String name,int score,int rounds,int wins,int losses,int hukum,int partners){
            this.name=name;this.score=score;this.rounds=rounds;this.wins=wins;
            this.losses=losses;this.hukum=hukum;this.partners=partners;
        }
    }
    public static final class AwardRow {
        public final String title, value;
        public AwardRow(String title,String value){this.title=title;this.value=value;}
    }

    private static final class Art {
        final Bitmap bitmap;
        final Canvas canvas;
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        final int height;
        int y;
        Art(int height) {
            this.height=height;
            bitmap=Bitmap.createBitmap(W,height,Bitmap.Config.ARGB_8888);
            canvas=new Canvas(bitmap);
            canvas.drawColor(BACKGROUND);
            p.setShader(new LinearGradient(0,0,W,height,
                    new int[]{0xFF10101D,0xFF15132A,0xFF0D0D18},null,Shader.TileMode.CLAMP));
            canvas.drawRect(0,0,W,height,p);p.setShader(null);
            p.setColor(0x24F5A623);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);
            canvas.drawRoundRect(18,18,W-18,height-18,34,34,p);p.setStyle(Paint.Style.FILL);
            // Native decorative gold geometric shapes: no image assets or external fonts.
            p.setColor(0x16F5A623);canvas.drawCircle(W-80,88,175,p);
            p.setColor(0x0CFFFFFF);canvas.drawCircle(55,height-130,115,p);
            y=66;
        }
        void rect(float left,float top,float right,float bottom,float radius,int color){
            p.setShader(null);p.setColor(color);p.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(left,top,right,bottom,radius,radius,p);
        }
        void line(float l,float top,float r,int color){
            p.setColor(color);p.setStrokeWidth(2);canvas.drawLine(l,top,r,top,p);
        }
        void draw(String value,float x,float base,int size,int color,boolean bold,Paint.Align align,float maximum){
            p.setShader(null);p.setColor(color);p.setTextSize(size);
            p.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
            p.setTextAlign(align);
            p.setStyle(Paint.Style.FILL);
            String render=value==null?"":value;
            while(render.length()>1 && p.measureText(render)>maximum)
                render=render.substring(0,render.length()-1);
            if(value!=null && !render.equals(value) && render.length()>2)render=render.substring(0,render.length()-1)+"…";
            canvas.drawText(render,x,base,p);
        }
        void header(String title,String subtitle){
            draw("🂡  3 Patti PRO",W/2f,y+40,52,GOLD,true,Paint.Align.CENTER,W-140);
            y+=84;
            draw(title,W/2f,y+26,37,WHITE,true,Paint.Align.CENTER,W-120);
            y+=45;
            draw(subtitle,W/2f,y+22,23,MUTED,false,Paint.Align.CENTER,W-120);
            y+=65;
            line(PAD,y,RIGHT,LINE);
            y+=38;
        }
        void sectionStart(String heading,int height) {
            rect(PAD,y,RIGHT,y+height,22,PANEL);
            draw(heading,PAD+32,y+48,23,MUTED,true,Paint.Align.LEFT,W-150);
        }
        void row(String left,String right,int color,int offset,int top,int rowHeight) {
            draw(left,PAD+32,top+offset,27,WHITE,false,Paint.Align.LEFT,530);
            draw(right,RIGHT-32,top+offset,28,color,true,Paint.Align.RIGHT,220);
            line(PAD+32,top+rowHeight-3,RIGHT-32,0x18FFFFFF);
        }
        void foot() {
            int bottom=height-70;
            line(PAD,bottom-46,RIGHT,LINE);
            draw("3 Patti PRO  •  Score Tracker",W/2f,bottom,22,MUTED,false,Paint.Align.CENTER,W-80);
        }
    }

    private static String score(int n){return n==0?"—":n>0?"+"+n:String.valueOf(n);}
    public static void round(Activity activity,String date,String hukum,String result,int bid,List<ScoreRow> rows){
        if(rows==null)rows=Collections.emptyList();
        int playerCount=Math.min(rows.size(),45);
        int details=255;
        int scores=94+playerCount*75;
        Art card=new Art(370+details+scores+125);
        card.header("Round Result","📅  "+date);
        int t=card.y;
        card.sectionStart("ROUND DETAILS",details);
        card.draw("♛  Hukum: "+hukum,PAD+32,t+105,32,WHITE,true,Paint.Align.LEFT,530);
        boolean win="win".equals(result);
        card.rect(RIGHT-268,t+70,RIGHT-30,t+129,16,win?0xFF1D503B:0xFF572B35);
        card.draw(win?"HUKUM WIN ✓":"HUKUM LOSE ✕",RIGHT-149,t+108,26,win?GREEN:RED,true,Paint.Align.CENTER,225);
        card.line(PAD+32,t+152,RIGHT-32,LINE);
        card.draw("💰  Bid",PAD+32,t+203,28,WHITE,false,Paint.Align.LEFT,450);
        card.draw(bid+" points",RIGHT-32,t+203,28,GOLD,true,Paint.Align.RIGHT,280);
        card.y=t+details+25;

        t=card.y;
        card.sectionStart("SCORE CHANGES  •  "+playerCount+" PLAYERS",scores);
        for(int i=0;i<playerCount;i++){
            ScoreRow player=rows.get(i);
            String symbol="hukum".equals(player.role)?"♛":("partner".equals(player.role)?"◆":"◎");
            card.row(symbol+"  "+player.name,score(player.delta),
                    player.delta>0?GREEN:player.delta<0?RED:MUTED,51,t+69+i*75,75);
        }
        card.y=t+scores;
        card.foot();
        deliver(activity,card.bitmap,"3 Patti PRO - Round Result");
    }

    public static void leaderboard(Activity activity,String filter,List<RankRow> rows){
        if(rows==null)rows=Collections.emptyList();
        int count=Math.min(rows.size(),100);
        int section=90+count*105;
        Art card=new Art(370+section+135);
        card.header("Rankings","📅  "+filter);
        int t=card.y;card.sectionStart("LEADERBOARD  •  "+count+" PLAYERS",section);
        for(int i=0;i<count;i++){
            RankRow r=rows.get(i);
            int at=t+100+i*105;
            String medal=i==0?"🥇":i==1?"🥈":i==2?"🥉":"#"+(i+1);
            card.draw(medal+"  "+r.name,PAD+32,at,31,WHITE,true,Paint.Align.LEFT,550);
            card.draw(score(r.score),RIGHT-32,at,32,r.score>=0?GREEN:RED,true,Paint.Align.RIGHT,215);
            card.draw(r.rounds+"R    "+r.wins+"W/"+r.losses+"L     ♛"+r.hukum+"    ◆"+r.partners,
                    PAD+95,at+37,23,MUTED,false,Paint.Align.LEFT,720);
            card.line(PAD+32,at+49,RIGHT-32,0x24FFFFFF);
        }
        card.y=t+section;
        card.foot();
        deliver(activity,card.bitmap,"3 Patti PRO - Rankings");
    }

    public static void session(Activity activity,String title,String date,
                               List<AwardRow> awards,List<RankRow> ranked){
        if(awards==null)awards=Collections.emptyList();
        if(ranked==null)ranked=Collections.emptyList();
        int awardsCount=Math.min(awards.size(),25),count=Math.min(ranked.size(),100);
        int awardsHeight=awardsCount==0?0:85+awardsCount*75;
        int ranksHeight=90+count*86;
        Art card=new Art(370+awardsHeight+ranksHeight+155);
        card.header("Session Summary","📅  "+date);
        if(awardsCount>0) {
            int t=card.y;
            card.sectionStart("🏆  SESSION AWARDS",awardsHeight);
            for(int i=0;i<awardsCount;i++){
                AwardRow a=awards.get(i);
                card.row(a.title,a.value,GOLD,55,t+63+i*75,75);
            }
            card.y=t+awardsHeight+26;
        }
        int t=card.y;
        card.sectionStart("🏆  LEADERBOARD",ranksHeight);
        for(int i=0;i<count;i++){
            RankRow p=ranked.get(i);
            int top=t+105+i*86;
            String prefix=i==0?"🥇":i==1?"🥈":i==2?"🥉":"#"+(i+1);
            card.draw(prefix+"  "+p.name,PAD+35,top,29,WHITE,true,Paint.Align.LEFT,530);
            card.draw(score(p.score),RIGHT-33,top,29,p.score>=0?GREEN:RED,true,Paint.Align.RIGHT,225);
            card.line(PAD+35,top+17,RIGHT-35,0x18FFFFFF);
        }
        card.y=t+ranksHeight;
        card.foot();
        deliver(activity,card.bitmap,"3 Patti PRO - "+title);
    }

    private static void deliver(Activity activity,Bitmap bitmap,String title){
        try {
            File dir=new File(activity.getCacheDir(),"share");
            if(!dir.exists()&&!dir.mkdirs())throw new IllegalStateException("Cannot create cache");
            File file=new File(dir,"3-patti-pro-scorecard.png");
            try(FileOutputStream stream=new FileOutputStream(file)){
                if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,stream))
                    throw new IllegalStateException("PNG write failed");
            }
            Uri uri=FileProvider.getUriForFile(activity,activity.getPackageName()+".fileprovider",file);
            Intent send=new Intent(Intent.ACTION_SEND);
            send.setType("image/png");
            send.putExtra(Intent.EXTRA_STREAM,uri);
            send.setClipData(ClipData.newRawUri(title,uri));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(send,title));
        }catch(Exception ex){
            android.widget.Toast.makeText(activity,"Image sharing failed: "+ex.getMessage(),
                    android.widget.Toast.LENGTH_LONG).show();
        }finally {bitmap.recycle();}
    }
}
