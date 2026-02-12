package com.dduddupark.test.utils.ad

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.dduddupark.test.utils.ad.AdStatus.AdLoadFail
import com.dduddupark.test.utils.ad.AdStatus.AdLoadSuccess
import com.dduddupark.test.utils.ad.AdStatus.AdLoading
import com.dduddupark.test.utils.ad.AdStatus.AdShowClosed
import com.dduddupark.test.utils.ad.AdStatus.AdShowFail
import com.dduddupark.test.utils.ad.AdStatus.AdShowSuccess
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import java.util.concurrent.ConcurrentHashMap

/**
 * AdMob Interstitial Ad Manager
 * Manages loading, caching, and showing of interstitial ads.
 */
object AdManager {
    private const val TAG = "AdManager"

    // Data class to hold cached ad information
    data class InterstitialAdData(
        var interstitialAd: InterstitialAd? = null,
        val loadedTime: Long = 0L,
        var adStatus: AdStatus = AdLoading
    )

    // Cache to store loaded ads mapped by placement ID
    private val interstitialAdMap = ConcurrentHashMap<String, InterstitialAdData?>()

    // Callback interface for ad events
    interface OnAdCallback {
        fun onAdEvent(identifier: String, status: AdStatus, message: String? = null)
    }

    private var adCallback: OnAdCallback? = null

    fun setAdCallback(callback: OnAdCallback) {
        this.adCallback = callback
    }

    // Handler for timeout logic
    private val timeoutHandler = Handler(Looper.getMainLooper())

    /**
     * Load an interstitial ad based on the provided AdInfo.
     */
    fun loadAd(activity: Activity, adInfo: AdInfo) {
        val placementId = adInfo.placementId
        Log.d(TAG, "loadAd: identifier=${adInfo.identifier}, placementId=$placementId")

        if (placementId.isEmpty()) {
            notifyCallback(adInfo, AdLoadFail, AdErrorCode.AdNotFound.code)
            return
        }

        // Check if ad is already loading or loaded and valid
        val existingAdData = interstitialAdMap[placementId]
        if (existingAdData != null) {
            if (existingAdData.adStatus is AdLoading) {
                Log.w(TAG, "Ad is already loading: $placementId")
                notifyCallback(adInfo, AdLoading, AdErrorCode.AdLoading.code)
                return
            }

            // Check if existing ad is valid (loaded less than 1 hour ago)
            if (existingAdData.interstitialAd != null && isAdValid(existingAdData)) {
                Log.d(TAG, "Ad is already loaded and valid.")
                notifyCallback(adInfo, AdLoadSuccess)
                return
            } else {
                // Expired ad, clear cache
                Log.d(TAG, "Ad expired, clearing cache.")
                existingAdData.interstitialAd?.fullScreenContentCallback = null
                interstitialAdMap.remove(placementId)
            }
        }

        // Start loading new ad
        val newAdData = InterstitialAdData(adStatus = AdLoading)
        interstitialAdMap[placementId] = newAdData

        // Set timeout
        val timeoutRunnable = Runnable {
            Log.w(TAG, "Ad load timeout: $placementId")
            if (interstitialAdMap[placementId]?.adStatus is AdLoading) {
                interstitialAdMap.remove(placementId)
                notifyCallback(adInfo, AdLoadFail, AdErrorCode.AdLoadTimeOut.code)
            }
        }
        timeoutHandler.postDelayed(timeoutRunnable, adInfo.adLoadTimeoutMs)

        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(
            activity,
            placementId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    timeoutHandler.removeCallbacks(timeoutRunnable)
                    Log.e(TAG, "onAdFailedToLoad: ${loadAdError.message}")
                    
                    interstitialAdMap.remove(placementId)
                    notifyCallback(adInfo, AdLoadFail, loadAdError.code.toString())
                }

                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    timeoutHandler.removeCallbacks(timeoutRunnable)
                    Log.d(TAG, "onAdLoaded")

                    val loadedData = InterstitialAdData(
                        interstitialAd = interstitialAd,
                        loadedTime = System.currentTimeMillis(),
                        adStatus = AdLoadSuccess
                    )
                    interstitialAdMap[placementId] = loadedData
                    notifyCallback(adInfo, AdLoadSuccess)
                }
            }
        )
    }

    /**
     * Show the loaded interstitial ad.
     */
    fun showAd(activity: Activity, adInfo: AdInfo) {
        val placementId = adInfo.placementId
        val adData = interstitialAdMap[placementId]

        if (adData?.interstitialAd != null) {
            // Check if ad is valid
            if (!isAdValid(adData)) {
                Log.w(TAG, "Ad expired when trying to show.")
                adData.interstitialAd = null
                interstitialAdMap.remove(placementId)
                notifyCallback(adInfo, AdShowFail, AdErrorCode.AdCanNotShow.code)
                // Try to reload
                loadAd(activity, adInfo)
                return
            }

            adData.interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "onAdDismissedFullScreenContent")
                    interstitialAdMap.remove(placementId) // Consume ad
                    notifyCallback(adInfo, AdShowClosed)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.e(TAG, "onAdFailedToShowFullScreenContent: ${adError.message}")
                    interstitialAdMap.remove(placementId)
                    notifyCallback(adInfo, AdShowFail, adError.code.toString())
                }

                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG, "onAdShowedFullScreenContent")
                    interstitialAdMap[placementId]?.adStatus = AdShowSuccess
                    notifyCallback(adInfo, AdShowSuccess)
                }
            }
            
            adData.interstitialAd?.show(activity)
        } else {
            Log.w(TAG, "Ad not ready to show.")
            notifyCallback(adInfo, AdShowFail, AdErrorCode.AdNotFound.code)
            // Try to load for next time
            loadAd(activity, adInfo)
        }
    }

    private fun isAdValid(adData: InterstitialAdData): Boolean {
        val currentTime = System.currentTimeMillis()
        val difference = currentTime - adData.loadedTime
        // AdMob valid for 1 hour (3600000 ms). We use slightly less for safety.
        return difference < 3600000 
    }

    private fun notifyCallback(adInfo: AdInfo, status: AdStatus, message: String? = null) {
        // Run on main thread
        Handler(Looper.getMainLooper()).post {
            adCallback?.onAdEvent(adInfo.identifier, status, message)
        }
    }
}
