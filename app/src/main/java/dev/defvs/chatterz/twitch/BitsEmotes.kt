package dev.defvs.chatterz.twitch

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.text.style.StyleSpan
import android.util.Log
import androidx.core.graphics.scale
import androidx.core.text.bold
import androidx.core.text.color
import com.beust.klaxon.Json
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import com.beust.klaxon.Parser
import dev.defvs.chatterz.openHttps
import dev.defvs.chatterz.runAndNull
import io.multimoon.colorful.Colorful
import java.net.URL
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

object BitsEmotes {
	
	fun getEmoteSpannable(
		spannable: Spannable,
		context: Context,
		apiKey: String,
		width: Int? = 56,
		dark: Boolean = Colorful().getDarkTheme(),
		animated: Boolean = false,
		apiSize: Int = 4
	): Spannable {
		val spacePositions = arrayListOf(0)
		spannable.forEachIndexed { i, c -> if (c == ' ') spacePositions += (i + 1) }
		spannable.split(" ").forEachIndexed { index, s ->
			val regex = "([a-zA-Z]+)([0-9]+)".toRegex()
			if (!regex.matches(s)) return@forEachIndexed
			val regexResult = regex.find(s)
			val prefix = regexResult?.groupValues?.getOrNull(1) ?: return@forEachIndexed
			val amount = regexResult.groupValues.getOrNull(2) ?: return@forEachIndexed
			
			val emoteDrawable = getTwitchBitsEmoteDrawable(context, apiKey, prefix, amount.toInt(), width, dark, animated, apiSize)
			if (emoteDrawable != null) {
				val image = ImageSpan(emoteDrawable, ImageSpan.ALIGN_BASELINE)
				spannable.setSpan(
					image,
					regexResult.range.first + spacePositions[index],
					regexResult.groups[1]!!.range.last + spacePositions[index] + 1,
					Spannable.SPAN_INCLUSIVE_EXCLUSIVE
				)
				spannable.setSpan(
					StyleSpan(android.graphics.Typeface.BOLD),
					regexResult.groups[2]!!.range.first + spacePositions[index],
					regexResult.groups[2]!!.range.last + spacePositions[index] + 1,
					Spannable.SPAN_INCLUSIVE_EXCLUSIVE
				)
				getBitsActions(apiKey)?.getBitsTier(prefix, amount.toInt())?.color?.let {
					spannable.setSpan(
						ForegroundColorSpan(Color.parseColor(it)),
						regexResult.groups[2]!!.range.first + spacePositions[index],
						regexResult.groups[2]!!.range.last + spacePositions[index] + 1,
						Spannable.SPAN_INCLUSIVE_EXCLUSIVE
					)
				}
			}
		}
		return spannable
	}
	
	@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
	data class BitsTier(
		@Json("min_bits") val minBits: java.lang.Integer,
		val id: String,
		val color: String,
		val images: Map<String, Map<String, Map<String, String>>>,
		val canCheer: Boolean = true
	)
	
	private fun List<BitsAction>.getBitsEmoteURL(
		prefix: String,
		bitsAmount: Int,
		dark: Boolean,
		animated: Boolean,
		scale: String
	): String? =
		getBitsTier(prefix, bitsAmount)
			?.images
			?.get(if (dark) "dark" else "light")
			?.get(if (animated) "animated" else "static")
			?.get(scale)
	
	private fun List<BitsAction>.getBitsTier(
		prefix: String,
		bitsAmount: Int
	) = find { it.prefix == prefix }?.tiers?.filter { it.minBits <= bitsAmount }?.maxBy { it.minBits.toInt() }
	
	data class BitsAction(
		val prefix: String,
		val scales: List<String>,
		val tiers: List<BitsTier>
	)
	
	private var cachedBitsActions: List<BitsAction>? = null
	private fun getBitsActions(apiKey: String) =
		if (cachedBitsActions.isNullOrEmpty())
			with(TwitchAPI.getFromAPI(URL("https://api.twitch.tv/v5/bits/actions"), apiKey)) {
				if (responseCode != 200) null else {
					(Parser.default().parse(inputStream) as JsonObject)
						.array<JsonObject>("actions")
						?.let { Klaxon().parseFromJsonArray<BitsAction>(it) }
				}.also { cachedBitsActions = it }
			} else cachedBitsActions
	
	private fun getTwitchBitsDrawable(context: Context, url: URL, height: Int?): BitmapDrawable? {
		val bitmap = with(url.openHttps()) {
			if (responseCode != 200) return null
			BitmapFactory.decodeStream(inputStream)
		}
		
		val scaleRatio = height?.div(bitmap.height.toFloat())
		return BitmapDrawable(
			context.resources,
			height?.let {
				bitmap.scale((bitmap.width * scaleRatio!!).roundToInt(), (bitmap.height * scaleRatio).roundToInt())
			} ?: bitmap).apply {
			setBounds(
				0,
				0,
				(scaleRatio?.times(bitmap.width))?.roundToInt() ?: bitmap.width,
				(scaleRatio?.times(bitmap.height))?.roundToInt() ?: bitmap.height
			)
		}
	}
	
	private fun getTwitchBitsEmoteDrawable(
		context: Context,
		apiKey: String,
		prefix: String,
		quantity: Int,
		width: Int? = 56,
		dark: Boolean = Colorful().getDarkTheme(),
		animated: Boolean = false,
		apiSize: Int = 4
	): BitmapDrawable? {
		val bitsAction = getBitsActions(apiKey) ?: return runAndNull { Log.w("BitsEmotes", "Failed to get bits information") }
		val url = URL(bitsAction.getBitsEmoteURL(prefix, quantity, dark, animated, apiSize.toString())
			?: return runAndNull { Log.w("BitsEmotes", "Failed to get emote URL for $prefix") })
		return getTwitchBitsDrawable(context, url, width)
	}
}