package com.anymore.okrouter.core

data class RouterDestination(
    val uriPattern: String,
    val type: RouterType,
    val description: String
)
