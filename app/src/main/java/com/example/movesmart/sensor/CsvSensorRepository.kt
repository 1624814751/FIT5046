package com.example.movesmart.sensor

import com.example.movesmart.shared.DataSource
import com.example.movesmart.shared.MotionContext
import com.example.movesmart.shared.MovementBand
import com.example.movesmart.shared.SensorRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class CsvSensorRepository(
    private val assetReader: SensorAssetReader,
    private val pacer: SamplePacer = SamplePacer { milliseconds -> delay(milliseconds) }
) : SensorRepository {
    override fun playback(datasetId: String): Flow<MotionContext> = flow {
        val dataset = SensorDatasets.require(datasetId)
        val rows = readRows(dataset)
        val classifier = MotionWindowClassifier()
        var globalSampleIndex = 0L

        while (true) {
            currentCoroutineContext().ensureActive()
            for (row in rows) {
                currentCoroutineContext().ensureActive()
                if (globalSampleIndex > 0) {
                    pacer.wait(SAMPLE_PERIOD_MILLISECONDS)
                }

                val unclassified = MotionContext(
                    datasetId = dataset.id,
                    sampleIndex = globalSampleIndex,
                    elapsedMilliseconds = globalSampleIndex * SAMPLE_PERIOD_MILLISECONDS,
                    accelerometer = row.accelerometer,
                    gyroscope = row.gyroscope,
                    accelerometerUnit = ACCELEROMETER_UNIT,
                    gyroscopeUnit = GYROSCOPE_UNIT,
                    activityLabel = row.activityLabel,
                    movementBand = MovementBand.UNKNOWN,
                    source = DataSource.DEMO
                )
                emit(unclassified.copy(movementBand = classifier.add(unclassified)))
                globalSampleIndex += 1
            }
        }
    }

    private fun readRows(dataset: SensorDataset): List<CsvRow> =
        assetReader.open(dataset.assetPath).buffered().use { reader ->
            val header = reader.readLine()
                ?: throw IllegalArgumentException("Dataset ${dataset.id} contains no CSV header.")
            require(header == CSV_HEADER) {
                "Dataset ${dataset.id} has an invalid CSV header."
            }

            val rows = mutableListOf<CsvRow>()
            var expectedElapsed = 0L
            var rowNumber = 1
            reader.forEachLine { line ->
                rowNumber += 1
                require(line.isNotBlank()) {
                    "Dataset ${dataset.id} row $rowNumber is blank."
                }
                val columns = line.split(',')
                require(columns.size == COLUMN_COUNT) {
                    "Dataset ${dataset.id} row $rowNumber must contain $COLUMN_COUNT columns."
                }

                val elapsed = columns[0].toLongOrNull()
                    ?: throw IllegalArgumentException("Dataset ${dataset.id} row $rowNumber has an invalid elapsed time.")
                require(elapsed == expectedElapsed) {
                    "Dataset ${dataset.id} row $rowNumber must advance in 20 ms steps; expected $expectedElapsed but found $elapsed."
                }

                rows += CsvRow(
                    accelerometer = listOf(
                        finiteDouble(columns[1], dataset.id, rowNumber),
                        finiteDouble(columns[2], dataset.id, rowNumber),
                        finiteDouble(columns[3], dataset.id, rowNumber)
                    ),
                    gyroscope = listOf(
                        finiteDouble(columns[4], dataset.id, rowNumber),
                        finiteDouble(columns[5], dataset.id, rowNumber),
                        finiteDouble(columns[6], dataset.id, rowNumber)
                    ),
                    activityLabel = columns[7].trim().also { label ->
                        require(label.isNotEmpty()) { "Dataset ${dataset.id} row $rowNumber has no activity label." }
                    }
                )
                expectedElapsed += SAMPLE_PERIOD_MILLISECONDS
            }

            require(rows.isNotEmpty()) { "Dataset ${dataset.id} contains no sensor rows." }
            rows
        }

    private fun finiteDouble(value: String, datasetId: String, rowNumber: Int): Double {
        val parsed = value.toDoubleOrNull()
            ?: throw IllegalArgumentException("Dataset $datasetId row $rowNumber contains an invalid number: $value")
        require(parsed.isFinite()) {
            "Dataset $datasetId row $rowNumber contains a non-finite number: $value"
        }
        return parsed
    }

    private data class CsvRow(
        val accelerometer: List<Double>,
        val gyroscope: List<Double>,
        val activityLabel: String
    )

    companion object {
        const val CSV_HEADER = "elapsed_ms,ax_g,ay_g,az_g,gx_rad_s,gy_rad_s,gz_rad_s,activity_label"
        const val SAMPLE_PERIOD_MILLISECONDS = 20L

        private const val COLUMN_COUNT = 8
        private const val ACCELEROMETER_UNIT = "g"
        private const val GYROSCOPE_UNIT = "rad/s"
    }
}
