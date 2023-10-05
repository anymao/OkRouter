package com.anymore.okrouter

import android.databinding.tool.ext.toCamelCase
import com.anymore.okrouter.Types.isSubTypeOf
import javassist.ClassPool
import javassist.CtClass
import javassist.Loader
import javassist.bytecode.AnnotationsAttribute
import javassist.bytecode.annotation.*
import javassist.bytecode.annotation.Annotation
import org.gradle.api.Project
import java.io.File
import java.io.FileInputStream
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.jar.JarFile

/**
 * Created by anymore on 2023/6/15.
 */
internal abstract class AbsOkRouterAction(
    protected val classpath: Collection<File>,
    protected val targetDir: File,
    protected val project: Project
) {

    companion object {
        val regex = "[\\w/]*".toRegex()
        const val targetPkg = "com.anymore.okrouter.warehouse"
        const val loaderName = "OkRouterLoader"

        fun forTransform(classpath: Collection<File>, targetDir: File, project: Project) =
            OkRouterTransformAction(classpath, targetDir, project)

        fun forTask(classpath: Collection<File>, targetDir: File, project: Project) =
            OkRouterTaskAction(classpath, targetDir, project)
    }

    protected val okRouter = project.extensions.getByType(OkRouterExtension::class.java)

    protected val classPool: ClassPool = object : ClassPool(true) {
        override fun getClassLoader(): ClassLoader {
            return Loader(this)
        }
    }

    private fun load(): List<CtClass> {
        val result = LinkedList<CtClass>()
        classpath.forEach {
            classPool.appendClassPath(it.absolutePath)
        }
        classpath.forEach {
            load(result, classPool, it)
        }
        return result
    }

    private fun load(result: MutableList<CtClass>, pool: ClassPool, file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach {
                load(result, pool, it)
            }
        } else {
            if (file.name.endsWith(".class")) {
                loadClass(result, pool, file)
            } else if (file.name.endsWith(".jar")) {
                loadJar(result, pool, file)
            }
        }
    }

    private fun loadClass(result: MutableList<CtClass>, pool: ClassPool, file: File) {
        FileInputStream(file).use {
            result.add(pool.makeClass(it))
        }
    }

    private fun loadJar(result: MutableList<CtClass>, pool: ClassPool, file: File) {
        val jarFile = JarFile(file)
        jarFile.entries().asSequence().forEach { entry ->
            if (entry.name.endsWith(".class")) {
                jarFile.getInputStream(entry).use {
                    result.add(pool.makeClass(it))
                }
            }
        }
    }


    fun execute() {
        val cls = load()
        //稳定路由
        val stableRouterElements = mutableListOf<RouterElement>()
        //正则路由
        val regexRouterElements = mutableListOf<RouterElement>()
        //拦截器的Class->Element映射
        val interceptorElements = mutableMapOf<String, InterceptorElement>()
        //拦截器的alias->Element映射
        val interceptorAlias = mutableMapOf<String, InterceptorElement>()
        loadElements(
            cls, stableRouterElements, regexRouterElements,
            interceptorElements, interceptorAlias
        )
        regexRouterElements.sortBy { it.priority }
        createRouterLoader(
            stableRouterElements, regexRouterElements,
            interceptorElements, interceptorAlias, targetDir
        )
        if (okRouter.createOkRouterDoc) {
            val docName = okRouter.okRouterDocFile
            val docFile = if (docName.isNullOrBlank()) {
                project.file("${project.name}-OkRouter-Doc.md")
            } else {
                File(docName)
            }
            createRouterDoc(
                stableRouterElements, regexRouterElements,
                interceptorElements, interceptorAlias, docFile
            )
        }
    }


    private fun loadElements(
        cls: List<CtClass>,
        stableRouterElements: MutableList<RouterElement>,
        regexRouterElements: MutableList<RouterElement>,
        interceptorElements: MutableMap<String, InterceptorElement>,
        interceptorAlias: MutableMap<String, InterceptorElement>
    ) {
        cls.forEach {
            val routerAnnotation = getAnnotation(it, Types.router.canonicalName())
            if (routerAnnotation != null) {
                Logger.v("${it.name} has @Router annotation")
                val routerType = when {
                    it.isSubTypeOf(classPool, Types.activity) -> RouterType.ACTIVITY
                    it.isSubTypeOf(classPool, Types.fragment) -> RouterType.FRAGMENT
                    it.isSubTypeOf(classPool, Types.service) -> RouterType.SERVICE
                    it.isSubTypeOf(classPool, Types.handler) -> RouterType.HANDLER
                    it.isSubTypeOf(classPool, Types.view) -> RouterType.VIEW
                    else -> {
                        throw IllegalStateException("@Router only support annotation on Activity/Fragment/Service/Handler/View")
                    }
                }
                val smv = routerAnnotation.getMemberValue("scheme") as? StringMemberValue
                val scheme = smv?.value.orEmpty()
                val hmv = routerAnnotation.getMemberValue("host") as? StringMemberValue
                val host = hmv?.value.orEmpty()
                val pmv = routerAnnotation.getMemberValue("path") as? StringMemberValue
                val path = pmv?.value
                check(!path.isNullOrEmpty()) {
                    "@Router path must not be null or empty!"
                }
                //如果path不是一个正则表达式，那么需要以 '/'开头
                if (path.matches(regex)) {
                    check(path.matches("^/[\\w/]*".toRegex())) {
                        "if path is not regex,path must begin with '/',please check ${it.name}"
                    }
                }
                val className = it.name
                val icmv = routerAnnotation.getMemberValue("interceptors") as? ArrayMemberValue
                val ics = mutableListOf<String>()
                icmv?.value?.forEach { mv ->
                    val cm = mv as? ClassMemberValue
                    if (cm != null) {
                        ics.add(cm.value)
                    }
                }
                val iams =
                    routerAnnotation.getMemberValue("interceptorsByAlias") as? ArrayMemberValue
                val ias = mutableListOf<String>()
                iams?.value?.forEach { mv ->
                    val sm = mv as? StringMemberValue
                    if (sm != null) {
                        ias.add(sm.value)
                    }
                }
                val pmv2 = routerAnnotation.getMemberValue("priority") as? IntegerMemberValue
                val priority = pmv2?.value ?: 0
                val dmv = routerAnnotation.getMemberValue("desc") as? StringMemberValue
                val desc = dmv?.value.orEmpty()
                val element =
                    RouterElement(
                        routerType, scheme, host, path, className, ics,
                        ias, priority, desc
                    )
                if (isRegexRouter(scheme, host, path)) {
                    regexRouterElements.add(element)
                } else {
                    stableRouterElements.add(element)
                }
            }
            val interceptorAnnotation = getAnnotation(it, Types.interceptor.canonicalName())
            if (interceptorAnnotation != null) {
                Logger.v("${it.name} has @Interceptor annotation")
                check(it.isSubTypeOf(classPool, Types.routerInterceptor)) {
                    "@Interceptor only support annotation on subtype of RouterInterceptor!"
                }
                val className = it.name
                val amv = interceptorAnnotation.getMemberValue("alias") as? StringMemberValue
                val alias = amv?.value.orEmpty()
                val pmv = interceptorAnnotation.getMemberValue("priority") as? IntegerMemberValue
                val priority = pmv?.value ?: 0
                val gmv = interceptorAnnotation.getMemberValue("global") as? BooleanMemberValue
                val global = gmv?.value ?: false
                val smv = interceptorAnnotation.getMemberValue("singleton") as? BooleanMemberValue
                val singleton = smv?.value ?: false
                val dmv = interceptorAnnotation.getMemberValue("desc") as? StringMemberValue
                val desc = dmv?.value.orEmpty()
                val element =
                    InterceptorElement(className, alias, priority, global, singleton, desc)
                interceptorElements[className] = element
                if (!alias.isNullOrBlank()) {
                    check(!interceptorAlias.containsKey(alias)) {
                        "the alias for RouterInterceptor must be unique,please check ${interceptorAlias[alias]} and $className"
                    }
                    interceptorAlias[alias] = element
                }
            }
        }
    }

    private fun getAnnotation(cls: CtClass, annotationName: String): Annotation? {
        if (cls.hasAnnotation(annotationName)) {
            val file = cls.classFile
            val visibleAttr =
                file.getAttribute(AnnotationsAttribute.visibleTag) as? AnnotationsAttribute
            if (visibleAttr != null) {
                val result = visibleAttr.getAnnotation(annotationName)
                if (result != null) return result
            }
            val invisibleAttr =
                file.getAttribute(AnnotationsAttribute.invisibleTag) as? AnnotationsAttribute
                    ?: return null
            return invisibleAttr.getAnnotation(annotationName)
        }
        return null
    }

    private fun isRegexRouter(scheme: String, host: String, path: String): Boolean {
        return !regex.matches(scheme) || !regex.matches(host) || !regex.matches(path)
    }

    abstract fun createRouterLoader(
        stableRouterElements: List<RouterElement>,
        regexRouterElements: List<RouterElement>,
        interceptorElements: Map<String, InterceptorElement>,
        interceptorAlias: Map<String, InterceptorElement>,
        targetDir: File
    )

    /**
     * 创建路由文档
     */
    private fun createRouterDoc(
        stableRouterElements: List<RouterElement>,
        regexRouterElements: List<RouterElement>,
        interceptorElements: Map<String, InterceptorElement>,
        interceptorAlias: Map<String, InterceptorElement>,
        targetFile: File
    ) {
        Logger.v("createRouterDoc")
        if (targetFile.exists()) {
            targetFile.delete()
        }
        targetFile.createNewFile()
        val writer = FileWriter(targetFile)
        writer.appendln("# OkRouter-Doc")
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
        writer.appendln("创建时间:${sdf.format(Date(System.currentTimeMillis()))}")
        writer.appendln("## 1.路由")
        if (stableRouterElements.isNotEmpty()) {
            writer.appendln("### 1.1 固定路由")
            writer.appendln("|uri|class|type|interceptors|desc|")
            writer.appendln("|----|----|----|----|----|")
            stableRouterElements.forEach {
                val interceptors =
                    TreeSet<InterceptorElement> { o1, o2 ->
                        (o1?.priority ?: 0) - (o2?.priority ?: 0)
                    }
                interceptors.addAll(it.ics.mapNotNull { clazz -> interceptorElements[clazz] })
                interceptors.addAll(it.ias.mapNotNull { alias -> interceptorAlias[alias] })
                writer.appendln("|${it.uri}|${it.className}|${it.routerType.name.toCamelCase()}|${
                    interceptors.joinToString(separator = "->") { interceptor -> interceptor.className }
                }|${it.desc}|")
            }
            writer.flush()
        }
        if (regexRouterElements.isNotEmpty()) {
            writer.appendln("### 1.2 正则路由")
            writer.appendln("|uri|class|type|interceptors|desc|")
            writer.appendln("|----|----|----|----|----|")
            regexRouterElements.forEach {
                val interceptors =
                    TreeSet<InterceptorElement> { o1, o2 ->
                        (o1?.priority ?: 0) - (o2?.priority ?: 0)
                    }
                interceptors.addAll(it.ics.mapNotNull { clazz -> interceptorElements[clazz] })
                interceptors.addAll(it.ias.mapNotNull { alias -> interceptorAlias[alias] })
                writer.appendln(
                    "|${it.uri}|${it.className}|${it.routerType.name.toCamelCase()}|${
                        interceptors.joinToString(
                            separator = "->"
                        ) { interceptor -> interceptor.className }
                    }|${it.desc}|"
                )
            }
            writer.flush()
        }

        if (interceptorElements.isNotEmpty()) {
            writer.appendln("## 2.拦截器")
            writer.appendln("|class|alias|priority|global|singleton|desc|")
            writer.appendln("|----|----|----|----|----|----|")
            interceptorElements.forEach {
                val key = it.key
                val value = it.value
                writer.appendln("|${key}|${value.alias}|${value.priority}|${value.global}|${value.singleton}|${value.desc}|")
            }
            writer.flush()
        }
        writer.close()
        Logger.s("create OkRouter-doc success:${targetFile.canonicalPath}")
    }
}