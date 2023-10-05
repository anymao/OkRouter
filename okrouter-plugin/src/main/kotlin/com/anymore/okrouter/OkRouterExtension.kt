package com.anymore.okrouter

/**
 * Created by anymore on 2023/6/17.
 */
open class OkRouterExtension {

    var isIncremental: Boolean = false

    var sourceCompatibility = "1.7"

    var createOkRouterDoc = true

    /**
     * 生成路由文档的文件名，不写默认在app文件夹下
     */
    var okRouterDocFile = ""

    private var logLevel = Logger.level

    fun setLogLevel(level: String) {
        logLevel = when (level) {
            "VERBOSE" -> {
                Logger.VERBOSE
            }
            "DEBUG" -> {
                Logger.DEBUG
            }
            "INFO" -> {
                Logger.INFO
            }
            "WARN" -> {
                Logger.WARN
            }
            "ERROR" -> {
                Logger.ERROR
            }
            else -> {
                Logger.DEBUG
            }
        }
    }

    fun getLogLevel() = logLevel
    override fun toString(): String {
        return "OkRouterExtension(isIncremental=$isIncremental, sourceCompatibility='$sourceCompatibility', createOkRouterDoc=$createOkRouterDoc, okRouterDocFile='$okRouterDocFile', logLevel=$logLevel)"
    }


}