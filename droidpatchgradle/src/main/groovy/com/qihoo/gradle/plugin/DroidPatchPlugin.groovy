package com.qihoo.gradle.plugin

import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.qihoo.gradle.transform.DroidPatchTransform
import org.gradle.api.Plugin
import org.gradle.api.Project

public class DroidPatchPlugin implements Plugin<Project>{

    @Override
    void apply(Project project) {
        /**
         * 注册transform接口
         */
        def isApp = project.plugins.hasPlugin(AppPlugin)
        if (isApp) {
            def android = project.extensions.getByType(AppExtension)
            def transform = new DroidPatchTransform(project)
            android.registerTransform(transform)

        }
    }

}