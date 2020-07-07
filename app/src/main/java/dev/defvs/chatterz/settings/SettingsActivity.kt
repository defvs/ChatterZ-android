package dev.defvs.chatterz.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import dev.defvs.chatterz.MainActivity
import dev.defvs.chatterz.R
import dev.defvs.chatterz.themes.ThemedActivity
import io.multimoon.colorful.Colorful
import kotlinx.android.synthetic.main.settings_activity.*

private const val TITLE_TAG = "settingsActivityTitle"

class SettingsActivity : ThemedActivity(),
	PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
	
	private val sharedPreferencesListener =
		SharedPreferences.OnSharedPreferenceChangeListener { _, s ->
			when (s) {
				"dark_theme" -> Colorful().edit()
					.setDarkTheme(MainActivity.sharedPreferences.getBoolean("dark_theme", true))
					.apply(this) {
						this.recreate()
					}
			}
		}
	
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.settings_activity)
		setSupportActionBar(toolbar)
		if (savedInstanceState == null) {
			supportFragmentManager
				.beginTransaction()
				.replace(
					R.id.settings,
					HeaderFragment()
				)
				.commit()
		} else {
			title = savedInstanceState.getCharSequence(TITLE_TAG)
		}
		supportFragmentManager.addOnBackStackChangedListener {
			if (supportFragmentManager.backStackEntryCount == 0) {
				setTitle(R.string.title_activity_settings)
			}
		}
		supportActionBar?.setDisplayHomeAsUpEnabled(true)
		
		MainActivity.sharedPreferences.registerOnSharedPreferenceChangeListener(
			sharedPreferencesListener
		)
	}
	
	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)
		// Save current activity title so we can set it again after a configuration change
		outState.putCharSequence(TITLE_TAG, title)
	}
	
	override fun onSupportNavigateUp(): Boolean {
		if (supportFragmentManager.popBackStackImmediate()) {
			return true
		}
		if (supportFragmentManager.backStackEntryCount == 0) {
			finish()
			return true
		}
		return super.onSupportNavigateUp()
	}
	
	override fun finish() {
		super.finish()
		overridePendingTransition(android.R.anim.fade_in, android.R.anim.slide_out_right)
	}
	
	override fun onPreferenceStartFragment(
		caller: PreferenceFragmentCompat,
		pref: Preference
	): Boolean {
		// Instantiate the new Fragment
		val args = pref.extras
		val fragment = supportFragmentManager.fragmentFactory.instantiate(
			classLoader,
			pref.fragment
		).apply {
			arguments = args
			setTargetFragment(caller, 0)
		}
		// Replace the existing Fragment with the new Fragment
		supportFragmentManager.beginTransaction()
			.setCustomAnimations(
				R.anim.slide_in_right,
				android.R.anim.fade_out,
				android.R.anim.fade_in,
				android.R.anim.slide_out_right
			)
			.replace(R.id.settings, fragment)
			.addToBackStack(null)
			.commit()
		title = pref.title
		return true
	}
	
	class HeaderFragment : PreferenceFragmentCompat() {
		override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
			setPreferencesFromResource(R.xml.header_preferences, rootKey)
		}
	}
	
	class ThemeFragment : PreferenceFragmentCompat() {
		override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
			setPreferencesFromResource(R.xml.themes_preferences, rootKey)
		}
	}
	
	class DebugFragment : PreferenceFragmentCompat() {
		override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
			setPreferencesFromResource(R.xml.debug_preferences, rootKey)
		}
	}
}