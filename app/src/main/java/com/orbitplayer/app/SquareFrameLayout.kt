package com.orbitplayer.app

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/** Square covers adapt to the actual grid column width without a layout dependency. */
class SquareFrameLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    override fun onFinishInflate() {
        super.onFinishInflate()
        clipToOutline = true
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY))
    }
}
