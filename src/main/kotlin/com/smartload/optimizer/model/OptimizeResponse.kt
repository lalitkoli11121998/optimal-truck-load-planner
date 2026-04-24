package com.smartload.optimizer.model

data class OptimizeResponse(
    val truckId: String,
    val selectedOrderIds: List<String>,
    /** Total revenue in integer cents – never float. */
    val totalPayoutCents: Long,
    val totalWeightLbs: Int,
    val totalVolumeCuft: Int,
    val utilizationWeightPercent: Double,
    val utilizationVolumePercent: Double,
)
