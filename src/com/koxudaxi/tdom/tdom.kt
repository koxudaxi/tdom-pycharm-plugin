package com.koxudaxi.tdom

import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveResult
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.types.PyCollectionType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyUnionType
import com.jetbrains.python.psi.types.TypeEvalContext

const val HTM_HTM_Q_NAME = "htm.htm"
const val TDOM_H_HTML_Q_NAME = "tdom.h.html"
const val TDOM_HTML_Q_NAME = "tdom.html"
const val TDOM_HTML_Q_LAST = "html"
const val TDOM_NODE_Q_NAME = "tdom.VDOMNode"
const val DATA_CLASS_Q_NAME = "dataclasses.dataclass"

val HTM_HTM_QUALIFIED_NAME = QualifiedName.fromDottedString(HTM_HTM_Q_NAME)
val VIEWDOM_H_HTML_QUALIFIED_NAME = QualifiedName.fromDottedString(TDOM_H_HTML_Q_NAME)
val VIEWDOM_HTML_QUALIFIED_NAME = QualifiedName.fromDottedString(TDOM_HTML_Q_NAME)
val DATA_CLASS_QUALIFIED_NAME = QualifiedName.fromDottedString(DATA_CLASS_Q_NAME)

val DATA_CLASS_QUALIFIED_NAMES = listOf(
    DATA_CLASS_QUALIFIED_NAME
)

internal fun hasDecorator(pyDecoratable: PyDecoratable, refName: String): Boolean {
    pyDecoratable.decoratorList?.decorators?.mapNotNull { it.callee as? PyReferenceExpression }?.forEach {
        PyResolveUtil.resolveImportedElementQNameLocally(it).forEach { decoratorQualifiedName ->
            if (decoratorQualifiedName == QualifiedName.fromDottedString(refName)) return true
        }
    }
    return false
}


internal fun getContext(pyElement: PyElement): TypeEvalContext {
    return TypeEvalContext.codeAnalysis(pyElement.project, pyElement.containingFile)
}

internal fun getContextForCodeCompletion(pyElement: PyElement): TypeEvalContext {
    return TypeEvalContext.codeCompletion(pyElement.project, pyElement.containingFile)
}

internal fun getResolveElements(
    referenceExpression: PyReferenceExpression,
    context: TypeEvalContext,
): Array<ResolveResult> {
    return PyResolveContext.defaultContext(context).let {
        referenceExpression.getReference(it).multiResolve(false)
    }
}

internal fun multiResolveCalledPyTargetExpression(
    expression: Any?,
    functionName: String?,
    index: Int,
): List<PyTargetExpression> {
    if (!isCallArgument(expression, functionName, index)) return emptyList()

    val call = (expression as? PyExpression)?.parent?.parent as? PyCallExpression ?: return emptyList()
    val referenceExpression = call.callee as? PyReferenceExpression ?: return emptyList()
    val resolveResults = getResolveElements(referenceExpression, getContext(call))
    return PyUtil.filterTopPriorityResults(resolveResults)
        .asSequence()
        .filterIsInstance(PyTargetExpression::class.java)
        .toList()
}

internal fun isCallArgument(expression: Any?, functionName: String?, index: Int): Boolean {
    if (expression !is PyExpression) return false

    val argumentList = expression.parent as? PyArgumentList ?: return false
    val call = argumentList.parent as? PyCallExpression ?: return false
    val referenceToCallee = call.callee as? PyReferenceExpression ?: return false
    val referencedName = referenceToCallee.referencedName ?: return false
    if (referencedName != functionName) return false
    argumentList.children.let { arguments ->
        return index < arguments.size && expression === arguments[index]
    }
}

