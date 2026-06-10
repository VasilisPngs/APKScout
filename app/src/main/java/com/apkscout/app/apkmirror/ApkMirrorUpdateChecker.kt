package com.apkscout.app.apkmirror

import com.apkscout.app.core.compat.CompatibilityPolicy
import com.apkscout.app.core.model.ApkFormat
import com.apkscout.app.core.model.AppUpdateStatus
import com.apkscout.app.core.model.DeviceSpec
import com.apkscout.app.core.model.UpdateCandidate

object ApkMirrorUpdateChecker {
    suspend fun check(
        packageName: String,
        installedVersionCode: Long,
        device: DeviceSpec,
        regularApkOnly: Boolean
    ): AppUpdateStatus {
        val searchResult = ApkMirrorHtmlFetcher.fetchSearchPage(packageName)

        return when (searchResult) {
            is ApkMirrorFetchResult.Success -> {
                val links = ApkMirrorSearchParser.parseReleaseLinks(searchResult.html)
                val firstReleaseUrl = links.firstOrNull()

                if (firstReleaseUrl == null) {
                    AppUpdateStatus.NoCompatibleApk
                } else {
                    when (val releaseResult = ApkMirrorHtmlFetcher.fetchReleasePage(firstReleaseUrl)) {
                        is ApkMirrorFetchResult.Success -> {
                            val metadata = ApkMirrorReleaseParser.parse(
                                html = releaseResult.html,
                                releaseUrl = releaseResult.url
                            )

                            if (metadata == null) {
                                AppUpdateStatus.Error(
                                    message = "Release page loaded, but metadata could not be parsed."
                                )
                            } else {
                                val variants = ApkMirrorReleaseParser.parseVariantLinks(releaseResult.html)

                                if (variants.isEmpty()) {
                                    AppUpdateStatus.ReleaseMetadataParsed(
                                        title = metadata.title,
                                        versionCode = metadata.versionCode,
                                        releaseUrl = metadata.releaseUrl
                                    )
                                } else {
                                    val regularApkVariants = variants.filter { it.format == ApkFormat.APK }
                                    val nonApkCount = variants.size - regularApkVariants.size

                                    if (regularApkVariants.isEmpty() && nonApkCount > 0) {
                                        AppUpdateStatus.OnlyBundleFound
                                    } else {
                                        val compatibleRegularApks = regularApkVariants.filter { variant ->
                                            val candidate = UpdateCandidate(
                                                packageName = packageName,
                                                versionName = metadata.title,
                                                versionCode = metadata.versionCode ?: installedVersionCode,
                                                format = variant.format,
                                                minSdk = variant.minSdk,
                                                architectures = variant.architectures,
                                                dpi = variant.dpi,
                                                webUrl = variant.url
                                            )

                                            CompatibilityPolicy.isCompatible(
                                                candidate = candidate,
                                                device = device,
                                                regularApkOnly = regularApkOnly
                                            )
                                        }

                                        if (compatibleRegularApks.isEmpty()) {
                                            AppUpdateStatus.NoCompatibleApk
                                        } else {
                                            AppUpdateStatus.CompatibleApkCandidatesParsed(
                                                title = metadata.title,
                                                totalCount = variants.size,
                                                regularApkCount = regularApkVariants.size,
                                                compatibleApkCount = compatibleRegularApks.size,
                                                nonApkCount = nonApkCount,
                                                releaseUrl = metadata.releaseUrl
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        is ApkMirrorFetchResult.HttpError -> {
                            AppUpdateStatus.Error(
                                message = "APKMirror release HTTP ${releaseResult.code}: ${releaseResult.message}"
                            )
                        }

                        is ApkMirrorFetchResult.NetworkError -> {
                            AppUpdateStatus.Error(
                                message = "APKMirror release network error: ${releaseResult.message}"
                            )
                        }
                    }
                }
            }

            is ApkMirrorFetchResult.HttpError -> {
                AppUpdateStatus.Error(
                    message = "APKMirror search HTTP ${searchResult.code}: ${searchResult.message}"
                )
            }

            is ApkMirrorFetchResult.NetworkError -> {
                AppUpdateStatus.Error(
                    message = "APKMirror search network error: ${searchResult.message}"
                )
            }
        }
    }
}
