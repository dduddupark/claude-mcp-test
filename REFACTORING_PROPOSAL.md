## 🔧 코드 리팩토링 제안

### 📌 현재 빌드 문제 및 개선점

본 이슈는 프로젝트의 빌드 실패 원인을 분석하고 개선 방안을 제시합니다.

---

### ❌ 식별된 주요 문제점

#### 1️⃣ **Jetpack Compose 버전 불일치 (심각)**

**문제**:
```gradle
composeOptions {
    kotlinCompilerExtensionVersion '1.5.8'
}

dependencies {
    implementation platform('androidx.compose:compose-bom:2024.01.00')
    implementation 'androidx.compose.ui:ui'
    implementation 'androidx.compose.material3:material3'
}
```

- Kotlin Compiler Extension Version `1.5.8`은 Compose BOM `2024.01.00`과 호환되지 않음
- Compose BOM 2024.01.00은 `kotlinCompilerExtensionVersion >= 1.5.9` 필요
- **빌드 에러**: `Compose version 1.5.8 is not compatible with Compose Compiler`

---

#### 2️⃣ **누락된 AppCompat 설정**

**문제**:
```xml
<!-- AndroidManifest.xml -->
android:theme="@style/Theme.AppCompat.Light.DarkActionBar"
```

```gradle
<!-- app/build.gradle에 없음 -->
implementation 'androidx.appcompat:appcompat:1.x.x'
```

- manifest에서 AppCompat 테마 참조하지만 의존성 미선언
- **빌드 에러**: `Unable to find style 'Theme.AppCompat.Light.DarkActionBar'`

---

#### 3️⃣ **Compose + AppCompat 아키텍처 혼동**

**문제**:
```kotlin
// MainActivity는 Compose 전용인데 AppCompat 테마 사용
class MainActivity : ComponentActivity()  // Jetpack Compose용

android:theme="@style/Theme.AppCompat.Light.DarkActionBar"  // AppCompat용
```

- `ComponentActivity`는 Material Design 3 테마를 사용해야 함
- AppCompat 테마와 Compose 테마가 충돌 가능
- UI 일관성 저하

---

#### 4️⃣ **AdMob 테스트 광고 ID 하드코딩**

**문제**:
```kotlin
private val TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
```

- 테스트 ID가 프로덕션 빌드에 포함될 수 있음
- BuildConfig를 활용하지 않음
- 환경별 설정 관리 부재

---

#### 5️⃣ **느슨한 에러 처리**

**문제**:
```kotlin
override fun onAdFailedToLoad(loadAdError: LoadAdError) {
    interstitialAdMap.remove(placementId)
    notifyCallback(adInfo, AdLoadFail, loadAdError.code.toString())  // ⚠️ 암호화된 코드
}
```

- 에러 코드를 숫자로만 반환 (의미 불명)
- 상세한 에러 메시지 누락
- 로깅 부족

---

#### 6️⃣ **스레드 안전성 미흡**

**문제**:
```kotlin
private val interstitialAdMap = ConcurrentHashMap<String, InterstitialAdData?>()

// UI 스레드에서만 호출되는 메서드인데 여러 스레드 접근 가능
fun showAd(activity: Activity, adInfo: AdInfo) {
    val adData = interstitialAdMap[placementId]  // Race condition 가능
    // ...
    adData.interstitialAd?.show(activity)  // UI 스레드 강제 필요
}
```

- `show()` 호출은 메인 스레드에서만 실행되어야 함
- Handler/Looper 확보 로직 부재

---

### ✅ 개선 방안

#### **개선안 1: Compose 버전 통일**

```gradle
android {
    composeOptions {
        // Compose BOM 2024.01.00과 호환되는 버전으로 업그레이드
        kotlinCompilerExtensionVersion '1.5.10'
    }
}

dependencies {
    // Compose BOM 최신 버전 사용
    implementation platform('androidx.compose:compose-bom:2024.04.00')
    
    // Material 3 명시적 지정
    implementation 'androidx.compose.material3:material3:1.1.1'
}
```

---

#### **개선안 2: AppCompat 의존성 추가**

```gradle
dependencies {
    // AppCompat 테마 지원
    implementation 'androidx.appcompat:appcompat:1.6.1'
}
```

```xml
<!-- AndroidManifest.xml 수정 (선택) -->
<!-- Compose 사용시 테마 필수 아님 -->
<application
    android:label="Claude MCP Test"
    android:supportsRtl="true">
```

---

#### **개선안 3: 아키텍처 정리**

**Option A: Jetpack Compose 100% (권장)**
```kotlin
// MainActivity.kt - 완전 Compose 기반
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {  // Material 3 테마 사용
                Surface(modifier = Modifier.fillMaxSize()) {
                    AdMainScreen()
                }
            }
        }
    }
}
```

