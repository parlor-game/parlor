package com.parlor.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import org.jetbrains.compose.resources.painterResource
import parlor.shared.design_system.generated.resources.Res
import parlor.shared.design_system.generated.resources.parlor_brand_mark

/** Decorative Parlor brand mark. Callers provide the spoken brand label. */
@Composable
fun ParlorMark(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(Res.drawable.parlor_brand_mark),
        contentDescription = null,
        modifier = modifier.clearAndSetSemantics { },
    )
}
