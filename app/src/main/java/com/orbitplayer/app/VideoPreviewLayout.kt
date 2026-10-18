package com.orbitplayer.app

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/** Lightweight 16:9 cover; adapts to grid width and larger fonts without decoding work. */
class VideoPreviewLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(width * 9 / 16, MeasureSpec.EXACTLY))
    }
}
