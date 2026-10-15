package com.example.movesmart.sensor

import android.content.Context
import java.io.Reader

data class SensorDataset(
    val id: String,
    val assetPath: String
)

object SensorDatasets {
    const val UCI_HAPT_SUBSET_ID = "uci-hapt-exp01-user01-subset-v1"

    private val supported = mapOf(
        UCI_HAPT_SUBSET_ID to SensorDataset(
            id = UCI_HAPT_SUBSET_ID,
            assetPath = "sensor/uci_hapt_exp01_user01_subset.csv"
        )
    )

    fun require(datasetId: String): SensorDataset =
        supported[datasetId]
            ?: throw IllegalArgumentException("Unknown sensor dataset: $datasetId")
}

fun interface SensorAssetReader {
    fun open(assetPath: String): Reader
}

fun interface SamplePacer {
    suspend fun wait(milliseconds: Long)
}

class AndroidSensorAssetReader(context: Context) : SensorAssetReader {
    private val assets = context.applicationContext.assets

    override fun open(assetPath: String): Reader =
        assets.open(assetPath).reader(Charsets.UTF_8)
}
