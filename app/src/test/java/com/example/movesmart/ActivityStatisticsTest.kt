package com.example.movesmart

import com.example.movesmart.shared.*
import com.example.movesmart.statistics.*
import java.time.LocalDate
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class ActivityStatisticsTest {
    private fun record(id: Int, date: String, minutes: Int, user: String = "d") =
        WorkoutRecord(id, user, "Walking", minutes, LocalDate.parse(date), null, DataSource.LIVE)

    @Test fun dayIncludesOnlySelectedDateAndUser() {
        val records = listOf(record(1,"2026-10-11",30), record(2,"2026-10-11",15),
            record(3,"2026-10-10",60), record(4,"2026-10-12",60), record(5,"2026-10-11",100,"other"))
        val result = statisticsFor(records,"d",LocalDate.parse("2026-10-11"),StatisticsPeriod.DAY)
        assertEquals(45L,result.totalMinutes)
        assertEquals(2,result.sessions)
        assertEquals(1,result.activeDays)
    }
    @Test fun weekStartsMondayAndIncludesSundayAcrossYear() {
        val result = statisticsFor(listOf(record(1,"2025-12-29",10),record(2,"2026-01-04",20),
            record(3,"2026-01-05",99)),"d",LocalDate.parse("2026-01-01"),StatisticsPeriod.WEEK)
        assertEquals(LocalDate.parse("2025-12-29"),result.start)
        assertEquals(LocalDate.parse("2026-01-04"),result.end)
        assertEquals(listOf(10L,0L,0L,0L,0L,0L,20L),result.daily.map { it.minutes })
    }
    @Test fun leapMonthIncludes29thButNotMarch() {
        val result = statisticsFor(listOf(record(1,"2024-02-29",25),record(2,"2024-03-01",60)),
            "d",LocalDate.parse("2024-02-10"),StatisticsPeriod.MONTH)
        assertEquals(29,result.daily.size)
        assertEquals(25L,result.totalMinutes)
        assertEquals(1,result.activeDays)
    }
    @Test fun ordinaryFebruaryAndEmptyPeriodHaveZeroTotals() {
        val result = statisticsFor(emptyList(),"d",LocalDate.parse("2025-02-01"),StatisticsPeriod.MONTH)
        assertEquals(28,result.daily.size)
        assertEquals(0L,result.totalMinutes)
        assertEquals(0,result.sessions)
        assertEquals(0f,result.progress(150),0f)
    }
    @Test fun monthNavigationDoesNotDriftOrSkipDecember() {
        val feb = shiftStatisticsDate(LocalDate.parse("2026-01-31"),StatisticsPeriod.MONTH,1)
        assertEquals(LocalDate.parse("2026-02-01"),feb)
        assertEquals(LocalDate.parse("2026-01-01"),shiftStatisticsDate(feb,StatisticsPeriod.MONTH,-1))
        assertEquals(LocalDate.parse("2025-12-01"),shiftStatisticsDate(feb,StatisticsPeriod.MONTH,-2))
    }
    @Test fun negativeAndZeroDurationsDoNotCreateActiveSessions() {
        val result = statisticsFor(listOf(record(1,"2026-10-11",-10),record(2,"2026-10-11",0)),
            "d",LocalDate.parse("2026-10-11"),StatisticsPeriod.DAY)
        assertEquals(0,result.sessions)
        assertEquals(0,result.activeDays)
    }
    @Test fun goalIsClampedAndLargeTotalsDoNotOverflow() {
        val result = statisticsFor(listOf(record(1,"2026-10-11",Int.MAX_VALUE),record(2,"2026-10-11",Int.MAX_VALUE)),
            "d",LocalDate.parse("2026-10-11"),StatisticsPeriod.DAY)
        assertEquals(4294967294L,result.totalMinutes)
        assertEquals(1f,result.progress(150),0f)
        assertEquals(0f,result.progress(0),0f)
        assertEquals(0f,result.progress(-1),0f)
    }
    @Test fun updatedAndDeletedRecordsAreReflectedOnNextCalculation() {
        val date = LocalDate.parse("2026-10-11")
        val original = record(1,"2026-10-11",30)
        assertEquals(30L,statisticsFor(listOf(original),"d",date,StatisticsPeriod.DAY).totalMinutes)
        assertEquals(50L,statisticsFor(listOf(original.copy(minutes=50)),"d",date,StatisticsPeriod.DAY).totalMinutes)
        assertEquals(0L,statisticsFor(emptyList(),"d",date,StatisticsPeriod.DAY).totalMinutes)
    }
    @Test fun recordedDateTakesPrecedenceOverStartInstant() {
        val item = record(1,"2026-10-11",30).copy(startedAt=Instant.parse("2026-10-10T14:30:00Z"))
        assertEquals(30L,statisticsFor(listOf(item),"d",LocalDate.parse("2026-10-11"),StatisticsPeriod.DAY).totalMinutes)
    }
}
