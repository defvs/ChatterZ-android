package dev.defvs.chatterz.autocomplete

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import androidx.core.graphics.scale
import com.beust.klaxon.Json
import dev.defvs.chatterz.twitch.ChannelBTTVEmotes
import dev.defvs.chatterz.twitch.TwitchAPI
import java.net.URL

data class CompletableTwitchEmote(
	val name: String,
	val id: String,
	val type: EmoteType
) {
	suspend fun getDrawable(context: Context, apiSize: Int = 2, width: Int?): BitmapDrawable {
		val bitmap: Bitmap = TwitchEmoteCache.cache[this] ?: let {
			val url: URL = when (type) {
				EmoteType.BTTV -> {
					URL("https://cdn.betterttv.net/emote/$id/${apiSize.coerceIn(1..3)}x")
				}
				EmoteType.TWITCH -> {
					URL("https://static-cdn.jtvnw.net/emoticons/v1/$id/${apiSize.coerceIn(1..3)}.0")
				}
			}
			BitmapFactory.decodeStream(url.openConnection().apply { useCaches = true }
				.getInputStream()).also { TwitchEmoteCache.cache[this] = it }
		}
		
		return BitmapDrawable(
			context.resources,
			width?.let { bitmap.scale(it, it) } ?: bitmap).apply {
			setBounds(
				0,
				0,
				width ?: bitmap.width,
				width ?: bitmap.height
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
				TwitchAPI.getTwitchEmotes(
					apiKey,
					oauthId,
					username
				)
			)
			
			return list
		}
	}
}

data class TwitchEmotesResponse(@Json(name = "emoticon_sets") val emoteSets: Map<String, ArrayList<EmoticonSet>>)
data class EmoticonSet(val code: String, val id: Int)

enum class EmoteType { BTTV, TWITCH }