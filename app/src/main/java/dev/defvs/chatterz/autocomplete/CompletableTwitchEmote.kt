package dev.defvs.chatterz.autocomplete

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.text.Spannable
import android.text.style.ImageSpan
import androidx.core.graphics.scale
import com.beust.klaxon.Json
import dev.defvs.chatterz.twitch.*
import java.net.URL
import kotlin.math.roundToInt

data class CompletableTwitchEmote(
	val name: String,
	val id: String,
	val type: EmoteType
) {
	constructor(name: String, id: String, type: EmoteType, urls: Map<String, String>): this(name, id, type) {
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
		suspend fun List<CompletableTwitchEmote>.getEmoteSpannable(context: Context, spannable: Spannable, width: Int?, apiSize: Int = 2): Spannable {
			this.filter { it.type == EmoteType.BTTV || it.type == EmoteType.FFZ }.forEach {
				spannable.mapIndexed { index, _ -> spannable.indexOf(it.name, index) }
					.filter { it in 0 until spannable.length }.forEach { start ->
						val emote = it.getDrawable(
							context,
							apiSize,
							width
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
		
		fun getAllEmotes(
			channelId: String,
			apiKey: String,
			oauthId: String,
			username: String
		): List<CompletableTwitchEmote> {
			val list = arrayListOf<CompletableTwitchEmote>()
			
			// BTTV
			list.addAll(ChannelBTTVEmotes.getEmotesForChannel(channelId).allEmotes
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
			
			// FFZ
			list.addAll(ChannelFFZEmote.getEmotesForChannel(channelId).allEmotes.map {
				CompletableTwitchEmote(
					it.name,
					it.id.toString(),
					EmoteType.FFZ,
					it.urls
				)
			})
			
			return list
		}
	}
}

data class TwitchEmotesResponse(@Json(name = "emoticon_sets") val emoteSets: Map<String, ArrayList<EmoticonSet>>)
data class EmoticonSet(val code: String, val id: Int)

enum class EmoteType { FFZ, BTTV, TWITCH }