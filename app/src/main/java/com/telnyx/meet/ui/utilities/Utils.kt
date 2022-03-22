@file:Suppress("DEPRECATION")
package com.telnyx.meet.ui.utilities

import android.app.Activity
import android.net.ParseException
import android.os.Build
import android.view.View
import android.view.View.SYSTEM_UI_FLAG_VISIBLE
import android.view.WindowManager
import java.lang.System.currentTimeMillis
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.*

fun calculateTokenExpireTime(tokenExpire: String): Int {
    val TIME_FRAGMENT = 0.90
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val now = Instant.now()
        val nowMillis = now.toEpochMilli()
        val tokenExpires = Instant.parse(tokenExpire).toEpochMilli()
        return ((tokenExpires - nowMillis) * TIME_FRAGMENT).toInt()
    } else {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return try {
            // formatting the dateString to convert it into a Date
            val tokenExpireMillis: Long = sdf.parse(tokenExpire)!!.time
            val currentTimeMillis: Long = currentTimeMillis()
            ((tokenExpireMillis - currentTimeMillis) * TIME_FRAGMENT).toInt()
        } catch (e: ParseException) {
            e.printStackTrace()
            0
        }
    }
}

fun getTimeHHmm(date: String): String {
    val inFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss+0000")
    val outFormat = SimpleDateFormat("HH:mm")
    inFormat.timeZone = TimeZone.getTimeZone("UTC")
    val d: Date = inFormat.parse(date)
    return outFormat.format(d)
}

fun getCurrentTimeHHmm(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss+0000")
    return sdf.format(Date())
}

fun Activity.hideSystemUI() {
    window.decorView.systemUiVisibility = (
        // Do not let system steal touches for showing the navigation bar
        View.SYSTEM_UI_FLAG_IMMERSIVE
            // Hide the nav bar and status bar
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            // Keep the app content behind the bars even if user swipes them up
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )
    // make navbar translucent - do this already in hideSystemUI() so that the bar
    // is translucent if user swipes it up
    window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
}

fun Activity.showSystemUI() {
    // Shows the system bars by removing all the flags
    // except for the ones that make the content appear under the system bars.
    window.decorView.systemUiVisibility =
        (SYSTEM_UI_FLAG_VISIBLE)
}

fun Activity.isFullScreenEnabled(): Boolean {
    return window.attributes.flags and WindowManager.LayoutParams.FLAG_FULLSCREEN != 0
}
