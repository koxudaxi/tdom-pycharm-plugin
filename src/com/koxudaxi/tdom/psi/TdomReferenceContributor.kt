package com.koxudaxi.tdom.psi

import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.xml.XmlElement
import com.intellij.util.ProcessingContext
import com.jetbrains.python.psi.PyFormattedStringElement
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.koxudaxi.htmpy.psi.HtmpyReferenceProvider
import com.koxudaxi.tdom.getContext
import com.koxudaxi.tdom.isHtmpy


class HtmpyReferenceContributor : PsiReferenceContributor() {
    internal object Holder {
        @JvmField
        val TAG_STRING_PATTERN = PlatformPatterns.psiElement()
        //.with(object : PatternCondition<PsiElement>("isFormatFunctionHtmp") {
        //    override fun accepts(expression: PsiElement, context: ProcessingContext): Boolean {
        //        if (expression !is PyFormattedStringElement) return false
        //        return isHtmpy(expression, getContext(expression))
        //    }
        //})
    }

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(Holder.TAG_STRING_PATTERN,
            HtmpyReferenceProvider()
        )
    }
}