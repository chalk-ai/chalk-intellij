package com.chalk.intellij

import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class ChalkSettingsConfigurable(private val project: Project) : Configurable {
    private val version = JBLabel()
    private val conflicts = JBLabel()

    override fun getDisplayName(): String = "Chalk"

    override fun createComponent(): JComponent {
        refreshVersion()
        return panel {
            row("Language server:") { cell(version) }
            row("Potential conflicts:") { cell(conflicts) }
            row {
                button("Update language server") { ChalkServerActions.update(project, ::refreshVersion) }
                button("Restart") { ChalkServerActions.restart(project) }
                button("Open IDE log") { ChalkServerActions.showLog() }
            }
            row {
                comment("Chalk is the semantic and typechecking authority inside roots containing chalk.yml or chalk.yaml. Project pyproject.toml settings suppress third-party typecheckers.")
            }
        }
    }

    override fun isModified(): Boolean = false
    override fun apply() = Unit
    override fun reset() = refreshVersion()

    private fun refreshVersion() {
        version.text = service<ChalkLspInstaller>().installedVersion()?.let { "chalk-lsp $it" }
            ?: "Not installed (installs when a Chalk file opens)"
        conflicts.text = ChalkServerActions.conflictingLanguageServerPlugins().joinToString().ifEmpty { "None detected" }
    }
}
