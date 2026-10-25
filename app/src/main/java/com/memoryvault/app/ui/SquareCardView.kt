package com.memoryvault.app.ui

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.card.MaterialCardView

/** A rounded card that is always as tall as it is wide. */
class SquareCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : MaterialCardView(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec)
    }
}
