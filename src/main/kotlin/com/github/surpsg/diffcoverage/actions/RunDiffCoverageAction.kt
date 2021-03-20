package com.github.surpsg.diffcoverage.actions

import com.github.surpsg.diffcoverage.domain.DiffCoverageConfiguration
import com.github.surpsg.diffcoverage.services.CoverageVizualizeService
import com.github.surpsg.diffcoverage.services.gradle.GradleDiffCoveragePluginService
import com.github.surpsg.diffcoverage.services.gradle.GradleService
import com.intellij.notification.Notification
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import java.nio.file.Paths

class RunDiffCoverageAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        if (!project.service<GradleService>().isGradleProject()) {
            Messages.showMessageDialog(
                project, "" +
                        "Not a Gradle project", "DiffCoverage plugin", Messages.getWarningIcon()
            )
            return
        }

        val settings: GradleProjectSettings = GradleSettings.getInstance(project).linkedProjectsSettings.first()

        val diffCoveragePluginService = project.service<GradleDiffCoveragePluginService>()
        val diffCoverageModule = diffCoveragePluginService.lookupDiffCoveragePluginModule(settings.externalProjectPath)
            ?: return

        ProgressManager.getInstance().runProcess({
            diffCoveragePluginService.collectDiffCoverageInfo(diffCoverageModule).thenAccept { coverageInfo ->
                project.service<GradleService>().executeGradleTask("diffCoverage", diffCoverageModule.second) {
                    showDiffCoverageReportNotification(coverageInfo, project)
                }

                project.service<CoverageVizualizeService>().showCoverage(coverageInfo)
            }
        }, EmptyProgressIndicator(ModalityState.defaultModalityState()))
    }


    private fun showDiffCoverageReportNotification(diffCoverageInfo: DiffCoverageConfiguration, project: Project) {
        val defaultDiffCoverageReportPath = "diffCoverage/html/index.html"
        val reportUrl = Paths.get(diffCoverageInfo.reportsRoot, defaultDiffCoverageReportPath).toUri().toString()

        val reportLink = """<a href="$reportUrl">Diff coverage report</a>""".trimIndent()
        Notifications.Bus.notify(
            Notification(
                "",
                "Diff Coverage",
                reportLink,
                NotificationType.INFORMATION,
                NotificationListener.URL_OPENING_LISTENER
            ),
            project
        )
    }

    override fun isDumbAware(): Boolean = false
}
