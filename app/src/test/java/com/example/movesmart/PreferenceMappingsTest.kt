package com.example.movesmart

import com.example.movesmart.shared.Environment
import com.example.movesmart.shared.Intensity
import com.example.movesmart.ui.preferences.environmentFromText
import com.example.movesmart.ui.preferences.intensityFromText
import com.example.movesmart.ui.preferences.makePreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferenceMappingsTest {
    @Test
    fun textMapsToSharedEnums() {
        assertEquals(Intensity.LOW, intensityFromText("Easy"))
        assertEquals(Intensity.MEDIUM, intensityFromText("Medium"))
        assertEquals(Intensity.HIGH, intensityFromText("High"))
        assertEquals(Environment.ANY, environmentFromText("No preference"))
    }

    @Test
    fun unknownValuesAreRejected() {
        assertNull(intensityFromText("Very hard"))
        assertNull(environmentFromText("Beach"))
    }

    @Test
    fun invalidWeeklyGoalFails() {
        val result = makePreferences("user-a", "Medium", emptyList(), "Stay Active", 0, "Indoor")
        assertTrue(result.isFailure)
    }
}
