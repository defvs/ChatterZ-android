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
import dev.defvs.chatterz.darkenColor
import dev.defvs.chatterz.lightenColor
import dev.defvs.chatterz.twitch.TwitchAPI.getUserId
import io.multimoon.colorful.Colorful
import org.jibble.pircbot.PircBot
import java.util.*


class ChatClient(
	val username: String,
	private val oauthToken: String,
	twitchAPIKey: String,
	channel: String = username,
	private val ircServer: String = "irc.chat.twitch.tv",
	private val ircPort: Int = 6667
) : PircBot() {
	private val ircChannel = "#${channel.toLowerCase(Locale.ROOT)}"
	
	val BTTVemotes = getUserId(twitchAPIKey, channel)?.let {
		ChannelBTTVEmotes.getEmotesForChannel(it)
	}
	
	suspend fun getMessageSpannable(
		context: Context,
		message: TwitchMessage,
		bttvEmotes: ChannelBTTVEmotes? = BTTVemotes,
		size: Int? = null
	): Spannable {
		with(message) {
			val spannable = SpannableStringBuilder()
			
			if (isChatEvent) {
				tags.find { it.name == "system-msg" }?.data?.let {
					spannable.bold { underline { append(it) } }
					spannable.append("\n")
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
									size
								)
							)
						}
					}
				else spannable.bold {
					append(
						badges.getBadgedSpannable(
							context,
							sender.displayName ?: sender.username,
							size
						)
					)
				}
			} ?: spannable.append(sender.displayName ?: sender.username)
			spannable.append(": ")
			
			var emoteSpan = SpannableString(this.message) as Spannable
			emoteSpan = tags.find { it.name == "emotes" }?.data?.let { Emotes(it) }
				?.getEmotedSpannable(context, emoteSpan, size) ?: emoteSpan
			
			emoteSpan = bttvEmotes?.getEmotedSpannable(context, emoteSpan, size) ?: emoteSpan
			
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
		sendLine("CAP REQ :twitch.tv/tags")
		joinChannel(ircChannel)
	}
	
	override fun handleLine(line: String?) {
		super.handleLine(line)
		Log.d("[IN]ChatClient", "$line")
		serverResponseCallback?.invoke(line)
		if (line == null) return
		
		val tags = TwitchMessageTag.parseTags(line)
		
		if (line.contains("PRIVMSG") || line.contains("USERNOTICE")) {
			val message = line.substringAfter("PRIVMSG").trim().substringAfter(" :")
			val sender = line.substringBefore(".tmi.twitch.tv").substringAfterLast("@").trim()
			receiveMessage(TwitchMessage(sender, message, tags, line.contains("USERNOTICE")))
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
	var serverResponseCallback: ((response: String?) -> Unit)? = null
	
	fun shutdown() {
		partChannel(ircChannel)
		disconnect()
	}
}