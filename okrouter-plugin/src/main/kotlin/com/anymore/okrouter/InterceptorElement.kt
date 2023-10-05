package com.anymore.okrouter

import java.io.Serializable

/**
 * Created by anymore on 2023/6/17.
 */
data class InterceptorElement(
    val className: String,
    val alias: String,
    val priority: Int,
    val global: Boolean,
    val singleton: Boolean,
    val desc: String
) : Serializable
