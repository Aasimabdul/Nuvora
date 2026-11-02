package com.simbuild.nuvora

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared Nuvora design tokens.
 *
 * Single source of truth for spacing, corner radii and component sizes used by the
 * glassmorphism / neomorphism UI pass described in docs/UI_ANALYSIS_AND_BLUEPRINT.md.
 * Screens adopt these incrementally so the visual language stays consistent across
 * every theme palette.
 */
object NuvoraTokens {
    // Spacing
    val SpaceS: Dp = 8.dp
    val SpaceM: Dp = 12.dp
    val SpaceL: Dp = 16.dp
    val SpaceXL: Dp = 20.dp

    // Corner radii
    val RadiusM: Dp = 14.dp
    val RadiusL: Dp = 22.dp
    val RadiusSheet: Dp = 28.dp

    // Component sizes
    val QueueRowHeight: Dp = 64.dp
    val CoverSize: Dp = 44.dp
}
