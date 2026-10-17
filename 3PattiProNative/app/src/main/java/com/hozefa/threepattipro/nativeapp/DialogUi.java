package com.hozefa.threepattipro.nativeapp;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

/** Shared dialog surface, bounded bodies and wrapping rows. Does not own action callbacks. */
final class DialogUi {
    static final int SURFACE=0xFF16171C, CARD=0xFF22242B, BORDER=0xFF35343A;
    static final int WHITE=0xFFFFFFFF, MUTED=0xFFA5A7B0, GOLD=0xFFF6D365, GREEN=0xFF2ECD71, RED=0xFFE74C3C;
    private DialogUi(){}
    static int dp(Context c,float value){return Math.round(value*c.getResources().getDisplayMetrics().density);}
    static Activity activity(Context context){
        while(context instanceof ContextWrapper){if(context instanceof Activity)return (Activity)context;Context next=((ContextWrapper)context).getBaseContext();if(next==context)break;context=next;}
        return context instanceof Activity?(Activity)context:null;
    }
    static int availableHeight(Context context){
        Activity owner=activity(context);Rect frame=new Rect();
        if(owner!=null)owner.getWindow().getDecorView().getWindowVisibleDisplayFrame(frame);
        return frame.height()>0?frame.height():context.getResources().getDisplayMetrics().heightPixels;
    }
    static int bodyLimit(Context context){
        return Math.max(dp(context,72),Math.round(availableHeight(context)*.64f));
    }
    static GradientDrawable background(Context context,int color,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(context,radius));d.setStroke(dp(context,1),BORDER);return d;
    }
    static TextView text(Context c,String label,int size,int color,boolean bold){
        TextView v=new TextView(c);v.setText(label);v.setTextSize(size);v.setTextColor(color);v.setIncludeFontPadding(false);
        v.setLineSpacing(dp(c,3),1);v.setGravity(Gravity.CENTER_VERTICAL);
        if(bold)v.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));return v;
    }
    static LinearLayout column(Context c){LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.VERTICAL);return v;}
    static void gap(LinearLayout v,int height){View gap=new View(v.getContext());v.addView(gap,new LinearLayout.LayoutParams(1,dp(v.getContext(),height)));}
    static ScrollView scroll(Context c,View child){
        ScrollView scroll=new BoundedScroll(c);scroll.setFillViewport(false);scroll.setClipToPadding(false);scroll.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
        scroll.setContentDescription("Scroll dialog details");scroll.addView(child,new ScrollView.LayoutParams(-1,-2));return scroll;
    }
    static LinearLayout body(Context c){LinearLayout v=column(c);v.setPadding(dp(c,18),dp(c,4),dp(c,18),dp(c,12));return v;}
    static void heading(LinearLayout parent,String title){
        Context c=parent.getContext();gap(parent,14);TextView label=text(c,title,12,GOLD,true);parent.addView(label);gap(parent,10);
    }
    static void fact(LinearLayout parent,String label,String value,int color){
        Context c=parent.getContext();LinearLayout row=column(c);row.setPadding(dp(c,14),dp(c,12),dp(c,14),dp(c,12));row.setBackground(background(c,CARD,12));
        row.addView(text(c,label,11,MUTED,false));gap(row,6);TextView result=text(c,value.isEmpty()?"—":value,15,color,true);result.setTextIsSelectable(true);row.addView(result);
        parent.addView(row,new LinearLayout.LayoutParams(-1,-2));gap(parent,8);
    }
    static void score(LinearLayout parent,String name,String role,int points){
        Context c=parent.getContext();LinearLayout row=column(c);row.setPadding(dp(c,14),dp(c,12),dp(c,14),dp(c,12));row.setBackground(background(c,CARD,12));
        row.addView(text(c,name,15,WHITE,true));if(!role.isEmpty()){gap(row,5);row.addView(text(c,role,12,MUTED,false));}gap(row,8);
        TextView value=text(c,points>0?"+"+points:String.valueOf(points),21,points>0?GREEN:points<0?RED:MUTED,true);
        value.setContentDescription((points>0?"Plus ":points<0?"Minus ":"")+Math.abs((long)points)+" points");row.addView(value);
        parent.addView(row,new LinearLayout.LayoutParams(-1,-2));gap(parent,9);
    }
    static final class BoundedScroll extends ScrollView {
        BoundedScroll(Context c){super(c);}
        @Override protected void onMeasure(int width,int height){
            int limit=bodyLimit(getContext());if(MeasureSpec.getMode(height)!=MeasureSpec.UNSPECIFIED)limit=Math.min(limit,MeasureSpec.getSize(height));
            super.onMeasure(width,MeasureSpec.makeMeasureSpec(limit,MeasureSpec.AT_MOST));
        }
    }
    static final class BoundedList extends ListView {
        BoundedList(Context c){super(c);setDivider(null);setClipToPadding(false);}
        @Override protected void onMeasure(int width,int height){
            int limit=bodyLimit(getContext());if(MeasureSpec.getMode(height)!=MeasureSpec.UNSPECIFIED)limit=Math.min(limit,MeasureSpec.getSize(height));
            super.onMeasure(width,MeasureSpec.makeMeasureSpec(limit,MeasureSpec.EXACTLY));
        }
    }
    static final class PreviewBody extends LinearLayout {
        PreviewBody(Context c){super(c);setOrientation(VERTICAL);}
        @Override protected void onMeasure(int width,int height){
            int limit=bodyLimit(getContext());if(MeasureSpec.getMode(height)!=MeasureSpec.UNSPECIFIED)limit=Math.min(limit,MeasureSpec.getSize(height));
            super.onMeasure(width,MeasureSpec.makeMeasureSpec(limit,MeasureSpec.EXACTLY));
        }
    }
    static void install(AlertDialog dialog){
        if(dialog.getWindow()==null)return;
        Window window=dialog.getWindow();window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        View decor=window.getDecorView();
        decor.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
            public void onViewAttachedToWindow(View v){decorate(dialog);}
            public void onViewDetachedFromWindow(View v){v.removeOnAttachStateChangeListener(this);}
        });
    }
    private static void decorate(AlertDialog dialog){
        Context c=dialog.getContext();
        TextView message=dialog.findViewById(android.R.id.message);
        if(message!=null){message.setTextSize(14);message.setTextColor(WHITE);message.setLineSpacing(dp(c,5),1);}
        Button[] buttons={dialog.getButton(AlertDialog.BUTTON_NEGATIVE),dialog.getButton(AlertDialog.BUTTON_NEUTRAL),dialog.getButton(AlertDialog.BUTTON_POSITIVE)};
        LinearLayout bar=null;float required=0;int count=0;
        for(Button button:buttons)if(button!=null&&button.getVisibility()==View.VISIBLE){
            String label=button.getText().toString();boolean destructive=label.equalsIgnoreCase("Delete")||label.equalsIgnoreCase("Clear All");int color=destructive?RED:GOLD;
            button.setAllCaps(false);button.setTextColor(new ColorStateList(new int[][]{new int[]{android.R.attr.state_enabled},new int[]{}},new int[]{color,MUTED}));
            button.setTextSize(14);button.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));button.setMaxLines(3);button.setMinHeight(dp(c,48));
            required+=Math.max(dp(c,64),button.getPaint().measureText(label)+dp(c,32));count++;
            if(button.getParent() instanceof LinearLayout)bar=(LinearLayout)button.getParent();
        }
        int width=Math.round(c.getResources().getDisplayMetrics().widthPixels*.94f)-dp(c,32);
        if(bar!=null&&required>width&&count>1){
            bar.setOrientation(LinearLayout.VERTICAL);bar.setGravity(Gravity.END);bar.setPadding(dp(c,10),dp(c,4),dp(c,10),dp(c,8));
            bar.setMinimumHeight(dp(c,count*48+12));
            for(int i=0;i<bar.getChildCount();i++){
                View child=bar.getChildAt(i);
                if(child instanceof Button&&child.getVisibility()==View.VISIBLE)child.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));
                else if(!(child instanceof Button))child.setVisibility(View.GONE);
            }
        }
        ListView list=dialog.getListView();if(list!=null){list.setDivider(new android.graphics.drawable.ColorDrawable(BORDER));list.setDividerHeight(dp(c,1));}
        styleInputs(dialog.getWindow().getDecorView());
    }
    private static void styleInputs(View view){
        if(view instanceof DatePicker)return;
        if(view instanceof EditText){EditText input=(EditText)view;Context c=view.getContext();input.setTextColor(WHITE);input.setHintTextColor(MUTED);input.setMinHeight(dp(c,48));input.setPadding(dp(c,12),dp(c,12),dp(c,12),dp(c,12));input.setBackground(background(c,CARD,12));}
        else if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)styleInputs(((ViewGroup)view).getChildAt(i));
    }
}
