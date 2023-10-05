package com.anymore.okrouter.demo.base

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/**
 * Created by anymore on 2023/7/30.
 */

val mainHandler = Handler(Looper.getMainLooper())

fun Context.toast(message: String) {
    if (Thread.currentThread() == Looper.getMainLooper().thread) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    } else {
        mainHandler.post {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
}