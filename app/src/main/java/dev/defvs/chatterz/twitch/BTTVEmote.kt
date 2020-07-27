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
	val allEmotes: List<BTTVEmote> get() = channelEmotes + sharedEmotes + globalEmotes
	
	companion object {
		fun getEmotesForChannel(channelId: String): ChannelBTTVEmotes {
			val global = Klaxon().parseArray<BTTVEmote>(
				URL("https://api.betterttv.net/3/cached/emotes/global").openStream()
			) ?: listOf()
			val connection = URL("https://api.betterttv.net/3/cached/users/twitch/$channelId")
				.openConnection() as HttpURLConnection
			if (connection.responseCode != 200) return ChannelBTTVEmotes(listOf(), listOf(), global)
			return Klaxon().parse<ChannelBTTVEmotes>(connection.inputStream)?.apply { globalEmotes = global }
				?: ChannelBTTVEmotes(listOf(), listOf(), global)
		}
	}
}

data class BTTVEmote(
	val id: String,
	@Json(name = "code") val name: String,
	val imageType: String
)
