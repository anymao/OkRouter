package com.anymore.okrouter.core

import android.os.Bundle

sealed class RouterMatch {
    data class Found(
        val destination: RouterDestination,
        val request: RouterRequest,
        val parameters: Bundle
    ) : RouterMatch()

    data object NotFound : RouterMatch()

    data class Invalid(val reason: String) : RouterMatch()
}
