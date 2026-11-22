package com.parlor.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parlor.designsystem.theme.ParlorTheme

/** A destination in the shell dock, with a localized label and accessible description. */
data class ParlorBottomTab(
    val label: String,
    val contentDescription: String,
    val icon: ImageVector,
)

@Composable
fun ParlorBottomTabBar(
    tabs: List<ParlorBottomTab>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ParlorTheme.colors
    val dockShape = RoundedCornerShape(24.dp)
    val tabShape = RoundedCornerShape(19.dp)
    Column(
        modifier = modifier.fillMaxWidth().background(colors.surfaceCanvas)
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = DockMaxWidth)
                .fillMaxWidth()
                .clip(dockShape)
                .background(colors.surfaceElevated)
                .border(ParlorTheme.borders.hairline, colors.borderSubtle, dockShape)
                .selectableGroup()
                .padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            tabs.forEachIndexed { index, tab ->
                val selected = index == selectedIndex
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = ParlorTheme.spacing.xxl)
                        .clip(tabShape)
                        .background(if (selected) colors.accentEmber.copy(alpha = SELECTED_WASH_ALPHA) else colors.transparent)
                        .libraryFocusOutline(tabShape)
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = { onTabSelected(index) },
                        )
                        .padding(vertical = 5.dp)
                        .semantics {
                            contentDescription = tab.contentDescription
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                ) {
                    val contentColor = if (selected) colors.accentEmber else colors.textTertiary
                    Icon(tab.icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
                    Text(
                        text = tab.label,
                        style = ParlorTheme.typography.labelMedium.copy(fontSize = 11.sp, lineHeight = 13.sp),
                        color = contentColor,
                        textAlign = TextAlign.Center,
                    )
                    Box(
                        Modifier.size(18.dp, 2.dp).clip(tabShape)
                            .background(if (selected) colors.accentEmber else colors.transparent),
                    )
                }
            }
        }
    }
}

private val DockMaxWidth = 480.dp
private const val SELECTED_WASH_ALPHA = 0.12f
