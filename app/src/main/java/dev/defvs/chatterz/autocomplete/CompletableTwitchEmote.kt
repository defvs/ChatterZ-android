package dev.defvs.chatterz.autocomplete

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon
import dev.defvs.chatterz.twitch.ChannelBTTVEmotes
import dev.defvs.chatterz.twitch.ChatClient
import java.net.HttpURLConnection
import java.net.URL

data class CompletableTwitchEmote(
	val name: String,
	val id: String,
	val type: EmoteType
) {
	suspend fun getDrawable(context: Context, size: Int): BitmapDrawable {
		val url: URL = when (type) {
			EmoteType.BTTV -> {
				URL("https://cdn.betterttv.net/emote/$id/${size.coerceIn(1..3)}x")
			}
			EmoteType.TWITCH -> {
				URL("https://static-cdn.jtvnw.net/emoticons/v1/$id/${size.coerceIn(1..3)}.0")
			}
		}
		val bitmap = BitmapFactory.decodeStream(url.openConnection().apply { useCaches = true }
			.getInputStream())
		
		return BitmapDrawable(context.resources, bitmap).apply {
			setBounds(
				0,
				0,
				bitmap.width,
				bitmap.height
			)
		}
	}
	
	companion object {
		fun getAllEmotes(
			channelId: String,
			apiKey: String,
			oauthId: String,
			username: String
		): List<CompletableTwitchEmote> {
			val list = arrayListOf<CompletableTwitchEmote>()
			
			// BTTV
			list.addAll(ChannelBTTVEmotes.getEmotesForChannel(channelId).getAllEmotes()
				.map {
					CompletableTwitchEmote(
						it.name,
						it.id,
						EmoteType.BTTV
					)
				})
			
			// TWITCH
			list.addAll(
				getTwitchEmotes(
					apiKey,
					oauthId,
					username
				)
			)
			
			return list
		}
		
		private fun getTwitchEmotes(
			apiKey: String,
			oauthId: String,
			username: String
		): List<CompletableTwitchEmote> {
			val userId = ChatClient.getUserId(apiKey, username)
			val connection = URL("https://api.twitch.tv/kraken/users/$userId/emotes")
				.openConnection() as HttpURLConnection
			connection.apply {
				addRequestProperty("Client-ID", apiKey)
				addRequestProperty("Authorization", "OAuth $oauthId")
				addRequestProperty("Accept", "application/vnd.twitchtv.v5+json")
			}
			if (connection.responseCode != 200) return listOf()
			return Klaxon().parse<TwitchEmotesResponse>(connection.inputStream)?.emoteSets?.map { it.value }
				?.flatten()?.map { CompletableTwitchEmote(it.code, it.id.toString(), EmoteType.TWITCH) }
				?: listOf()
		}
	}
}

data class TwitchEmotesResponse(@Json(name = "emoticon_sets") val emoteSets: Map<String, ArrayList<EmoticonSet>>)
data class EmoticonSet(val code: String, val id: Int)

enum class EmoteType { BTTV, TWITCH }