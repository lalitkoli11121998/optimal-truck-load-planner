package com.smartload.optimizer.api

import com.smartload.optimizer.model.OptimizeRequest
import com.smartload.optimizer.model.OptimizeResponse
import com.smartload.optimizer.service.OptimizerService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Bitmask DP is tractable for n ≤ 22 (2²² ≈ 4 M states). Beyond that we surface a clear error. */
private const val MAX_ORDERS = 22

@RestController
@RequestMapping("/api/v1/load-optimizer")
class LoadOptimizerController(private val optimizerService: OptimizerService) {

    @PostMapping("/optimize")
    fun optimize(@Valid @RequestBody request: OptimizeRequest): ResponseEntity<Any> {
        if (request.orders.size > MAX_ORDERS) {
            return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(mapOf("detail" to "Order count ${request.orders.size} exceeds maximum of $MAX_ORDERS"))
        }
        val result: OptimizeResponse = optimizerService.optimize(request)
        return ResponseEntity.ok(result)
    }
}
