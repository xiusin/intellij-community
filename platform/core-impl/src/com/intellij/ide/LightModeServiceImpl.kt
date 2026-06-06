// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.application.LightModeService
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service

@Service
class LightModeServiceImpl : LightModeService {
  @Volatile
  private var _lightMode = false

  override val isLightMode: Boolean
    get() = _lightMode || LightModeServiceImpl.lightModeRequested

  override fun enterLightMode() {
    _lightMode = true
  }

  override fun exitLightMode() {
    _lightMode = false
  }

  companion object {
    @Volatile
    @JvmField
    var lightModeRequested: Boolean = false

    @JvmStatic
    fun getInstance(): LightModeService = service()
  }
}