package dev.defvs.chatterz.twitch

import dev.defvs.chatterz.nullIfEmpty

data class TwitchMessage(
	val sender: TwitchUser,
	var message: String,
	val tags: List<TwitchMessageTag>,
	val isChatEvent: Boolean = false
) {
	var isAction: Boolean
	
	constructor(sender: String, message: String, isChatEvent: Boolean = false) : this(TwitchUser(sender), message, listOf(), isChatEvent)
	
	constructor(sender: String, message: String, tags: List<TwitchMessageTag>, isChatEvent: Boolean = false) : this(
		TwitchUser(
			sender,
			tags
		), message, tags, isChatEvent
	)
	
	init {
		message = message.trim()
		if (message.startsWith("\u0001ACTION")) {
			message = message.removePrefix("\u0001ACTION")
			isAction = true
		} else isAction = false
	}
}

data class TwitchUser(
	val username: String,
	var isMod: Boolean = false,
	var color: Int? = null,
	var displayName: String? = null
) {
	constructor(username: String, tags: List<TwitchMessageTag>) : this(username) {
		fromTags(tags)
	}
	
	private fun fromTags(tags: List<TwitchMessageTag>) {
		isMod = tags.find { it.name == "mod" }?.data?.let { Mod(it).isMod } ?: false
		color = tags.find { it.name == "color" }?.data?.let { Color(it).color }
		displayName =
			tags.find { it.name == "display-name" }?.data?.let { DisplayName(it).displayName }
	}
	
	override fun toString(): String {
		return displayName.nullIfEmpty() ?: username
	}
}