**Option B: AppCompat + Compose (기존 프로젝트 마이그레이션)**
```kotlin
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AdMainScreen()
            }
        }
    }
}
```

---

#### **개선안 4: BuildConfig를 이용한 환경별 설정**

```gradle
// app/build.gradle
android {
    defaultConfig {
        buildConfigField "String", "AD_UNIT_ID_INTERSTITIAL", 
            '"ca-app-pub-3940256099942544/1033173712"'  // 테스트
    }
    
    productFlavors {
        debug {
            buildConfigField "String", "AD_UNIT_ID_INTERSTITIAL",
                '"ca-app-pub-3940256099942544/1033173712"'  // 테스트
        }
        release {
            buildConfigField "String", "AD_UNIT_ID_INTERSTITIAL",
                '"ca-app-pub-xxxxxxxxxxxxxxxx/yyyyyyyyyy"'  // 실제 ID
        }
    }
}
```

```kotlin
// AdConstants.kt
data class AdInfo(
    val identifier: String,
    val placementId: String = BuildConfig.AD_UNIT_ID_INTERSTITIAL,
    val adType: String = "interstitial",
    val adLoadTimeoutMs: Long = 5000L
)
```

---

#### **개선안 5: 에러 처리 강화**

```kotlin
// AdManager.kt
override fun onAdFailedToLoad(loadAdError: LoadAdError) {
    timeoutHandler.removeCallbacks(timeoutRunnable)
    Log.e(TAG, "onAdFailedToLoad: code=${loadAdError.code}, " +
        "message=${loadAdError.message}")
    
    // 의미 있는 에러 코드로 변환
    val errorCode = when (loadAdError.code) {
        0 -> AdErrorCode.AdNotFound
        1 -> AdErrorCode.AdLoadTimeOut
        2 -> "Network Error"
        3 -> "Invalid Request"
        else -> "Unknown Error (${loadAdError.code})"
    }
    
    interstitialAdMap.remove(placementId)
    notifyCallback(adInfo, AdLoadFail, "$errorCode: ${loadAdError.message}")
}
```

---

#### **개선안 6: 메인 스레드 안전성 보장**

```kotlin
// AdManager.kt
fun showAd(activity: Activity, adInfo: AdInfo) {
    val placementId = adInfo.placementId
    val adData = interstitialAdMap[placementId]

    if (adData?.interstitialAd != null) {
        // 메인 스레드에서 실행 보장
        Handler(Looper.getMainLooper()).post {
            try {
                adData.interstitialAd?.show(activity)
            } catch (e: Exception) {
                Log.e(TAG, "Error showing ad", e)
                notifyCallback(adInfo, AdShowFail, e.message)
            }
        }
    } else {
        notifyCallback(adInfo, AdShowFail, AdErrorCode.AdNotFound.code)
        loadAd(activity, adInfo)
    }
}
```

---

### 📋 리팩토링 우선순위

| 순위 | 문제 | 심각도 | 해결 시간 |
|------|------|--------|---------|
| 1️⃣ | Compose 버전 불일치 | 🔴 심각 | 5분 |
| 2️⃣ | AppCompat 의존성 누락 | 🔴 심각 | 2분 |
| 3️⃣ | 아키텍처 혼동 | 🟡 중간 | 15분 |
| 4️⃣ | BuildConfig 미사용 | 🟡 중간 | 20분 |
| 5️⃣ | 에러 처리 미흡 | 🟠 낮음 | 15분 |
| 6️⃣ | 메인 스레드 안전성 | 🟠 낮음 | 10분 |

---

### 🎯 예상 효과

✅ **빌드 성공**: Compose 버전 동기화 후 컴파일 에러 해결  
✅ **런타임 안정성**: 메인 스레드 안전성으로 충돌 방지  
✅ **유지보수성**: 환경별 설정 분리로 배포 관리 용이  
✅ **코드 품질**: 강화된 에러 처리로 디버깅 향상  
✅ **보안**: 실제 광고 ID를 BuildConfig로 관리

---

### 📝 제안 사항

1. 먼저 **개선안 1, 2**를 적용하여 빌드 성공 확인
2. **개선안 3**에서 Compose 100% 옵션 선택 권장
3. **개선안 4**로 환경별 설정 구분
4. **개선안 5, 6**으로 런타임 안정성 강화

---

### 🔗 참고 자료

- [Jetpack Compose 릴리스 노트](https://developer.android.com/jetpack/androidx/releases/compose)
- [AndroidX AppCompat](https://developer.android.com/jetpack/androidx/releases/appcompat)
- [Android Build Configuration](https://developer.android.com/build/build-variants)

---

**작성자**: 자동 분석  
**날짜**: 2026-02-12  
**상태**: 💡 논의 필요
