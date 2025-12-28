package com.koxudaxi.tdom

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.python.PythonTestUtil
import com.jetbrains.python.fixtures.PyLightProjectDescriptor
import com.jetbrains.python.psi.LanguageLevel

/**
 * Tests for TdomInspection based on tdom_spec.json diagnostics.
 *
 * Test cases are derived from the tdom specification:
 * - tdom/tdom_spec.json: diagnostics.errors, diagnostics.warnings, diagnostics.info
 * - tdom/specification.md: Static Analysis & Diagnostics section
 */
class TdomInspectionTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = PythonTestUtil.testDataPath

    override fun getProjectDescriptor() = PyLightProjectDescriptor(LanguageLevel.getLatest())

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(TdomInspection::class.java)
        myFixture.copyDirectoryToProject("mock/stub", "")
    }

    // ==========================================================================
    // E001: Void Element with Children
    // From tdom_spec.json: "Void element '<{tag}>' cannot have children"
    // ==========================================================================

    fun testE001_VoidElementWithoutChildren_Ok() {
        myFixture.configureByText("test.py", """
from tdom import html

html(f"<br />")
html(f"<img src='test.png' />")
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==========================================================================
    // E002: Mismatched Component Closing Tags
    // From tdom_spec.json: "Mismatched closing tag '</{closing_tag}>' for component '<{opening_component}>'"
    // ==========================================================================

    fun testE002_MatchedClosingTags_Ok() {
        myFixture.configureByText("test.py", """
from tdom import html

def Heading(children):
    pass

html(f'<{Heading}>Content</{Heading}>')
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==========================================================================
    // E003: Missing Component Braces
    // From tdom_spec.json: "Component name must be in curly braces"
    // ==========================================================================

    fun testE003_ComponentWithBraces_Ok() {
        myFixture.configureByText("test.py", """
from tdom import html

def MyComponent():
    pass

html(f"<{MyComponent} />")
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testE003_HtmlElementNotComponent() {
        // Lowercase HTML elements should not trigger this warning
        myFixture.configureByText("test.py", """
from tdom import html

html(f"<div>content</div>")
html(f"<span>text</span>")
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==========================================================================
    // W003: Component Missing children Parameter
    // From tdom_spec.json: "Component '{component}' doesn't accept 'children' parameter, content will be ignored"
    // ==========================================================================

    fun testW003_ComponentWithChildrenParameter_Ok() {
        myFixture.configureByText("test.py", """
from tdom import html

def Heading(children, title: str):
    pass

html(f'<{Heading} title="Hi">Content</{Heading}>')
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testW003_SelfClosingComponent_Ok() {
        // Self-closing components without children should not trigger warning
        myFixture.configureByText("test.py", """
from tdom import html

def Heading(title: str):
    pass

html(f'<{Heading} title="Hi" />')
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==========================================================================
    // Existing Tests: Missing Required Argument
    // ==========================================================================

    fun testMissingRequiredArgument() {
        myFixture.configureByText("test.py", """
from tdom import html

def Heading(title: str):
    pass

html(f"<{<warning descr="missing a required argument: 'title'">Heading</warning>} />")
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testRequiredArgumentProvided() {
        myFixture.configureByText("test.py", """
from tdom import html

def Heading(title: str):
    pass

html(f'<{Heading} title="Hello" />')
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testOptionalArgumentNotRequired() {
        myFixture.configureByText("test.py", """
from tdom import html

def Heading(title: str = "Default"):
    pass

html(f"<{Heading} />")
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testCorrectType() {
        myFixture.configureByText("test.py", """
from tdom import html

def Counter(count: int):
    pass

html(f"<{Counter} count={42} />")
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testInvalidArgumentName() {
        myFixture.configureByText("test.py", """
from tdom import html

def Heading(title: str):
    pass

html(f'<{Heading} title="Hello" <error descr="invalid a argument: 'unknown'">unknown</error>="value" />')
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // Children parameter should not be reported as missing
    fun testChildrenParameterNotReportedAsMissing() {
        myFixture.configureByText("test.py", """
from tdom import html

def Container(children):
    pass

html(f"<{Container}>Content</{Container}>")
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==========================================================================
    // W001: Content Element Without :safe
    // From tdom_spec.json: "Interpolated content in '<{tag}>' should use :safe if content is trusted"
    // ==========================================================================

    fun testW001_ScriptWithInterpolation_Warning() {
        myFixture.configureByText("test.py", """
from tdom import html

user_script = "console.log('hello')"
html(f"<script><weak_warning descr="Interpolated content in '<script>' should use :safe if content is trusted">{user_script}</weak_warning></script>")
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testW001_ScriptWithSafeFormat_Ok() {
        myFixture.configureByText("test.py", """
from tdom import html

trusted_js = "console.log('hello')"
html(f"<script>{trusted_js:safe}</script>")
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testW001_StyleWithInterpolation_Warning() {
        myFixture.configureByText("test.py", """
from tdom import html

css = "body { color: red; }"
html(f"<style><weak_warning descr="Interpolated content in '<style>' should use :safe if content is trusted">{css}</weak_warning></style>")
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testW001_ScriptWithoutInterpolation_Ok() {
        // Static script content should not trigger warning
        myFixture.configureByText("test.py", """
from tdom import html

html(f"<script>console.log('static')</script>")
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==========================================================================
    // I001: Boolean Attribute Values
    // From tdom_spec.json: "Attribute '{attribute}' should use boolean value instead of string"
    // ==========================================================================

    fun testI001_BooleanStringValue_Info() {
        myFixture.configureByText("test.py", """
from tdom import html

html(f'<button <weak_warning descr="Attribute 'disabled' should use boolean value instead of string \"true\"">disabled="true"</weak_warning>>Submit</button>')
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testI001_BooleanTrueValue_Ok() {
        myFixture.configureByText("test.py", """
from tdom import html

html(f"<button disabled={True}>Submit</button>")
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testI001_BareAttribute_Ok() {
        myFixture.configureByText("test.py", """
from tdom import html

html(f"<input disabled>")
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==========================================================================
    // I002: Empty Fragments
    // From tdom_spec.json: "Empty template returns empty Fragment - this may be unintentional"
    // ==========================================================================

    fun testI002_EmptyTemplate_Info() {
        myFixture.configureByText("test.py", """
from tdom import html

html(f"<weak_warning descr="Empty template returns empty Fragment - this may be unintentional"></weak_warning>")
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testI002_NonEmptyTemplate_Ok() {
        myFixture.configureByText("test.py", """
from tdom import html

html(f"<div>content</div>")
        """.trimIndent())
        myFixture.checkHighlighting()
    }
}
