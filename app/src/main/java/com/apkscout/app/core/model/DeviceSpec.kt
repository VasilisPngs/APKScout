package com.apkscout.app.core.model

data class DeviceSpec(
    val sdk: Int,
    val densityDpi: Int,
    val abis: List<String>
)
