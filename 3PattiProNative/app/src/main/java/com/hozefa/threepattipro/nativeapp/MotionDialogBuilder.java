package com.hozefa.threepattipro.nativeapp;

import android.app.AlertDialog;
import android.content.Context;

/** Window animations keep existing onShow/onDismiss authentication and validation callbacks intact. */
final class MotionDialogBuilder extends AlertDialog.Builder {
    private final MotionEngine motion;
    private CharSequence title;
    MotionDialogBuilder(Context context, MotionEngine motion) { super(context,R.style.Theme_ThreePatti_Dialog); this.motion = motion; }
    @Override public MotionDialogBuilder setTitle(CharSequence value){title=value;super.setTitle(value);return this;}
    @Override public MotionDialogBuilder setTitle(int value){return setTitle(getContext().getText(value));}
    @Override public MotionDialogBuilder setView(android.view.View view){
        if(view instanceof android.widget.EditText){android.widget.LinearLayout body=DialogUi.body(getContext());body.addView(view,new android.widget.LinearLayout.LayoutParams(-1,-2));super.setView(body);}
        else super.setView(view);
        return this;
    }
    @Override public AlertDialog create() {
        if(title!=null){
            android.widget.TextView heading=DialogUi.text(getContext(),title.toString(),20,DialogUi.GOLD,true);
            heading.setPadding(DialogUi.dp(getContext(),22),DialogUi.dp(getContext(),20),DialogUi.dp(getContext(),22),DialogUi.dp(getContext(),14));
            heading.setMaxLines(3);heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
            androidx.core.view.ViewCompat.setAccessibilityHeading(heading,true);super.setCustomTitle(heading);
        }
        AlertDialog dialog = super.create();
        if (motion != null) motion.dialog(dialog);
        DialogUi.install(dialog);
        return dialog;
    }
}
