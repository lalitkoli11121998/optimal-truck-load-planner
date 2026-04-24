package com.smartload.optimizer.model

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import java.time.LocalDate

data class Truck(
    @field:NotBlank val id: String,
    @field:Positive val maxWeightLbs: Int,
    @field:Positive val maxVolumeCuft: Int,
)

data class Order(
    @field:NotBlank val id: String,
    /** Always stored as integer cents – never as float/double. */
    @field:PositiveOrZero val payoutCents: Long,
    @field:PositiveOrZero val weightLbs: Int,
    @field:PositiveOrZero val volumeCuft: Int,
    @field:NotBlank val origin: String,
    @field:NotBlank val destination: String,
    val pickupDate: LocalDate,
    val deliveryDate: LocalDate,
    val isHazmat: Boolean,
)

data class OptimizeRequest(
    @field:Valid val truck: Truck,
    @field:Valid val orders: List<Order> = emptyList(),
)
