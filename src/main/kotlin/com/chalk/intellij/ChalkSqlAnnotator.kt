package com.chalk.intellij

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * Annotator that highlights "-- resolves: ClassName" patterns in .chalk.sql files.
 * Makes the class name appear as a clickable reference (like a hyperlink).
 */
class ChalkSqlAnnotator : Annotator {

    companion object {
        // Pattern: "-- resolves: ClassName" or "-- source: source_name"
        private val RESOLVES_PATTERN = Regex("""--\s*resolves:\s*(\w+)""")
        private val SOURCE_PATTERN = Regex("""--\s*source:\s*(\w+)""")
    }

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        // Only process .chalk.sql files
        val fileName = element.containingFile?.name ?: return
        if (!fileName.endsWith(".chalk.sql")) {
            return
        }

        // Only process the file-level element to avoid duplicates
        if (element !is PsiFile && element.parent !is PsiFile) {
            return
        }

        val text = element.text
        val elementOffset = element.textRange.startOffset

        val hasResolves = RESOLVES_PATTERN.containsMatchIn(text)
        val hasSource = SOURCE_PATTERN.containsMatchIn(text)

        // Show errors for missing required comments
        if (!hasResolves || !hasSource) {
            // Find the end of the first line for error placement
            val firstLineEnd = text.indexOf('\n').let { if (it == -1) text.length else it }
            val errorRange = TextRange(elementOffset, elementOffset + firstLineEnd.coerceAtLeast(1))

            val missingItems = mutableListOf<String>()
            if (!hasResolves) missingItems.add("resolves")
            if (!hasSource) missingItems.add("source")

            holder.newAnnotation(HighlightSeverity.ERROR,
                "Missing required comment${if (missingItems.size > 1) "s" else ""}: ${missingItems.joinToString(", ") { item -> "-- $item:" }}")
                .range(errorRange)
                .withFix(AddMissingCommentsQuickFix(!hasSource, !hasResolves))
                .create()
        }

        // Annotate "resolves:" references
        for (match in RESOLVES_PATTERN.findAll(text)) {
            val classNameGroup = match.groups[1] ?: continue
            val startOffset = elementOffset + classNameGroup.range.first
            val endOffset = elementOffset + classNameGroup.range.last + 1
            val textRange = TextRange(startOffset, endOffset)

            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(textRange)
                .textAttributes(DefaultLanguageHighlighterColors.CLASS_REFERENCE)
                .create()
        }

        // Optionally highlight "source:" values too (dimmer)
        for (match in SOURCE_PATTERN.findAll(text)) {
            val sourceNameGroup = match.groups[1] ?: continue
            val startOffset = elementOffset + sourceNameGroup.range.first
            val endOffset = elementOffset + sourceNameGroup.range.last + 1
            val textRange = TextRange(startOffset, endOffset)

            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(textRange)
                .textAttributes(DefaultLanguageHighlighterColors.CONSTANT)
                .create()
        }
    }
}

/**
 * Quick fix to add missing source and/or resolves comments to a .chalk.sql file.
 */
class AddMissingCommentsQuickFix(
    private val addSource: Boolean,
    private val addResolves: Boolean
) : IntentionAction {

    override fun getText(): @IntentionName String {
        return when {
            addSource && addResolves -> "Add missing source and resolves comments"
            addSource -> "Add missing source comment"
            addResolves -> "Add missing resolves comment"
            else -> "Add missing comments"
        }
    }

    override fun getFamilyName(): @IntentionFamilyName String = "Chalk SQL"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        return file?.name?.endsWith(".chalk.sql") == true
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || file == null) return

        val document = editor.document
        val text = document.text

        // Build the comments to insert
        val commentsToAdd = StringBuilder()
        if (addSource) {
            commentsToAdd.append("-- source: YOUR_SOURCE_NAME\n")
        }
        if (addResolves) {
            commentsToAdd.append("-- resolves: YourClassName\n")
        }

        // Insert at the beginning of the file
        document.insertString(0, commentsToAdd.toString())

        // Position cursor at the first placeholder
        if (addSource) {
            val sourcePos = "-- source: ".length
            editor.caretModel.moveToOffset(sourcePos)
            // Select "YOUR_SOURCE_NAME"
            editor.selectionModel.setSelection(sourcePos, sourcePos + "YOUR_SOURCE_NAME".length)
        } else if (addResolves) {
            val resolvesPos = "-- resolves: ".length
            editor.caretModel.moveToOffset(resolvesPos)
            editor.selectionModel.setSelection(resolvesPos, resolvesPos + "YourClassName".length)
        }
    }

    override fun startInWriteAction(): Boolean = true
}
