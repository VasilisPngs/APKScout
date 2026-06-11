package com.apkscout.app.apkmirror

import android.os.Build
import com.apkscout.app.InstalledApp
import com.apkscout.app.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ApkMirrorCheckResult(
    val updates: Map<String, UpdateInfo>,
    val error: String?
)

object ApkMirrorApiClient {
    private const val ENDPOINT = "https://www.apkmirror.com/wp-json/apkm/v1/app_exists/"
    private const val API_USER = "api-apkupdater"
    private const val API_TOKEN = "rm5rcfruUjKy04sMpyMPJXW8"
    private const val PACKAGE_CHUNK_SIZE = 80

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun checkUpdates(apps: List<InstalledApp>): ApkMirrorCheckResult {
        return withContext(Dispatchers.IO) {
            val updates = linkedMapOf<String, UpdateInfo>()

            for (chunk in apps.chunked(PACKAGE_CHUNK_SIZE)) {
                val result = runCatching {
                    checkChunk(chunk)
                }.getOrElse { error ->
                    return@withContext ApkMirrorCheckResult(
                        updates = updates,
                        error = error.message ?: error::class.java.simpleName
                    )
                }

                updates.putAll(result)
            }

            ApkMirrorCheckResult(
                updates = updates,
                error = null
            )
        }
    }

    private fun checkChunk(apps: List<InstalledApp>): Map<String, UpdateInfo> {
        if (apps.isEmpty()) return emptyMap()

        val installedByPackage = apps.associateBy { it.packageName }
        val bodyJson = JSONObject()
            .put("pnames", JSONArray(apps.map { it.packageName }))
            .put("exclude", JSONArray(listOf("alpha", "beta", "rc", "test", "other")))

        val request = Request.Builder()
            .url(ENDPOINT)
            .header("User-Agent", "APKScout/${Build.VERSION.SDK_INT}")
            .header("Authorization", Credentials.basic(API_USER, API_TOKEN))
            .post(bodyJson.toString().toRequestBody(jsonMediaType))
            .build()

        val response: Response = client.newCall(request).execute()

        try {
            val body = response.body.string()

            if (response.code !in 200..299) {
                error("APKMirror API HTTP ${response.code}")
            }

            return parseResponse(
                body = body,
                installedByPackage = installedByPackage
            )
        } finally {
            response.close()
        }
    }

    private fun parseResponse(
        body: String,
        installedByPackage: Map<String, InstalledApp>
    ): Map<String, UpdateInfo> {
        val root = JSONObject(body)
        val status = root.optInt("status", -1)

        if (status != 200) {
            error("APKMirror API status $status")
        }

        val data = root.optJSONArray("data") ?: return emptyMap()
        val updates = linkedMapOf<String, UpdateInfo>()

        for (index in 0 until data.length()) {
            val item = data.optJSONObject(index) ?: continue
            val packageName = item.optString("pname")
            val installed = installedByPackage[packageName] ?: continue
            val release = item.optJSONObject("release") ?: continue
            val apks = item.optJSONArray("apks") ?: continue

            val bestApk = findBestApk(
                apks = apks,
                installedVersionCode = installed.versionCode
            ) ?: continue

            val foundVersionCode = bestApk.optString("version_code").toLongOrNull() ?: continue

            if (foundVersionCode <= installed.versionCode) {
                continue
            }

            val foundVersionName = release.optString("version").takeIf { it.isNotBlank() } ?: continue
            val releaseUrl = ApkMirrorSource.absoluteUrl(release.optString("link"))
                ?: ApkMirrorSource.searchUrl(packageName).toString()

            updates[packageName] = UpdateInfo(
                versionName = foundVersionName,
                versionCode = foundVersionCode,
                url = releaseUrl
            )
        }

        return updates
    }

    private fun findBestApk(
        apks: JSONArray,
        installedVersionCode: Long
    ): JSONObject? {
        val candidates = mutableListOf<JSONObject>()

        for (index in 0 until apks.length()) {
            val apk = apks.optJSONObject(index) ?: continue
            val versionCode = apk.optString("version_code").toLongOrNull() ?: continue

            if (versionCode <= installedVersionCode) continue
            if (!isMinApiCompatible(apk.optString("minapi"))) continue
            if (!isArchitectureCompatible(apk.optJSONArray("arches"))) continue

            candidates += apk
        }

        return candidates.maxByOrNull {
            it.optString("version_code").toLongOrNull() ?: 0L
        }
    }

    private fun isMinApiCompatible(minApi: String?): Boolean {
        val value = minApi
            ?.filter { it.isDigit() }
            ?.toIntOrNull()
            ?: return true

        return value <= Build.VERSION.SDK_INT
    }

    private fun isArchitectureCompatible(arches: JSONArray?): Boolean {
        if (arches == null || arches.length() == 0) return true

        val deviceAbis = Build.SUPPORTED_ABIS
            .map { it.lowercase() }
            .toSet()

        for (index in 0 until arches.length()) {
            val arch = arches.optString(index).lowercase().trim()

            if (arch.isBlank()) continue
            if (arch in setOf("universal", "noarch", "all", "any")) return true
            if (arch in deviceAbis) return true
        }

        return false
    }
}
