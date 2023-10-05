package com.anymore.okrouter

import com.squareup.javapoet.ClassName
import javassist.ClassPool
import javassist.CtClass

/**
 * Created by anymore on 2023/6/14.
 */
object Types {
    val okRouter = ClassName.get("com.anymore.okrouter","OkRouter")
    val classType = ClassName.get(Class::class.java)
    val nullable = ClassName.get("androidx.annotation","Nullable")
    val nonNull = ClassName.get("androidx.annotation","NonNull")
    val context = ClassName.get("android.content","Context")
    val activity = ClassName.get("android.app", "Activity")
    val fragment = ClassName.get("androidx.fragment.app", "Fragment")
    val handler = ClassName.get("com.anymore.okrouter.core", "RouterHandler")
    val view = ClassName.get("android.view", "View")
    val service = ClassName.get("android.app", "Service")
    val routerInterceptor = ClassName.get("com.anymore.okrouter.core", "RouterInterceptor")
    val wareHouse = ClassName.get("com.anymore.okrouter.warehouse","WareHouse")
    val routerMeta = ClassName.get("com.anymore.okrouter.warehouse","RouterMeta")
    val routerUri = ClassName.get("com.anymore.okrouter.warehouse","RouterUri")
    val routerType = ClassName.get("com.anymore.okrouter.core","RouterType")
    val routerInterceptorMeta = ClassName.get("com.anymore.okrouter.warehouse","RouterInterceptorMeta")
    val contextFactory = ClassName.get("com.anymore.okrouter.warehouse","ContextFactory")
    val routerInterceptorFactory = ClassName.get("com.anymore.okrouter.warehouse","RouterInterceptorFactory")

    val router = ClassName.get("com.anymore.okrouter.annotation", "Router")
    val interceptor = ClassName.get("com.anymore.okrouter.annotation", "Interceptor")

    fun String.toCtClass(pool: ClassPool):CtClass = pool.get(this)

    fun ClassName.toCtClass(pool: ClassPool): CtClass = canonicalName().toCtClass(pool)

    fun CtClass.isSubTypeOf(pool: ClassPool, cn: ClassName): Boolean = subtypeOf(cn.toCtClass(pool))
}