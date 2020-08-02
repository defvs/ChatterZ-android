package dev.defvs.chatterz.ads

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import dev.defvs.chatterz.R

object AdsLoader {
	fun initializeAds(context: Context) {
		Log.d("AppAdsLoader", "Initializing MobileAds")
		MobileAds.initialize(context)
	}
	fun initializeAdView(activity: AppCompatActivity) {
		activity.findViewById<AdView>(R.id.adView).let {
			if (it == null) {
				Log.w("AppAdsLoader", "AdView is null!")
				return
			}
			it.loadAd(AdRequest.Builder().build())
			Log.d("AppAdsLoader", "Initializing adview")
		}
	}
}