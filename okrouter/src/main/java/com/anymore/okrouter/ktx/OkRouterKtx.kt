@file:Suppress("DEPRECATION")

package com.anymore.okrouter.ktx

import android.os.Bundle
import com.anymore.okrouter.core.RouterRequest

fun Bundle.getBooleanCompatibly(key: String, defaultValue: Boolean = false): Boolean {
    val value = get(key) ?: return defaultValue
    return when (value) {
        is Boolean -> {
            value
        }
        is String -> {
            value.contentEquals("true", true)
        }
        is Number -> {
            1 == value
        }
        else -> {
            defaultValue
        }
    }
}

fun Bundle.getByteCompatibly(key: String, defaultValue: Byte): Byte {
    val value = get(key) ?: return defaultValue
    return when (value) {
        is Number -> {
            value.toByte()
        }
        is String -> {
            try {
                value.toByte()
            } catch (e: Throwable) {
                defaultValue
            }
        }
        else -> {
            defaultValue
        }
    }
}

fun Bundle.getShortCompatibly(key: String, defaultValue: Short): Short {
    val value = get(key) ?: return defaultValue
    return when (value) {
        is Number -> {
            value.toShort()
        }
        is String -> {
            try {
                value.toShort()
            } catch (e: Throwable) {
                defaultValue
            }
        }
        else -> {
            defaultValue
        }
    }
}

fun Bundle.getIntCompatibly(key: String, defaultValue: Int): Int {
    val value = get(key) ?: return defaultValue
    return when (value) {
        is Number -> {
            value.toInt()
        }
        is String -> {
            try {
                value.toInt()
            } catch (e: Throwable) {
                defaultValue
            }
        }
        else -> {
            defaultValue
        }
    }
}

fun Bundle.getLongCompatibly(key: String, defaultValue: Long): Long {
    val value = get(key) ?: return defaultValue
    return when (value) {
        is Number -> {
            value.toLong()
        }
        is String -> {
            try {
                value.toLong()
            } catch (e: Throwable) {
                defaultValue
            }
        }
        else -> {
            defaultValue
        }
    }
}

fun Bundle.getFloatCompatibly(key: String, defaultValue: Float): Float {
    val value = get(key) ?: return defaultValue
    return when (value) {
        is Number -> {
            value.toFloat()
        }
        is String -> {
            try {
                value.toFloat()
            } catch (e: Throwable) {
                defaultValue
            }
        }
        else -> {
            defaultValue
        }
    }
}

fun Bundle.getDoubleCompatibly(key: String, defaultValue: Double): Double {
    val value = get(key) ?: return defaultValue
    return when (value) {
        is Number -> {
            value.toDouble()
        }
        is String -> {
            try {
                value.toDouble()
            } catch (e: Throwable) {
                defaultValue
            }
        }
        else -> {
            defaultValue
        }
    }
}

@JvmOverloads
fun Bundle.getStringCompatibly(key: String, defaultValue: String? = null): String? {
    val value = get(key) ?: return defaultValue
    return when (value) {
        is String -> {
            value
        }
        else -> {
            value.toString()
        }
    }
}

/**
 *
 */

fun RouterRequest.getBooleanCompatibly(key: String, defaultValue: Boolean = false) =
    extras.getBooleanCompatibly(key, defaultValue)

fun RouterRequest.getByteCompatibly(key: String, defaultValue: Byte) =
    extras.getByteCompatibly(key, defaultValue)

fun RouterRequest.getShortCompatibly(key: String, defaultValue: Short) =
    extras.getShortCompatibly(key, defaultValue)

fun RouterRequest.getIntCompatibly(key: String, defaultValue: Int) =
    extras.getIntCompatibly(key, defaultValue)

fun RouterRequest.getLongCompatibly(key: String, defaultValue: Long) =
    extras.getLongCompatibly(key, defaultValue)

fun RouterRequest.getFloatCompatibly(key: String, defaultValue: Float) =
    extras.getFloatCompatibly(key, defaultValue)

fun RouterRequest.getDoubleCompatibly(key: String, defaultValue: Double) =
    extras.getDoubleCompatibly(key, defaultValue)

@JvmOverloads
fun RouterRequest.getStringCompatibly(key: String, defaultValue: String? = null) =
    extras.getStringCompatibly(key, defaultValue)