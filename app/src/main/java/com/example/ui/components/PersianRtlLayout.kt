package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

@Composable
fun PersianRtlLayout(content: @Composable () -> Unit) {
    if (LocalLayoutDirection.current == LayoutDirection.Rtl) {
        content()
    } else {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            content()
        }
    }
}
