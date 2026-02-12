package com.dduddupark.test

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dduddupark.test.utils.ad.AdInfo
import com.dduddupark.test.utils.ad.AdManager
import com.dduddupark.test.utils.ad.AdStatus
import com.google.android.gms.ads.MobileAds

class MainActivity : ComponentActivity() {

    private val TAG = "MainActivity"
    // AdMob test interstitial ad unit ID
    private val TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    
    // Define an AdInfo for our main button
    private val mainAdInfo = AdInfo(
        identifier = "main_button_ad",
        placementId = TEST_AD_UNIT_ID
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Mobile Ads SDK
        MobileAds.initialize(this) {
            Log.d(TAG, "MobileAds initialized")
            // Pre-load the ad
            AdManager.loadAd(this, mainAdInfo)
        }

        // Set up ad callback
        AdManager.setAdCallback(object : AdManager.OnAdCallback {
            override fun onAdEvent(identifier: String, status: AdStatus, message: String?) {
                when (status) {
                    is AdStatus.AdLoadSuccess -> {
                        Log.d(TAG, "Ad Loaded: ")
                        Toast.makeText(this@MainActivity, "광고 로드 완료", Toast.LENGTH_SHORT).show()
                    }
                    is AdStatus.AdLoadFail -> {
                        Log.e(TAG, "Ad Load Failed: , msg: ")
                    }
                    is AdStatus.AdShowClosed -> {
                        Log.d(TAG, "Ad Closed: ")
                        // Reload for next time
                        AdManager.loadAd(this@MainActivity, mainAdInfo)
                    }
                    is AdStatus.AdShowFail -> {
                        Log.e(TAG, "Ad Show Failed: , msg: ")
                        Toast.makeText(this@MainActivity, "광고 준비중입니다.", Toast.LENGTH_SHORT).show()
                    }
                    else -> {}
                }
            }
        })

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AdMainScreen(onCallAdClicked = {
                        AdManager.showAd(this, mainAdInfo)
                    })
                }
            }
        }
    }
}

@Composable
fun AdMainScreen(onCallAdClicked: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Button(onClick = onCallAdClicked) {
            Text(text = "광고 호출")
        }
    }
}
