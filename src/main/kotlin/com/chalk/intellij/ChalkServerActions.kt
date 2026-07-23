package com.chalk.intellij

import com.intellij.ide.actions.RevealFileAction
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.LspServerSupportProvider
import java.io.File

object ChalkServerActions {
    fun update(project: Project, finished: (() -> Unit)? = null) {
        for (openProject in ProjectManager.getInstance().openProjects) {
            @Suppress("DEPRECATION")
            LspServerManager.getInstance(openProject).stopServers(ChalkLspServerSupportProvider::class.java)
        }
        object : Task.Backgroundable(project, "Updating Chalk language server", false) {
            override fun run(indicator: ProgressIndicator) {
                service<ChalkLspInstaller>().ensureInstalled(true)
            }

            override fun onSuccess() {
                startOpenProjectServers()
                finished?.invoke()
            }

            override fun onThrowable(error: Throwable) {
                startOpenProjectServers()
                reportFailure(project, "update", error)
                finished?.invoke()
            }
        }.queue()
    }

    fun restart(project: Project) {
        @Suppress("DEPRECATION")
        LspServerManager.getInstance(project).stopAndRestartIfNeeded(ChalkLspServerSupportProvider::class.java)
    }

    fun showLog() {
        RevealFileAction.openFile(File(PathManager.getLogPath(), "idea.log"))
    }

    fun reportFailure(project: Project, operation: String, error: Throwable) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Chalk Language Server")
            .createNotification("Chalk language server $operation failed", error.message ?: error.toString(), NotificationType.ERROR)
            .notify(project)
    }

    fun conflictingLanguageServerPlugins(): List<String> {
        val competingName = Regex("(?i)(pylance|basedpyright|pyright|pyrefly|\\bpyre\\b|mypy|python[ ._-]?lsp|jedi|ruff|\\bty\\b)")
        return LspServerSupportProvider.EP_NAME.extensionList
            .mapNotNull { provider ->
                val plugin = (provider.javaClass.classLoader as? PluginAwareClassLoader)?.pluginDescriptor
                    ?: return@mapNotNull null
                if (
                    plugin.pluginId.idString == "com.chalk.plugin" ||
                    !competingName.containsMatchIn("${plugin.name} ${plugin.pluginId.idString} ${provider.javaClass.name}")
                ) {
                    return@mapNotNull null
                }
                plugin.name
            }
            .distinct()
            .sorted()
    }

    fun reportConflicts(project: Project) {
        val conflicts = conflictingLanguageServerPlugins()
        if (conflicts.isEmpty()) return
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Chalk Language Server")
            .createNotification(
                "Potential Python language server conflict",
                "Chalk does not change global third-party plugin settings. Check these plugins for this Chalk project: ${conflicts.joinToString(", ")}",
                NotificationType.WARNING,
            )
            .notify(project)
    }

    private fun startOpenProjectServers() {
        for (openProject in ProjectManager.getInstance().openProjects) {
            @Suppress("DEPRECATION")
            LspServerManager.getInstance(openProject).startServersIfNeeded(ChalkLspServerSupportProvider::class.java)
        }
    }
}

class ChalkUpdateServerAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        event.project?.let { ChalkServerActions.update(it) }
    }
}

class ChalkRestartServerAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        event.project?.let { ChalkServerActions.restart(it) }
    }
}
