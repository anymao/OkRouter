package com.anymore.okrouter

/**
 * Created by anymore on 2023/6/14.
 */
object Logger {

    const val VERBOSE = 2
    const val DEBUG = 3
    const val INFO = 4
    const val WARN = 5
    const val ERROR = 6

    var level = VERBOSE

    private const val TAG = "[OkRouter]"


    fun v(message: String) {
        if (isLoggable(VERBOSE)) {
            println(String.format("${TAG}[V]::>>$message"))
        }
    }

    fun d(message: String) {
        if (isLoggable(DEBUG)) {
            println(String.format("${TAG}[D]::>>$message"))
        }
    }

    fun i(message: String) {
        if (isLoggable(INFO)) {
            println(String.format("${TAG}[I]::>>$message"))
        }
    }

    fun w(message: String) {
        if (isLoggable(WARN)) {
            println(String.format("${TAG}[W]::>>$message"))
        }
    }

    fun e(message: String) {
        if (isLoggable(ERROR)) {
            System.err.println(String.format("${TAG}[E]::>>$message"))
        }
    }

    fun s(message: String){
        println("${TAG}::>>\uD83E\uDD73\uD83E\uDD73\uD83C\uDF89\uD83C\uDF89 $message")
    }



    private fun isLoggable(target: Int): Boolean {
        return level <= target
    }
}