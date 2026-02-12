Title: 🔧 코드 리팩토링 제안

## 🔧 코드 리팩토링 제안

### 📌 현재 빌드 문제 및 개선점

본 이슈는 프로젝트의 빌드 실패 원인을 분석하고 개선 방안을 제시합니다.

---

## ❌ 식별된 주요 문제점

### 1️⃣ **Jetpack Compose 버전 불일치 (심각)**

**문제 상황**:
```gradle
composeOptions {
    kotlinCompilerExtensionVersion '1.5.8'
}

dependencies {
    implementation platform('androidx.compose:compose-bom:2024.01.00')
}
```

**문제 분석**:
- Kotlin Compiler Extension Version `1.5.8`은 Compose BOM `2024.01.00`과 호환되지 않음
- Compose BOM 2024.01.00은 `kotlinCompilerExtensionVersion >= 1.5.9` 필요
- **빌드 에러**: `Compose version 1.5.8 is not compatible with Compose Compiler`

**해결책**:
```gradle
composeOptions {
    kotlinCompilerExtensionVersion '1.5.10'  // 또는 1.5.9 이상
}

dependencies {
    implementation platform('androidx.compose:compose-bom:2024.04.00')
}
```

---

### 2️⃣ **누락된 AppCompat 의존성**

**문제 상황**:
```xml
<!-- AndroidManifest.xml -->
android:theme="@style/Theme.AppCompat.Light.DarkActionBar"
```

```gradle
<!-- app/build.gradle에 선언되지 않음 -->
dependencies {
    // implementation 'androidx.appcompat:appcompat:1.x.x'  ← 누락됨!
}
```

**문제 분석**:
- Manifest에서 AppCompat 테마를 참조하지만 의존성이 선언되지 않음
- **빌드 에러**: `Unable to find style 'Theme.AppCompat.Light.DarkActionBar'`

**해결책**:
```gradle
dependencies {
    implementation 'androidx.appcompat:appcompat:1.6.1'
}
```

---

### 3️⃣ **Compose + AppCompat 아키텍처 혼동**

**문제 상황**:
```kotlin
// MainActivity는 Jetpack Compose 기반
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AdMainScreen()  // Compose UI
            }
        }
    }
}
```

```xml
<!-- 하지만 Manifest에는 AppCompat 테마 참조 -->
<application
    android:theme="@style/Theme.AppCompat.Light.DarkActionBar">
```

**문제 분석**:
- `ComponentActivity`는 Material Design 3 테마를 기반으로 함
- AppCompat 테마와 Compose 테마가 혼재되어 충돌 가능
- UI 일관성 및 스타일 적용이 불명확

**해결책 (권장: 완전 Compose 기반)**:
```xml
<!-- AndroidManifest.xml에서 테마 제거 또는 Material3로 변경 -->
<application
    android:label="Claude MCP Test"
    android:supportsRtl="true">
    <!-- 테마 선언 제거 (Compose에서 MaterialTheme으로 관리) -->
</application>
```

---

### 4️⃣ **광고 ID 하드코딩 및 환경별 설정 부재**

**문제 상황**:
```kotlin
class MainActivity : ComponentActivity() {
    private val TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    
    private val mainAdInfo = AdInfo(
        identifier = "main_button_ad",
        placementId = TEST_AD_UNIT_ID  // ← 하드코딩된 테스트 ID
    )
}
```

**문제 분석**:
- 테스트 광고 ID가 소스 코드에 하드코딩됨
- 프로덕션 빌드에서도 테스트 ID가 포함될 수 있음
- 환경별(debug/release) 설정 관리 불가능
- ID 변경 시 코드 수정 및 재빌드 필요

**해결책 (BuildConfig 활용)**:
```gradle
// app/build.gradle
android {
    defaultConfig {
        buildConfigField "String", "AD_UNIT_ID_INTERSTITIAL", 
            '"ca-app-pub-3940256099942544/1033173712"'
    }
    
    productFlavors {
        debug {
            buildConfigField "String", "AD_UNIT_ID_INTERSTITIAL",
                '"ca-app-pub-3940256099942544/1033173712"'  // Google 제공 테스트 ID
        }
        release {
            buildConfigField "String", "AD_UNIT_ID_INTERSTITIAL",
                '"ca-app-pub-xxxxxxxxxxxxxxxx/yyyyyyyyyy"'  // 실제 프로덕션 ID
        }
    }
}
```

```kotlin
// MainActivity.kt
class MainActivity : ComponentActivity() {
    private val mainAdInfo = AdInfo(
        identifier = "main_button_ad",
        placementId = BuildConfig.AD_UNIT_ID_INTERSTITIAL  // BuildConfig에서 동적 로드
    )
}
```

---

### 5️⃣ **부실한 에러 처리 및 로깅**

**문제 상황**:
```kotlin
override fun onAdFailedToLoad(loadAdError: LoadAdError) {
    timeoutHandler.removeCallbacks(timeoutRunnable)
    Log.e(TAG, "onAdFailedToLoad: ${loadAdError.message}")
    
    interstitialAdMap.remove(placementId)
    // ⚠️ 에러 코드가 숫자로만 전달됨
    notifyCallback(adInfo, AdLoadFail, loadAdError.code.toString())
}
```

**문제 분석**:
- 에러 코드가 숫자(예: "1", "2")로만 반환되어 의미 불명확
- 사용자/개발자가 에러 원인을 파악하기 어려움
- 상세한 에러 메시지 정보 손실
- 에러 추적 및 디버깅 어려움

