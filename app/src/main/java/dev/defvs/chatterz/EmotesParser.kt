package dev.defvs.chatterz

import dev.defvs.chatterz.twitch.Badge

class EmotesParser(
	val message: String,
	val emotes: List<Badge>
)