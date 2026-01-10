package com.chalk.intellij

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.util.ProcessingContext

/**
 * Registers reference providers for Chalk SQL files.
 * Detects "-- resolves: ClassName" patterns and makes the class name a navigable reference.
 */
class ChalkReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        // Register for all PsiElements - we'll filter in the provider
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(PsiComment::class.java),
            ChalkResolvesReferenceProvider()
        )

        // Also register for plain text elements (some SQL dialects use these)
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(),
            ChalkResolvesReferenceProvider()
        )
    }
}

/**
 * Provides references for "-- resolves: ClassName" comments in .chalk.sql files.
 */
class ChalkResolvesReferenceProvider : PsiReferenceProvider() {

    companion object {
        // Pattern: "-- resolves: ClassName" where ClassName is captured
        private val RESOLVES_PATTERN = Regex("""--\s*resolves:\s*(\w+)""")
    }

    override fun getReferencesByElement(
        element: PsiElement,
        context: ProcessingContext
    ): Array<PsiReference> {
        // Only process .chalk.sql files
        val fileName = element.containingFile?.name ?: return PsiReference.EMPTY_ARRAY
        if (!fileName.endsWith(".chalk.sql")) {
            return PsiReference.EMPTY_ARRAY
        }

        val text = element.text
        val match = RESOLVES_PATTERN.find(text) ?: return PsiReference.EMPTY_ARRAY

        val className = match.groupValues[1]
        val classNameStart = match.groups[1]?.range?.first ?: return PsiReference.EMPTY_ARRAY
        val classNameEnd = match.groups[1]?.range?.last?.plus(1) ?: return PsiReference.EMPTY_ARRAY

        // Create a reference for the class name portion
        val textRange = TextRange(classNameStart, classNameEnd)

        return arrayOf(ChalkResolvesReference(element, className, textRange))
    }
}
