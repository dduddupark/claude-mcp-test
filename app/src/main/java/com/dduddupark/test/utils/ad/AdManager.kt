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
                    
                    // ✅ Improved: Convert error code to meaningful error message
                    val errorDescription = getErrorDescription(loadAdError.code)
                    Log.e(TAG, "onAdFailedToLoad: code=${loadAdError.code}, description=$errorDescription, message=${loadAdError.message}")
                    
                    interstitialAdMap.remove(placementId)
                    notifyCallback(adInfo, AdLoadFail, "$errorDescription: ${loadAdError.message}")
                }

                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    timeoutHandler.removeCallbacks(timeoutRunnable)
                    Log.d(TAG, "onAdLoaded: ${adInfo.identifier}")

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
                Log.w(TAG, "Ad expired when trying to show: $placementId")
                adData.interstitialAd = null
                interstitialAdMap.remove(placementId)
                notifyCallback(adInfo, AdShowFail, AdErrorCode.AdCanNotShow.code)
                // Try to reload
                loadAd(activity, adInfo)
                return
            }

            adData.interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "onAdDismissedFullScreenContent: ${adInfo.identifier}")
                    interstitialAdMap.remove(placementId) // Consume ad
                    notifyCallback(adInfo, AdShowClosed)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    // ✅ Improved: Better error message
                    Log.e(TAG, "onAdFailedToShowFullScreenContent: code=${adError.code}, message=${adError.message}")
                    interstitialAdMap.remove(placementId)
                    notifyCallback(adInfo, AdShowFail, "ShowError(${adError.code}): ${adError.message}")
                }

                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG, "onAdShowedFullScreenContent: ${adInfo.identifier}")
                    interstitialAdMap[placementId]?.adStatus = AdShowSuccess
                    notifyCallback(adInfo, AdShowSuccess)
                }
            }
            
            // ✅ Fixed: Ensure show() is called on main thread
            if (Looper.myLooper() == Looper.getMainLooper()) {
                try {
                    adData.interstitialAd?.show(activity)
                } catch (e: Exception) {
                    Log.e(TAG, "Error showing ad", e)
                    notifyCallback(adInfo, AdShowFail, e.message ?: "Unknown error")
                }
            } else {
                Handler(Looper.getMainLooper()).post {
                    try {
                        adData.interstitialAd?.show(activity)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error showing ad", e)
                        notifyCallback(adInfo, AdShowFail, e.message ?: "Unknown error")
                    }
                }
            }
        } else {
            Log.w(TAG, "Ad not ready to show: ${adInfo.identifier}")
            notifyCallback(adInfo, AdShowFail, AdErrorCode.AdNotFound.code)
            // Try to load for next time
            loadAd(activity, adInfo)
        }
    }

    // ✅ Added: Helper function to convert error codes to meaningful messages
    private fun getErrorDescription(errorCode: Int): String {
        return when (errorCode) {
            0 -> "INTERNAL_ERROR"
            1 -> "INVALID_REQUEST"
            2 -> "NETWORK_ERROR"
            3 -> "NO_FILL"
            else -> "UNKNOWN_ERROR($errorCode)"
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
