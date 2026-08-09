package com.anymore.okrouter.core

sealed class RouterOutcome {
    data class Completed(val target: Any? = null) : RouterOutcome()

    data class Redirect(
        val uri: String,
        val options: RouterOptions = RouterOptions.DEFAULT
    ) : RouterOutcome()

    data class Intercepted(val reason: String? = null) : RouterOutcome()

    data class Failed(val cause: Throwable) : RouterOutcome()
}
