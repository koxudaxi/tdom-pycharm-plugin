package com.koxudaxi.tdom

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.inspections.PyInspectionVisitor
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.*

@Suppress("UnstableApiUsage")
class TdomInspection : PyInspection() {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession,
    ): PsiElementVisitor = Visitor(holder, PyInspectionVisitor.getContext(session))

    inner class Visitor(holder: ProblemsHolder, context: TypeEvalContext)  :
        PyInspectionVisitor(holder, context) {
        override fun visitPyFormattedStringElement(node: PyFormattedStringElement) {
            super.visitPyFormattedStringElement(node)
            if (!isHtmpy(node, myTypeEvalContext)) return
            val pyStringLiteralExpression = node.parent as? PyStringLiteralExpression ?: return
            @Suppress("UnstableApiUsage") val prefixLength = node.literalPartRanges.count() - 1
            collectComponents(pyStringLiteralExpression, { resolvedComponent, tag, component, keys ->
                when (resolvedComponent) {
//                    is PyClass -> {
//                                                resolvedComponent.classAttributes.forEach { classAttribute ->
//                                                    val name = classAttribute.name
//                                                    if (!classAttribute.hasAssignedValue() && !keys.contains(name) && name is String) {
//                                                        val field = PyDataclassFieldStubImpl.create(classAttribute)
//                                                        if (!(field is PyDataclassFieldStub && !field.initValue()) && (myTypeEvalContext.getType(
//                                                                classAttribute
//                                                            ) as? PyUnionType)?.members?.any { it != null } != true
//                                                        ) {
//                                                            warnMissingRequiredArgument(node, name, tag, component)
//                                                        }
//                                                    }
//                                                }
//                    }
                    is PyCallable -> {
                                                    resolvedComponent.parameterList.parameters.filterIsInstance<PyNamedParameter>().forEach { parameter ->
                                                        val name = parameter.name
                                                        if (!parameter.hasDefaultValue() && !keys.contains(name) && name is String) {
                                                            warnMissingRequiredArgument(node, name, tag, component, prefixLength)
                                                    }}
                    }
                    else -> {
                        val componentStart = tag.range.first + component.range.first + prefixLength
                        val actualComponent =
                            PyUtil.createExpressionFromFragment(component.value.substring(IntRange(1, component.value.length - 2)),
                                node)
                        if (actualComponent != null) {
                            registerProblem(
                                node,
                                "Component should be a callable",
                                ProblemHighlightType.GENERIC_ERROR,
                                null,
                                TextRange(
                                    componentStart,
                                    componentStart + actualComponent.textLength
                                )
                            )
                        }
                    }
                }
                keys.forEach { (name, key) ->
                    val argument = resolvedComponent?.let { getArgumentByName(it, name) }
                    if (argument is PyTypedElement) {
                        val value = key.destructured.component2()
                        if (value.isEmpty()) {
                            val startPoint = tag.range.first + component.range.first + key.range.first + prefixLength + 1
                            val targetRange = TextRange(startPoint + name.length, startPoint + name.length + 1)
                            if (targetRange.substring(node.text) != " ") {
                                registerProblem(
                                    node,
                                    "Expression expected",
                                    ProblemHighlightType.GENERIC_ERROR,
                                    null,
                                    targetRange
                                )
                            }
                        }
                        else {
                            val expectedType = myTypeEvalContext.getType(argument)
                            val actualValue = getTaggedActualValue(value)
                            val actualType = PyUtil.createExpressionFromFragment(actualValue, node)
                                ?.let { getPyTypeFromPyExpression(it, myTypeEvalContext) }

                            if (!PyTypeChecker.match(expectedType, actualType, myTypeEvalContext)
                            ) {
                                val startPoint = tag.range.first + component.range.first + prefixLength - 1
                                registerProblem(
                                    node, String.format(
                                        "Expected type '%s', got '%s' instead",
                                        PythonDocumentationProvider.getTypeName(
                                            expectedType,
                                            myTypeEvalContext
                                        ),
                                        PythonDocumentationProvider.getTypeName(actualType, myTypeEvalContext)
                                    ),
                                    ProblemHighlightType.WARNING,
                                    null,
                                    TextRange(startPoint + key.range.first, startPoint + key.range.last + 1)
                                )
                            }
                        }
                    }
                    else {
                        val startPoint = tag.range.first + component.range.first + key.range.first + prefixLength
                        registerProblem(
                            node,
                            "invalid a argument: '${name}'",
                            ProblemHighlightType.GENERIC_ERROR,
                            null,
                            TextRange(startPoint, startPoint + name.length)
                        )
                    }
                }

            }, { resolvedComponent, tag, component, emptyKeys ->
                emptyKeys.forEach { (name, key) ->
                    val argument = getArgumentByName(resolvedComponent, name)
                    if (argument is PyTypedElement) {
                        val startPoint = tag.range.first + component.range.first + key.range.first + prefixLength + 1
                        registerProblem(
                            node,
                            "Expression expected",
                            ProblemHighlightType.GENERIC_ERROR,
                            null,
                            TextRange(startPoint + name.length, startPoint + name.length + 1)
                        )
                    }
                }
            })
        }
        private fun getArgumentByName(pyTypedElement: PyTypedElement, name: String): PyTypedElement? {
            return when (pyTypedElement) {
                is PyClass -> {
                    pyTypedElement.findClassAttribute(name, true, myTypeEvalContext)
                }
                is PyFunction  -> {
                    pyTypedElement.parameterList.findParameterByName(name)
                }
                else -> null
            }
        }
        private fun warnMissingRequiredArgument(node: PyFormattedStringElement, name: String, tag: MatchResult, component: MatchResult, prefixLength: Int) {
            registerProblem(
                node,
                "missing a required argument: '${name}'",
                ProblemHighlightType.WARNING,
                null,
                TextRange(
                    tag.range.first + component.range.first + 2 + prefixLength,
                    component.value.length + tag.range.first + prefixLength + 1
                )
            )
        }
    }
}