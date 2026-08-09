package com.anymore.okrouter.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.os.PersistableBundle
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.RequiresApi
import androidx.collection.ArrayMap
import com.anymore.okrouter.OkRouter
import com.anymore.okrouter.OkRouter.logger
import com.anymore.okrouter.core.internal.RouterDispatcher
import java.io.Serializable

/**
 * Created by anymore on 2023/6/5.
 */
class RouterRequest private constructor(
    val requestCode: Int = -1,
    val uri: String,
    val headers: Map<String, Any>,
    val extras: Bundle,
    val routerType: RouterType,
    val launcher: ActivityResultLauncher<Intent>?
) {

    fun newBuilder(): Builder =
        Builder(
            requestCode,
            uri,
            ArrayMap<String, Any>().apply { putAll(headers) },
            Bundle().apply { putAll(extras) },
            routerType,
            launcher
        )

    /**
     * 以新 URI 创建重定向请求，同时保留发起请求时已显式提供的配置。
     *
     * 新 URI 的 query 仅补充原 extras 中不存在的键；同名键始终保留原 extras 的值。
     */
    internal fun withRedirectUri(uri: String): RouterRequest {
        val redirectedRequest = newBuilder().uri(uri).build()
        redirectedRequest.extras.putAll(extras)
        redirectedRequest.extras.putString(Extend.OKROUTER_RAW_URI, uri)
        return redirectedRequest
    }

    override fun toString(): String {
        return "RouterRequest(requestCode=$requestCode, uri='$uri', headers=$headers, extras=$extras, routerType=$routerType)"
    }


    class Builder internal constructor(
        private var requestCode: Int = -1,
        private var uri: String?,
        private var headers: MutableMap<String, Any>,
        private var extras: Bundle,
        private var routerType: RouterType,
        private var launcher: ActivityResultLauncher<Intent>? = null
    ) {

        constructor() : this(-1, null, ArrayMap(), Bundle(), RouterType.UNDEFINED)

        fun uri(uri: String) = apply { this.uri = uri }

        @Deprecated("replace with launcher(launcher)", replaceWith = ReplaceWith(expression = "launcher(launcher)"))
        fun requestCode(requestCode: Int) = apply { this.requestCode = requestCode }

        fun routerType(routerType: RouterType) = apply { this.routerType = routerType }

        fun launcher(launcher: ActivityResultLauncher<Intent>) = apply { this.launcher = launcher }

        fun header(key: String, value: Any) = apply {
            headers[key] = value
        }

        fun putHeaders(value: Map<String, Any>) = apply {
            headers.putAll(value)
        }

        fun applyExtras(block: Bundle.() -> Unit) = apply {
            block(extras)
        }

        fun putBoolean(key: String?, value: Boolean) = apply {
            extras.putBoolean(key, value)
        }

        /**
         * Inserts a byte value into the mapping of this Bundle, replacing
         * any existing value for the given key.
         *
         * @param key a String, or null
         * @param value a byte
         */
        fun putByte(key: String?, value: Byte) = apply {
            extras.putByte(key, value)
        }

        /**
         * Inserts a char value into the mapping of this Bundle, replacing
         * any existing value for the given key.
         *
         * @param key a String, or null
         * @param value a char
         */
        fun putChar(key: String?, value: Char) = apply {
            extras.putChar(key, value)
        }

        /**
         * Inserts a short value into the mapping of this Bundle, replacing
         * any existing value for the given key.
         *
         * @param key a String, or null
         * @param value a short
         */
        fun putShort(key: String?, value: Short) = apply {
            extras.putShort(key, value)
        }

        /**
         * Inserts an int value into the mapping of this Bundle, replacing
         * any existing value for the given key.
         *
         * @param key a String, or null
         * @param value an int
         */
        fun putInt(key: String?, value: Int) = apply {
            extras.putInt(key, value)
        }

        /**
         * Inserts a long value into the mapping of this Bundle, replacing
         * any existing value for the given key.
         *
         * @param key a String, or null
         * @param value a long
         */
        fun putLong(key: String?, value: Long) = apply {
            extras.putLong(key, value)
        }

        /**
         * Inserts a float value into the mapping of this Bundle, replacing
         * any existing value for the given key.
         *
         * @param key a String, or null
         * @param value a float
         */
        fun putFloat(key: String?, value: Float) = apply {
            extras.putFloat(key, value)
        }

        /**
         * Inserts a double value into the mapping of this Bundle, replacing
         * any existing value for the given key.
         *
         * @param key a String, or null
         * @param value a double
         */
        fun putDouble(key: String?, value: Double) = apply {
            extras.putDouble(key, value)
        }

        /**
         * Inserts a String value into the mapping of this Bundle, replacing
         * any existing value for the given key.  Either key or value may be null.
         *
         * @param key a String, or null
         * @param value a String, or null
         */
        fun putString(key: String?, value: String?) = apply {
            extras.putString(key, value)
        }

        /**
         * Inserts a CharSequence value into the mapping of this Bundle, replacing
         * any existing value for the given key.  Either key or value may be null.
         *
         * @param key a String, or null
         * @param value a CharSequence, or null
         */
        fun putCharSequence(key: String?, value: CharSequence?) = apply {
            extras.putCharSequence(key, value)
        }

        /**
         * Inserts an ArrayList<Integer> value into the mapping of this Bundle, replacing
         * any existing value for the given key.  Either key or value may be null.
         *
         * @param key a String, or null
         * @param value an ArrayList<Integer> object, or null
        </Integer></Integer> */
        fun putIntegerArrayList(key: String?, value: ArrayList<Int>?) = apply {
            extras.putIntegerArrayList(key, value)
        }

        /**
         * Inserts an ArrayList<String> value into the mapping of this Bundle, replacing
         * any existing value for the given key.  Either key or value may be null.
         *
         * @param key a String, or null
         * @param value an ArrayList<String> object, or null
        </String></String> */
        fun putStringArrayList(key: String?, value: ArrayList<String>?) = apply {
            extras.putStringArrayList(key, value)
        }

        /**
         * Inserts an ArrayList<CharSequence> value into the mapping of this Bundle, replacing
         * any existing value for the given key.  Either key or value may be null.
         *
         * @param key a String, or null
         * @param value an ArrayList<CharSequence> object, or null
        </CharSequence></CharSequence> */
        fun putCharSequenceArrayList(key: String?, value: ArrayList<CharSequence>?) = apply {
            extras.putCharSequenceArrayList(key, value)
        }

        /**
         * Inserts a Serializable value into the mapping of this Bundle, replacing
         * any existing value for the given key.  Either key or value may be null.
         *
         * @param key a String, or null
         * @param value a Serializable object, or null
         */
        fun putSerializable(key: String?, value: Serializable?) = apply {
            extras.putSerializable(key, value)
        }

        /**
         * Inserts a boolean array value into the mapping of this Bundle, replacing
         * any existing value for the given key.  Either key or value may be null.
         *
         * @param key a String, or null
         * @param value a boolean array object, or null
         */
        fun putBooleanArray(key: String?, value: BooleanArray?) = apply {
            extras.putBooleanArray(key, value)
        }

        /**
         * Inserts a byte array value into the mapping of this Bundle, replacing
         * any existing value for the given key.  Either key or value may be null.
         *
         * @param key a String, or null
         * @param value a byte array object, or null
         */
        fun putByteArray(key: String?, value: ByteArray?) = apply {
            extras.putByteArray(key, value)
        }

        /**
         * Inserts a short array value into the mapping of this Bundle, replacing
         * any existing value for the given key.  Either key or value may be null.
         *
         * @param key a String, or null
         * @param value a short array object, or null
         */
        fun putShortArray(key: String?, value: ShortArray?) = apply {
            extras.putShortArray(key, value)
        }

        /**
         * Inserts a char array value into the mapping of this Bundle, replacing
         * any existing value for the given key.  Either key or value may be null.
         *
         * @param key a String, or null
         * @param value a char array object, or null
         */
        fun putCharArray(key: String?, value: CharArray?) = apply {
            extras.putCharArray(key, value)
        }

        /**
         * Inserts an int array value into the mapping of this Bundle, replacing
         * any existing value for the given key.  Either key or value may be null.
         *
         * @param key a String, or null
         * @param value an int array object, or null
         */
        fun putIntArray(key: String?, value: IntArray?) = apply {
            extras.putIntArray(key, value)
        }

        /**
         * Inserts a long array value into the mapping of this Bundle, replacing
         * any existing value for the given key.  Either key or value may be null.
         *
         * @param key a String, or null
         * @param value a long array object, or null
         */
        fun putLongArray(key: String?, value: LongArray?) = apply {
            extras.putLongArray(key, value)
        }

        /**
         * Inserts a float array value into the mapping of this Bundle, replacing
         * any existing value for the given key.  Either key or value may be null.
         *
         * @param key a String, or null
         * @param value a float array object, or null
         */
        fun putFloatArray(key: String?, value: FloatArray?) = apply {
            extras.putFloatArray(key, value)
        }

        /**
         * Inserts a double array value into the mapping of this Bundle, replacing
         * any existing value for the given key.  Either key or value may be null.
         *
         * @param key a String, or null
         * @param value a double array object, or null
         */
        fun putDoubleArray(key: String?, value: DoubleArray?) = apply {
            extras.putDoubleArray(key, value)
        }

        /**
         * Inserts a String array value into the mapping of this Bundle, replacing
         * any existing value for the given key.  Either key or value may be null.
         *
         * @param key a String, or null
         * @param value a String array object, or null
         */
        fun putStringArray(key: String?, value: Array<String>?) = apply {
            extras.putStringArray(key, value)
        }

        /**
         * Inserts a CharSequence array value into the mapping of this Bundle, replacing
         * any existing value for the given key.  Either key or value may be null.
         *
         * @param key a String, or null
         * @param value a CharSequence array object, or null
         */
        fun putCharSequenceArray(key: String?, value: Array<CharSequence>?) = apply {
            extras.putCharSequenceArray(key, value)
        }

        fun putParcelable(key: String?, value: Parcelable?) = apply {
            extras.putParcelable(key, value)
        }

        fun putBundle(key: String?, value: Bundle?) = apply {
            extras.putBundle(key, value)
        }

        fun putAll(bundle: Bundle) = apply {
            extras.putAll(bundle)
        }

        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        fun putAll(bundle: PersistableBundle) = apply {
            extras.putAll(bundle)
        }


        fun build(): RouterRequest {
            val u = uri
            check(!u.isNullOrEmpty()) {
                "uri is null or empty!"
            }
            parseQueries()
            putString(Extend.OKROUTER_RAW_URI, uri)
            return RouterRequest(requestCode, u, headers, extras, routerType, launcher)
        }

        @JvmOverloads
        fun start(context: Context? = null, requestCode: Int = -1): RouterResponse {
            requestCode(requestCode)
            val ctx = context ?: OkRouter.application
                ?: throw IllegalStateException("OkRouter 未初始化，请先调用 OkRouter.init(context)")
            val request = try {
                build()
            } catch (e: IllegalStateException) {
                // 仅捕获 build() 中 check(!u.isNullOrEmpty()) 抛出的异常
                OkRouter.logger.e("RouterRequest: URI 参数非法", e)
                return invalidRequestResponse(e)
            }
            // 分发链路（含用户自定义拦截器）不在捕获范围内：
            // 拦截器抛出的 IllegalStateException 不应被误归类为 InvalidRequest
            return RouterDispatcher.start(ctx, request)
        }

        fun start(context: Context, launcher: ActivityResultLauncher<Intent>): RouterResponse {
            launcher(launcher)
            val request = try {
                build()
            } catch (e: IllegalStateException) {
                // 与 start(context, requestCode) 保持同等防护：URI 非法时转为 InvalidRequest
                OkRouter.logger.e("RouterRequest: URI 参数非法", e)
                return invalidRequestResponse(e)
            }
            return RouterDispatcher.start(context, request)
        }

        /** 构造 URI 参数非法时的 InvalidRequest 响应 */
        private fun invalidRequestResponse(e: IllegalStateException): RouterResponse =
            RouterResponse.Builder()
                .uri(uri ?: "(null)")
                .routerType(routerType)
                .routerResult(RouterResult.InvalidRequest(e.message ?: "URI 为空"))
                .build()

        /**
         * 解析uri query部分的参数，以key-value形式存在bundle中，且全部为String类型
         */
        private fun parseQueries() {
            val uri = Uri.parse(uri)
            val queryKeys = uri.queryParameterNames
            if (queryKeys.isNullOrEmpty()) return
            val argumentsFromQuery = mutableMapOf<String, Any>()
            queryKeys.forEach {
                val value = uri.getQueryParameters(it)
                if (value.isNullOrEmpty()) {
                    //DO NOTHING
                } else if (value.size == 1) {
                    putString(it, value.first())
                    argumentsFromQuery[it] = value.first()
                } else {
                    val list = ArrayList(value)
                    putStringArrayList(it, list)
                    argumentsFromQuery[it] = list
                }
            }
            if (argumentsFromQuery.isNotEmpty()) {
                logger.d("find arguments from uri query:$argumentsFromQuery")
            }
        }

    }
}
