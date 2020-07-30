package dev.defvs.chatterz.twitch

import android.util.Log
import androidx.annotation.Keep
import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon
import dev.defvs.chatterz.openHttps
import java.net.URL

@Keep
data class ChannelFFZEmote(
	val channelEmotes: List<FFZEmote>,
	val globalEmotes: List<FFZEmote>
) {
	val allEmotes: List<FFZEmote> get() = channelEmotes + globalEmotes
	
	companion object {
		fun getEmotesForChannel(channelId: String): ChannelFFZEmote {
			var connection = URL("https://api.frankerfacez.com/v1/set/global").openHttps()
			val global = (if (connection.responseCode == 200)
				Klaxon().parse<FFZGlobalSetResponse>(connection.inputStream)?.getDefaultSets()?.flatMap { it.emoticons }
			else null) ?: let {
				Log.w(
					"FFZEmoteLoader",
					"Failed to fetch global emotes; response code was ${connection.responseCode} ${connection.responseMessage}"
				)
				emptyList<FFZEmote>()
			}
			connection = URL("https://api.frankerfacez.com/v1/room/id/$channelId").openHttps()
			val channel = (if (connection.responseCode == 200)
				Klaxon().parse<FFZRoomResponse>(connection.inputStream)?.getEmotes()
			else null) ?: let {
				Log.w(
					"FFZEmoteLoader",
					"Failed to fetch channel emotes; response code was ${connection.responseCode} ${connection.responseMessage}"
				)
				emptyList<FFZEmote>()
			}
			
			return ChannelFFZEmote(channel, global)
		}
	}
}

@Keep
data class FFZRoomResponse(val sets: HashMap<Int, FFZEmoteSet>) {
	fun getEmotes() = sets.values.flatMap { it.emoticons }
}

@Keep
data class FFZGlobalSetResponse(
	@Json(name = "default_sets") val defaultSetsIds: List<Int>,
	val sets: HashMap<String, FFZEmoteSet>
) {
	fun getDefaultSets() = sets.filterKeys { it.toInt() in defaultSetsIds }.values.toList()
}

@Keep
data class FFZEmoteSet(val emoticons: List<FFZEmote>)

@Keep
data class FFZEmote(
	val name: String,
	val id: Int,
	val urls: HashMap<String, String>
)
