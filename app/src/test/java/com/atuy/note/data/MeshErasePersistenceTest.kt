package com.atuy.note.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class MeshErasePersistenceTest {
    @Test
    fun cutOperationsRoundTripThroughManifest() {
        val cut = MeshErasePath(
            radius = 12f,
            points = listOf(MeshErasePoint(1f, 2f), MeshErasePoint(6f, 7f)),
        )
        val stroke = StoredStroke(
            id = "sample",
            brush = BrushSpec(),
            meshErases = listOf(cut),
        )
        val decoded = Json.decodeFromString<StoredStroke>(Json.encodeToString(stroke))
        assertEquals(stroke, decoded)
    }

    @Test
    fun previousStrokeManifestsDefaultToUncut() {
        val stroke = StoredStroke(id = "legacy", brush = BrushSpec())
        val payload = Json.encodeToString(stroke)
        val loaded = Json.decodeFromString<StoredStroke>(payload)
        assertEquals(emptyList<MeshErasePath>(), loaded.meshErases)
    }
}
