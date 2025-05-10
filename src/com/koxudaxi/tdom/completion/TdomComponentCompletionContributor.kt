package com.koxudaxi.tdom.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import com.jetbrains.python.psi.*
import com.koxudaxi.tdom.getContextForCodeCompletion
import com.koxudaxi.tdom.isHtmpy

class TdomComponentCompletionContributor : CompletionContributor() {
    class ComponentCallRemoveInsertHandler() : InsertHandler<LookupElement> {
        override fun handleInsert(
            insertionCntext: InsertionContext,
            lookupElement: LookupElement
        ) {
            lookupElement.handleInsert(insertionCntext)
            val tailOffset = insertionCntext.tailOffset
            val document = insertionCntext.document
            if (tailOffset < 2) return
            val lastTwoChars = document.getText(TextRange(tailOffset - 2, tailOffset))
            if (lastTwoChars != "()") return
            document.deleteString(tailOffset - 2, tailOffset)

        }

    }

    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters, context: ProcessingContext, resultSet: CompletionResultSet,
                ) {
                    val position = parameters.position
                    val containingFile = position.parent.containingFile
                    if (containingFile !is PyFile) return
                    val pyFormattedStringElement = position.parent.parent.parent as? PyFormattedStringElement ?: return
                    isHtmpy(pyFormattedStringElement, getContextForCodeCompletion(pyFormattedStringElement))
                    val typeContext = getContextForCodeCompletion(pyFormattedStringElement)
                    if (!isHtmpy(pyFormattedStringElement, typeContext)) return
                    resultSet.runRemainingContributors(parameters) { completionResult ->
                        val lookupElement = completionResult.lookupElement
                        when (lookupElement.psiElement as? PyCallable) {
                            is PyCallable ->
                                LookupElementDecorator.withDelegateInsertHandler(
                                    lookupElement, ComponentCallRemoveInsertHandler()
                                )

                            else -> lookupElement
                        }?.let { resultSet.addElement(it) }
                    }
                }
            })

    }
}