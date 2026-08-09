package com.anymore.okrouter.core.internal

import com.anymore.okrouter.core.RouterInterceptor
import com.anymore.okrouter.warehouse.WareHouse

internal object PriorityRouterInterceptorComparator : Comparator<RouterInterceptor> {

    override fun compare(o1: RouterInterceptor?, o2: RouterInterceptor?): Int {
        val p1 = o1?.javaClass?.let { WareHouse.getInterceptorInstancePriority(it) } ?: 0
        val p2 = o2?.javaClass?.let { WareHouse.getInterceptorInstancePriority(it) } ?: 0
        val priorityDiff = p1.compareTo(p2)
        if (priorityDiff != 0) return priorityDiff
        // 同优先级按 className 字典序稳定排序
        val name1 = o1?.javaClass?.name.orEmpty()
        val name2 = o2?.javaClass?.name.orEmpty()
        return name1.compareTo(name2)
    }

}