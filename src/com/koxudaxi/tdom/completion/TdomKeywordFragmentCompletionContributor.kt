package com.koxudaxi.tdom.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiReference
import com.intellij.util.ProcessingContext
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFormattedStringElement
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.PyUtil
import com.koxudaxi.tdom.collectComponents
import com.koxudaxi.tdom.getContextForCodeCompletion
import com.koxudaxi.tdom.isHtmpy

class TdomKeywordFragmentCompletionContributor : CompletionContributor() {
    fun getName(name: String, parameters: CompletionParameters): String {
        return if (parameters.position.nextSibling?.nextSibling?.text?.first() == '=') {
            name
        } else {
            "$name="
        }
    }
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            object : CompletionProvider<CompletionParameters>() {
                @Suppress("UnstableApiUsage")
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet,
                ) {
                    val position = parameters.position
                    val containingFile = position.parent.containingFile
                    if (containingFile !is PyFile) return
                    val pyFormattedStringElement = position.parent as? PyFormattedStringElement ?: return
                    val hostElement = pyFormattedStringElement.parent as? PyStringLiteralExpression ?: return
                    val typeContext = getContextForCodeCompletion(pyFormattedStringElement)
                    isHtmpy(pyFormattedStringElement, typeContext)

//                    val hostElement =
//                        position.parent.containingFile.getUserData(FileContextUtil.INJECTED_IN_ELEMENT)?.element as? com.jetbrains.python.psi.PyStringLiteralExpression
//                            ?: return
//                    val pyFormattedStringElement = hostElement.firstChild as? com.jetbrains.python.psi.PyFormattedStringElement
//                        ?: return
//                    val typeContext =
//                        getContextForCodeCompletion(pyFormattedStringElement)
//                    if (!isHtmpy(pyFormattedStringElement, typeContext)) return
                    collectComponents(
                        hostElement,
                        { resolvedComponent, tag, _, keys ->
                            if (tag.range.contains(position.startOffsetInParent)) {
                                // For parameters
                                when (resolvedComponent) {
                                    is PyClass -> {
                                        resolvedComponent.classAttributes.filter { instanceAttribute ->
                                            !instanceAttribute.hasAssignedValue() && !keys.contains(instanceAttribute.name)
                                        }
                                            .mapNotNull { validKey -> validKey.name }
                                            .forEach { name ->
                                                val attribute = resolvedComponent.findClassAttribute(name, true, null)
                                                if (attribute != null) {
                                                    val element =
                                                        PrioritizedLookupElement.withGrouping(
                                                            LookupElementBuilder
                                                                .createWithSmartPointer(
                                                                    getName(name, parameters),
                                                                    attribute
                                                                )
                                                                .withTypeText(typeContext.getType(attribute)?.name)
                                                                .withIcon(AllIcons.Nodes.Field),
                                                            1
                                                        )
                                                    result.addElement(
                                                        PrioritizedLookupElement.withPriority(
                                                            element,
                                                            100.0
                                                        )
                                                    )
                                                }
                                            }
                                    }

                                    else -> {
                                        val pyFunction = resolvedComponent as? PyFunction ?: return@collectComponents
                                        pyFunction.parameterList.parameters.filter { parameter ->
                                            !parameter.hasDefaultValue() && !keys.contains(parameter.name)
                                        }
                                            .mapNotNull { validKey -> validKey.name }
                                            .forEach { name ->
                                                val parameter =
                                                    resolvedComponent.parameterList.findParameterByName(name)
                                                if (parameter != null) {
                                                    val element =
                                                        PrioritizedLookupElement.withGrouping(
                                                            LookupElementBuilder
                                                                .createWithSmartPointer(
                                                                    getName(name, parameters),
                                                                    parameter
                                                                )
                                                                .withTypeText(typeContext.getType(parameter)?.name)
                                                                .withIcon(AllIcons.Nodes.Field),
                                                            1
                                                        )
                                                    result.addElement(
                                                        PrioritizedLookupElement.withPriority(
                                                            element,
                                                            100.0
                                                        )
                                                    )
                                                }
                                            }
                                    }
                                }
                            }
                        },
                        { _, _, _, _ -> })
                    val targetBrace =
                        Regex("\\{([^}]*)}").findAll(hostElement.text)
                            .firstOrNull { it.range.contains(position.textOffset - 1) }
                            ?: return
                    val completionPrefix: String =
                        hostElement.text.substring(targetBrace.range.first + 1, position.textOffset - 1)
                    val autoPopupAfterOpeningBrace = completionPrefix.isEmpty() && parameters.isAutoPopup
                    if (autoPopupAfterOpeningBrace) {
                        return
                    }
                    val prefixCannotStartReference =
                        completionPrefix.isNotEmpty() && (!PyNames.isIdentifier(completionPrefix) && !completionPrefix.endsWith("."))
                    if (prefixCannotStartReference) {
                        return
                    }

                    val reference: PsiReference = hostElement.findReferenceAt(parameters.offset - 1) ?: return
                    val tagVariants = reference.variants.mapNotNull({ v: Any? ->
                        PyUtil.`as`(v,
                            LookupElement::class.java)
                    })
                    if (tagVariants.isEmpty()) {
                        return
                    }
                    result.withPrefixMatcher(completionPrefix)
                }
            }
        )

    }
}