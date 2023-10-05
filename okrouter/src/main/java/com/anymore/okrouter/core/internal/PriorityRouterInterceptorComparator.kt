package com.anymore.okrouter.core.internal

import com.anymore.okrouter.core.RouterInterceptor
import com.anymore.okrouter.warehouse.WareHouse

internal object PriorityRouterInterceptorComparator : Comparator<RouterInterceptor> {

    override fun compare(o1: RouterInterceptor?, o2: RouterInterceptor?): Int {
        val p1 = o1?.run { WareHouse.getInterceptorInstancePriority(javaClass) } ?: 0
        val p2 = o2?.run { WareHouse.getInterceptorInstancePriority(javaClass) } ?: 0
        return p1.compareTo(p2)
    }

}