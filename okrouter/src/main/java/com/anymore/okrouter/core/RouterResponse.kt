package com.anymore.okrouter.core

import androidx.collection.ArrayMap

/**
 * Created by anymore on 2023/6/5.
 */
class RouterResponse private constructor(
    private val uri: String,
    private val headers: Map<String, Any>,
    private val routerType: RouterType,
    private val routerResult: RouterResult,
    private val target: Any?
) {

    fun newBuilder(): Builder = Builder(
        uri,
        ArrayMap<String, Any>().apply { putAll(headers) },
        routerType,
        routerResult,
        target
    )

    override fun toString(): String {
        return "RouterResponse(uri='$uri', headers=$headers, routerType=$routerType, routerResult=$routerResult, target=$target)"
    }


    class Builder internal constructor(
        private var uri: String?,
        private var headers: MutableMap<String, Any>,
        private var routerType: RouterType?,
        private var routerResult: RouterResult?,
        private var target: Any?
    ) {

        constructor() : this(null, ArrayMap(), null, null, null)

        fun uri(uri: String) = apply { this.uri = uri }

        fun header(key: String, value: Any) = apply {
            headers[key] = value
        }

        fun putHeaders(value: Map<String, Any>) = apply {
            headers.putAll(value)
        }

        fun routerType(routerType: RouterType) = apply { this.routerType = routerType }

        fun routerResult(routerResult: RouterResult) = apply { this.routerResult = routerResult }

        fun target(target: Any?) = apply { this.target = target }

        fun build(): RouterResponse {
            val u = uri
            check(!u.isNullOrEmpty()) {
                "uri is null or empty!"
            }
            val type = routerType
            check(type != null) {
                "routerType is null"
            }
            val result = routerResult ?: throw IllegalStateException("routerResult is null!")
            if (result == RouterResult.Ok && type == RouterType.UNDEFINED) {
                throw IllegalStateException("routerType == RouterType.UNDEFINED is NOT allowed when routerResult == RouterResult.Ok")
            }
            return RouterResponse(u, headers, type, result, target)
        }
    }
}