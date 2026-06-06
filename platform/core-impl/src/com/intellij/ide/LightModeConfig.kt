// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service

@Service
class LightModeConfig {
  var disableIndexing: Boolean = true
  var disableInspections: Boolean = true
  var disableCodeAnalysis: Boolean = true

  companion object {
    @JvmStatic
    fun getInstance(): LightModeConfig = service()
  }
}