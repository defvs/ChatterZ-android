package dev.defvs.chatterz

import android.content.Context
import androidx.multidex.MultiDex
import androidx.multidex.MultiDexApplication
import androidx.preference.PreferenceManager
import io.multimoon.colorful.Defaults
import io.multimoon.colorful.ThemeColor
import io.multimoon.colorful.initColorful

class MainApplication : MultiDexApplication() {
	
	override fun attachBaseContext(base: Context?) {
		super.attachBaseContext(base)
		MultiDex.install(this)
	}
	
	override fun onCreate() {
		super.onCreate()
		initColorful(
			this, Defaults(
				primaryColor = ThemeColor.DEEP_PURPLE,
				accentColor = ThemeColor.ORANGE,
				useDarkTheme = PreferenceManager.getDefaultSharedPreferences(this)
					.getBoolean("dark_theme", true),
				translucent = false
			)
		)
	}
}