package com.yu.reinforce

import com.yu.reinforce.extension.PgyExtension
import com.yu.reinforce.extension.ReinforceExtension360
import com.yu.reinforce.task.ChannelTask
import com.yu.reinforce.task.PublishApkTask
import com.yu.reinforce.task.ReinforceTask360
import com.yu.reinforce.util.Util
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task

class ReinforcePlugin implements Plugin<Project> {

    private Project project
    private ReinforceExtension360 m360Extension
    private PgyExtension mPgyExtension

    @Override
    void apply(Project project) {
        this.project = project
        m360Extension = project.extensions.create("reinforceConfig360", ReinforceExtension360)
        mPgyExtension = project.extensions.create("pgyConfig", PgyExtension)

        project.afterEvaluate {
            project.android.applicationVariants.all { variant ->
                def flavorName = variant.flavorName
                def versionCode = variant.versionCode
                def versionName = variant.versionName
                def buildTypeName = variant.buildType.getName()
                def signConfig = variant.getSigningConfig()

                def assembleTask = project.tasks.find {
                    it.name == "assemble${Util.toUpperFirstChar(flavorName)}${Util.toUpperFirstChar(buildTypeName)}"
                }

                variant.outputs.each {
                    if (!"debug".equalsIgnoreCase(buildTypeName)) {

                        String apkPath = it.getOutputFile().absolutePath
                        String jiaguOutput = it.getOutputFile().getParentFile().absolutePath + File.separator + "jiagu" + File.separator + "temp_jiagu_signed.apk"
                        println "待加固apk path = ${apkPath}"
                        println "加固后输出apk path = ${jiaguOutput}"

                        // 创建task，分为依赖assemble和不依赖两种
                        // 360加固
                        Task _360Task = create360Task("", signConfig, assembleTask, apkPath, jiaguOutput)
                        create360Task("Only", signConfig, assembleTask, apkPath, jiaguOutput)
                        // 发布到蒲公英
                        createPublishApkTask("publishToPgy${Util.toUpperFirstChar(buildTypeName)}", _360Task, jiaguOutput, buildTypeName)
                        createPublishApkTask("publishToPgy${Util.toUpperFirstChar(buildTypeName)}Only", null, jiaguOutput, buildTypeName)

                        if ("release".equalsIgnoreCase(buildTypeName)) {
                            createChannelTask("buildChannel${Util.toUpperFirstChar(buildTypeName)}", flavorName, versionCode, versionName, buildTypeName)
                        }
                    }
                }
            }
        }
    }

    private Task createChannelTask(taskName, flavorName, versionCode, versionName, buildTypeName) {
        ChannelTask task = project.tasks.create(taskName, ChannelTask)
        task.flavorName = flavorName
        task.versionCode = versionCode
        task.versionName = versionName
        task.buildTypeName = buildTypeName
        task.setGroup("jiagu_sign")

        println "创建${taskName}完毕"
        return task
    }

    private Task create360Task(taskNameSuffix, signConfig, assembleTask, apkPath, outputPath) {
        def subName = assembleTask.name.replace("assemble", "")
        def taskName = "_360Jiagu${subName}${taskNameSuffix}"

        ReinforceTask360 task = project.tasks.create(taskName, ReinforceTask360)
        task.signingConfig = signConfig
        task.account = m360Extension.account
        task.password = m360Extension.password
        task.apkPath = apkPath
        task.outputPath = outputPath

        task.setGroup("jiagu_sign")
        if (Util.isEmpty(taskNameSuffix))
            task.dependsOn(assembleTask)
        println "创建${taskName}完毕"

        return task
    }

    private Task createPublishApkTask(taskName, jiaguTask, apkPath, buildTypeName) {
        PublishApkTask task = project.tasks.create(taskName, PublishApkTask)
        task.apkPath = apkPath
        task.buildTypeName = buildTypeName

        task.password = mPgyExtension.password
        task.deadTime = mPgyExtension.deadTime
        task.apiKey = mPgyExtension.apiKey
        task.dingTalkUrl = mPgyExtension.dingTalkUrl
        task.dingTalkSec = mPgyExtension.dingTalkSec

        task.setGroup("jiagu_sign")
        if (jiaguTask != null)
            task.dependsOn(jiaguTask)

        println "创建${taskName}完毕"

        return task
    }
}