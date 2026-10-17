package com.hozefa.threepattipro.nativeapp;

import android.app.AlertDialog;
import android.content.Context;

/** Window animations keep existing onShow/onDismiss authentication and validation callbacks intact. */
final class MotionDialogBuilder extends AlertDialog.Builder {
    private final MotionEngine motion;
    MotionDialogBuilder(Context context, MotionEngine motion) { super(context); this.motion = motion; }
    @Override public AlertDialog create() {
        AlertDialog dialog = super.create();
        if (motion != null) motion.dialog(dialog);
        return dialog;
    }
}
