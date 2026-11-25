package com.parlor.designsystem.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import parlor.shared.design_system.generated.resources.Res
import parlor.shared.design_system.generated.resources.cover_whodunit
import parlor.shared.design_system.generated.resources.cover_blur_whodunit
import parlor.shared.design_system.generated.resources.cover_mafia
import parlor.shared.design_system.generated.resources.cover_blur_mafia
import parlor.shared.design_system.generated.resources.cover_last_light
import parlor.shared.design_system.generated.resources.cover_blur_last_light
import parlor.shared.design_system.generated.resources.cover_egyptian_dominoes
import parlor.shared.design_system.generated.resources.cover_blur_egyptian_dominoes
import parlor.shared.design_system.generated.resources.cover_ghamza
import parlor.shared.design_system.generated.resources.cover_blur_ghamza
import parlor.shared.design_system.generated.resources.cover_word_impostor
import parlor.shared.design_system.generated.resources.cover_blur_word_impostor

/** Original miniature prints. The objects keep their physical orientation in either language. */
enum class ParlorArtwork(
    val cover: DrawableResource,
    val blurredCover: DrawableResource,
    val pigment: Color,
    val imageVector: ImageVector,
) {
    CaseFile(Res.drawable.cover_whodunit, Res.drawable.cover_blur_whodunit, Plum, print("Sealed envelope") {
        ink(PaleInk, "M.4,4.2 Q5.6,1 10.6,3.2 T22.6,2.7 V3 Q17,5.8 10.6,3.6 T.4,4.6 Z")
        ink(PaleInk, "M.4,5.4 Q5.6,2.2 10.6,4.4 T22.6,3.9 V4.2 Q17,7 10.6,4.8 T.4,5.8 Z")
        ink(PaleInk, "M13.6,20.7 H23.2 V21 H13.6 Z M16.4,21.8 H23.2 V22.1 H16.4 Z")
        group(rotate = -13f, pivotX = 12f, pivotY = 12f) {
            ink(Paper, "M2,6 H22 V18 H2 Z")
            ink(Plum, "M2,6 L12,12.4 L22,6 L12,13.5 Z M2,18 L8.8,11.8 L9.3,12.2 Z M22,18 L15,12.2 L15.5,12 Z")
            ink(Plum, "M3.4,15.5 H6.8 V15.8 H3.4 Z M3.4,16.3 H5.8 V16.6 H3.4 Z")
            ink(Plum, "M15.9,10.3 l1,.6 .3,1.1 -.3,1.1 -1,.6 -1.2,-.1 -.9,-.8 -.1,-1.2 .7,-1 Z")
            ink(Paper, "M14.9,12 l.6,-.8 .6,.8 -.6,.8 Z")
        }
    }),
    Moon(Res.drawable.cover_mafia, Res.drawable.cover_blur_mafia, Midnight, print("Windows after dark") {
        ink(PaleInk, "M1,22 H12 V23 H1 Z M13,1 l.35,.9 .9,.35 -.9,.35 -.35,.9 -.35,-.9 -.9,-.35 .9,-.35 Z")
        ink(Paper, "M21.8,1.2 l.2,.6 .6,.2 -.6,.2 -.2,.6 -.2,-.6 -.6,-.2 .6,-.2 Z")
        ink(Paper, "M2,19 V7 Q2,3 6.3,3 Q10.6,3 10.6,7 V19 Z M13.4,21 V9 Q13.4,5 17.7,5 Q22,5 22,9 V21 Z")
        ink(Midnight, "M3,18 V7 Q3,4 6.3,4 Q9.6,4 9.6,7 V18 Z M17.4,5.8 H18 V20 H17.4 Z M14,11 H21.5 V11.6 H14 Z")
        ink(Paper, "M6.9,5.2 Q4.8,8.3 8.6,8.6 Q6.3,10.4 5.4,8.5 Q4.4,6.3 6.9,5.2 Z M1,19.7 H11.6 V20.4 H1 Z M12.4,21.7 H23 V22.4 H12.4 Z")
        ink(Midnight, "M14,20 V16.5 L15.8,14.5 L17.2,16 V20 Z M18.5,20 V17.8 L20,16 L21.4,17.6 V20 Z")
    }),
    Cards(Res.drawable.cover_last_light, Res.drawable.cover_blur_last_light, Amber, print("Last ember") {
        ink(PaleInk, "M1,14 L2.8,12 L2.3,14.6 Z M18,2.3 L19,0 L19,2 Z M21.6,5 L23.5,3.3 L22.7,5.8 Z M1,18 L2.2,16 L1.9,18.7 Z")
        group(rotate = -19f, pivotX = 10f, pivotY = 12f) {
            ink(Paper, "M4,4 H15 V20 H4 Z")
            ink(Amber, "M4.7,4.7 H14.3 V19.3 H4.7 Z")
            ink(Paper, "M5.5,7 L8,5.5 H9 L5.5,7.7 Z M5.5,9 L11,5.5 H12 L5.5,9.7 Z M5.5,11 L14,5.5 V6.2 L5.5,11.7 Z")
        }
        group(rotate = 10f, pivotX = 14f, pivotY = 12f) {
            ink(Paper, "M8,3 H20 V21 H8 Z")
            ink(Amber, "M14.7,6 C15.4,9 12.2,10.2 13.8,12.4 Q15.7,11.1 16,9.7 C19.4,15.4 15.7,17.7 14.1,17.7 " +
                "C10.2,17.7 9.6,13.5 11.4,11.2 Q11.5,12.6 12,13 C11.5,9.4 14,9 14.7,6 Z")
            ink(Amber, "M9,4 H12 V4.4 H9 Z M16,19.6 H19 V20 H16 Z")
        }
    }),
    Tiles(Res.drawable.cover_egyptian_dominoes, Res.drawable.cover_blur_egyptian_dominoes, Blue, print("Double six") {
        ink(PaleInk, "M16,3 l.5,.1 -.5,3.1 -.5,-.1 Z M17.3,3.2 l.5,.1 -.5,3.1 -.5,-.1 Z M18.6,3.4 l.5,.1 -.5,3.1 -.5,-.1 Z")
        ink(PaleInk, "M15,5.4 l5.1,-1 .1,.4 -5.1,1 Z M0,21 H6.2 V21.3 H0 Z M0,22 H4.3 V22.3 H0 Z")
        group(rotate = -16f, pivotX = 12f, pivotY = 12f) {
            ink(Paper, "M1,12 H15 V19 H1 Z M15.4,5 H22.4 V19 H15.4 Z")
            ink(Blue, "M7.8,12.5 H8.2 V18.5 H7.8 Z M15.9,11.8 H21.9 V12.2 H15.9 Z")
            pip(3f, 14f)
            pip(4.5f, 15.5f)
            pip(6f, 17f)
            for (x in listOf(9.7f, 11.5f, 13.3f)) {
                pip(x, 14f)
                pip(x, 17f)
            }
            for (x in listOf(17.4f, 20.4f)) {
                for (y in listOf(14f, 15.5f, 17f)) pip(x, y)
            }
            pip(18.9f, 8.5f)
        }
    }),
    Wink(Res.drawable.cover_ghamza, Res.drawable.cover_blur_ghamza, Lagoon, print("Across the table") {
        ink(PaleInk, "M.3,15 Q5,20 10,15.2 Q5.2,21 .3,15 Z M14,3 Q18,0 22,2 Q18,1.2 14,3 Z")
        ink(Paper, "M.6,11.8 Q5.6,3 11.8,10.3 Q6.6,18 .6,11.8 Z")
        ink(Lagoon, "M4.4,10.4 a2.5,2.5 0,1 0,5,0 a2.5,2.5 0,1 0,-5,0")
        ink(Paper, "M13,10.4 Q18.6,14.6 23.6,7.6 Q20.4,17 13,10.4 Z")
        ink(Paper, "M2,7 Q6.1,2.4 9.8,4.6 Q5.4,4.1 2,7 Z M16,7.8 Q19,4.5 22.5,5.2 Q18.7,6.1 16,7.8 Z")
        ink(Paper, "M12.9,16.4 l.7,1.8 1.8,.7 -1.8,.7 -.7,1.8 -.7,-1.8 -1.8,-.7 1.8,-.7 Z")
    }),
    Conversation(Res.drawable.cover_word_impostor, Res.drawable.cover_blur_word_impostor, Mulberry, print("Missing line") {
        ink(PaleInk, "M1,19 H5 V19.35 H1 Z M1,20 H4 V20.35 H1 Z M19,3 H23 V3.35 H19 Z M18,4 H23 V4.35 H18 Z")
        group(rotate = -12f, pivotX = 12f, pivotY = 12f) {
            ink(Paper, "M2,4 H17 V13 H7 L3,16 V13 H2 Z")
            ink(Mulberry, "M4,6.4 H14.8 V7.1 H4 Z M4,8.6 H11 V9.3 H4 Z")
        }
        group(rotate = 9f, pivotX = 15f, pivotY = 15f) {
            ink(Mulberry, "M6,10 H23 V20 H21.5 V23.8 L15.8,20 H6 Z")
            ink(Paper, "M6.6,10.6 H22.4 V19.4 H20.9 V22.6 L16,19.4 H6.6 Z")
            ink(Mulberry, "M9,16.7 H20 V17.2 H9 Z M9,12 H10.3 L9.7,14 H8.7 Z M11.1,12 H12.4 L11.8,14 H10.8 Z")
        }
    }),
}

private val Paper = Color(0xFFFFE7BD)
private val PaleInk = Paper.copy(alpha = 0.36f)
private val Plum = Color(0xFF654765)
private val Midnight = Color(0xFF354769)
private val Amber = Color(0xFFA95429)
private val Lagoon = Color(0xFF26736E)
private val Mulberry = Color(0xFF833D59)
private val Blue = Color(0xFF40568B)

private fun print(name: String, draw: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(draw).build()

private fun ImageVector.Builder.ink(color: Color, outline: String) {
    addPath(pathData = addPathNodes(outline), fill = SolidColor(color))
}

private fun ImageVector.Builder.pip(x: Float, y: Float) {
    ink(Blue, "M${x - 0.65f},$y a.65,.65 0,1 0,1.3,0 a.65,.65 0,1 0,-1.3,0")
}
