package dev.defvs.chatterz

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.widget.TextView
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.mikepenz.materialdrawer.Drawer
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem
import java.net.URL
import java.net.URLConnection
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory

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

fun HttpsURLConnection.useTLS(): HttpsURLConnection {
	SSLContext.getInstance("TLS").let {
		it.init(null, null, null)
		val factory: SSLSocketFactory = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT)
			TLSSocketFactory(it.socketFactory)
		else it.socketFactory
		this@useTLS.sslSocketFactory = factory
	}
	return this
}

fun URLConnection.asHttps(): HttpsURLConnection = (this as HttpsURLConnection).useTLS()

fun URL.openHttps(): HttpsURLConnection = this.openConnection().asHttps()

fun Drawer.updateItems(vararg items: PrimaryDrawerItem?) =
	items.forEach { if (it != null) this.updateItem(it) }

inline fun runAndNull(action: () -> Unit) = action().let { null }