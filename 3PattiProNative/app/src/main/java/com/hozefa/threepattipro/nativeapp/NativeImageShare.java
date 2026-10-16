package com.hozefa.threepattipro.nativeapp;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.net.Uri;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public final class NativeImageShare {
    private NativeImageShare(){}

    public static void share(Activity activity,String title,String body) {
        try {
            // Generate a real PNG scorecard directly from the native Android Canvas.
            final int width=880,pad=52;
            List<String> lines=new ArrayList<>();
            for(String raw:body.split("\\n")) {
                if(raw.length()<=41) {lines.add(raw);continue;}
                String value=raw;
                while(value.length()>41) {
                    int cutoff=value.lastIndexOf(' ',41);
                    if(cutoff<15)cutoff=41;
                    lines.add(value.substring(0,cutoff));
                    value=value.substring(cutoff).trim();
                }
                if(!value.isEmpty())lines.add(value);
            }
            if(lines.size()>140)lines=new ArrayList<>(lines.subList(0,140));
            final int height=Math.max(500,220+lines.size()*43);
            Bitmap picture=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(picture);
            Paint painter=new Paint(Paint.ANTI_ALIAS_FLAG);
            painter.setShader(new LinearGradient(0,0,width,height,
                    new int[]{0xFF111827,0xFF131D30,0xFF080F1F},null,Shader.TileMode.CLAMP));
            canvas.drawRect(0,0,width,height,painter);
            painter.setShader(null);
            painter.setStyle(Paint.Style.STROKE);painter.setStrokeWidth(3);
            painter.setColor(0xFFEBC781);canvas.drawRoundRect(16,16,width-16,height-16,30,30,painter);
            painter.setStyle(Paint.Style.FILL);
            painter.setColor(0xFFF6D78D);
            painter.setTextSize(43);painter.setFakeBoldText(true);
            canvas.drawText("3 PATTI PRO",pad,90,painter);
            painter.setColor(0xFFAFBDD5);
            painter.setTextSize(24);painter.setFakeBoldText(false);
            canvas.drawText(title,pad,132,painter);
            painter.setColor(0xFFAA8550);
            canvas.drawRect(pad,160,width-pad,163,painter);
            int y=212;
            for(String line:lines) {
                if(line.trim().isEmpty()){y+=22;continue;}
                painter.setFakeBoldText(line.matches(".*(SCORE|ROUND|LEADERBOARD|MVP|WIN|LOSS).*"));
                painter.setColor(line.contains(" +")||line.startsWith("+")?0xFF6DEBC2:
                        line.contains(" -")?0xFFFF9BAD:0xFFEAF0FF);
                painter.setTextSize(25);
                canvas.drawText(line,pad,y,painter);
                y+=43;
            }
            painter.setFakeBoldText(false);
            painter.setColor(0xFF93A1BD);painter.setTextSize(18);
            canvas.drawText("3 Patti PRO   •   Native Score Tracker",pad,height-51,painter);

            File dir=new File(activity.getCacheDir(),"share");
            if(!dir.exists()&&!dir.mkdirs())throw new Exception("Share directory unavailable");
            File target=new File(dir,"three-patti-pro.png");
            try(FileOutputStream stream=new FileOutputStream(target)){
                if(!picture.compress(Bitmap.CompressFormat.PNG,100,stream))throw new Exception("PNG encoding failed");
            }
            picture.recycle();
            Uri uri=FileProvider.getUriForFile(activity,
                    activity.getPackageName()+".fileprovider",target);
            Intent intent=new Intent(Intent.ACTION_SEND);
            intent.setType("image/png");
            intent.putExtra(Intent.EXTRA_STREAM,uri);
            intent.setClipData(ClipData.newRawUri(title,uri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(intent,"Share "+title));
        } catch(Exception error) {
            // Text share fallback keeps the button functional on restrictive devices.
            Intent fallback=new Intent(Intent.ACTION_SEND);
            fallback.setType("text/plain");
            fallback.putExtra(Intent.EXTRA_TEXT,title+"\n\n"+body);
            activity.startActivity(Intent.createChooser(fallback,"Share 3 Patti PRO"));
        }
    }
}
