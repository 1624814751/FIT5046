package com.example.movesmart

import com.example.movesmart.model.*
import org.junit.Assert.*
import org.junit.Test

class ContextRecommendationTest {
    @Test fun recommendationAndRecordedSampleNeverExceedAvailableTime() {
        for (minutes in listOf(15, 30, 45, 60)) {
            for (rainy in listOf(false, true)) {
                val recommendation = contextRecommendation(minutes, "Medium", "Chadstone", "Outdoor", rainy)
                assertTrue(recommendation.minutes <= minutes)
                assertTrue(sampleSessionMinutes(recommendation.title, recommendation.minutes) <= recommendation.minutes)
            }
        }
    }
    @Test fun rainOverridesOutdoorPreferenceWithAnExplanation() {
        val plan = contextRecommendation(30, "High", "Campus", "Outdoor", true)
        assertEquals("Indoor Mobility", plan.title)
        assertTrue(plan.preferenceReason.contains("despite your outdoor preference"))
    }
    @Test fun indoorLocationAndPreferenceAreRespected() {
        assertEquals("Indoor Mobility", contextRecommendation(30, "Medium", "Home", "Outdoor", false).title)
        assertEquals("Indoor Mobility", contextRecommendation(30, "Medium", "Campus", "Indoor", false).title)
        assertEquals("Outdoor Jogging", contextRecommendation(30, "High", "Campus", "Outdoor", false).title)
    }
}
