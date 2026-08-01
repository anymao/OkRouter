package com.anymore.okrouter

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * 扫描变体编译类路径，并生成 OkRouterLoader 的 Java 源码。
 */
abstract class OkRouterRegisterTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val classpath: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val start = System.currentTimeMillis()
        val outputDir = outputDirectory.get().asFile
        AbsOkRouterAction.forTask(classpath.files, outputDir, project).execute()
        val duration = (System.currentTimeMillis() - start) / 1000.0
        Logger.s("OkRouter 路由表生成完成，耗时 ${duration}s")
    }
}
