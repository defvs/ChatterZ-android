package dev.defvs.chatterz.autocomplete

import android.graphics.Bitmap

object TwitchEmoteCache {
	val cache: HashMap<CompletableTwitchEmote, Bitmap> = hashMapOf()
}