package com.chalk.intellij

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor
import java.util.concurrent.ConcurrentHashMap

class ChalkLspServerSupportProvider : LspServerSupportProvider {
    private val descriptors = ConcurrentHashMap<String, ChalkLspServerDescriptor>()
    private val installing = ConcurrentHashMap.newKeySet<String>()
    private val reportedConflicts = ConcurrentHashMap.newKeySet<String>()

    override fun fileOpened(project: Project, file: VirtualFile, serverStarter: LspServerSupportProvider.LspServerStarter) {
        val root = ChalkProjectRootUtil.findChalkRoot(file) ?: return
        if (!ChalkLspServerDescriptor.supports(root, file)) return
        if (reportedConflicts.add(root.path)) ChalkServerActions.reportConflicts(project)
        val descriptor = descriptors.computeIfAbsent(root.path) { ChalkLspServerDescriptor(project, root) }
        val installer = service<ChalkLspInstaller>()
        if (installer.installedVersion() != null) {
            serverStarter.ensureServerStarted(descriptor)
            return
        }
        if (!installing.add(root.path)) return

        object : Task.Backgroundable(project, "Installing Chalk language server", false) {
            override fun run(indicator: ProgressIndicator) {
                installer.ensureInstalled()
            }

            override fun onSuccess() {
                installing.remove(root.path)
                @Suppress("DEPRECATION")
                LspServerManager.getInstance(project).ensureServerStarted(ChalkLspServerSupportProvider::class.java, descriptor)
            }

            override fun onThrowable(error: Throwable) {
                installing.remove(root.path)
                ChalkServerActions.reportFailure(project, "install", error)
            }
        }.queue()
    }
}

private class ChalkLspServerDescriptor(
    project: Project,
    private val root: VirtualFile,
) : ProjectWideLspServerDescriptor(project, "Chalk") {
    override fun isSupportedFile(file: VirtualFile): Boolean = supports(root, file)

    override fun createCommandLine(): GeneralCommandLine = GeneralCommandLine(
        service<ChalkLspInstaller>().ensureInstalled().toString(),
        "lsp",
    ).withWorkDirectory(root.path)

    companion object {
        fun supports(root: VirtualFile, file: VirtualFile): Boolean {
            var current: VirtualFile? = if (file.isDirectory) file else file.parent
            while (current != null && current != root) current = current.parent
            if (current == null) return false
            return file.extension == "py" || file.name.endsWith(".chalk.sql") || file.name == "chalk.yml" || file.name == "chalk.yaml"
        }
    }
}
