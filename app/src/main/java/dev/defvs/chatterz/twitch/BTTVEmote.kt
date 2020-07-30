package dev.defvs.chatterz.twitch

import android.util.Log
import androidx.annotation.Keep
import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon
import dev.defvs.chatterz.openHttps
import java.net.URL

@Keep
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
				.openHttps()
			if (connection.responseCode != 200) return let {
				Log.w(
					"BTTVEmoteLoader",
					"Failed to fetch emotes for $channelId: response was ${connection.responseCode} ${connection.responseMessage}"
				)
				ChannelBTTVEmotes(listOf(), listOf(), global)
			}
			return Klaxon().parse<ChannelBTTVEmotes>(connection.inputStream)?.apply { globalEmotes = global }
				?: let {
					Log.w(
						"BTTVEmoteLoader",
						"Failed to fetch emotes for $channelId: response was ${connection.responseCode} ${connection.responseMessage}"
					)
					ChannelBTTVEmotes(listOf(), listOf(), global)
				}
		}
	}
}

@Keep
data class BTTVEmote(
	val id: String,
	@Json(name = "code") val name: String,
	val imageType: String
)
