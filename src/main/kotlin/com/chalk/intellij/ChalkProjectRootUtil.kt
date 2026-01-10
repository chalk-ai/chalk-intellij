package com.chalk.intellij

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

/**
 * Utility to find the Chalk project root by looking for chalk.yaml or chalk.yml
 */
object ChalkProjectRootUtil {

    private val CHALK_CONFIG_FILES = listOf("chalk.yaml", "chalk.yml")

    /**
     * Find the Chalk project root by searching upward from the given file
     * until we find a chalk.yaml or chalk.yml file.
     *
     * @param file The file to start searching from
     * @return The directory containing chalk.yaml/yml, or null if not found
     */
    fun findChalkRoot(file: VirtualFile): VirtualFile? {
        var current: VirtualFile? = file.parent
        while (current != null) {
            for (configFile in CHALK_CONFIG_FILES) {
                if (current.findChild(configFile) != null) {
                    return current
                }
            }
            current = current.parent
        }
        return null
    }

    /**
     * Find the Chalk project root from a PsiFile
     */
    fun findChalkRoot(psiFile: PsiFile): VirtualFile? {
        val virtualFile = psiFile.virtualFile ?: return null
        return findChalkRoot(virtualFile)
    }

    /**
     * Create a search scope limited to the Chalk project root and its descendants.
     * This ensures we only search within the Chalk project, not the entire IDE project.
     */
    fun createChalkSearchScope(project: Project, fromFile: VirtualFile): GlobalSearchScope? {
        val chalkRoot = findChalkRoot(fromFile) ?: return null
        return GlobalSearchScope.getScopeRestrictedByFileTypes(
            GlobalSearchScope.fileScope(project, chalkRoot).union(
                createDescendantsScope(project, chalkRoot)
            ),
            *arrayOf() // All file types
        )
    }

    /**
     * Create a scope that includes all descendants of the given directory
     */
    private fun createDescendantsScope(project: Project, root: VirtualFile): GlobalSearchScope {
        return object : GlobalSearchScope(project) {
            override fun contains(file: VirtualFile): Boolean {
                var current: VirtualFile? = file
                while (current != null) {
                    if (current == root) return true
                    current = current.parent
                }
                return false
            }

            override fun isSearchInModuleContent(aModule: com.intellij.openapi.module.Module): Boolean = true
            override fun isSearchInLibraries(): Boolean = false
        }
    }
}
