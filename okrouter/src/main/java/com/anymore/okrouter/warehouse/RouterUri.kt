package com.anymore.okrouter.warehouse

import java.io.Serializable

/**
 * Created by anymore on 2023/6/8.
 */
data class RouterUri(val scheme: String, val host: String, val path: String, val priority: Int) :
    Serializable, Comparable<RouterUri> {

    companion object {
        private val regex = "[\\w/]*".toRegex()
    }

    val isRegex: Boolean by lazy {
        !regex.matches(scheme) || !regex.matches(host) || !regex.matches(path)
    }

    override fun compareTo(other: RouterUri): Int {
        return priority.compareTo(other.priority)
    }

    override fun toString(): String {
        return if (scheme.isEmpty()){
            "$host$path"
        }else{
            "$scheme://$host$path"
        }
    }
}
