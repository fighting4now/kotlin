/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.low.level.api.fir.project.structure

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.low.level.api.fir.providers.LLFirBuiltinSymbolProvider
import org.jetbrains.kotlin.analysis.low.level.api.fir.providers.LLFirBuiltinsAndCloneableSessionProvider
import org.jetbrains.kotlin.analysis.low.level.api.fir.sessions.LLFirBuiltinsAndCloneableSession
import org.jetbrains.kotlin.analysis.project.structure.KtBuiltinsModule
import org.jetbrains.kotlin.analyzer.common.CommonPlatformAnalyzerServices
import org.jetbrains.kotlin.fir.BuiltinTypes
import org.jetbrains.kotlin.fir.PrivateSessionConstructor
import org.jetbrains.kotlin.fir.SessionConfiguration
import org.jetbrains.kotlin.fir.backend.jvm.FirJvmTypeMapper
import org.jetbrains.kotlin.fir.resolve.providers.FirProvider
import org.jetbrains.kotlin.fir.resolve.providers.FirSymbolProvider
import org.jetbrains.kotlin.fir.resolve.providers.impl.FirCloneableSymbolProvider
import org.jetbrains.kotlin.fir.resolve.scopes.wrapScopeWithJvmMapped
import org.jetbrains.kotlin.fir.resolve.transformers.FirCompilerLazyDeclarationResolver
import org.jetbrains.kotlin.fir.scopes.FirKotlinScopeProvider
import org.jetbrains.kotlin.fir.session.registerCommonComponents
import org.jetbrains.kotlin.fir.session.registerCommonJavaComponents
import org.jetbrains.kotlin.fir.session.registerModuleData
import org.jetbrains.kotlin.fir.symbols.FirLazyDeclarationResolver
import java.util.concurrent.ConcurrentHashMap
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.platform.js.isJs
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.resolve.jvm.modules.JavaModuleResolver
import org.jetbrains.kotlin.resolve.jvm.platform.JvmPlatformAnalyzerServices

@OptIn(PrivateSessionConstructor::class, SessionConfiguration::class)
class LLFirBuiltinsSessionFactory(
    private val project: Project,
) {
    private val builtInTypes = BuiltinTypes() // TODO should be platform-specific
    private val builtinsAndCloneableSession = ConcurrentHashMap<TargetPlatform, LLFirBuiltinsAndCloneableSession>()

    fun getBuiltinsSession(platform: TargetPlatform): LLFirBuiltinsAndCloneableSession {
        return builtinsAndCloneableSession.getOrPut(platform) { createBuiltinsAndCloneableSession(platform) }
    }

    private fun createBuiltinsAndCloneableSession(platform: TargetPlatform): LLFirBuiltinsAndCloneableSession {
        val builtinsModule = KtBuiltinsModule(platform, platform.getAnalyzerServices(), project)
        return LLFirBuiltinsAndCloneableSession(builtinsModule, project, builtInTypes).apply session@{
            val moduleData = LLFirModuleData(builtinsModule).apply {
                bindSession(this@session)
            }
            registerIdeComponents(project)
            register(FirLazyDeclarationResolver::class, FirCompilerLazyDeclarationResolver)
            registerCommonComponents(LanguageVersionSettingsImpl.DEFAULT/*TODO*/)
            registerCommonJavaComponents(JavaModuleResolver.getInstance(project))
            registerModuleData(moduleData)

            val kotlinScopeProvider = FirKotlinScopeProvider(::wrapScopeWithJvmMapped)
            register(FirKotlinScopeProvider::class, kotlinScopeProvider)
            val symbolProvider = createCompositeSymbolProvider(this) {
                add(LLFirBuiltinSymbolProvider(this@session, moduleData, kotlinScopeProvider))
                add(FirCloneableSymbolProvider(this@session, moduleData, kotlinScopeProvider))
            }

            register(FirSymbolProvider::class, symbolProvider)
            register(FirProvider::class, LLFirBuiltinsAndCloneableSessionProvider(symbolProvider))
            register(FirJvmTypeMapper::class, FirJvmTypeMapper(this))
        }
    }

    companion object {
        fun getInstance(project: Project): LLFirBuiltinsSessionFactory =
            project.getService(LLFirBuiltinsSessionFactory::class.java)
    }
}

private fun TargetPlatform.getAnalyzerServices() = when {
    isJvm() -> JvmPlatformAnalyzerServices
    isJs() -> JvmPlatformAnalyzerServices/*TODO*/
//    isNative() -> NativePlatformAnalyzerServices
    isCommon() -> CommonPlatformAnalyzerServices
    else -> JvmPlatformAnalyzerServices/*TODO*/
}