internal fun multiResolveCalledDecoratedFunction(expression: Any?, decoratorQName: String): List<PyFunction> {
    if (expression !is PyExpression) return emptyList()

    val argumentList = expression.parent as? PyArgumentList ?: return emptyList()
    val call = argumentList.parent as? PyCallExpression ?: return emptyList()
    val referenceToCallee = call.callee as? PyReferenceExpression ?: return emptyList()
    val resolveResults = getResolveElements(referenceToCallee, getContext(expression))
    return PyUtil.filterTopPriorityResults(resolveResults)
        .asSequence()
        .filterIsInstance(PyFunction::class.java)
        .filter { hasDecorator(it, decoratorQName) }
        .toList()

}

internal fun hasDecorator(pyDecoratable: PyDecoratable, refNames: List<QualifiedName>): Boolean {
    return pyDecoratable.decoratorList?.decorators?.mapNotNull { it.callee as? PyReferenceExpression }?.any {
        PyResolveUtil.resolveImportedElementQNameLocally(it).any { decoratorQualifiedName ->
            refNames.any { refName -> decoratorQualifiedName == refName }
        }
    } ?: false
}

internal fun isDataclass(pyClass: PyClass): Boolean {
    return hasDecorator(pyClass, DATA_CLASS_QUALIFIED_NAMES)
}

fun isHtm(context: PsiElement): Boolean {
    return multiResolveCalledDecoratedFunction(context, HTM_HTM_Q_NAME).any()
}

fun isTDomHtm(context: PsiElement): Boolean {
    val x =  multiResolveCalledPyTargetExpression(context,
        TDOM_HTML_Q_LAST,
        0)
    return x.any { it.qualifiedName == TDOM_H_HTML_Q_NAME || it.qualifiedName == TDOM_HTML_Q_NAME }
}

fun isQualifiedNamedType(pyType: PyType?, qualifiedNames: List<String>): Boolean {
    return when (pyType) {
        is PyUnionType -> pyType.members.any { isQualifiedNamedType(it, qualifiedNames) }
        is PyCollectionType -> pyType.elementTypes.any { isQualifiedNamedType(it, qualifiedNames) }
        else ->  pyType?.declarationElement?.qualifiedName in qualifiedNames

    }
}

fun isHtmpy(pyFormattedStringElement: PyFormattedStringElement, typeEvalContext: TypeEvalContext): Boolean {
    val pyStringLiteralExpression = pyFormattedStringElement.parent as? PyStringLiteralExpression ?: return false
    val pyArgumentList = pyStringLiteralExpression.parent as? PyArgumentList ?: return false
    val pyCallExpression = pyArgumentList.parent as? PyCallExpression ?: return false
    val resolvedTDomFunc = pyCallExpression.multiResolveCalleeFunction(PyResolveContext.defaultContext(typeEvalContext)).any { pyFunction ->
        typeEvalContext.getReturnType(pyFunction)?.let { pyType ->
            isQualifiedNamedType(pyType, listOf(TDOM_NODE_Q_NAME))
        } ?: false
    }
    if (resolvedTDomFunc) true

    // html = ...
    val resolvedHtml = pyCallExpression.callee?.reference?.resolve() as? PyTargetExpression ?: return false
    return resolvedHtml.qualifiedName == TDOM_HTML_Q_NAME
}

fun isHtmpy(psiElement: PsiElement): Boolean = isTDomHtm(psiElement) || isHtm(psiElement)


