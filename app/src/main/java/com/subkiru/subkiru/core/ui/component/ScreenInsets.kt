package com.subkiru.subkiru.core.ui.component

import androidx.compose.foundation.layout.WindowInsets

// MainActivity の Scaffold がシステムバーぶんの余白を確保済みのため、
// 各画面の Scaffold / TopAppBar では同じ余白を重ねて取らないようにする
internal val NoWindowInsets = WindowInsets(0, 0, 0, 0)