**해결책**:
```kotlin
override fun onAdFailedToLoad(loadAdError: LoadAdError) {
    timeoutHandler.removeCallbacks(timeoutRunnable)
    
    Log.e(TAG, "Ad Load Failed: " +
        "code=${loadAdError.code}, " +
        "message=${loadAdError.message}, " +
        "placementId=$placementId")
    
    // 에러 코드를 의미 있는 텍스트로 변환
    val errorDescription = when (loadAdError.code) {
        0 -> "ERROR_CODE_INTERNAL_ERROR"
        1 -> "ERROR_CODE_INVALID_REQUEST"
        2 -> "ERROR_CODE_NETWORK_ERROR"
        3 -> "ERROR_CODE_NO_FILL"
        else -> "ERROR_CODE_UNKNOWN (${loadAdError.code})"
    }
    
    interstitialAdMap.remove(placementId)
    notifyCallback(adInfo, AdLoadFail, "$errorDescription: ${loadAdError.message}")
}
```

---

### 6️⃣ **메인 스레드 안전성 미흡**

**문제 상황**:
```kotlin
fun showAd(activity: Activity, adInfo: AdInfo) {
    val placementId = adInfo.placementId
    val adData = interstitialAdMap[placementId]

    if (adData?.interstitialAd != null) {
        adData.interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            // 콜백 설정...
        }
        
        // ⚠️ 메인 스레드 보장 없이 show() 호출
        adData.interstitialAd?.show(activity)
    }
}
```

**문제 분석**:
- AdMob의 `show()` 메서드는 반드시 메인 스레드에서 호출되어야 함
- 백그라운드 스레드에서 호출 시 런타임 충돌 발생 가능
- 동시 접근 시 Race Condition 가능성
- NullPointerException 위험성

**해결책**:
```kotlin
fun showAd(activity: Activity, adInfo: AdInfo) {
    val placementId = adInfo.placementId
    val adData = interstitialAdMap[placementId]

    if (adData?.interstitialAd != null) {
        if (!isAdValid(adData)) {
            Log.w(TAG, "Ad expired when trying to show: $placementId")
            adData.interstitialAd = null
            interstitialAdMap.remove(placementId)
            notifyCallback(adInfo, AdShowFail, AdErrorCode.AdCanNotShow.code)
            loadAd(activity, adInfo)
            return
        }

        adData.interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Ad dismissed: $placementId")
                interstitialAdMap.remove(placementId)
                notifyCallback(adInfo, AdShowClosed)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e(TAG, "Ad failed to show: ${adError.message}")
                interstitialAdMap.remove(placementId)
                notifyCallback(adInfo, AdShowFail, adError.code.toString())
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Ad shown: $placementId")
                notifyCallback(adInfo, AdShowSuccess)
            }
        }
        
        // 메인 스레드에서 실행 보장
        if (Looper.myLooper() == Looper.getMainLooper()) {
            try {
                adData.interstitialAd?.show(activity)
            } catch (e: Exception) {
                Log.e(TAG, "Error showing ad", e)
                notifyCallback(adInfo, AdShowFail, e.message)
            }
        } else {
            // 메인 스레드가 아닌 경우 Handler로 전달
            Handler(Looper.getMainLooper()).post {
                try {
                    adData.interstitialAd?.show(activity)
                } catch (e: Exception) {
                    Log.e(TAG, "Error showing ad", e)
                    notifyCallback(adInfo, AdShowFail, e.message)
                }
            }
        }
    } else {
        Log.w(TAG, "Ad not loaded: $placementId")
        notifyCallback(adInfo, AdShowFail, AdErrorCode.AdNotFound.code)
        loadAd(activity, adInfo)
    }
}
```

---

## ✅ 개선 방안 요약

### 🎯 우선순위별 해결 계획

| 순위 | 문제 | 심각도 | 예상 해결시간 |
|------|------|--------|------------|
| **1️⃣** | Compose 버전 불일치 | 🔴 **심각** | 5분 |
| **2️⃣** | AppCompat 의존성 누락 | 🔴 **심각** | 2분 |
| **3️⃣** | 아키텍처 혼동 (Compose vs AppCompat) | 🟡 **중간** | 15분 |
| **4️⃣** | BuildConfig 미사용 (광고 ID 관리) | 🟡 **중간** | 20분 |
| **5️⃣** | 에러 처리 미흡 | 🟠 **낮음** | 15분 |
| **6️⃣** | 메인 스레드 안전성 | 🟠 **낮음** | 10분 |

---

## 📝 예상 개선 효과

✅ **빌드 성공**: Compose 버전 동기화로 컴파일 에러 완전 해결  
✅ **런타임 안정성**: 메인 스레드 안전성으로 ANR 및 충돌 방지  
✅ **유지보수성**: 환경별 설정 분리로 배포 관리 간소화  
✅ **코드 품질**: 강화된 에러 처리로 디버깅 및 모니터링 향상  
✅ **보안**: 실제 광고 ID를 BuildConfig로 관리하여 보안 강화  
✅ **확장성**: 향후 다른 광고 포맷 추가 시 쉬운 확장 가능

---

## 🔗 참고 자료

- [Jetpack Compose 버전 호환성](https://developer.android.com/jetpack/androidx/releases/compose)
- [AppCompat 라이브러리](https://developer.android.com/jetpack/androidx/releases/appcompat)
- [Android Build Configuration](https://developer.android.com/build/build-variants)
- [AdMob 안드로이드 통합 가이드](https://developers.google.com/admob/android/quick-start)

---

**레이블**: `bug` `enhancement` `android` `compose` `build`  
**담당자**: -  
**마일스톤**: -  
**우선순위**: 🔴 High
