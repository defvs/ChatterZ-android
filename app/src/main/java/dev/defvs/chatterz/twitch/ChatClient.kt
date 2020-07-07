package dev.defvs.chatterz.twitch

import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.util.Log
import androidx.core.text.bold
import androidx.core.text.color
import androidx.core.text.toSpannable
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import dev.defvs.chatterz.darkenColor
import dev.defvs.chatterz.lightenColor
import io.multimoon.colorful.Colorful
import org.jibble.pircbot.PircBot
import java.net.HttpURLConnection
import java.net.URL
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
		bttvEmotes: ChannelBTTVEmotes? = BTTVemotes
	): Spannable {
		with(message) {
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
			
			var emoteSpan = SpannableString(this.message) as Spannable
			emoteSpan = tags.find { it.name == "emotes" }?.data?.let { Emotes(it) }
				?.getEmotedSpannable(context, emoteSpan) ?: emoteSpan
			
			emoteSpan = bttvEmotes?.getEmotedSpannable(context, emoteSpan) ?: emoteSpan
			
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
		serverResponseEvent?.invoke(line)
		if (line == null) return
		
		val tags = TwitchMessageTag.parseTags(line)
		
		if (line.contains("PRIVMSG")) {
			val message = line.substringAfter("PRIVMSG").trim().substringAfter(" :")
			val sender = line.substringBefore(".tmi.twitch.tv").substringAfterLast("@").trim()
			receiveMessage(TwitchMessage(sender, message, tags))
		}
		
	}
	
	private fun sendLine(line: String) {
		Log.d("[OUT]ChatClient", line)
		sendRawLineViaQueue(line)
	}
	
	private fun receiveMessage(message: TwitchMessage) {
		messageReceivedEvent?.invoke(message)
	}
	
	override fun onDisconnect() {
		disconnectedEvent?.invoke()
	}
	
	fun sendMessage(message: String) = sendLine("PRIVMSG $ircChannel :$message")
	
	var messageReceivedEvent: ((message: TwitchMessage) -> Unit)? = null
	var disconnectedEvent: (() -> Unit)? = null
	var serverResponseEvent: ((response: String?) -> Unit)? = null
	
	fun shutdown() {
		partChannel(ircChannel)
		disconnect()
	}
	
	companion object {
		private val userIdCache: MutableMap<String, String> = mutableMapOf()
		fun getUserId(apiKey: String, username: String): String? {
			userIdCache[username]?.let { return it } // return cached ID
			val url = URL("https://api.twitch.tv/kraken/users?login=$username")
			
			val data: HttpURLConnection = (url.openConnection() as HttpURLConnection).apply {
				setRequestProperty("Accept", "application/vnd.twitchtv.v5+json")
				setRequestProperty("Client-ID", apiKey)
			}
			
			if (data.responseCode != 200) return null
			return (Parser.default().parse(data.inputStream) as? JsonObject)
				?.array<JsonObject>("users")?.get(0)?.string("_id")
				.also { if (it != null) { userIdCache[username] = it } }
		}
	}
}