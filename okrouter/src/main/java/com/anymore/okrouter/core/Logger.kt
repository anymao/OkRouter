package com.anymore.okrouter.core

import android.util.Log
import com.anymore.okrouter.OkRouter

/**
 * Created by anymore on 2023/6/8.
 */
interface Logger {
    fun v(message: String) {
        v(OkRouter.TAG, message)
    }

    fun v(tag: String, message: String)

    fun d(message: String) {
        d(OkRouter.TAG, message)
    }

    fun d(tag: String = OkRouter.TAG, message: String)

    fun i(message: String) {
        i(OkRouter.TAG, message)
    }

    fun i(tag: String = OkRouter.TAG, message: String)

    fun w(message: String, throwable: Throwable? = null) {
        w(OkRouter.TAG, message, throwable)
    }

    fun w(tag: String = OkRouter.TAG, message: String, throwable: Throwable? = null)

    fun e(message: String, throwable: Throwable? = null) {
        e(OkRouter.TAG, message, throwable)
    }

    fun e(tag: String = OkRouter.TAG, message: String, throwable: Throwable? = null)

    object Empty : Logger {
        override fun v(tag: String, message: String) {

        }

        override fun d(tag: String, message: String) {

        }

        override fun i(tag: String, message: String) {

        }

        override fun w(tag: String, message: String, throwable: Throwable?) {

        }

        override fun e(tag: String, message: String, throwable: Throwable?) {

        }

    }

    object Default : Logger {
        override fun v(tag: String, message: String) {
            Log.v(tag, message)
        }

        override fun d(tag: String, message: String) {
            Log.d(tag, message)
        }

        override fun i(tag: String, message: String) {
            Log.i(tag, message)
        }

        override fun w(tag: String, message: String, throwable: Throwable?) {
            Log.w(tag, message, throwable)
        }

        override fun e(tag: String, message: String, throwable: Throwable?) {
            Log.e(tag, message, throwable)
        }

    }
}