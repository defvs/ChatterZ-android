package dev.defvs.chatterz.twitch

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import androidx.core.graphics.scale
import com.beust.klaxon.Json
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import com.beust.klaxon.Parser
import dev.defvs.chatterz.autocomplete.CompletableTwitchEmote
import dev.defvs.chatterz.autocomplete.EmoteType
import dev.defvs.chatterz.autocomplete.TwitchEmotesResponse
import dev.defvs.chatterz.openHttps
import dev.defvs.chatterz.runAndNull
import io.multimoon.colorful.Colorful
import java.net.URL
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

object TwitchAPI {
	fun getFromAPI(
		url: URL,
		apiKey: String,
		oauthToken: String? = null
	) = url.openHttps().apply {
		setRequestProperty("Accept", "application/vnd.twitchtv.v5+json")
		setRequestProperty("Client-ID", apiKey)
		oauthToken?.let { setRequestProperty("Authorization", "OAuth $it") }
	}
	
	private val userIdCache: MutableMap<String /* username */, String /* id */> = mutableMapOf()
	fun getUserId(apiKey: String, username: String): String? {
		userIdCache[username]?.let { return it } // return cached ID
		
		return getFromAPI(URL("https://api.twitch.tv/kraken/users?login=$username"), apiKey).let {
			if (it.responseCode == 200)
				(Parser.default().parse(it.inputStream) as? JsonObject)
					?.array<JsonObject>("users")?.getOrNull(0)?.string("_id")
					.also {
						if (it != null) {
							userIdCache[username] = it
						}
					}
			else let { _ ->
				Log.w("TwitchAPI", "Failed to get user id for $username: response was ${it.responseCode} ${it.responseMessage}")
				null
			}
		}
	}
	
	private fun getUserInfo(apiKey: String, oauthToken: String) = getFromAPI(URL("https://api.twitch.tv/kraken/user"), apiKey, oauthToken)
		.let {
			if (it.responseCode == 200) (Parser.default().parse(it.inputStream) as? JsonObject)
			else let { _ ->
				Log.w("TwitchAPI", "Failed to fetch user info. Response was ${it.responseCode} ${it.responseMessage}")
				null
			}
		}
	
	fun getUsername(apiKey: String, oauthToken: String) = getUserInfo(apiKey, oauthToken)?.string("name")
	fun getUserIconURL(apiKey: String, oauthToken: String) = getUserInfo(apiKey, oauthToken)?.string("logo")
	
	fun getTwitchEmotes(
		apiKey: String,
		oauthToken: String,
		username: String
	): List<CompletableTwitchEmote> {
		val userId = getUserId(apiKey, username)
		val connection = getFromAPI(URL("https://api.twitch.tv/kraken/users/$userId/emotes"), apiKey, oauthToken)
		return if (connection.responseCode == 200) {
			Klaxon().parse<TwitchEmotesResponse>(connection.inputStream)?.emoteSets?.map { it.value }
				?.flatten()
				?.map { CompletableTwitchEmote(it.code, it.id.toString(), EmoteType.TWITCH) }
				?: let { Log.w("TwitchEmotesLoader", "Incorrect Response."); listOf<CompletableTwitchEmote>() }
		} else let {
			Log.w(
				"TwitchEmotesLoader",
				"Failed to fetch Twitch emotes for channel $username: response was ${connection.responseCode} ${connection.responseMessage}"
			)
			listOf<CompletableTwitchEmote>()
		}
	}
}