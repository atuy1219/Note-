package com.atuy.note.data

import androidx.ink.brush.Brush
import androidx.ink.brush.InputToolType
import androidx.ink.brush.StockBrushes
import androidx.ink.geometry.ImmutableAffineTransform
import androidx.ink.geometry.Intersection.intersects
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.ExperimentalInkEraserApi
import androidx.ink.strokes.StrokeInput
import kotlinx.serialization.Serializable

@Serializable
data class MeshErasePoint(val x: Float, val y: Float)

@Serializable
data class MeshErasePath(
    val radius: Float,
    val points: List<MeshErasePoint>,
)

/**
 * Partial eraser: subtract actual Ink mesh geometry rather than masking paint.
 *
 * Ink 1.1 does not serialize PartitionedMesh. Each cut is saved as an eraser
 * path in StoredStroke and replayed on load, so the edited shape survives a
 * save/restart while keeping the original editable stroke inputs.
 *
 * Geometry work is expensive; caller must run apply() on Dispatchers.Default.
 */
@OptIn(ExperimentalInkEraserApi::class)
object MeshEraser {
    private val identity = ImmutableAffineTransform(1f, 0f, 0f, 0f, 1f, 0f)

    private fun eraserMask(path: MeshErasePath): Stroke? {
        if (path.points.isEmpty() || path.radius <= 0f || !path.radius.isFinite()) return null
        val batch = MutableStrokeInputBatch()
        path.points.forEachIndexed { index, point ->
            if (point.x.isFinite() && point.y.isFinite()) {
                batch.add(
                    type = InputToolType.STYLUS,
                    x = point.x,
                    y = point.y,
                    elapsedTimeMillis = index.toLong(),
                    strokeUnitLengthCm = StrokeInput.NO_STROKE_UNIT_LENGTH,
                    pressure = 1f,
                    tiltRadians = 0f,
                    orientationRadians = 0f,
                )
            }
        }
        if (batch.size == 0) return null
        // A tap-only erase must also have a non-degenerate mask. Some brush tips
        // emit no tessellation for a batch containing a single input sample.
        if (batch.size == 1) {
            val p = path.points.first { it.x.isFinite() && it.y.isFinite() }
            batch.add(
                type = InputToolType.STYLUS,
                x = p.x + path.radius * 0.01f,
                y = p.y,
                elapsedTimeMillis = 1L,
                strokeUnitLengthCm = StrokeInput.NO_STROKE_UNIT_LENGTH,
                pressure = 1f,
                tiltRadians = 0f,
                orientationRadians = 0f,
            )
        }
        return Stroke(
            Brush.createWithColorIntArgb(
                StockBrushes.marker(), 0xff000000.toInt(),
                (path.radius * 2f).coerceAtLeast(0.01f), 0.1f,
            ),
            batch,
        )
    }

    /** Reconstruct the persisted cut geometry from the immutable input stroke. */
    fun replay(original: Stroke, cuts: List<MeshErasePath>): Stroke {
        var result = original
        for (cut in cuts) {
            val mask = eraserMask(cut) ?: continue
            if (result.shape.intersects(mask.shape, identity, identity)) {
                result = result.subtract(mask.shape, identity, identity)
            }
        }
        return result
    }

    /**
     * Returns the same list if nothing intersects. The cut metadata is append-only
     * for edited strokes, allowing Undo/Redo to preserve original geometry.
     */
    fun apply(input: List<RuntimeStroke>, path: MeshErasePath): List<RuntimeStroke> {
        val mask = eraserMask(path) ?: return input
        var changed = false
        val result = input.mapNotNull { runtime ->
            if (!runtime.stroke.shape.intersects(mask.shape, identity, identity)) {
                return@mapNotNull runtime
            }
            changed = true
            val edited = runtime.stroke.subtract(mask.shape, identity, identity)
            if (edited.shape.computeBoundingBox() == null) {
                null
            } else {
                RuntimeStroke(
                    stored = runtime.stored.copy(
                        meshErases = runtime.stored.meshErases + path,
                    ),
                    stroke = edited,
                    samples = runtime.samples,
                )
            }
        }
        return if (changed) result else input
    }

    fun transformCuts(
        cuts: List<MeshErasePath>,
        dx: Float, dy: Float, scale: Float,
        centerX: Float, centerY: Float,
    ): List<MeshErasePath> = cuts.map { cut ->
        cut.copy(
            radius = cut.radius * scale,
            points = cut.points.map { point ->
                MeshErasePoint(
                    centerX + (point.x - centerX) * scale + dx,
                    centerY + (point.y - centerY) * scale + dy,
                )
            },
        )
    }
}
