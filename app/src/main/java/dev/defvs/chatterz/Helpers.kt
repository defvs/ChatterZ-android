package dev.defvs.chatterz

import android.content.Context
import android.graphics.Color
import android.widget.TextView
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView

fun RecyclerView.LayoutManager.scrollTo(context: Context, pos: Int) {
	this.startSmoothScroll(
		(object : LinearSmoothScroller(context) {
			override fun getVerticalSnapPreference() = SNAP_TO_START
		}).apply { targetPosition = pos }
	)
}

fun TextView.clear() {
	text = ""
}

fun Int.lightenColor(): Int {
	val hsv = FloatArray(3)
	Color.colorToHSV(this, hsv)
	hsv[1] = hsv[1] / 2
	return Color.HSVToColor(hsv)
}

fun Int.darkenColor(): Int {
	val hsv = FloatArray(3)
	Color.colorToHSV(this, hsv)
	hsv[1] = 1 - (1 - hsv[1]) / 2
	return Color.HSVToColor(hsv)
}

fun String?.nullIfEmpty(): String? = if (this.isNullOrBlank()) null else this