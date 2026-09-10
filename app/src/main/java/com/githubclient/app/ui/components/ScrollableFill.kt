package com.githubclient.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 让不可滚动的内容（空态 / 加载态）也能响应下拉刷新。
 *
 * PullToRefreshBox 依赖子内容产生嵌套滚动事件来识别下拉手势。
 * 如果直接放一个不可滚动的 Row，手势会被吞掉，表现为「下拉没反应」。
 * 套一层 verticalScroll 后即可正常触发。
 */
@Composable
fun ScrollableFill(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        content()
    }
}
