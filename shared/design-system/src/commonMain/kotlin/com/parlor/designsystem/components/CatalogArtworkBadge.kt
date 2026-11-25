package com.parlor.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.parlor.designsystem.icons.ParlorArtwork
import com.parlor.designsystem.theme.ParlorTheme
import org.jetbrains.compose.resources.painterResource

/** A matte printed cover. The parent supplies all live text and interaction. */
@Composable
fun CatalogArtworkBadge(artwork: ParlorArtwork, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(ParlorTheme.spacing.xxxl).background(artwork.pigment)
            .clipToBounds().clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Image(artwork.imageVector, contentDescription = null, modifier = Modifier.fillMaxSize(0.86f))
    }
}


/** Minimalist cover used by the shell catalog and Setup. */
@Composable
fun CatalogCover(artwork: ParlorArtwork, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(artwork.cover),
        contentDescription = null,
        modifier = modifier.size(ParlorTheme.spacing.xxxl).clearAndSetSemantics { },
        contentScale = ContentScale.Crop,
    )
}
