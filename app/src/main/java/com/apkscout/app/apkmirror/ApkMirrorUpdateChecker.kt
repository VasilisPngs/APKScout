package com.apkscout.app.apkmirror

import com.apkscout.app.core.model.AppUpdateStatus
import com.apkscout.app.core.model.DeviceSpec

object ApkMirrorUpdateChecker {
    suspend fun check(
        packageName: String,
        installedVersionCode: Long,
        device: DeviceSpec,
        regularApkOnly: Boolean
    ): AppUpdateStatus {
        val result = ApkMirrorHtmlFetcher.fetchSearchPage(packageName)

        return when (result) {
            is ApkMirrorFetchResult.Success -> {
                val links = ApkMirrorSearchParser.parseReleaseLinks(result.html)

                if (links.isEmpty()) {
                    AppUpdateStatus.NoCompatibleApk
                } else {
                    AppUpdateStatus.SearchResultsFound(
                        count = links.size
                    )
                }
            }

            is ApkMirrorFetchResult.HttpError -> {
                AppUpdateStatus.Error(
                    message = "APKMirror HTTP ${result.code}: ${result.message}"
                )
            }

            is ApkMirrorFetchResult.NetworkError -> {
                AppUpdateStatus.Error(
                    message = "APKMirror network error: ${result.message}"
                )
            }
        }
    }
}
