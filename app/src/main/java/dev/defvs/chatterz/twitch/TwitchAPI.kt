package dev.defvs.chatterz.twitch

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import com.beust.klaxon.Parser
import dev.defvs.chatterz.autocomplete.CompletableTwitchEmote
import dev.defvs.chatterz.autocomplete.EmoteType
import dev.defvs.chatterz.autocomplete.TwitchEmotesResponse
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
				?.array<JsonObject>("users")?.getOrNull(0)?.string("_id")
				.also {
					if (it != null) {
						userIdCache[username] = it
					}
				}
		}
	}
	
	fun getUserInfo(apiKey: String, oauthToken: String) = getFromAPI(URL("https://api.twitch.tv/kraken/user"), apiKey, oauthToken)
		?.let { (Parser.default().parse(it.inputStream) as? JsonObject) }
	
	fun getUsername(apiKey: String, oauthToken: String) = getUserInfo(apiKey, oauthToken)?.string("name")
	fun getUserIconURL(apiKey: String, oauthToken: String) = getUserInfo(apiKey, oauthToken)?.string("logo")
	
	fun getTwitchEmotes(
		apiKey: String,
		oauthToken: String,
		username: String
	): List<CompletableTwitchEmote> {
		val userId = getUserId(apiKey, username)
		val connection = getFromAPI(URL("https://api.twitch.tv/kraken/users/$userId/emotes"), apiKey, oauthToken)
		return if (connection != null) {
			Klaxon().parse<TwitchEmotesResponse>(connection.inputStream)?.emoteSets?.map { it.value }
				?.flatten()
				?.map { CompletableTwitchEmote(it.code, it.id.toString(), EmoteType.TWITCH) }
				?: listOf()
		} else listOf()
	}
	
}