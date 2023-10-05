package com.anymore.okrouter

import com.squareup.javapoet.*
import org.gradle.api.Project
import java.io.File
import javax.lang.model.element.Modifier

internal class OkRouterTaskAction(classpath: Collection<File>, targetDir: File, project: Project) :
    AbsOkRouterAction(classpath, targetDir, project) {

    override fun createRouterLoader(
        stableRouterElements: List<RouterElement>,
        regexRouterElements: List<RouterElement>,
        interceptorElements: Map<String, InterceptorElement>,
        interceptorAlias: Map<String, InterceptorElement>,
        targetDir: File
    ) {
        val staticRegisterBlock = CodeBlock.builder().let { builder ->
            if (stableRouterElements.isNotEmpty()) {
                builder.add("//register stable router\n")
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
                    val interceptors = CodeBlock.builder().run {
                        add("new \$T[]{", Types.classType)
                        var first = true
                        ics.forEach {
                            if (first) {
                                first = false
                            } else {
                                add(",")
                            }
                            add("\$T.class", ClassName.bestGuess(it))
                        }
                        add("}")
                        build()
                    }
                    val cn = ClassName.bestGuess(element.className)
                    val factory = when (element.routerType) {
                        RouterType.FRAGMENT, RouterType.HANDLER -> {
                            TypeSpec.anonymousClassBuilder("")
                                .addSuperinterface(Types.contextFactory)
                                .addMethod(
                                    MethodSpec.methodBuilder("create")
                                        .addModifiers(Modifier.PUBLIC)
                                        .addAnnotation(Types.nullable)
                                        .addAnnotation(Override::class.java)
                                        .addParameter(
                                            ParameterSpec.builder(
                                                Types.context,
                                                "context"
                                            ).addAnnotation(Types.nonNull).build()
                                        )
                                        .addStatement("return new \$T()", cn)
                                        .returns(cn)
                                        .build()
                                ).build()
                        }
                        RouterType.VIEW -> {
                            TypeSpec.anonymousClassBuilder("")
                                .addSuperinterface(Types.contextFactory)
                                .addMethod(
                                    MethodSpec.methodBuilder("create")
                                        .addModifiers(Modifier.PUBLIC)
                                        .addAnnotation(Types.nullable)
                                        .addAnnotation(Override::class.java)
                                        .addParameter(
                                            ParameterSpec.builder(
                                                Types.context,
                                                "context"
                                            ).addAnnotation(Types.nonNull).build()
                                        )
                                        .addStatement("return new \$T(context)", cn)
                                        .returns(cn)
                                        .build()
                                ).build()
                        }
                        else -> {
                            null
                        }
                    }
                    builder.addStatement(
                        "\$T.registerStableRouter(\$S,new \$T(new \$T(\$S,\$S,\$S,\$L),\$T.\$L,\$S,\$T.class,\$L,\$L))",
                        Types.wareHouse, element.uri, Types.routerMeta, Types.routerUri,
                        element.scheme, element.host, element.path, element.priority,
                        Types.routerType, element.routerType.name, element.className,
                        cn, interceptors, factory
                    )
                }
            }

            if (regexRouterElements.isNotEmpty()) {
                builder.add("//register regex router\n")
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
                    val interceptors = CodeBlock.builder().run {
                        add("new \$T[]{", Types.classType)
                        var first = true
                        ics.forEach {
                            if (first) {
                                first = false
                            } else {
                                add(",")
                            }
                            add("\$T.class", ClassName.bestGuess(it))
                        }
                        add("}")
                        build()
                    }
                    val cn = ClassName.bestGuess(element.className)
                    val factory = when (element.routerType) {
                        RouterType.FRAGMENT, RouterType.HANDLER -> {
                            TypeSpec.anonymousClassBuilder("")
                                .addSuperinterface(Types.contextFactory)
                                .addMethod(
                                    MethodSpec.methodBuilder("create")
                                        .addModifiers(Modifier.PUBLIC)
                                        .addAnnotation(Types.nullable)
                                        .addAnnotation(Override::class.java)
                                        .addParameter(
                                            ParameterSpec.builder(
                                                Types.context,
                                                "context"
                                            ).addAnnotation(Types.nonNull).build()
                                        )
                                        .addStatement("return new \$T()", cn)
                                        .returns(cn)
                                        .build()
                                ).build()
                        }
                        RouterType.VIEW -> {
                            TypeSpec.anonymousClassBuilder("")
                                .addSuperinterface(Types.contextFactory)
                                .addMethod(
                                    MethodSpec.methodBuilder("create")
                                        .addModifiers(Modifier.PUBLIC)
                                        .addAnnotation(Types.nullable)
                                        .addAnnotation(Override::class.java)
                                        .addParameter(
                                            ParameterSpec.builder(
                                                Types.context,
                                                "context"
                                            ).addAnnotation(Types.nonNull).build()
                                        )
                                        .addStatement("return new \$T(context)", cn)
                                        .returns(cn)
                                        .build()
                                ).build()
                        }
                        else -> {
                            null
                        }
                    }
                    builder.addStatement(
                        "\$T.registerRegexRouter(new \$T(\$S,\$S,\$S,\$L),new \$T(new \$T(\$S,\$S,\$S,\$L),\$T.\$L,\$S,\$T.class,\$L,\$L))",
                        Types.wareHouse, Types.routerUri, element.scheme,
                        element.host, element.path, element.priority, Types.routerMeta,
                        Types.routerUri, element.scheme, element.host, element.path,
                        element.priority, Types.routerType, element.routerType.name,
                        element.className, cn, interceptors, factory
                    )
                }
            }

            if (interceptorElements.isNotEmpty()) {
                builder.add("//register router interceptor\n")
                interceptorElements.forEach { entry ->
                    val key = entry.key
                    val value = entry.value
                    val cn = ClassName.bestGuess(key)
                    val factory = TypeSpec.anonymousClassBuilder("\$L", value.singleton)
                        .addSuperinterface(Types.routerInterceptorFactory)
                        .addMethod(
                            MethodSpec.methodBuilder("newInstance")
                                .addModifiers(Modifier.PUBLIC)
                                .addAnnotation(Types.nonNull)
                                .addAnnotation(Override::class.java)
                                .addStatement("return new \$T()", cn)
                                .returns(Types.routerInterceptor)
                                .build()
                        )
                        .build()
                    builder.addStatement(
                        "\$T.registerInterceptor(\$T.class,new \$T(\$T.class,\$S,\$S,\$L,\$L,\$L,\$L),\$L)",
                        Types.wareHouse, cn, Types.routerInterceptorMeta, cn, key, value.alias,
                        factory, value.priority, value.global, value.singleton, value.global
                    )

                }
            }

            builder.build()
        }

        val loadMethod = MethodSpec.methodBuilder("load")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addStatement(
                CodeBlock.builder().add(
                    """
                    final java.lang.StringBuilder builder = new java.lang.StringBuilder();
                    builder.append("================================================================\n");
                    builder.append("OkRouter load completed!\n");
                    builder.append("stable routers("+${Types.wareHouse.canonicalName()}.getStableRouters().size()+")\n");
                    builder.append("regex routers("+${Types.wareHouse.canonicalName()}.getRegexRouters().size()+")\n");
                    builder.append("router interceptors("+${Types.wareHouse.canonicalName()}.getInterceptorMetas().size()+")\n");
                    builder.append("global router interceptors("+${Types.wareHouse.canonicalName()}.getGlobalInterceptors().size()+")\n");
                    ${Types.okRouter.canonicalName()}.getLogger().d(${Types.okRouter.canonicalName()}.TAG,builder.toString());
                    ${Types.okRouter.canonicalName()}.getLogger().d(${Types.okRouter.canonicalName()}.TAG,"================================================================");
                """.trimIndent()
                ).build()
            )
            .build()

        val warehouseLoader = TypeSpec.classBuilder(loaderName)
            .addJavadoc("Automatically generated file by OkRouter. DO NOT MODIFY!!!")
            .addModifiers(Modifier.FINAL, Modifier.PUBLIC)
            .addAnnotation(
                AnnotationSpec.builder(
                    SuppressWarnings::class.java
                ).addMember(
                    "value",
                    "\$S",
                    "unchecked"
                ).build()
            )
            .addStaticBlock(staticRegisterBlock)
            .addMethod(loadMethod)
            .build()
        val javaFile = JavaFile.builder(targetPkg, warehouseLoader).build()
        javaFile.writeTo(targetDir)
    }

}