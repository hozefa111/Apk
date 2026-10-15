package com.orbitplayer.app

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View

/** Center-edge gestures avoid intercepting the player's main seek bar or playback button. */
class PlayerGestureZone(
    context: Context,
    private val right: Boolean,
    private val onDoubleTap: () -> Unit,
    private val onVerticalScroll: (Float) -> Unit,
    private val onTap: () -> Unit
) : View(context) {
    private val gesture = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onDoubleTap(e: MotionEvent): Boolean {
            onDoubleTap()
            return true
        }
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            onTap()
            return true
        }
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (e1 == null || kotlin.math.abs(e2.y - e1.y) < kotlin.math.abs(e2.x - e1.x)) return false
            onVerticalScroll(distanceY / height.coerceAtLeast(1))
            return true
        }
    })

    override fun onTouchEvent(event: MotionEvent): Boolean = gesture.onTouchEvent(event) || event.action == MotionEvent.ACTION_UP
}
