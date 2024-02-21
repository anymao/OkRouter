package com.anymore.okrouter

import com.anymore.okrouter.Types.toCtClass
import javassist.CtClass
import javassist.CtNewConstructor
import javassist.CtNewMethod
import javassist.Modifier
import org.gradle.api.Project
import java.io.File

/**
 * 通过javassist完成类生成，直接生成class文件
 */
internal class OkRouterTransformAction(
    classpath: Collection<File>,
    targetDir: File,
    project: Project
) :
    AbsOkRouterAction(classpath, targetDir, project) {


    override fun createRouterLoader(
        stableRouterElements: List<RouterElement>,
        regexRouterElements: List<RouterElement>,
        interceptorElements: Map<String, InterceptorElement>,
        interceptorAlias: Map<String, InterceptorElement>,
        targetDir: File
    ) {
        val loader = classPool.makeClass("${targetPkg}.$loaderName")
        loader.modifiers = Modifier.PUBLIC or Modifier.FINAL
        //interceptors register code
        val irs = mutableListOf<String>()
        if (interceptorElements.isNotEmpty()) {
            interceptorElements.forEach { entry ->
                val cn = entry.key
                val element = entry.value
                val factory = buildInterceptorFactory(cn, element)
                irs.add(
                    """
                    ${Types.wareHouse.canonicalName()}.registerInterceptor(${cn}.class,new ${Types.routerInterceptorMeta.canonicalName()}($cn.class,"$cn","${element.alias}",$factory,${element.priority},${element.global},${element.singleton}),${element.global});
                    """.trimIndent()
                )
            }
        }
        //stable routers register code
        val srs = mutableListOf<String>()
        if (stableRouterElements.isNotEmpty()) {
            stableRouterElements.forEach { element ->
                val ics = mutableSetOf<String>()
                ics.addAll(element.ics)
                element.ias.forEach {
                    val cn = interceptorAlias[it]?.className
                    require(!cn.isNullOrEmpty()) {
                        "the alias:$it do not match any RouterInterceptor,please check!!"
                    }
                    ics.add(cn)
                }

                val interceptors = "new java.lang.Class[]{${
                    ics.joinToString(separator = ",", transform = { "${it}.class" })
                }}"
                val factory = if (element.routerType in
                    listOf(RouterType.FRAGMENT, RouterType.HANDLER, RouterType.VIEW)
                ) {
                    buildContextFactory(element)
                } else {
                    "null"
                }
                srs.add(
                    """
                        ${Types.wareHouse}.registerStableRouter("${element.uri}",new ${Types.routerMeta.canonicalName()}(new ${Types.routerUri.canonicalName()}("${element.scheme}","${element.host}","${element.path}",${element.priority}),${Types.routerType.canonicalName()}.${element.routerType.name},"${element.className}",${element.className}.class,$interceptors,$factory));
                    """.trimIndent()
                )
            }
        }
        //regex routers register code
        val rrs = mutableListOf<String>()
        if (regexRouterElements.isNotEmpty()) {
            regexRouterElements.forEach { element ->
                val ics = mutableSetOf<String>()
                ics.addAll(element.ics)
                element.ias.forEach {
                    val cn = interceptorAlias[it]?.className
                    require(!cn.isNullOrEmpty()) {
                        "the alias:$it do not match any RouterInterceptor,please check!!"
                    }
                    ics.add(cn)
                }

                val interceptors = "new java.lang.Class[]{${
                    ics.joinToString(separator = ",", transform = { "${it}.class" })
                }}"
                val factory = if (element.routerType in
                    listOf(RouterType.FRAGMENT, RouterType.HANDLER, RouterType.VIEW)
                ) {
                    buildContextFactory(element)
                } else {
                    "null"
                }
                rrs.add(
                    """
                    ${Types.wareHouse}.registerRegexRouter(new ${Types.routerUri.canonicalName()}("${element.scheme}","${element.host}","${element.path}",${element.priority}),new ${Types.routerMeta.canonicalName()}(new ${Types.routerUri.canonicalName()}("${element.scheme}","${element.host}","${element.path}",${element.priority}),${Types.routerType.canonicalName()}.${element.routerType.name},"${element.className}",${element.className}.class,$interceptors,$factory));
                    """.trimIndent()
                )
            }
        }

        val static = loader.makeClassInitializer()
        static.insertBefore(buildString {
            append("{")
            append(srs.joinToString(separator = "\n"))
            append("\n")
            append(rrs.joinToString(separator = "\n"))
            append("\n")
            append(irs.joinToString(separator = "\n"))
            append("}")
        })
        loader.addMethod(
            CtNewMethod.make(
                Modifier.PUBLIC or Modifier.STATIC, CtClass.voidType, "load",
                emptyArray(), emptyArray(), """
                {
                    final java.lang.StringBuilder builder = new java.lang.StringBuilder();
                    builder.append("================================================================\n");
                    builder.append("OkRouter load completed!\n");
                    builder.append("stable routers("+${Types.wareHouse.canonicalName()}.getStableRouters().size()+")\n");
                    builder.append("regex routers("+${Types.wareHouse.canonicalName()}.getRegexRouters().size()+")\n");
                    builder.append("router interceptors("+${Types.wareHouse.canonicalName()}.getInterceptorMetas().size()+")\n");
                    builder.append("global router interceptors("+${Types.wareHouse.canonicalName()}.getGlobalInterceptors().size()+")\n");
                    ${Types.okRouter.canonicalName()}.getLogger().i(${Types.okRouter.canonicalName()}.TAG,builder.toString());
                    ${Types.okRouter.canonicalName()}.getLogger().i(${Types.okRouter.canonicalName()}.TAG,"================================================================");
                }
                """.trimIndent(), loader
            )
        )
        loader.writeFile(targetDir.canonicalPath)
    }

    private fun buildInterceptorFactory(name: String, element: InterceptorElement): String {
        val factory = classPool.makeClass(
            getInterceptorFactoryName(name),
            Types.routerInterceptorFactory.toCtClass(classPool)
        )
        factory.modifiers = Modifier.FINAL or  Modifier.PUBLIC
        val constructor = CtNewConstructor.make(
            arrayOf(CtClass.booleanType),
            emptyArray(), "{ super(\$1); }", factory
        )
        factory.addConstructor(constructor)
        val method = CtNewMethod.make(
            Modifier.PUBLIC or Modifier.FINAL,
            Types.routerInterceptor.toCtClass(classPool),
            "newInstance",
            emptyArray(),
            emptyArray(),
            "{ return new $name(); }",
            factory
        )
        factory.addMethod(method)
        factory.writeFile(targetDir.canonicalPath)
        return "new ${factory.name}(${element.singleton})"
    }

    private fun buildContextFactory(element: RouterElement): String {
        check(element.routerType == RouterType.FRAGMENT || element.routerType == RouterType.HANDLER || element.routerType == RouterType.VIEW)
        val factory = classPool.makeClass(getRouterTargetContextFactoryName(element.className))
        factory.modifiers = Modifier.PUBLIC or Modifier.FINAL
        factory.addInterface(Types.contextFactory.toCtClass(classPool))
        factory.addMethod(
            CtNewMethod.make(
                Modifier.PUBLIC or Modifier.FINAL,
                classPool.get(Object::class.java.canonicalName),
                "create",
                arrayOf(Types.context.toCtClass(classPool)),
                emptyArray(),
                "{ return new ${element.className}(${
                    if (element.routerType == RouterType.VIEW) {
                        "\$1"
                    } else {
                        ""
                    }
                }); }",
                factory
            )
        )
        factory.writeFile(targetDir.canonicalPath)
        return "new ${factory.name}()"
    }

    private fun getRouterTargetContextFactoryName(name: String): String {
        val pkg = name.subSequence(0, name.lastIndexOf("."))
        val cn = name.substring(startIndex = name.lastIndexOf(".") + 1)
        val factory = "_${cn}OkRouterContextFactory_"
        return "${pkg}.$factory"
    }

    private fun getInterceptorFactoryName(name: String): String {
        val pkg = name.subSequence(0, name.lastIndexOf("."))
        val cn = name.substring(startIndex = name.lastIndexOf(".") + 1)
        val factory = "_${cn}OkRouterFactory_"
        return "${pkg}.$factory"
    }
}