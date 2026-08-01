package com.anymore.okrouter

import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.api.ApplicationVariant
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile

/**
 * OkRouter Transform 插件
 *
 * 适配 AGP 8.13.0
 * 在应用源码编译后生成并编译路由表。
 */
class OkRouterTransformPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        if (!target.plugins.hasPlugin(AppPlugin::class.java)) {
            throw GradleException("OkRouterTransformPlugin must apply in application module!")
        }

        target.extensions.create("okRouter", OkRouterExtension::class.java)
        val android = target.extensions.getByType(AppExtension::class.java)

        android.applicationVariants.all { variant ->
            setupVariantTasks(target, variant, android)
        }

        Logger.v("${target.displayName} 已注册 OkRouter AGP 8 任务。")
    }

    /**
     * 为每个 variant 设置任务
     */
    private fun setupVariantTasks(
        project: Project,
        variant: ApplicationVariant,
        android: AppExtension
    ) {
        val variantName = variant.name
        val variantNameCapitalized = variantName.replaceFirstChar { it.uppercase() }
        val javaCompileTask = variant.javaCompileProvider
        val generatedSourceDir = project.layout.buildDirectory.dir(
            "generated/okrouter/$variantName/source"
        )

        val registerTask = project.tasks.register(
            "okRouterRegister$variantNameCapitalized",
            OkRouterRegisterTask::class.java
        ) {
            it.dependsOn(javaCompileTask)
            it.classpath.from(android.bootClasspath)
            it.classpath.from(javaCompileTask.map { task -> task.classpath })
            it.classpath.from(javaCompileTask.flatMap { task -> task.destinationDirectory })
            it.outputDirectory.set(generatedSourceDir)
        }

        // auto-service 在 Java 编译之后生成注册表类；存在时必须先完成，
        // 否则 Gradle 会拒绝读取其未声明依赖的 classes 输出。
        val autoServiceRegistryTask = project.tasks.matching {
            it.name == "compileAndroidAutoServiceRegistry$variantNameCapitalized"
        }
        registerTask.configure { it.dependsOn(autoServiceRegistryTask) }

        val compileTask = project.tasks.register(
            "okRouterCompile$variantNameCapitalized",
            JavaCompile::class.java
        ) {
            it.dependsOn(registerTask)
            it.source(generatedSourceDir)
            it.classpath = project.files(
                android.bootClasspath,
                javaCompileTask.map { task -> task.classpath },
                javaCompileTask.flatMap { task -> task.destinationDirectory }
            )
            it.destinationDirectory.set(
                javaCompileTask.flatMap { task -> task.destinationDirectory }
            )
            it.sourceCompatibility = "17"
            it.targetCompatibility = "17"
        }

        // 这些 AGP 任务都会消费 Java classes 输出；显式依赖可避免 Gradle 8
        // 将同一输出目录判定为隐式任务依赖。
        project.tasks.matching {
            it.name in setOf(
                "dexBuilder$variantNameCapitalized",
                "bundle${variantNameCapitalized}ClassesToCompileJar",
                "bundle${variantNameCapitalized}ClassesToRuntimeJar"
            ) || (it.name.contains(variantNameCapitalized) && it.name.lowercase().contains("lint"))
        }.configureEach {
            it.dependsOn(autoServiceRegistryTask)
            it.dependsOn(compileTask)
        }
        variant.assembleProvider.configure { it.dependsOn(compileTask) }
        Logger.v("已注册 $variantName 的 OkRouter 生成任务。")
    }
}
