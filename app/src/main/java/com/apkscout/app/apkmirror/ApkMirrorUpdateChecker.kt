package com.apkscout.app.apkmirror

import com.apkscout.app.core.model.AppUpdateStatus
import com.apkscout.app.core.model.DeviceSpec
import kotlinx.coroutines.delay

object ApkMirrorUpdateChecker {
    suspend fun check(
        packageName: String,
        installedVersionCode: Long,
        device: DeviceSpec,
        regularApkOnly: Boolean
    ): AppUpdateStatus {
        delay(350)

        return AppUpdateStatus.Error(
            message = "APKMirror parser is not connected yet."
        )
    }
}
