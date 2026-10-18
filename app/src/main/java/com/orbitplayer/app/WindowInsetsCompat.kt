package com.orbitplayer.app

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsets

/** Preserve existing padding while keeping content outside status/navigation bars. */
internal fun Activity.applyContentInsets(view: View) {
    if (Build.VERSION.SDK_INT < 30) return
    window.setDecorFitsSystemWindows(false)
    val left = view.paddingLeft; val top = view.paddingTop
    val right = view.paddingRight; val bottom = view.paddingBottom
    view.setOnApplyWindowInsetsListener { target, insets ->
        val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
        target.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom)
        insets
    }
    view.requestApplyInsets()
}
