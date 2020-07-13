package dev.defvs.chatterz.twitch

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import java.net.HttpURLConnection
import java.net.URL

object TwitchAPI {
	private fun getFromAPI(
		url: URL,
		apiKey: String,
		oauthToken: String? = null
	): HttpURLConnection? {
		val data: HttpURLConnection = (url.openConnection() as HttpURLConnection).apply {
			setRequestProperty("Accept", "application/vnd.twitchtv.v5+json")
			setRequestProperty("Client-ID", apiKey)
			oauthToken?.let { setRequestProperty("Authorization", "OAuth $oauthToken") }
		}
		
		if (data.responseCode != 200) return null
		return data
	}
	
	private val userIdCache: MutableMap<String /* username */, String /* id */> = mutableMapOf()
	fun getUserId(apiKey: String, username: String): String? {
		userIdCache[username]?.let { return it } // return cached ID
		
		return getFromAPI(URL("https://api.twitch.tv/kraken/users?login=$username"), apiKey)?.let {
			(Parser.default().parse(it.inputStream) as? JsonObject)
				?.array<JsonObject>("users")?.get(0)?.string("_id")
				.also {
					if (it != null) {
						userIdCache[username] = it
					}
				}
		}
	}
	
	fun getUsername(apiKey: String, oauthToken: String) =
		getFromAPI(URL("https://api.twitch.tv/kraken/user"), apiKey, oauthToken)?.let {
			(Parser.default().parse(it.inputStream) as? JsonObject)
				?.string("name")
		}
}