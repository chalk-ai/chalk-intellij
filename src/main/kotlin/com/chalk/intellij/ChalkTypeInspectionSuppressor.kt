package com.chalk.intellij

import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.psi.PsiElement

class ChalkTypeInspectionSuppressor : InspectionSuppressor {
    private val typeInspectionIds = setOf(
        "PyArgumentList",
        "PyCallingNonCallable",
        "PyDataclass",
        "PyFinal",
        "PyInitNewSignature",
        "PyInvalidCast",
        "PyNamedTuple",
        "PyProtocol",
        "PyRedeclaration",
        "PyReturnFromInit",
        "PyTypeChecker",
        "PyTypeHints",
        "PyTypedDict",
        "PyUnresolvedReferences",
    )

    override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
        val file = element.containingFile?.virtualFile ?: return false
        return toolId in typeInspectionIds && ChalkProjectRootUtil.findChalkRoot(file) != null
    }

    override fun getSuppressActions(element: PsiElement?, toolId: String): Array<SuppressQuickFix> = emptyArray()
}
