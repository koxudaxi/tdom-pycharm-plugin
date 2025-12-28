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

/**
 * TdomInspection implements diagnostics based on tdom specification.
 *
 * Diagnostic rules from tdom/tdom_spec.json:
 * - E001: Void Element with Children
 * - E002: Mismatched Component Closing Tags
 * - E003: Missing Component Braces
 * - W003: Component Missing children Parameter
 */
@Suppress("UnstableApiUsage")
class TdomInspection : PyInspection() {

    companion object {
        // From tdom_spec.json void_elements
        val VOID_ELEMENTS = setOf("br", "hr", "img", "input", "meta", "link", "area", "base", "col", "embed", "source", "track", "wbr")

        // From tdom_spec.json content_elements
        val CONTENT_ELEMENTS = setOf("script", "style", "textarea", "title")
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession,
    ): PsiElementVisitor = Visitor(holder, PyInspectionVisitor.getContext(session))

    inner class Visitor(holder: ProblemsHolder, context: TypeEvalContext) :
        PyInspectionVisitor(holder, context) {

        override fun visitPyFormattedStringElement(node: PyFormattedStringElement) {
            super.visitPyFormattedStringElement(node)
            if (!isHtmpy(node, myTypeEvalContext)) return
            val pyStringLiteralExpression = node.parent as? PyStringLiteralExpression ?: return
            val text = pyStringLiteralExpression.stringValue

            // Original prefixLength calculation for collectComponents (legacy)
            @Suppress("UnstableApiUsage")
            val prefixLength = node.literalPartRanges.count() - 1

            // Actual prefix length for E001/E002/E003 checks: t" or f" = 2, t""" or f""" = 4
            val nodeText = node.text
            val actualPrefixLength = when {
                nodeText.startsWith("t\"\"\"") || nodeText.startsWith("f\"\"\"") ||
                nodeText.startsWith("t'''") || nodeText.startsWith("f'''") -> 4
                nodeText.startsWith("t\"") || nodeText.startsWith("f\"") ||
                nodeText.startsWith("t'") || nodeText.startsWith("f'") -> 2
                nodeText.startsWith("\"\"\"") || nodeText.startsWith("'''") -> 3
                nodeText.startsWith("\"") || nodeText.startsWith("'") -> 1
                else -> 0
            }

            // E001: Check for void elements with children
            checkVoidElementsWithChildren(node, text, actualPrefixLength)

            // E002: Check for mismatched component closing tags
            checkMismatchedComponentClosingTags(node, text, actualPrefixLength)

            // E003: Check for missing component braces (PascalCase tag without braces)
            checkMissingComponentBraces(node, text, actualPrefixLength)

            // W001: Check for interpolated content in content elements without :safe
            checkContentElementWithoutSafe(node, text, actualPrefixLength)

            // I001: Check for boolean attributes with string values
            checkBooleanAttributeValues(node, text, actualPrefixLength)

            // I002: Check for empty templates
            checkEmptyTemplate(node, text, actualPrefixLength)

            // Existing component checks
            collectComponents(pyStringLiteralExpression, { resolvedComponent, tag, component, keys ->
                when (resolvedComponent) {
                    is PyCallable -> {
                        // Check for missing required arguments
                        resolvedComponent.parameterList.parameters.filterIsInstance<PyNamedParameter>().forEach { parameter ->
                            val name = parameter.name
                            if (!parameter.hasDefaultValue() && !keys.contains(name) && name is String && name != "children") {
                                warnMissingRequiredArgument(node, name, tag, component, prefixLength)
                            }
                        }

                        // W003: Check if children are provided but component doesn't accept them
                        checkChildrenParameter(node, resolvedComponent, tag, component, text, prefixLength)
                    }
                    else -> {
                        val componentStart = tag.range.first + component.range.first + prefixLength
                        val actualComponent =
                            PyUtil.createExpressionFromFragment(component.value.substring(IntRange(1, component.value.length - 2)),
                                node)
//                        if (actualComponent != null) {
//                            registerProblem(
//                                node,
//                                "Component should be a callable",
//                                ProblemHighlightType.GENERIC_ERROR,
//                                null,
//                                TextRange(
//                                    componentStart,
//                                    componentStart + actualComponent.textLength
//                                )
//                            )
//                        }
                    }
                }

                // Check attribute types
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
                        } else {
                            val expectedType = myTypeEvalContext.getType(argument)
                            val actualValue = getTaggedActualValue(value)
                            val actualType = PyUtil.createExpressionFromFragment(actualValue, node)
                                ?.let { getPyTypeFromPyExpression(it, myTypeEvalContext) }

                            if (!PyTypeChecker.match(expectedType, actualType, myTypeEvalContext)) {
                                val startPoint = tag.range.first + component.range.first + prefixLength - 1
                                registerProblem(
                                    node, String.format(
                                        "Expected type '%s', got '%s' instead",
                                        PythonDocumentationProvider.getTypeName(expectedType, myTypeEvalContext),
                                        PythonDocumentationProvider.getTypeName(actualType, myTypeEvalContext)
                                    ),
                                    ProblemHighlightType.WARNING,
                                    null,
                                    TextRange(startPoint + key.range.first, startPoint + key.range.last + 1)
                                )
                            }
                        }
                    } else {
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

        /**
         * E001: Void Element with Children
         * Message: "Void element '<{tag}>' cannot have children"
         */
        private fun checkVoidElementsWithChildren(node: PyFormattedStringElement, text: String, prefixLength: Int) {
            // Match void elements with content: <br>content</br> or <br>content<
            val voidElementPattern = Regex("<(${VOID_ELEMENTS.joinToString("|")})(?:\\s[^>]*)?>([^<]+)<")
            voidElementPattern.findAll(text).forEach { match ->
                val tagName = match.groupValues[1]
                val content = match.groupValues[2].trim()
                if (content.isNotEmpty()) {
                    val startOffset = match.range.first + prefixLength
                    registerProblem(
                        node,
                        "Void element '<$tagName>' cannot have children",
                        ProblemHighlightType.GENERIC_ERROR,
                        null,
                        TextRange(startOffset, startOffset + match.value.length)
                    )
                }
            }
        }

        /**
         * E002: Mismatched Component Closing Tags
         * Message: "Mismatched closing tag '</{closing_tag}>' for component '<{opening_component}>'"
         */
        private fun checkMismatchedComponentClosingTags(node: PyFormattedStringElement, text: String, prefixLength: Int) {
            // Find component opening tags: <{ComponentName}...>
            val openingPattern = Regex("<\\{([^}]+)}[^>]*>")
            val closingPattern = Regex("</\\{([^}]+)}>")

            val openings = openingPattern.findAll(text).toList()
            val closings = closingPattern.findAll(text).toList()

            // Simple check: match opening and closing tags in order
            openings.forEachIndexed { index, opening ->
                val openingName = opening.groupValues[1].trim()
                if (index < closings.size) {
                    val closing = closings[index]
                    val closingName = closing.groupValues[1].trim()
                    if (openingName != closingName) {
                        val startOffset = closing.range.first + prefixLength
                        registerProblem(
                            node,
                            "Mismatched closing tag '</{$closingName}>' for component '<{$openingName}>'",
                            ProblemHighlightType.GENERIC_ERROR,
                            null,
                            TextRange(startOffset, startOffset + closing.value.length)
                        )
                    }
                }
            }
        }

        /**
         * E003: Missing Component Braces
         * Message: "Component name must be in curly braces: '<ComponentName>' should be '<{ComponentName}>'"
         *
         * Detects PascalCase tags that look like components but aren't wrapped in braces.
         */
        private fun checkMissingComponentBraces(node: PyFormattedStringElement, text: String, prefixLength: Int) {
            // Match PascalCase tags that are NOT in braces: <MyComponent> but not <{MyComponent}>
            // Exclude known HTML tags and void elements
            val pascalCasePattern = Regex("<([A-Z][a-zA-Z0-9]*)(?:\\s[^>]*)?>")
            pascalCasePattern.findAll(text).forEach { match ->
                val tagName = match.groupValues[1]
                // Check if it's not a known HTML element (HTML elements are lowercase)
                if (!isKnownHtmlElement(tagName.lowercase())) {
                    val startOffset = match.range.first + prefixLength
                    registerProblem(
                        node,
                        "Component name must be in curly braces: '<$tagName>' should be '<{$tagName}>'",
                        ProblemHighlightType.GENERIC_ERROR,
                        null,
                        TextRange(startOffset, startOffset + tagName.length + 1) // +1 for '<'
                    )
                }
            }
        }

        /**
         * W003: Component Missing children Parameter
         * Message: "Component '{component}' doesn't accept 'children' parameter, content will be ignored"
         */
        private fun checkChildrenParameter(
            node: PyFormattedStringElement,
            component: PyCallable,
            tag: MatchResult,
            componentMatch: MatchResult,
            text: String,
            prefixLength: Int
        ) {
            // Check if component has closing tag (meaning children are provided)
            val componentName = componentMatch.groupValues[1].trim()
            val closingTagPattern = Regex("</${Regex.escape("{$componentName}")}>")

            if (closingTagPattern.containsMatchIn(text)) {
                // Children are provided, check if component accepts them
                val hasChildrenParam = component.parameterList.parameters
                    .filterIsInstance<PyNamedParameter>()
                    .any { it.name == "children" }

                if (!hasChildrenParam) {
                    val startOffset = tag.range.first + componentMatch.range.first + prefixLength
                    registerProblem(
                        node,
                        "Component '$componentName' doesn't accept 'children' parameter, content will be ignored",
                        ProblemHighlightType.WARNING,
                        null,
                        TextRange(startOffset, startOffset + componentMatch.value.length)
                    )
                }
            }
        }

        /**
         * W001: Content Element Without :safe
         * Message: "Interpolated content in '<{tag}>' should use :safe if content is trusted"
         */
        private fun checkContentElementWithoutSafe(node: PyFormattedStringElement, text: String, prefixLength: Int) {
            // Find content elements with interpolation: <script>{var}</script>
            CONTENT_ELEMENTS.forEach { element ->
                val pattern = Regex("<$element[^>]*>(.*?)</$element>", RegexOption.DOT_MATCHES_ALL)
                pattern.findAll(text).forEach { match ->
                    val content = match.groupValues[1]
                    // Find interpolations without :safe
                    val interpolationPattern = Regex("\\{([^}:]+)}")
                    interpolationPattern.findAll(content).forEach { interpolation ->
                        val startOffset = match.range.first + match.value.indexOf(interpolation.value) + prefixLength
                        registerProblem(
                            node,
                            "Interpolated content in '<$element>' should use :safe if content is trusted",
                            ProblemHighlightType.WEAK_WARNING,
                            null,
                            TextRange(startOffset, startOffset + interpolation.value.length)
                        )
                    }
                }
            }
        }

        /**
         * I001: Boolean Attribute Values
         * Message: "Attribute '{attribute}' should use boolean value instead of string \"{value}\""
         */
        private fun checkBooleanAttributeValues(node: PyFormattedStringElement, text: String, prefixLength: Int) {
            val booleanAttributes = setOf(
                "disabled", "checked", "readonly", "required", "autofocus", "autoplay",
                "controls", "default", "defer", "hidden", "ismap", "loop", "multiple",
                "muted", "novalidate", "open", "reversed", "selected", "async"
            )
            // Match boolean attributes with string "true" or "false" values (both single and double quotes)
            val pattern = Regex("(${booleanAttributes.joinToString("|")})=[\"'](true|false)[\"']")
            pattern.findAll(text).forEach { match ->
                val attrName = match.groupValues[1]
                val attrValue = match.groupValues[2]
                val startOffset = match.range.first + prefixLength
                registerProblem(
                    node,
                    "Attribute '$attrName' should use boolean value instead of string \"$attrValue\"",
                    ProblemHighlightType.WEAK_WARNING,
                    null,
                    TextRange(startOffset, startOffset + match.value.length)
                )
            }
        }

        /**
         * I002: Empty Fragments
         * Message: "Empty template returns empty Fragment - this may be unintentional"
         */
        private fun checkEmptyTemplate(node: PyFormattedStringElement, text: String, prefixLength: Int) {
            if (text.isEmpty()) {
                registerProblem(
                    node,
                    "Empty template returns empty Fragment - this may be unintentional",
                    ProblemHighlightType.WEAK_WARNING,
                    null,
                    TextRange(prefixLength, prefixLength)
                )
            }
        }

        private fun isKnownHtmlElement(tagName: String): Boolean {
            val htmlElements = setOf(
                "a", "abbr", "address", "article", "aside", "audio", "b", "bdi", "bdo", "blockquote",
                "body", "button", "canvas", "caption", "cite", "code", "colgroup", "data", "datalist",
                "dd", "del", "details", "dfn", "dialog", "div", "dl", "dt", "em", "fieldset",
                "figcaption", "figure", "footer", "form", "h1", "h2", "h3", "h4", "h5", "h6",
                "head", "header", "hgroup", "html", "i", "iframe", "ins", "kbd", "label", "legend",
                "li", "main", "map", "mark", "menu", "meter", "nav", "noscript", "object", "ol",
                "optgroup", "option", "output", "p", "picture", "pre", "progress", "q", "rp", "rt",
                "ruby", "s", "samp", "section", "select", "slot", "small", "span", "strong", "sub",
                "summary", "sup", "table", "tbody", "td", "template", "tfoot", "th", "thead",
                "time", "tr", "u", "ul", "var", "video"
            ) + VOID_ELEMENTS + CONTENT_ELEMENTS
            return tagName in htmlElements
        }

        private fun getArgumentByName(pyTypedElement: PyTypedElement, name: String): PyTypedElement? {
            return when (pyTypedElement) {
                is PyClass -> {
                    pyTypedElement.findClassAttribute(name, true, myTypeEvalContext)
                }
                is PyFunction -> {
                    pyTypedElement.parameterList.findParameterByName(name)
                }
                else -> null
            }
        }

        private fun warnMissingRequiredArgument(
            node: PyFormattedStringElement,
            name: String,
            tag: MatchResult,
            component: MatchResult,
            prefixLength: Int
        ) {
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
