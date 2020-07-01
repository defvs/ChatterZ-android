package dev.defvs.chatterz.twitch

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import java.io.IOException
import java.net.URL

open class TwitchMessageTag(
	open val name: String,
	open val data: String
) {
	companion object {
		fun parseTags(tagMessage: String): List<TwitchMessageTag> =
			tagMessage.substringBefore(":").substringAfter('@').trim().split(';')
				.map { TwitchMessageTag(it.substringBefore('='), it.substringAfter('=')) }
	}
	
	override fun toString() = "$name=$data"
}

data class Badges(
	override val data: String
) : TwitchMessageTag("badges", data) {
	val badges: List<Badge>
		get() = data.split(',')
			.map { Badge(it.substringBefore('/'), it.substringAfter('/')) }
}

data class Badge(val name: String, val version: String)

data class Emotes(
	override val data: String
) : TwitchMessageTag("emotes", data) {
	val emotes: ArrayList<Emote>
		get() {
			val emotes = arrayListOf<Emote>()
			
			data.split('/').forEach {
				val id = it.substringBefore(':').toInt()
				it.substringAfter(':').split(',').let {
					it.forEach {
						emotes.add(
							Emote(
								"",
								id,
								it.substringBefore('-').toInt(),
								it.substringAfter('-').toInt()
							)
						)
					}
				}
			}
			
			return emotes
		}
}

data class Emote(
	val name: String,
	val id: Int,
	val positionStart: Int,
	val positionEnd: Int
) {
	suspend fun loadDrawable(context: Context, size: Int = 3): BitmapDrawable {
		// TODO : cache bitmaps
		val url = URL("http://static-cdn.jtvnw.net/emoticons/v1/$id/${size.coerceIn(1..3)}.0")
		val bitmap = BitmapFactory.decodeStream(url.openStream())
		return BitmapDrawable(context.resources, bitmap)
	}
}

data class Color(
	override val data: String
) : TwitchMessageTag("color", data) {
	val color
		get() = data.ifEmpty { null }?.let { Color.parseColor(it) }
}

data class Mod(
	override val data: String
) : TwitchMessageTag("mod", data) {
	val isMod: Boolean
		get() = data.toIntOrNull()?.equals(1) ?: false
}

data class DisplayName(
	override val data: String
) : TwitchMessageTag("display_name", data) {
	val displayName = data.ifBlank { null }
}