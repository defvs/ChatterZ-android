package dev.defvs.chatterz.twitch

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.text.Spannable
import android.text.SpannableStringBuilder
import androidx.core.text.bold
import androidx.core.text.color
import androidx.core.text.toSpannable
import dev.defvs.chatterz.darkenColor
import dev.defvs.chatterz.lightenColor
import io.multimoon.colorful.Colorful
import kotlinx.android.parcel.Parcelize

data class TwitchMessage(
	val sender: TwitchUser,
	var message: String,
	val tags: List<TwitchMessageTag>
) : Parcelable {
	var isAction: Boolean
	
	constructor(parcel: Parcel) : this(
		parcel.readParcelable<TwitchUser>(TwitchUser::class.java.classLoader) ?: TwitchUser(""),
		parcel.readString() ?: "",
		parcel.readParcelableArray(TwitchMessageTag::class.java.classLoader)!!
			.toList() as? List<TwitchMessageTag> ?: listOf()
	) {
		isAction = parcel.readByte() != 0.toByte()
	}
	
	constructor(sender: String, message: String) : this(TwitchUser(sender), message, listOf())
	
	constructor(sender: String, message: String, tags: List<TwitchMessageTag>) : this(
		TwitchUser(
			sender,
			tags
		), message, tags
	)
	
	init {
		message = message.trim()
		if (message.startsWith("\u0001ACTION")) {
			message = message.removePrefix("\u0001ACTION")
			isAction = true
		} else isAction = false
	}
	
	suspend fun getMessageSpannable(context: Context): Spannable {
		val spannable = SpannableStringBuilder()
		
		val color =
			if (Colorful().getDarkTheme()) sender.color?.lightenColor() else sender.color?.darkenColor()
		tags.find { it.name == "badges" }?.data?.let { Badges(it) }?.let { badges ->
			if (color != null)
				spannable.bold {
					color(color) {
						append(
							badges.getBadgedSpannable(
								context,
								sender.displayName ?: sender.username
							)
						)
					}
				}
			else spannable.bold {
				append(
					badges.getBadgedSpannable(
						context,
						sender.displayName ?: sender.username
					)
				)
			}
		} ?: spannable.append(sender.displayName ?: sender.username)
		spannable.append(": ")
		tags.find { it.name == "emotes" }?.data?.let { Emotes(it) }?.let { emotes ->
			val emoteSpan = emotes.getEmotedSpannable(context, this@TwitchMessage)
			if (isAction && color != null)
				spannable.color(color) { append(emoteSpan) }
			else spannable.append(emoteSpan)
		} ?: spannable.append(message)
		
		return spannable.toSpannable()
	}
	
	override fun writeToParcel(parcel: Parcel, flags: Int) {
		parcel.writeParcelable(sender, Parcelable.PARCELABLE_WRITE_RETURN_VALUE)
		parcel.writeString(message)
		parcel.writeParcelableArray(tags.toTypedArray(), Parcelable.PARCELABLE_WRITE_RETURN_VALUE)
		parcel.writeByte(if (isAction) 1 else 0)
	}
	
	override fun describeContents(): Int {
		return 0
	}
	
	companion object CREATOR : Parcelable.Creator<TwitchMessage> {
		override fun createFromParcel(parcel: Parcel): TwitchMessage {
			return TwitchMessage(parcel)
		}
		
		override fun newArray(size: Int): Array<TwitchMessage?> {
			return arrayOfNulls(size)
		}
	}
}

@Parcelize
data class TwitchUser(
	val username: String
) : Parcelable {
	var isMod: Boolean = false
	var color: Int? = null
	var displayName: String? = null
	
	constructor(username: String, tags: List<TwitchMessageTag>) : this(username) {
		fromTags(tags)
	}
	
	fun fromTags(tags: List<TwitchMessageTag>) {
		isMod = tags.find { it.name == "mod" }?.data?.let { Mod(it).isMod } ?: false
		color = tags.find { it.name == "color" }?.data?.let { Color(it).color }
		displayName =
			tags.find { it.name == "display-name" }?.data?.let { DisplayName(it).displayName }
	}
}
