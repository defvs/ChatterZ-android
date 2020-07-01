package dev.defvs.chatterz.themes

import io.multimoon.colorful.BaseTheme
import io.multimoon.colorful.CThemeInterface
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

open class ThemedActivity : AppCompatActivity(), CThemeInterface {
	
	override var themeString: String = ""
	
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		handleOnCreate(this, savedInstanceState, BaseTheme.THEME_APPCOMPAT)
	}
	
	override fun onResume() {
		super.onResume()
		handleOnResume(this)
	}
}