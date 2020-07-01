package dev.defvs.chatterz.twitch

import android.util.Log
import org.jibble.pircbot.PircBot
import java.lang.Exception
import java.util.*
import kotlin.collections.ArrayList


class ChatClient(
	val username: String,
	private val oauthToken: String,
	channel: String = username,
	private val ircServer: String = "irc.chat.twitch.tv",
	private val ircPort: Int = 6667
) : PircBot() {
	private val ircChannel = "#${channel.toLowerCase(Locale.ROOT)}"
	
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
		connect()
	}
	
	fun sendMessage(message: String) = sendLine("PRIVMSG $ircChannel :$message")
	
	var messageReceivedEvent: ((message: TwitchMessage) -> Unit)? = null
	var disconnectedEvent: (() -> Unit)? = null
	var serverResponseEvent: ((response: String?) -> Unit)? = null
	
	fun shutdown() {
		partChannel(ircChannel)
		disconnect()
	}
}