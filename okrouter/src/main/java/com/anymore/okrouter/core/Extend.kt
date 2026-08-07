package com.anymore.okrouter.core

import com.anymore.okrouter.R

/**
 * 扩展字段
 */
object Extend {
    private const val internalKeyPrefix = "__OkRouter__"
    const val OKROUTER_RAW_URI = "${internalKeyPrefix}RAW_URI"
    /** 重定向完成后，最终实际执行的 URI。 */
    const val OKROUTER_FINAL_URI = "${internalKeyPrefix}FINAL_URI"
    val OKROUTER_VIEW_EXTRAS = R.id.__OkRouter_View_Extras__
    const val OKROUTER_NOTE = "${internalKeyPrefix}Note"
}
