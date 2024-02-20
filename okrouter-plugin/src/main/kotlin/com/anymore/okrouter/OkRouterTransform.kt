package com.anymore.okrouter

import com.android.build.api.transform.Format
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.AppExtension
import com.android.build.gradle.internal.pipeline.TransformManager
import org.apache.commons.io.FileUtils
import org.gradle.api.Project
import java.io.File
import java.util.*

/**
 * Created by anymore on 2023/6/17.
 */
@Suppress("unused")
class OkRouterTransform(private val project: Project) : Transform() {

    val okRouter = project.extensions.getByType(OkRouterExtension::class.java)

    val android = project.extensions.getByType(AppExtension::class.java)

    val classpath: Queue<File> = LinkedList<File>()

    override fun getName() = "OkRouterTransform"

    override fun getInputTypes() = TransformManager.CONTENT_CLASS

    override fun getScopes() = TransformManager.SCOPE_FULL_PROJECT

    override fun isIncremental() = okRouter.isIncremental

    override fun transform(transformInvocation: TransformInvocation?) {
        super.transform(transformInvocation)
        val start = System.currentTimeMillis()
        Logger.v("$okRouter")
        classpath.addAll(android.bootClasspath)
        if (transformInvocation == null) return
        if (!transformInvocation.isIncremental) {
            transformInvocation.outputProvider.deleteAll()
        }
        transformInvocation.inputs.forEach {
            it.directoryInputs.forEach { dir ->
                classpath.offer(dir.file)
                val destDir = transformInvocation.outputProvider.getContentLocation(
                    dir.name, dir.contentTypes, dir.scopes, Format.DIRECTORY
                )
                if (transformInvocation.isIncremental) {
                    //TODO
                } else {
                    FileUtils.copyDirectory(dir.file, destDir)
                }
            }
            it.jarInputs.forEach { jar ->
                classpath.offer(jar.file)
                val destJar = transformInvocation.outputProvider.getContentLocation(
                    jar.name, jar.contentTypes, jar.scopes, Format.JAR
                )
                if (transformInvocation.isIncremental) {
                    //TODO
                } else {
                    FileUtils.copyFile(jar.file, destJar)
                }
            }
        }
        val targetDir = transformInvocation.outputProvider.getContentLocation(
            "okRouter",
            TransformManager.CONTENT_CLASS,
            TransformManager.PROJECT_ONLY,
            Format.DIRECTORY
        )
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        AbsOkRouterAction.forTransform(classpath, targetDir, project).execute()
        Logger.s("build successful in [${(System.currentTimeMillis()-start).toFloat()/1000} s]")
    }

}