package com.apkscout.app.apkmirror

import android.net.Uri

object ApkMirrorSource {
    fun searchUrl(packageName: String): Uri {
        return Uri.Builder()
            .scheme("https")
            .authority("www.apkmirror.com")
            .appendQueryParameter("post_type", "app_release")
            .appendQueryParameter("searchtype", "apk")
            .appendQueryParameter("s", packageName)
            .build()
    }
}
