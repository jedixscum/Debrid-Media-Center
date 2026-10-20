package com.debridmediacenter.android

import androidx.compose.runtime.Composable
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope

fun LazyListScope.items(
    count: Int,
    itemContent: @Composable LazyItemScope.(index: Int) -> Unit
) {
    items(count = count, key = null, contentType = null, itemContent = itemContent)
}
