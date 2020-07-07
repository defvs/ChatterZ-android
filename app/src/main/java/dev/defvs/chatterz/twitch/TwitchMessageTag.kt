package dev.defvs.chatterz.twitch

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Parcelable
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ImageSpan
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.text.set
import androidx.core.text.toSpannable
import dev.defvs.chatterz.CenteredImageSpan
import dev.defvs.chatterz.R
import dev.defvs.chatterz.autocomplete.CompletableTwitchEmote
import dev.defvs.chatterz.autocomplete.EmoteType
import kotlinx.android.parcel.Parcelize
import java.net.URL
import kotlin.math.roundToInt


@Parcelize
open class TwitchMessageTag(
	open val name: String,
	open val data: String
) : Parcelable {
	companion object {
		fun parseTags(tagMessage: String): List<TwitchMessageTag> =
			tagMessage.substringBefore("tmi.twitch.tv").substringBeforeLast(":").substringAfter('@')
				.trim().split(';')
				.map { TwitchMessageTag(it.substringBefore('='), it.substringAfter('=')) }
	}
	
	override fun toString() = "$name=$data"
}

@Parcelize
data class Badges(
	override val data: String
) : TwitchMessageTag("badges", data) {
	val badges: List<Badge>
		get() = data.split(',')
			.map { Badge(it.substringBefore('/'), it.substringAfter('/')) }
	
	fun getBadgedSpannable(context: Context, sender: String, size: Int? = null): Spannable {
		val builder = SpannableStringBuilder()
		badges.forEach { badge ->
			badge.getBadgeDrawable(context, size?.div(2))?.let {
				builder.append("${badge.name.substring(0..2)} ")
				val range = builder.indexOf(badge.name.substring(0..2)).let { it..(it + 3) }
				builder[range] = ImageSpan(it, ImageSpan.ALIGN_BASELINE)
			}
		}
		builder.append(sender)
		
		return builder.toSpannable()
	}
}

data class Badge(val name: String, val version: String? = null, val imageUrl: String? = null) {
	fun getBadgeDrawable(context: Context, width: Int? = null) = when (name) {
		"moderator" -> ContextCompat.getDrawable(context, R.drawable.ic_moderator_24)
		"vip" -> ContextCompat.getDrawable(context, R.drawable.ic_star_24)
		"subscriber", "founder" -> ContextCompat.getDrawable(context, R.drawable.ic_subscriber_24)
		else -> null
	}?.let { drawable ->
		width?.let {
			BitmapDrawable(
				context.resources,
				drawable.toBitmap(width, width)
			)
		} ?: drawable
	}?.apply {
		setBounds(0, 0,  width ?: 48, width ?: 48)
	}
}

@Parcelize
data class Emotes(
	override val data: String
) : TwitchMessageTag("emotes", data) {
	val emotes: ArrayList<Emote>
		get() {
			val emotes = arrayListOf<Emote>()
			if (data.isBlank()) return emotes
			
			data.split('/').forEach {
				val id = it.substringBefore(':')
				it.substringAfter(':').split(',').let {
					it.forEach {
						emotes.add(
							Emote(
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
	
	suspend fun getEmotedSpannable(context: Context, message: TwitchMessage) =
		getEmotedSpannable(context, SpannableString(message.message))
	
	suspend fun getEmotedSpannable(context: Context, spannable: Spannable, width: Int? = null): Spannable {
		emotes.forEach {
			val emote = it.getDrawable(context, width = width)
			val image = ImageSpan(emote, ImageSpan.ALIGN_BASELINE)
			spannable.setSpan(
				image,
				it.positionStart,
				it.positionEnd + 1,
				Spannable.SPAN_INCLUSIVE_EXCLUSIVE
			)
		}
		return spannable
	}
}

data class Emote(
	val id: String,
	val positionStart: Int,
	val positionEnd: Int
) {
	suspend fun getDrawable(context: Context, size: Int = 2, width: Int? = null) =
		CompletableTwitchEmote("", id, EmoteType.TWITCH).getDrawable(context, size, width)
}

@Parcelize
data class Color(
	override val data: String
) : TwitchMessageTag("color", data) {
	val color
		get() = data.ifEmpty { null }?.let { Color.parseColor(it) }
}

@Parcelize
data class Mod(
	override val data: String
) : TwitchMessageTag("mod", data) {
	val isMod: Boolean
		get() = data.toIntOrNull()?.equals(1) ?: false
}

@Parcelize
data class DisplayName(
	override val data: String
) : TwitchMessageTag("display_name", data) {
	val displayName = data.ifBlank { null }
}