package com.parlor.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.parlor.designsystem.icons.ParlorArtwork
import com.parlor.designsystem.theme.ParlorTheme
import org.jetbrains.compose.resources.painterResource

/** Decorative game cover. The parent supplies live text and interaction. */
@Composable
fun CatalogArtworkBadge(artwork: ParlorArtwork, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(artwork.cover),
        contentDescription = null,
        modifier = modifier.size(ParlorTheme.spacing.xxxl).clearAndSetSemantics { },
        contentScale = ContentScale.Crop,
    )
}
