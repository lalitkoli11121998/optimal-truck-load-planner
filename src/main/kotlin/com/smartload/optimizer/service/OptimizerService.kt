package com.smartload.optimizer.service

import com.smartload.optimizer.exception.ValidationException
import com.smartload.optimizer.model.OptimizeRequest
import com.smartload.optimizer.model.OptimizeResponse
import com.smartload.optimizer.model.Order
import com.smartload.optimizer.model.Truck
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Load optimisation service.
 *
 * Algorithm: bitmask enumeration over all 2ⁿ subsets (n ≤ 22).
 *
 * Each subset is computed in O(1) amortised time by extending the sub-mask
 * that lacks its lowest set bit, giving an overall O(2ⁿ) pass.
 * For n = 22 this is ≈ 4 M iterations — comfortably under 800 ms on any
 * modern JVM.
 *
 * Compatibility rules (orders can only be combined when ALL match):
 *   • Same origin        (case-insensitive, trimmed)
 *   • Same destination   (case-insensitive, trimmed)
 *   • Same hazmat status — hazmat cargo must not share a load with non-hazmat
 */
@Service
class OptimizerService {

    fun optimize(request: OptimizeRequest): OptimizeResponse {
        val truck = request.truck
        val orders = request.orders

        validateOrders(orders)

        if (orders.isEmpty()) return emptyResponse(truck)

        // Partition into compatibility groups then find the best group.
        val best = orders
            .groupBy { it.compatibilityKey() }
            .values
            .map { group -> bitmaskDp(group, truck.maxWeightLbs, truck.maxVolumeCuft) }
            .maxByOrNull { it.totalPayoutCents }
            ?: return emptyResponse(truck)

        if (best.selectedIds.isEmpty()) return emptyResponse(truck)

        return OptimizeResponse(
            truckId = truck.id,
            selectedOrderIds = best.selectedIds,
            totalPayoutCents = best.totalPayoutCents,
            totalWeightLbs = best.totalWeightLbs,
            totalVolumeCuft = best.totalVolumeCuft,
            utilizationWeightPercent = pct(best.totalWeightLbs, truck.maxWeightLbs),
            utilizationVolumePercent = pct(best.totalVolumeCuft, truck.maxVolumeCuft),
        )
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    private fun validateOrders(orders: List<Order>) {
        val ids = orders.map { it.id }
        if (ids.size != ids.toSet().size) {
            throw ValidationException("Order IDs must be unique")
        }
        orders.forEach { order ->
            if (order.deliveryDate < order.pickupDate) {
                throw ValidationException(
                    "Order '${order.id}': delivery_date (${order.deliveryDate}) " +
                        "must be >= pickup_date (${order.pickupDate})"
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // Bitmask DP
    // -------------------------------------------------------------------------

    private data class GroupResult(
        val selectedIds: List<String>,
        val totalPayoutCents: Long,
        val totalWeightLbs: Int,
        val totalVolumeCuft: Int,
    )

    /**
     * Enumerates all 2ⁿ subsets of [orders] to find the maximum-payout
     * selection that stays within [maxWeight] and [maxVolume].
     *
     * Uses primitive arrays (IntArray / LongArray) to avoid boxing overhead
     * over the 2^22 entry tables.
     */
    private fun bitmaskDp(orders: List<Order>, maxWeight: Int, maxVolume: Int): GroupResult {
        val n = orders.size
        val totalMasks = 1 shl n

        val weights = IntArray(totalMasks)
        val volumes = IntArray(totalMasks)
        val payouts = LongArray(totalMasks)

        var bestMask = 0
        var bestPayout = 0L

        for (mask in 1 until totalMasks) {
            // Index of the lowest set bit — computed with a single hardware instruction.
            val lsb = mask and (-mask)
            val i = Integer.numberOfTrailingZeros(lsb)
            val prev = mask xor lsb

            val w = weights[prev] + orders[i].weightLbs
            val v = volumes[prev] + orders[i].volumeCuft
            val p = payouts[prev] + orders[i].payoutCents

            weights[mask] = w
            volumes[mask] = v
            payouts[mask] = p

            if (w <= maxWeight && v <= maxVolume && p > bestPayout) {
                bestPayout = p
                bestMask = mask
            }
        }

        val selectedIds = (0 until n)
            .filter { bestMask and (1 shl it) != 0 }
            .map { orders[it].id }

        return GroupResult(
            selectedIds = selectedIds,
            totalPayoutCents = payouts[bestMask],
            totalWeightLbs = weights[bestMask],
            totalVolumeCuft = volumes[bestMask],
        )
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun Order.compatibilityKey() =
        Triple(origin.trim().lowercase(), destination.trim().lowercase(), isHazmat)

    private fun pct(part: Int, total: Int): Double =
        BigDecimal(part.toDouble() / total * 100)
            .setScale(2, RoundingMode.HALF_UP)
            .toDouble()

    private fun emptyResponse(truck: Truck) = OptimizeResponse(
        truckId = truck.id,
        selectedOrderIds = emptyList(),
        totalPayoutCents = 0L,
        totalWeightLbs = 0,
        totalVolumeCuft = 0,
        utilizationWeightPercent = 0.0,
        utilizationVolumePercent = 0.0,
    )
}
