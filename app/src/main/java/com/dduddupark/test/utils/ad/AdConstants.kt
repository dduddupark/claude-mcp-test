package com.dduddupark.test.utils.ad

sealed class AdStatus {
    object AdLoading : AdStatus()
    object AdLoadSuccess : AdStatus()
    object AdLoadFail : AdStatus()
    object AdShowSuccess : AdStatus()
    object AdShowFail : AdStatus()
    object AdShowClosed : AdStatus()
}

enum class AdErrorCode(val code: String) {
    AdLoadTimeOut("TIMEOUT"),
    AdNotFound("NOT_FOUND"),
    AdLoading("LOADING"),
    AlreadyShow("ALREADY_SHOW"),
    AdCanNotShow("CANNOT_SHOW"),
    HasNotContext("NO_CONTEXT")
}

data class AdInfo(
    val identifier: String,
    val placementId: String,
    val adType: String = "interstitial",
    val adLoadTimeoutMs: Long = 5000L
)
