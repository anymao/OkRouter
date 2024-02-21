package com.anymore.okrouter.demo.base

object Common {
    const val scheme = "okrouter"
    const val host = "android"
    const val EXTRA_CHECK_LOGIN = "extra_check_login"
    const val LOGIN_CHECKER = "LoginCheckInterceptor"
    const val EXTRA_LOST_URI = "extra_lost_uri"

    fun String.toRoute():String{
        return if (startsWith("/")){
            "$scheme://$host$this"
        }else{
            "$scheme://$host/$this"
        }
    }
}