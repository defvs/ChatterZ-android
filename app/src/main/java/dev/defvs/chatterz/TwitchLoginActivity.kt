package dev.defvs.chatterz

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.preference.PreferenceManager
import dev.defvs.chatterz.themes.ThemedActivity
import dev.defvs.chatterz.twitch.TwitchAPI
import kotlinx.android.synthetic.main.activity_twitch_login.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.net.URI

class TwitchLoginActivity : ThemedActivity() {
	private lateinit var preferences: SharedPreferences
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_twitch_login)
		setSupportActionBar(toolbar)
		
		preferences = PreferenceManager.getDefaultSharedPreferences(this)
		
		handleIntent(intent)
		login_button.setOnClickListener(::login)
	}
	
	override fun finish() {
		super.finish()
		overridePendingTransition(android.R.anim.fade_in, android.R.anim.slide_out_right)
	}
	
	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		handleIntent(intent)
	}
	
	private fun handleIntent(intent: Intent) {
		if (intent.action == Intent.ACTION_VIEW) {
			val data = intent.data?.fragment?.split('&')?.map {
				it.split('=').let { it[0] to it[1] }
			}?.toMap() ?: mapOf()
			
			data["access_token"]?.let { token ->
				preferences.edit().putString("twitch_token", token).apply()
				GlobalScope.launch {
					val username = TwitchAPI.getUsername(getString(R.string.twitch_client_id), token)
					preferences.edit().putString("twitch_username", username).commit()
				}
			}
		}
	}
	
	private fun login(view: View) {
		val url = "https://id.twitch.tv/oauth2/authorize" +
				"?client_id=${getString(R.string.twitch_client_id)}" +
				"&redirect_uri=https://chatterz.live/twitch-oauth" +
				"&response_type=token" +
				"&scope=user_read chat:edit chat:read"
		startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
	}
}