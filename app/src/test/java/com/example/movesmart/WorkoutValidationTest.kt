package com.example.movesmart

import com.example.movesmart.data.local.workoutValidationError
import com.example.movesmart.shared.DataSource
import com.example.movesmart.shared.WorkoutRecord
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class WorkoutValidationTest {
    private val today = LocalDate.of(2026, 10, 11)
    private val valid = WorkoutRecord(0, "user-a", "Walk", 30, today, null, DataSource.LIVE)

    @Test fun acceptsNewAndExistingRecordsAtDurationBoundaries() {
        assertNull(workoutValidationError(valid.copy(minutes = 1), today))
        assertNull(workoutValidationError(valid.copy(id = 4, minutes = 1440), today))
    }

    @Test fun rejectsInvalidIdentityTitleDurationAndFutureDate() {
        val invalid = listOf(valid.copy(id = -1), valid.copy(userId = " "),
            valid.copy(title = " \n "), valid.copy(minutes = 0), valid.copy(minutes = 1441),
            valid.copy(date = today.plusDays(1)))
        invalid.forEach { assertNotNull("Must reject $it", workoutValidationError(it, today)) }
    }
}
