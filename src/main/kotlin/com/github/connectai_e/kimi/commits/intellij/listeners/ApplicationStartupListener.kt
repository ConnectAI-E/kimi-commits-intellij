package com.github.connectai_e.kimi.commits.intellij.listeners

import com.github.connectai_e.kimi.commits.intellij.AICommitsBundle
import com.github.connectai_e.kimi.commits.intellij.notifications.Notification
import com.github.connectai_e.kimi.commits.intellij.notifications.sendNotification
import com.github.connectai_e.kimi.commits.intellij.settings.AppSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class ApplicationStartupListener : ProjectActivity {

    private var firstTime = true
    override suspend fun execute(project: Project) {
        showVersionNotification(project)
    }
    private fun showVersionNotification(project: Project) {
        val settings = AppSettings.instance
        val version = AICommitsBundle.plugin()?.version

        if (version == settings.lastVersion) {
            return
        }

        settings.lastVersion = version
        if (firstTime && version != null) {
            sendNotification(Notification.welcome(version), project)
        }
        firstTime = false
    }
}