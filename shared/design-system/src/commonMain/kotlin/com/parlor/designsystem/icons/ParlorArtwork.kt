package com.parlor.designsystem.icons

import org.jetbrains.compose.resources.DrawableResource
import parlor.shared.design_system.generated.resources.Res
import parlor.shared.design_system.generated.resources.cover_whodunit
import parlor.shared.design_system.generated.resources.cover_mafia
import parlor.shared.design_system.generated.resources.cover_last_light
import parlor.shared.design_system.generated.resources.cover_egyptian_dominoes
import parlor.shared.design_system.generated.resources.cover_ghamza
import parlor.shared.design_system.generated.resources.cover_word_impostor

/** Catalog covers retain their physical orientation in Arabic and English. */
enum class ParlorArtwork(val cover: DrawableResource) {
    CaseFile(Res.drawable.cover_whodunit),
    Moon(Res.drawable.cover_mafia),
    Cards(Res.drawable.cover_last_light),
    Tiles(Res.drawable.cover_egyptian_dominoes),
    Wink(Res.drawable.cover_ghamza),
    Conversation(Res.drawable.cover_word_impostor),
}
