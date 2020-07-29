package dev.defvs.chatterz.twitch

import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.util.Log
import androidx.core.text.bold
import androidx.core.text.color
import androidx.core.text.toSpannable
import androidx.core.text.underline
import dev.defvs.chatterz.autocomplete.CompletableTwitchEmote
import dev.defvs.chatterz.autocomplete.CompletableTwitchEmote.Companion.getEmoteSpannable
import dev.defvs.chatterz.darkenColor
import dev.defvs.chatterz.lightenColor
import io.multimoon.colorful.Colorful
import org.jibble.pircbot.PircBot
import java.util.*


class ChatClient(
	val username: String,
	private val oauthToken: String,
	channel: String = username,
	private val emotes: List<CompletableTwitchEmote>,
	private val ircServer: String = "irc.chat.twitch.tv",
	private val ircPort: Int = 6667
) : PircBot() {
	private val ircChannel = "#${channel.toLowerCase(Locale.ROOT)}"
	
	suspend fun getMessageSpannable(
		context: Context,
		message: TwitchMessage,
		width: Int?
	): Spannable {
		with(message) {
			val spannable = SpannableStringBuilder()
			
			if (isChatEvent) {
				tags.find { it.name == "system-msg" }?.data?.let {
					spannable.bold { underline { append(it.replace("\\s", " ")) } }
					if (message.message.isNotBlank()) spannable.append("\n")
				}
			}
			
			val color =
				if (Colorful().getDarkTheme()) sender.color?.lightenColor() else sender.color?.darkenColor()
			tags.find { it.name == "badges" }?.data?.let { Badges(it) }?.let { badges ->
				if (color != null)
					spannable.bold {
						color(color) {
							append(
								badges.getBadgedSpannable(
									context,
									sender.displayName ?: sender.username,
									width
								)
							)
						}
					}
				else spannable.bold {
					append(
						badges.getBadgedSpannable(
							context,
							sender.displayName ?: sender.username,
							width
						)
					)
				}
			} ?: spannable.append(sender.displayName ?: sender.username)
			spannable.append(": ")
			
			var emoteSpan = SpannableString(this.message) as Spannable
			emoteSpan = tags.find { it.name == "emotes" }?.data?.let { Emotes(it) }
				?.getEmotedSpannable(context, emoteSpan, width) ?: emoteSpan
			
			emoteSpan = emotes.getEmoteSpannable(context, emoteSpan, width)
			
			if (isAction && color != null)
				spannable.color(color) { append(emoteSpan) }
			else spannable.append(emoteSpan)
			
			return spannable.toSpannable()
		}
	}
	
	init {
		connect()
	}
	
	fun connect() {
		name = username
		Log.d("ChatClient", "Initializing client")
		try {
			connect(ircServer, ircPort, "oauth:$oauthToken")
		} catch (e: Exception) {
			Log.w("ChatClient", "Could not connect to IRC", e)
			return
		}
		Log.d("ChatClient", "IRC connected")
		joinChannel(ircChannel)
	}
	
	var userTags = listOf<TwitchMessageTag>()
	
	override fun handleLine(line: String?) {
		Log.d("[IN]ChatClient", "$line")
		super.handleLine(line)
		serverResponseCallback?.invoke(line)
		if (line == null) return
		
		val tags = TwitchMessageTag.parseTags(line)
		
		when {
			line.contains("PRIVMSG") || line.contains("USERNOTICE") -> {
				val message = line.substringAfter("PRIVMSG").substringAfter("USERNOTICE").trim().substringAfter(ircChannel).substringAfter(" :")
				val sender = line.substringBefore(".tmi.twitch.tv").substringAfterLast("@").trim()
				receiveMessage(TwitchMessage(sender, message, tags, line.contains("USERNOTICE")))
			}
			line.contains("GLOBALUSERSTATE") -> {
				userTags = tags
			}
		}
		
	}
	
	private fun sendLine(line: String) {
		Log.d("[OUT]ChatClient", line)
		sendRawLineViaQueue(line)
	}
	
	private fun receiveMessage(message: TwitchMessage) {
		messageReceivedCallback?.invoke(message)
	}
	
	override fun onDisconnect() {
		disconnectedCallback?.invoke()
	}
	
	fun sendMessage(message: String) = sendLine("PRIVMSG $ircChannel :$message")
	
	var messageReceivedCallback: ((message: TwitchMessage) -> Unit)? = null
	var disconnectedCallback: (() -> Unit)? = null
	private var serverResponseCallback: ((response: String?) -> Unit)? = null
	
	fun shutdown() {
		partChannel(ircChannel)
		disconnect()
	}
}