fun collectComponents(
    pyStringLiteralExpression: PyStringLiteralExpression,
    actionForComponent: (resolvedComponent: PyTypedElement?, tag: MatchResult, component: MatchResult, keys: Map<String, MatchResult>) -> Unit,
    actionForEmptyComponent: ((resolvedComponent: PyTypedElement, tag: MatchResult, component: MatchResult, keys: Map<String, MatchResult>) -> Unit)? = null,
    actionForTag: ((resolvedComponent: PyTypedElement?, tag: MatchResult, first: Int, last: Int) -> Unit)? = null,
    actionForUnTag: ((tag: MatchResult) -> Unit)? = null,
    useStringValue: Boolean = true

) {
    val text = if (useStringValue) pyStringLiteralExpression.stringValue else pyStringLiteralExpression.text
    val results = Regex("<[^>]*\\{([^}]*)}[^/>]*(/\\s*>|.*$)").findAll(text).toList()
    if (results.isEmpty() && actionForUnTag != null) {
        Regex("<*\\{([^}]*)}(/\\s*>|.*$)").findAll(text).forEach { match ->
            actionForUnTag(match)
        }
        return
    }
    results.forEach { element ->
        val tag = Regex("\\{([^}]*)}").findAll(element.value).firstOrNull()
        if (tag == null) return@forEach
        val owner = ScopeUtil.getScopeOwner(pyStringLiteralExpression)
        if (tag.range.first == 0 || text[tag.range.first - 1] != '<') return@forEach
        if (tag.value.length > 2 && owner != null) {
            val component =
                PyResolveUtil.resolveLocally(owner, tag.destructured.component1().trim()).firstOrNull()
                    .let {
                        when (it) {
                            is PyImportElement -> {
                                PyUtil.filterTopPriorityResults(it.multiResolve())
                                    .asSequence()
                                    .map { result -> result.element }
                                    .firstOrNull { element ->  when(element) {
                                        is PyClass -> isDataclass(element)
                                        is PyFunction -> true
                                        else -> false
                                    }
                                    }
                            }
                            else -> (it as? PyClass)?.takeIf { pyClass -> isDataclass(pyClass) } ?: (it as? PyFunction)
                        }
                    }

            if (component is PyTypedElement) {
                val keys =
                    (Regex("([^=}\\s]+)=(\\{[^\"]*})").findAll(element.value) + Regex("([^=}\\s]+)=([^\\s/]*)").findAll(element.value)
                            + Regex("([^=}\\s]+)=\"([^\"]*)").findAll(element.value)
                            )
                        .map { key ->
                            Pair(key.destructured.component1(), key)
                        }.toMap()
                actionForComponent(component, element, tag, keys)

                val emptyKeys = Regex("([^=}\\s]+)=(\\s+)").findAll(element.value).map { key ->
                    Pair(key.destructured.component1(), key)
                }.toMap()
                actionForEmptyComponent?.let { it(component, element, tag,  emptyKeys) }
            } else {
//                    actionForComponent(null, element, tag, emptyMap())
            }
            if (actionForTag != null) {
                actionForTag(component as? PyTypedElement, tag, element.range.first + tag.range.first + 1, element.range.first + tag.range.last)
            }
        }
        else {
            if (actionForTag != null) {
                actionForTag(null, tag, element.range.first + tag.range.first + 1, element.range.first + tag.range.last)
            }
        }
    }

}

fun getPyTypeFromPyExpression(pyExpression: PyExpression, context: TypeEvalContext): PyType? {
    return when (pyExpression) {
        is PyType -> pyExpression
        is PyReferenceExpression -> {
//            val resolveResults = getResolveElements(pyExpression, context)
//            PyUtil.filterTopPriorityResults(resolveResults)
//                .filterIsInstance<PyTypedElement>()
//                .map { context.getType(it) }
//                .firstOrNull() ?:
            PyResolveUtil.resolveLocally(pyExpression).firstOrNull()
                .let {
                    when (it) {
                        is PyImportElement -> {
                            PyUtil.filterTopPriorityResults(it.multiResolve())
                                .firstOrNull()
                        }
                        else -> it
                    }
                }
                ?.let { (it as? PyTypedElement)?.let { typedElement-> context.getType(typedElement)} }
        }
        else -> context.getType(pyExpression)
    }
}

fun getTaggedActualValue(value: String): String {
    return when {
        value.startsWith("\"{") && value.endsWith("}\"") -> value.substring(
            2,
            value.length - 2
        )
        value.startsWith('"') && value.endsWith('"') -> value
        value.startsWith('{') && value.endsWith('}') -> value.substring(
            1,
            value.length - 1
        )
        else -> "\"$value\""
    }
}