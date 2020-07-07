package dev.defvs.chatterz.twitch

import android.content.Context
import android.text.Spannable
import android.text.style.ImageSpan
import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon
import dev.defvs.chatterz.autocomplete.CompletableTwitchEmote
import dev.defvs.chatterz.autocomplete.EmoteType
import java.net.HttpURLConnection
import java.net.URL

data class ChannelBTTVEmotes(
	val channelEmotes: List<BTTVEmote>,
	val sharedEmotes: List<BTTVEmote>,
	@Json(ignored = true) var globalEmotes: List<BTTVEmote> = listOf()
) {
	fun getAllEmotes() = channelEmotes + sharedEmotes + globalEmotes
	
	suspend fun getEmotedSpannable(
		context: Context,
		spannable: Spannable,
		width: Int? = null
	): Spannable {
		getAllEmotes().forEach {
			spannable.mapIndexed { index, _ -> spannable.indexOf(it.name, index) }
				.filter { it in 0 until spannable.length }.forEach { start ->
				val emote = CompletableTwitchEmote(it.name, it.id, EmoteType.BTTV).getDrawable(
					context,
					width = width
				)
				val image = ImageSpan(emote, ImageSpan.ALIGN_BASELINE)
				val end = start + it.name.length
				spannable.setSpan(
					image,
					start,
					end,
					Spannable.SPAN_INCLUSIVE_EXCLUSIVE
				)
			}
		}
		return spannable
	}
	
	companion object {
		fun getEmotesForChannel(channelId: String): ChannelBTTVEmotes {
			val global = Klaxon().parseArray<BTTVEmote>(
				URL("https://api.betterttv.net/3/cached/emotes/global").openStream()
			) ?: listOf()
			val connection = URL("https://api.betterttv.net/3/cached/users/twitch/$channelId")
				.openConnection() as HttpURLConnection
			if (connection.responseCode != 200) return ChannelBTTVEmotes(listOf(), listOf(), global)
			return Klaxon().parse<ChannelBTTVEmotes>(connection.inputStream)
				?: ChannelBTTVEmotes(listOf(), listOf(), global)
		}
	}
}

data class BTTVEmote(
	val id: String,
	@Json(name = "code") val name: String,
	val imageType: String
)
