package com.parlor.designsystem.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Color tokens for Parlor's warm editorial table.
 *
 * The shell uses quiet charcoal/cream surfaces while the active game supplies
 * the stage color: amber for Whodunit and crimson for Mafia. The historical
 * ember/brass/parchment names remain source-compatible with feature modules,
 * but now map to deliberate semantic roles rather than ornamental texture.
 *
 * Two utility tokens supplement the named set:
 *  - [transparent] — explicit transparent so feature code never needs
 *    `Color.Transparent`.
 *  - [overlayScrim] — modal/dim-the-world overlay so feature code
 *    never needs `Color.Black.copy(alpha=...)`.
 */
@Immutable
data class ParlorColors(
    val surfaceCanvas: Color,
    val surfaceElevated: Color,
    val surfaceHigher: Color,
    val surfaceInset: Color,
    /** Attention-grabbing surface for the hero card on a screen. */
    val surfaceHero: Color,
    /** High-contrast evidence/card stock used for public read-aloud content. */
    val surfacePaper: Color,
    val textOnPaper: Color,
    val textOnPaperSecondary: Color,

    /** Primary brand/game accent. Amber by default; overridable by a game scope. */
    val accentEmber: Color,
    /** Lighter accent — used for subtle highlights / focus glows. */
    val accentEmberGlow: Color,
    /** Deeper accent — used for pressed-state accents and rare emphasis. */
    val accentEmberDeep: Color,
    /** Neutral mid-grey, retained for back-compat. Editorial: a muted grey. */
    val accentBrass: Color,
    /** High-contrast surface highlight. Editorial: pure white-tone on dark; deep ink on light. */
    val accentParchment: Color,

    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textOnAccent: Color,
    /** Quoted / narration body — same as textSecondary in editorial direction. */
    val textNarration: Color,

    val semanticSuccess: Color,
    val semanticDanger: Color,
    val semanticMuted: Color,

    val borderSubtle: Color,
    val borderElevated: Color,
    /** Soft accent glow — used sparingly for focus rings, not decoration. */
    val borderGlow: Color,
    /** Strong accent rim — used on hero affordances. */
    val borderAccent: Color,

    /** Explicit fully-transparent token. Use instead of `Color.Transparent`. */
    val transparent: Color,
    /** Modal overlay scrim. Use instead of `Color.Black.copy(alpha=...)`. */
    val overlayScrim: Color,
    /** Cover-screen body. Pure black in both modes — used by the "hide the dossier" beats. */
    val coverScreen: Color,
    /** Light text colour readable on [coverScreen]. Identical across light + dark palettes. */
    val coverScreenTextPrimary: Color,
    /** Secondary text on [coverScreen]. */
    val coverScreenTextSecondary: Color,
    /** Tertiary / hint text on [coverScreen]. */
    val coverScreenTextTertiary: Color,
)

/** Dark mode — warm charcoal canvas, cream type, Whodunit amber by default. */
val CozyNoirPalette = ParlorColors(
    surfaceCanvas = Color(0xFF0A0A0C),
    surfaceElevated = Color(0xFF141418),
    surfaceHigher = Color(0xFF222127),
    surfaceInset = Color(0xFF101013),
    surfaceHero = Color(0xFF211811),
    surfacePaper = Color(0xFFF0E6D1),
    textOnPaper = Color(0xFF151310),
    textOnPaperSecondary = Color(0xFF5D5548),

    accentEmber = Color(0xFFFFA438),
    accentEmberGlow = Color(0xFFFFCA83),
    accentEmberDeep = Color(0xFF753408),
    accentBrass = Color(0xFFB7B5B0),
    accentParchment = Color(0xFFF0E6D1),

    textPrimary = Color(0xFFF7F3EB),
    textSecondary = Color(0xFFB7B5B0),
    textTertiary = Color(0xFF898C94),
    textOnAccent = Color(0xFF151310),
    textNarration = Color(0xFFD7CBB6),

    semanticSuccess = Color(0xFF72C49D),
    semanticDanger = Color(0xFFFF6961),
    semanticMuted = Color(0xFF454A54),

    borderSubtle = Color(0xFF343036),
    borderElevated = Color(0xFF454A54),
    borderGlow = Color(0x33FFA438),
    borderAccent = Color(0xFFFFA438),

    transparent = Color(0x00000000),
    overlayScrim = Color(0xD9000000),
    coverScreen = Color(0xFF000000),
    coverScreenTextPrimary = Color(0xFFF7F3EB),
    coverScreenTextSecondary = Color(0xFFB7B5B0),
    coverScreenTextTertiary = Color(0xFF7F828A),
)

/** Light mode — warm paper canvas with an AA-safe deeper amber. */
val LightCozyNoirPalette = ParlorColors(
    surfaceCanvas = Color(0xFFFCF5EB),
    surfaceElevated = Color(0xFFFFFAF1),
    surfaceHigher = Color(0xFFE9E1D5),
    surfaceInset = Color(0xFFECE5DA),
    surfaceHero = Color(0xFFFFF1DC),
    surfacePaper = Color(0xFFF0E6D1),
    textOnPaper = Color(0xFF151310),
    textOnPaperSecondary = Color(0xFF5D5548),

    accentEmber = Color(0xFFA15B0F),
    accentEmberGlow = Color(0xFF754006),
    accentEmberDeep = Color(0xFF6F3C06),
    accentBrass = Color(0xFF55524D),
    accentParchment = Color(0xFF181716),

    textPrimary = Color(0xFF181716),
    textSecondary = Color(0xFF55524D),
    textTertiary = Color(0xFF66625C),
    textOnAccent = Color(0xFFFFFAF1),
    textNarration = Color(0xFF55524D),

    semanticSuccess = Color(0xFF267653),
    semanticDanger = Color(0xFFB82F2B),
    semanticMuted = Color(0xFFAAA094),

    borderSubtle = Color(0xFFD4CABD),
    borderElevated = Color(0xFFAAA094),
    borderGlow = Color(0x33A15B0F),
    borderAccent = Color(0xFFA15B0F),

    transparent = Color(0x00000000),
    overlayScrim = Color(0x99000000),
    coverScreen = Color(0xFF000000),
    coverScreenTextPrimary = Color(0xFFF7F3EB),
    coverScreenTextSecondary = Color(0xFFB7B5B0),
    coverScreenTextTertiary = Color(0xFF7F828A),
)

/** True iff the palette is the light variant (used by the theme to pick a Material 3 scheme). */
val ParlorColors.isLight: Boolean
    get() = surfaceCanvas.luminance() > 0.5f

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue


/** The shell's illuminated action has its own foreground on both themes. */
val AmberActionBrush = Brush.horizontalGradient(listOf(Color(0xFFFFBA4E), Color(0xFFEE6D16)))
val AmberActionInk = Color(0xFF140B04)
val AmberActionGlow = Color(0xFFF17218)
