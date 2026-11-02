package com.andrerinas.openheadunit.main.onboarding

import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.andrerinas.openheadunit.R

/** A step that can hold the guide back: the risks not accepted yet, an install still running. */
interface OnboardingStep {
    fun canContinue(): Boolean
}

/** Fills [card] with one titled point per (title, text) pair of string resources. */
internal fun showPoints(card: LinearLayout, points: List<Pair<Int, Int>>) {
    val inflater = LayoutInflater.from(card.context)
    card.removeAllViews()
    for ((title, text) in points) {
        val row = inflater.inflate(R.layout.item_onb_point, card, false)
        row.findViewById<TextView>(R.id.onb_point_title).setText(title)
        row.findViewById<TextView>(R.id.onb_point_text).setText(text)
        card.addView(row)
    }
}

internal fun tintDot(dot: ImageView, colour: Int) {
    dot.imageTintList = ContextCompat.getColorStateList(dot.context, colour)
}

/** A step's latest message, in one quiet line. */
internal fun showLine(line: TextView, text: CharSequence) {
    line.text = text
    line.visibility = View.VISIBLE
}
