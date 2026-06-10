package com.apkscout.app.apkmirror

import com.apkscout.app.core.model.ApkFormat

data class ApkMirrorVariantLink(
    val url: String,
    val label: String,
    val format: ApkFormat
)
