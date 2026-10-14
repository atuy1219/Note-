package com.atuy.note.data

import kotlinx.serialization.Serializable

/** Independent brush settings, including the last normal pen to return to. */
@Serializable
data class BrushSelection(
    val selectedKind: BrushKind = BrushKind.PRESSURE_PEN,
    val penKind: BrushKind = BrushKind.PRESSURE_PEN,
    val brushes: Map<BrushKind, BrushSpec> = emptyMap(),
) {
    val currentBrush: BrushSpec
        get() = brushes[selectedKind] ?: defaultBrush(selectedKind)

    fun select(kind: BrushKind): BrushSelection = copy(
        selectedKind = kind,
        penKind = if (kind == BrushKind.HIGHLIGHTER) penKind else kind,
    )

    fun selectPen(): BrushSelection = select(
        penKind.takeUnless { it == BrushKind.HIGHLIGHTER } ?: BrushKind.PRESSURE_PEN,
    )

    fun update(brush: BrushSpec): BrushSelection = select(brush.kind).copy(
        brushes = brushes + (brush.kind to brush),
    )
}

private fun defaultBrush(kind: BrushKind): BrushSpec = when (kind) {
    BrushKind.PRESSURE_PEN -> BrushSpec()
    BrushKind.MARKER -> BrushSpec(kind = kind, size = 5.5f)
    BrushKind.HIGHLIGHTER -> BrushSpec(kind = kind, colorArgb = 0x66FFF176, size = 16f)
    BrushKind.CUSTOM -> BrushSpec(kind = kind, custom = CustomBrushSpec())
}
