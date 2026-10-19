package com.orbitplayer.app

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.abs

/**
 * Fullscreen player touch surface, below all clickable controls.
 * Distinguishes seek vs brightness/volume swipes and handles double-tap seeking.
 */
class PlayerGestureZone(
    context: Context,
    private val onGestureStarted: () -> Unit,
    private val onTap: () -> Unit,
    private val onDoubleTap: (Float) -> Unit,
    private val onVerticalScroll: (Boolean, Float) -> Unit,
    private val onHorizontalSeek: (Float, Boolean) -> Unit,
    private val onPinch: (Boolean) -> Unit,
    private val onHoldBoost: (Boolean) -> Unit,
    private val onGestureCancelled: () -> Unit
) : View(context) {

    private enum class Swipe { NONE, VERTICAL, HORIZONTAL }
    private var swipe = Swipe.NONE
    private var horizontalFraction = 0f
    private var zoomAccumulated = 1f
    private var boosting = false
    private var multiTouch = false
    private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent): Boolean = true

        override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
            if (boosting) return true
            performClick()
            return true
        }

        override fun onDoubleTap(event: MotionEvent): Boolean {
            if (boosting) return true
            onDoubleTap((event.x / width.coerceAtLeast(1)).coerceIn(0f, 1f))
            return true
        }

        override fun onLongPress(event: MotionEvent) {
            if (swipe == Swipe.NONE && !scaleDetector.isInProgress) {
                boosting = true
                onHoldBoost(true)
            }
        }

        override fun onScroll(start: MotionEvent?, end: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (boosting) return true
            if (start == null || end.pointerCount > 1) return false
            val dx = end.x - start.x
            val dy = end.y - start.y
            if (swipe == Swipe.NONE) {
                val threshold = 16f * resources.displayMetrics.density
                if (abs(dx) < threshold && abs(dy) < threshold) return false
                swipe = if (abs(dx) > abs(dy) * 1.2f) Swipe.HORIZONTAL else Swipe.VERTICAL
            }
            if (swipe == Swipe.HORIZONTAL) {
                horizontalFraction = (dx / width.coerceAtLeast(1)).coerceIn(-1f, 1f)
                onHorizontalSeek(horizontalFraction, false)
            } else {
                val rightSide = start.x >= width / 2f
                onVerticalScroll(rightSide, distanceY / height.coerceAtLeast(1))
            }
            return true
        }
    })

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            zoomAccumulated = 1f
            swipe = Swipe.NONE
            onGestureCancelled()
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoomAccumulated *= detector.scaleFactor
            if (zoomAccumulated > 1.12f) {
                onPinch(true)
                zoomAccumulated = 1f
            } else if (zoomAccumulated < 0.88f) {
                onPinch(false)
                zoomAccumulated = 1f
            }
            return true
        }
    })

    override fun performClick(): Boolean {
        super.performClick()
        onTap()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            multiTouch = false
            swipe = Swipe.NONE
            onGestureStarted()
        }
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            multiTouch = true
            swipe = Swipe.NONE
            onGestureCancelled()
            if (boosting) { boosting = false; onHoldBoost(false) }
            val cancel = MotionEvent.obtain(event)
            cancel.action = MotionEvent.ACTION_CANCEL
            detector.onTouchEvent(cancel)
            cancel.recycle()
        }
        scaleDetector.onTouchEvent(event)
        if (!multiTouch && event.pointerCount == 1 && !scaleDetector.isInProgress) detector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            if (boosting) {
                boosting = false
                onHoldBoost(false)
            }
            if (swipe == Swipe.HORIZONTAL && event.actionMasked == MotionEvent.ACTION_UP) {
                onHorizontalSeek(horizontalFraction, true)
            }
            if (event.actionMasked == MotionEvent.ACTION_CANCEL) onGestureCancelled()
            swipe = Swipe.NONE
            horizontalFraction = 0f
        }
        return true
    }
}
