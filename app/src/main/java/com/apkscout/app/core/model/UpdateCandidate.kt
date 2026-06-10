package com.apkscout.app.core.model

data class UpdateCandidate(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val format: ApkFormat,
    val minSdk: Int?,
    val architectures: List<String>,
    val dpi: String?,
    val webUrl: String
)
