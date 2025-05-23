package com.koxudaxi.tdom.psi.resolve

import com.intellij.psi.impl.source.resolve.FileContextUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.jetbrains.python.ast.PyAstExpression
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.resolve.*
import com.jetbrains.python.psi.types.TypeEvalContext


class HtmpyReferenceResolveProvider : PyReferenceResolveProvider {
    override fun resolveName(element: PyQualifiedExpression, context: TypeEvalContext): List<RatedResolveResult> {
        if (element !is PyReferenceExpression) return emptyList()
        val referencedName = element.referencedName ?: return emptyList()
        // For comprehension
        val parentPyComprehensionElement = PsiTreeUtil.getParentOfType(element, PyComprehensionElement::class.java)
        if (parentPyComprehensionElement is PyComprehensionElement) {
            if (PsiTreeUtil.collectElements(parentPyComprehensionElement.resultExpression) { it == element }.any()) {
                val resolved = parentPyComprehensionElement.forComponents.filter { it.getIteratorVariable<PyAstExpression>().name == referencedName }.map {
                    RatedResolveResult(RatedResolveResult.RATE_NORMAL, it.getIteratorVariable())
                }
                if (resolved.isNotEmpty()) return resolved
            }
        }
        val psiRange = element.containingFile.getUserData(FileContextUtil.INJECTED_IN_ELEMENT)?.psiRange
            ?: return emptyList()

        return PyResolveUtil.resolveLocally(element).filter {
            PsiTreeUtil.getTopmostParentOfType(it, PyComprehensionElement::class.java)?.let { listCompExpression ->
                listCompExpression.startOffset < psiRange.startOffset && listCompExpression.endOffset > psiRange.endOffset
            } ?: false
        }.map {
            RatedResolveResult(RatedResolveResult.RATE_NORMAL, it)
        }
    }
}