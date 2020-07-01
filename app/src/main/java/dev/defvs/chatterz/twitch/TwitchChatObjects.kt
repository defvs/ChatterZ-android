package dev.defvs.chatterz.twitch

data class TwitchMessage(
	val sender: TwitchUser,
	val message: String,
	val tags: List<TwitchMessageTag>
) {
	constructor(sender: String, message: String) : this(TwitchUser(sender), message, listOf())
	
	constructor(sender: String, message: String, tags: List<TwitchMessageTag>) : this(
		TwitchUser(
			sender,
			tags
		), message, tags
	)
}

data class TwitchUser(
	val username: String
) {
	var isMod: Boolean = false
	var color: Int? = null
	var displayName: String? = null
	
	constructor(username: String, tags: List<TwitchMessageTag>) : this(username) {
		fromTags(tags)
	}
	
	fun fromTags(tags: List<TwitchMessageTag>) {
		isMod = tags.find { it.name == "mod" }?.data?.let { Mod(it).isMod } ?: false
		color = tags.find { it.name == "color" }?.data?.let { Color(it).color }
		displayName = tags.find { it.name == "display-name" }?.data?.let { DisplayName(it).displayName }
	}
}
