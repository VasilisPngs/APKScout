package com.apkscout.app.core.compat

import com.apkscout.app.core.model.ApkFormat
import com.apkscout.app.core.model.DeviceSpec
import com.apkscout.app.core.model.UpdateCandidate

object CompatibilityPolicy {
    private val universalArchitectures = setOf(
        "universal",
        "noarch",
        "all",
        "any"
    )

    private val universalDpi = setOf(
        "nodpi",
        "alldpi",
        "universal",
        "any"
    )

    fun isValidUpdate(
        installedVersionCode: Long,
        candidate: UpdateCandidate,
        device: DeviceSpec,
        regularApkOnly: Boolean
    ): Boolean {
        return candidate.versionCode > installedVersionCode &&
            isCompatible(candidate, device, regularApkOnly)
    }

    fun isCompatible(
        candidate: UpdateCandidate,
        device: DeviceSpec,
        regularApkOnly: Boolean
    ): Boolean {
        return isFormatCompatible(candidate.format, regularApkOnly) &&
            isMinSdkCompatible(candidate.minSdk, device.sdk) &&
            isArchitectureCompatible(candidate.architectures, device.abis) &&
            isDpiCompatible(candidate.dpi, device.densityDpi)
    }

    fun isFormatCompatible(
        format: ApkFormat,
        regularApkOnly: Boolean
    ): Boolean {
        return !regularApkOnly || format == ApkFormat.APK
    }

    fun isMinSdkCompatible(
        minSdk: Int?,
        deviceSdk: Int
    ): Boolean {
        return minSdk == null || minSdk <= deviceSdk
    }

    fun isArchitectureCompatible(
        requiredArchitectures: List<String>,
        deviceAbis: List<String>
    ): Boolean {
        if (requiredArchitectures.isEmpty()) return true

        val required = requiredArchitectures
            .map { it.lowercase().trim() }
            .filter { it.isNotEmpty() }

        if (required.isEmpty()) return true
        if (required.any { it in universalArchitectures }) return true

        val device = deviceAbis
            .map { it.lowercase().trim() }
            .toSet()

        return required.any { it in device }
    }

    fun isDpiCompatible(
        requiredDpi: String?,
        deviceDensityDpi: Int
    ): Boolean {
        val value = requiredDpi
            ?.lowercase()
            ?.trim()
            .orEmpty()

        if (value.isEmpty()) return true
        if (value in universalDpi) return true

        val dpiValues = Regex("\\d+")
            .findAll(value)
            .mapNotNull { it.value.toIntOrNull() }
            .toList()

        return when (dpiValues.size) {
            0 -> false
            1 -> deviceDensityDpi == dpiValues.first()
            else -> deviceDensityDpi in dpiValues.min()..dpiValues.max()
        }
    }
}
