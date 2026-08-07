package com.anymore.okrouter.core

import java.util.Collections
import java.util.LinkedHashMap

sealed class RouterMatch {
    class Found internal constructor(
        val destination: RouterDestination,
        parameters: Map<String, Any?>
    ) : RouterMatch() {
        /** URI query 参数的只读快照，不包含可变的 [RouterRequest.extras]。 */
        val parameters: Map<String, Any?> =
            Collections.unmodifiableMap(LinkedHashMap(parameters))

        override fun equals(other: Any?): Boolean =
            other is Found && destination == other.destination && parameters == other.parameters

        override fun hashCode(): Int = 31 * destination.hashCode() + parameters.hashCode()

        override fun toString(): String =
            "Found(destination=$destination, parameters=$parameters)"
    }

    data object NotFound : RouterMatch()

    data class Invalid(val reason: String) : RouterMatch()
}
