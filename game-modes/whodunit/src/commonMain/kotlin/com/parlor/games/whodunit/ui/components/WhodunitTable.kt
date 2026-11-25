package com.parlor.games.whodunit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.StickyActionLayout
import com.parlor.designsystem.components.parlorSafeContentPadding
import com.parlor.designsystem.theme.ParlorTheme

@Composable
internal fun WhodunitTable(
    modifier: Modifier = Modifier,
    actions: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    HeroBackdrop(modifier = modifier) {
        // Let large-text actions share the scroll viewport instead of squeezing the body.
        val pinActions = LocalDensity.current.fontScale < LARGE_TABLE_TEXT_SCALE
        val body: @Composable () -> Unit = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .parlorSafeContentPadding(ParlorTheme.spacing.l),
                verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
            ) {
                content()
                if (!pinActions) actions?.invoke()
            }
        }
        if (pinActions && actions != null) {
            StickyActionLayout(content = { body() }, actions = actions)
        } else {
            body()
        }
    }
}

private const val LARGE_TABLE_TEXT_SCALE = 1.3f
