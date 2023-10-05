package com.anymore.okrouter

import java.io.Serializable

/**
 * Created by anymore on 2023/6/17.
 */
data class RouterElement(
    val routerType:RouterType,
    val scheme: String,
    val host: String,
    val path: String,
    val className:String,
    val ics: List<String>,
    val ias: List<String>,
    val priority: Int,
    val desc: String
) : Serializable{
    val uri by lazy {
        if (scheme.isNotBlank()){
            "$scheme://$host$path"
        }else{
            "$host$path"
        }
    }
}
