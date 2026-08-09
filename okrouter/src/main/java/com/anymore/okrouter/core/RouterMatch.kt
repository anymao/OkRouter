package com.anymore.okrouter.core

import java.util.Collections
import java.util.LinkedHashMap

sealed class RouterMatch {
    class Found internal constructor(
        /** 本次解析输入的完整 URI 快照，保留 query，不等同于路由规则。 */
        val uri: String,
        val destination: RouterDestination,
        parameters: Map<String, Any?>
    ) : RouterMatch() {
        /** URI query 参数的只读快照，不包含可变的 [RouterRequest.extras]。 */
        val parameters: Map<String, Any?> =
            Collections.unmodifiableMap(LinkedHashMap(parameters))

        override fun equals(other: Any?): Boolean =
            other is Found && uri == other.uri && destination == other.destination && parameters == other.parameters

        override fun hashCode(): Int = 31 * (31 * uri.hashCode() + destination.hashCode()) + parameters.hashCode()

        override fun toString(): String =
            "Found(uri=$uri, destination=$destination, parameters=$parameters)"
    }

    data object NotFound : RouterMatch()

    data class Invalid(val reason: String) : RouterMatch()
}
