package com.koxudaxi.tdom.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiReference
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.PyUtil

class TdomKeywordCompletionContributor : com.intellij.codeInsight.completion.CompletionContributor() {
    fun getName(name: String, parameters: com.intellij.codeInsight.completion.CompletionParameters): String {
        return if (parameters.position.nextSibling.nextSibling.text.first() == '=') {
            name
        } else {
            "$name="
        }
    }
    init {
        extend(
            _root_ide_package_.com.intellij.codeInsight.completion.CompletionType.BASIC,
            _root_ide_package_.com.intellij.patterns.PlatformPatterns.psiElement(),
            object : com.intellij.codeInsight.completion.CompletionProvider<com.intellij.codeInsight.completion.CompletionParameters>() {
                override fun addCompletions(
                    parameters: com.intellij.codeInsight.completion.CompletionParameters,
                    context: com.intellij.util.ProcessingContext,
                    result: com.intellij.codeInsight.completion.CompletionResultSet,
                ) {
                    val position = parameters.position
                    val type = position.node.elementType
                    if (type !== _root_ide_package_.com.intellij.psi.xml.XmlTokenType.XML_DATA_CHARACTERS) {
                        return
                    }
                    val hostElement =
                        position.parent.containingFile.getUserData(_root_ide_package_.com.intellij.psi.impl.source.resolve.FileContextUtil.INJECTED_IN_ELEMENT)?.element as? com.jetbrains.python.psi.PyStringLiteralExpression
                            ?: return
                    val pyFormattedStringElement = hostElement.firstChild as? com.jetbrains.python.psi.PyFormattedStringElement
                        ?: return
                    val typeContext =
                        _root_ide_package_.com.koxudaxi.tdom.getContextForCodeCompletion(pyFormattedStringElement)
                    if (!_root_ide_package_.com.koxudaxi.tdom.isHtmpy(pyFormattedStringElement, typeContext)) return
                    _root_ide_package_.com.koxudaxi.tdom.collectComponents(
                        hostElement,
                        { resolvedComponent, tag, _, keys ->
                            if (tag.range.contains(position.textOffset)) {
                                // For parameters
                                when (resolvedComponent) {
                                    is com.jetbrains.python.psi.PyClass -> {
                                        resolvedComponent.classAttributes.filter { instanceAttribute ->
                                            !instanceAttribute.hasAssignedValue() && !keys.contains(instanceAttribute.name)
                                        }
                                            .mapNotNull { validKey -> validKey.name }
                                            .forEach { name ->
                                                val attribute = resolvedComponent.findClassAttribute(name, true, null)
                                                if (attribute != null) {
                                                    val element =
                                                        _root_ide_package_.com.intellij.codeInsight.completion.PrioritizedLookupElement.withGrouping(
                                                            _root_ide_package_.com.intellij.codeInsight.lookup.LookupElementBuilder
                                                                .createWithSmartPointer(
                                                                    getName(name, parameters),
                                                                    attribute
                                                                )
                                                                .withTypeText(typeContext.getType(attribute)?.name)
                                                                .withIcon(_root_ide_package_.com.intellij.icons.AllIcons.Nodes.Field),
                                                            1
                                                        )
                                                    result.addElement(
                                                        _root_ide_package_.com.intellij.codeInsight.completion.PrioritizedLookupElement.withPriority(
                                                            element,
                                                            100.0
                                                        )
                                                    )
                                                }
                                            }
                                    }

                                    else -> {
                                        (resolvedComponent as com.jetbrains.python.psi.PyFunction).parameterList.parameters.filter { parameter ->
                                            !parameter.hasDefaultValue() && !keys.contains(parameter.name)
                                        }
                                            .mapNotNull { validKey -> validKey.name }
                                            .forEach { name ->
                                                val parameter =
                                                    resolvedComponent.parameterList.findParameterByName(name)
                                                if (parameter != null) {
                                                    val element =
                                                        _root_ide_package_.com.intellij.codeInsight.completion.PrioritizedLookupElement.withGrouping(
                                                            _root_ide_package_.com.intellij.codeInsight.lookup.LookupElementBuilder
                                                                .createWithSmartPointer(
                                                                    getName(name, parameters),
                                                                    parameter
                                                                )
                                                                .withTypeText(typeContext.getType(parameter)?.name)
                                                                .withIcon(_root_ide_package_.com.intellij.icons.AllIcons.Nodes.Field),
                                                            1
                                                        )
                                                    result.addElement(
                                                        _root_ide_package_.com.intellij.codeInsight.completion.PrioritizedLookupElement.withPriority(
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