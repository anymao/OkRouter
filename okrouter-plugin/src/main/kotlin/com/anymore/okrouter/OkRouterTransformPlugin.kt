package com.anymore.okrouter

import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

class OkRouterTransformPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        if (!target.plugins.hasPlugin(AppPlugin::class.java)) {
            throw GradleException("OkRouterTransformPlugin must apply in application module!")
        }
        target.extensions.create("okRouter", OkRouterExtension::class.java)
        val android = target.extensions.getByType(AppExtension::class.java)
        val okRouterTransform = OkRouterTransform(target)
        android.registerTransform(okRouterTransform)
        Logger.v("${target.displayName} registered OkRouterTransformPlugin.")
    }
}