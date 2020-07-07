package dev.defvs.chatterz.twitch

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.text.Spannable
import android.text.style.ImageSpan
import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon
import java.net.HttpURLConnection
import java.net.URL

data class ChannelBTTVEmotes(
	val channelEmotes: List<BTTVEmote>,
	val sharedEmotes: List<BTTVEmote>,
	@Json(ignored = true) var globalEmotes: List<BTTVEmote> = listOf()
) {
	fun getAllEmotes() = channelEmotes + sharedEmotes + globalEmotes
	
	suspend fun getEmotedSpannable(context: Context, spannable: Spannable): Spannable {
		getAllEmotes().forEach {
			val start = spannable.indexOf(it.name)
			if (start in 0 until spannable.length) {
				val emote = it.getEmoteDrawable(context, size = 2)
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
) {
	suspend fun getEmoteDrawable(context: Context, size: Int = 2): BitmapDrawable {
		// TODO: cache
		val url = URL("https://cdn.betterttv.net/emote/$id/${size.coerceIn(1..3)}x")
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
}
