package dev.defvs.chatterz.autocomplete

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.text.Spannable
import android.text.style.ImageSpan
import androidx.annotation.Keep
import androidx.core.graphics.scale
import com.beust.klaxon.Json
import dev.defvs.chatterz.twitch.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.net.URL
import kotlin.math.roundToInt

@Keep
data class CompletableTwitchEmote(
	val name: String,
	val id: String,
	val type: EmoteType
) {
	constructor(name: String, id: String, type: EmoteType, urls: Map<String, String>) : this(name, id, type) {
		this.urls = urls
	}
	
	private var urls: Map<String, String>? = null
	suspend fun getDrawable(context: Context, apiSize: Int = 3, height: Int? = null): BitmapDrawable {
		val bitmap: Bitmap = TwitchEmoteCache.cache[this] ?: let {
			val url: URL = when (type) {
				EmoteType.BTTV -> {
					URL("https://cdn.betterttv.net/emote/$id/${apiSize.coerceIn(1..3)}x")
				}
				EmoteType.TWITCH -> {
					URL("https://static-cdn.jtvnw.net/emoticons/v1/$id/${apiSize.coerceIn(1..3)}.0")
				}
				EmoteType.FFZ -> {
					urls ?: throw NullPointerException("Type is FFZ and urls are null")
					val url = if (apiSize.coerceIn(1..3) == 3) {
						urls!![4.toString()]
					} else urls!![apiSize.coerceIn(1..2).toString()]
					URL("https:" + (url ?: urls!!.values.first()))
				}
			}
			BitmapFactory.decodeStream(url.openConnection().apply { useCaches = true }
				.getInputStream()).also { TwitchEmoteCache.cache[this] = it }
		}
		
		val scaleRatio = height?.div(bitmap.height.toFloat())
		return BitmapDrawable(
			context.resources,
			height?.let {
				bitmap.scale((bitmap.width * scaleRatio!!).roundToInt(), (bitmap.height * scaleRatio).roundToInt())
			} ?: bitmap).apply {
			setBounds(
				0,
				0,
				(scaleRatio?.times(bitmap.width))?.roundToInt() ?: bitmap.width,
				(scaleRatio?.times(bitmap.height))?.roundToInt() ?: bitmap.height
			)
		}
	}
	
	companion object {
		suspend fun List<CompletableTwitchEmote>.getEmoteSpannable(
			context: Context,
			spannable: Spannable,
			width: Int?,
			apiSize: Int = 2,
			parseTwitchEmotes: Boolean
		): Spannable {
			this.filter { it.type == EmoteType.BTTV || it.type == EmoteType.FFZ || (parseTwitchEmotes && it.type == EmoteType.TWITCH)}.forEach {
				val spacePositions = arrayListOf(0)
				spannable.forEachIndexed {i, c -> if (c == ' ') spacePositions += (i + 1) }
				spannable.split(" ").forEachIndexed { index, s ->
					if (s != it.name) return@forEachIndexed
					val emote = it.getDrawable(
						context,
						apiSize,
						width
					)
					val image = ImageSpan(emote, ImageSpan.ALIGN_BASELINE)
					
					val start = spacePositions[index]
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
		
		suspend fun getAllEmotes(
			channelId: String,
			apiKey: String,
			oauthId: String,
			username: String
		): List<CompletableTwitchEmote> = coroutineScope {
			val list = arrayListOf<CompletableTwitchEmote>()
			
			// TWITCH
			launch {
				list.addAll(
					TwitchAPI.getTwitchEmotes(
						apiKey,
						oauthId,
						username
					)
				)
			}
			
			// BTTV
			launch {
				list.addAll(ChannelBTTVEmotes.getEmotesForChannel(channelId).allEmotes
					.map {
						CompletableTwitchEmote(
							it.name,
							it.id,
							EmoteType.BTTV
						)
					})
			}
			
			// FFZ
			launch {
				list.addAll(ChannelFFZEmote.getEmotesForChannel(channelId).allEmotes.map {
					CompletableTwitchEmote(
						it.name,
						it.id.toString(),
						EmoteType.FFZ,
						it.urls
					)
				})
			}
			
			list
		}
	}
}

@Keep
data class TwitchEmotesResponse(@Json(name = "emoticon_sets") val emoteSets: Map<String, ArrayList<EmoticonSet>>)

@Keep
data class EmoticonSet(val code: String, val id: Int)

@Keep
enum class EmoteType { FFZ, BTTV, TWITCH }