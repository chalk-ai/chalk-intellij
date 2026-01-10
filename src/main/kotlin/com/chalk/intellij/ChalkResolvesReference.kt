package com.chalk.intellij

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.psi.*
import com.intellij.psi.impl.FakePsiElement
import com.intellij.psi.search.GlobalSearchScope

/**
 * Reference from a "-- resolves: ClassName" comment to the Python class definition.
 */
class ChalkResolvesReference(
    element: PsiElement,
    private val className: String,
    textRange: TextRange
) : PsiReferenceBase<PsiElement>(element, textRange, true) {

    override fun resolve(): PsiElement? {
        val project = element.project
        val virtualFile = element.containingFile?.virtualFile ?: return null
        val chalkRoot = ChalkProjectRootUtil.findChalkRoot(virtualFile) ?: return null

        // Search for Python files containing the class definition
        return findPythonClass(project, chalkRoot, className)
    }

    override fun getVariants(): Array<Any> {
        // Could provide autocomplete suggestions here in the future
        return arrayOf()
    }

    private fun findPythonClass(project: Project, chalkRoot: VirtualFile, className: String): PsiElement? {
        val psiManager = PsiManager.getInstance(project)

        // Pattern to match class definition: "class ClassName" or "@features\nclass ClassName"
        val classPattern = Regex("""class\s+$className\s*[:(]""")

        // Search through Python files
        val pythonFiles = findPythonFiles(chalkRoot)

        for (file in pythonFiles) {
            val psiFile = psiManager.findFile(file) ?: continue
            val text = psiFile.text

            val match = classPattern.find(text)
            if (match != null) {
                // Find the exact position of the class name within the match
                val matchText = match.value
                val classNameOffsetInMatch = matchText.indexOf(className)
                val classNameStart = match.range.first + classNameOffsetInMatch
                if (classNameStart >= 0) {
                    // Return a navigatable element that goes to the exact offset
                    return OffsetNavigatableElement(psiFile, classNameStart, className)
                }
            }
        }

        return null
    }

    private fun findPythonFiles(root: VirtualFile): List<VirtualFile> {
        val result = mutableListOf<VirtualFile>()
        collectPythonFiles(root, result)
        return result
    }

    private fun collectPythonFiles(dir: VirtualFile, result: MutableList<VirtualFile>) {
        for (child in dir.children) {
            when {
                child.isDirectory && !child.name.startsWith(".") && child.name != "__pycache__" && child.name != "node_modules" -> {
                    collectPythonFiles(child, result)
                }
                child.extension == "py" -> {
                    result.add(child)
                }
            }
        }
    }
}

/**
 * A fake PSI element that navigates to a specific offset in a file.
 */
class OffsetNavigatableElement(
    private val psiFile: PsiFile,
    private val offset: Int,
    private val name: String
) : FakePsiElement(), Navigatable {

    override fun getParent(): PsiElement = psiFile

    override fun getContainingFile(): PsiFile = psiFile

    override fun getName(): String = name

    override fun getTextOffset(): Int = offset

    override fun getTextRange(): TextRange = TextRange(offset, offset + name.length)

    override fun navigate(requestFocus: Boolean) {
        val virtualFile = psiFile.virtualFile ?: return
        val descriptor = OpenFileDescriptor(psiFile.project, virtualFile, offset)
        descriptor.navigate(requestFocus)
    }

    override fun canNavigate(): Boolean = psiFile.virtualFile != null

    override fun canNavigateToSource(): Boolean = canNavigate()
}
