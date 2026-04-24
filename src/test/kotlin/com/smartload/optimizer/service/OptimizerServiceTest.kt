package com.smartload.optimizer.service

import com.smartload.optimizer.exception.ValidationException
import com.smartload.optimizer.model.OptimizeRequest
import com.smartload.optimizer.model.Order
import com.smartload.optimizer.model.Truck
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import java.time.LocalDate

class OptimizerServiceTest {

    private val service = OptimizerService()

    private val defaultTruck = Truck(
        id = "truck-123",
        maxWeightLbs = 44_000,
        maxVolumeCuft = 3_000,
    )

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun order(
        id: String,
        payoutCents: Long,
        weightLbs: Int,
        volumeCuft: Int,
        origin: String = "Los Angeles, CA",
        destination: String = "Dallas, TX",
        isHazmat: Boolean = false,
        pickup: LocalDate = LocalDate.of(2025, 12, 1),
        delivery: LocalDate = LocalDate.of(2025, 12, 10),
    ) = Order(id, payoutCents, weightLbs, volumeCuft, origin, destination, pickup, delivery, isHazmat)

    private fun optimize(truck: Truck = defaultTruck, orders: List<Order>) =
        service.optimize(OptimizeRequest(truck, orders))

    // ------------------------------------------------------------------
    // Core correctness
    // ------------------------------------------------------------------

    @Test
    fun `sample input selects ord-001 and ord-002`() {
        val orders = listOf(
            order("ord-001", 250_000L, 18_000, 1_200),
            order("ord-002", 180_000L, 12_000,   900),
            order("ord-003", 320_000L, 30_000, 1_800, isHazmat = true),
        )
        val result = optimize(orders = orders)

        assertThat(result.selectedOrderIds).containsExactlyInAnyOrder("ord-001", "ord-002")
        assertThat(result.totalPayoutCents).isEqualTo(430_000L)
        assertThat(result.totalWeightLbs).isEqualTo(30_000)
        assertThat(result.totalVolumeCuft).isEqualTo(2_100)
        assertThat(result.utilizationWeightPercent).isCloseTo(68.18, within(0.01))
        assertThat(result.utilizationVolumePercent).isCloseTo(70.0, within(0.01))
    }

    @Test
    fun `empty order list returns empty result`() {
        val result = optimize(orders = emptyList())

        assertThat(result.selectedOrderIds).isEmpty()
        assertThat(result.totalPayoutCents).isZero()
    }

    @Test
    fun `weight constraint is never exceeded`() {
        val truck = Truck("t", 10_000, 99_999)
        val orders = listOf(
            order("a", 100_000L, 9_000, 100),
            order("b", 200_000L, 9_000, 100),
        )
        val result = optimize(truck, orders)

        // Together they exceed weight → pick only the higher-payout one
        assertThat(result.selectedOrderIds).containsExactly("b")
        assertThat(result.totalWeightLbs).isLessThanOrEqualTo(10_000)
    }

    @Test
    fun `volume constraint is never exceeded`() {
        val truck = Truck("t", 99_999, 500)
        val orders = listOf(
            order("a", 100_000L, 100, 400),
            order("b",  50_000L, 100, 400),
        )
        val result = optimize(truck, orders)

        assertThat(result.selectedOrderIds).containsExactly("a")
        assertThat(result.totalVolumeCuft).isLessThanOrEqualTo(500)
    }

    @Test
    fun `hazmat orders are isolated from non-hazmat orders`() {
        val orders = listOf(
            order("nh-1", 150_000L, 5_000, 300, isHazmat = false),
            order("h-1",  130_000L, 5_000, 300, isHazmat = true),
        )
        val result = optimize(orders = orders)

        // Must not mix: higher-payout non-hazmat group wins
        assertThat(result.selectedOrderIds).containsExactly("nh-1")
        assertThat(result.totalPayoutCents).isEqualTo(150_000L)
    }

    @Test
    fun `orders on different routes are never combined`() {
        val orders = listOf(
            order("la-dal", 100_000L, 5_000, 200, destination = "Dallas, TX"),
            order("la-chi", 200_000L, 5_000, 200, destination = "Chicago, IL"),
        )
        val result = optimize(orders = orders)

        // Only one route group can win — the higher-payout one
        assertThat(result.selectedOrderIds).containsExactly("la-chi")
    }

    @Test
    fun `no feasible order returns empty result`() {
        val truck = Truck("t", 100, 100)
        val orders = listOf(order("heavy", 999_999L, 50_000, 50_000))
        val result = optimize(truck, orders)

        assertThat(result.selectedOrderIds).isEmpty()
        assertThat(result.totalPayoutCents).isZero()
    }

    @Test
    fun `all orders fit returns all selected`() {
        val truck = Truck("t", 999_999, 999_999)
        val orders = listOf(
            order("a", 10_000L, 1_000, 100),
            order("b", 20_000L, 1_000, 100),
            order("c", 30_000L, 1_000, 100),
        )
        val result = optimize(truck, orders)

        assertThat(result.selectedOrderIds).containsExactlyInAnyOrder("a", "b", "c")
        assertThat(result.totalPayoutCents).isEqualTo(60_000L)
    }

    @Test
    fun `utilization percentages are computed correctly`() {
        val truck = Truck("t", 10_000, 1_000)
        val orders = listOf(order("u", 1L, 2_500, 250))
        val result = optimize(truck, orders)

        assertThat(result.utilizationWeightPercent).isCloseTo(25.0, within(0.01))
        assertThat(result.utilizationVolumePercent).isCloseTo(25.0, within(0.01))
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    @Test
    fun `duplicate order IDs throw ValidationException`() {
        val orders = listOf(
            order("dup", 100L, 1_000, 100),
            order("dup", 200L, 1_000, 100),
        )
        assertThatThrownBy { optimize(orders = orders) }
            .isInstanceOf(ValidationException::class.java)
            .hasMessageContaining("unique")
    }

    @Test
    fun `delivery before pickup throws ValidationException`() {
        val orders = listOf(
            order(
                "bad-date", 100L, 1_000, 100,
                pickup = LocalDate.of(2025, 12, 10),
                delivery = LocalDate.of(2025, 12, 5),
            )
        )
        assertThatThrownBy { optimize(orders = orders) }
            .isInstanceOf(ValidationException::class.java)
            .hasMessageContaining("delivery_date")
    }
}
