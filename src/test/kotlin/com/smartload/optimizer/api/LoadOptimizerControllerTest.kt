package com.smartload.optimizer.api

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
class LoadOptimizerControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    // ------------------------------------------------------------------
    // Health check
    // ------------------------------------------------------------------

    @Test
    fun `GET healthz returns 200 ok`() {
        mockMvc.get("/healthz")
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("ok") }
            }
    }

    // ------------------------------------------------------------------
    // Happy path
    // ------------------------------------------------------------------

    @Test
    fun `sample input returns 200 with correct selection`() {
        mockMvc.post("/api/v1/load-optimizer/optimize") {
            contentType = MediaType.APPLICATION_JSON
            content = sampleRequest()
        }.andExpect {
            status { isOk() }
            jsonPath("$.truck_id") { value("truck-123") }
            jsonPath("$.total_payout_cents") { value(430_000) }
            jsonPath("$.total_weight_lbs") { value(30_000) }
            jsonPath("$.total_volume_cuft") { value(2_100) }
        }
    }

    @Test
    fun `empty orders list returns 200 with empty selection`() {
        mockMvc.post("/api/v1/load-optimizer/optimize") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"truck":{"id":"t-1","max_weight_lbs":44000,"max_volume_cuft":3000},"orders":[]}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.selected_order_ids") { isEmpty() }
            jsonPath("$.total_payout_cents") { value(0) }
        }
    }

    // ------------------------------------------------------------------
    // HTTP 400 – validation errors
    // ------------------------------------------------------------------

    @Test
    fun `missing truck field returns 400`() {
        mockMvc.post("/api/v1/load-optimizer/optimize") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"orders":[]}"""
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `zero truck weight returns 400`() {
        mockMvc.post("/api/v1/load-optimizer/optimize") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"truck":{"id":"t","max_weight_lbs":0,"max_volume_cuft":3000},"orders":[]}"""
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `negative payout_cents returns 400`() {
        mockMvc.post("/api/v1/load-optimizer/optimize") {
            contentType = MediaType.APPLICATION_JSON
            content = sampleRequest(overridePayout = -1)
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `delivery before pickup returns 400`() {
        mockMvc.post("/api/v1/load-optimizer/optimize") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "truck": {"id":"t","max_weight_lbs":44000,"max_volume_cuft":3000},
                  "orders": [{
                    "id":"ord-bad","payout_cents":100000,"weight_lbs":1000,"volume_cuft":100,
                    "origin":"Los Angeles, CA","destination":"Dallas, TX",
                    "pickup_date":"2025-12-10","delivery_date":"2025-12-05","is_hazmat":false
                  }]
                }
            """.trimIndent()
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `duplicate order IDs return 400`() {
        mockMvc.post("/api/v1/load-optimizer/optimize") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "truck": {"id":"t","max_weight_lbs":44000,"max_volume_cuft":3000},
                  "orders": [
                    {"id":"dup","payout_cents":100000,"weight_lbs":1000,"volume_cuft":100,
                     "origin":"LA","destination":"TX","pickup_date":"2025-12-01","delivery_date":"2025-12-10","is_hazmat":false},
                    {"id":"dup","payout_cents":200000,"weight_lbs":1000,"volume_cuft":100,
                     "origin":"LA","destination":"TX","pickup_date":"2025-12-01","delivery_date":"2025-12-10","is_hazmat":false}
                  ]
                }
            """.trimIndent()
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `malformed JSON returns 400`() {
        mockMvc.post("/api/v1/load-optimizer/optimize") {
            contentType = MediaType.APPLICATION_JSON
            content = """{ not valid json """
        }.andExpect { status { isBadRequest() } }
    }

    // ------------------------------------------------------------------
    // HTTP 413 – too many orders
    // ------------------------------------------------------------------

    @Test
    fun `23 orders returns 413`() {
        val orders = (1..23).joinToString(",") { i ->
            """{"id":"ord-$i","payout_cents":1000,"weight_lbs":100,"volume_cuft":10,
               "origin":"LA","destination":"TX","pickup_date":"2025-12-01",
               "delivery_date":"2025-12-10","is_hazmat":false}"""
        }
        mockMvc.post("/api/v1/load-optimizer/optimize") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"truck":{"id":"t","max_weight_lbs":999999,"max_volume_cuft":999999},"orders":[$orders]}"""
        }.andExpect { status { isPayloadTooLarge() } }
    }

    // ------------------------------------------------------------------
    // Response schema
    // ------------------------------------------------------------------

    @Test
    fun `response contains all required fields with correct types`() {
        mockMvc.post("/api/v1/load-optimizer/optimize") {
            contentType = MediaType.APPLICATION_JSON
            content = sampleRequest()
        }.andExpect {
            status { isOk() }
            jsonPath("$.truck_id") { exists() }
            jsonPath("$.selected_order_ids") { isArray() }
            jsonPath("$.total_payout_cents") { isNumber() }
            jsonPath("$.total_weight_lbs") { isNumber() }
            jsonPath("$.total_volume_cuft") { isNumber() }
            jsonPath("$.utilization_weight_percent") { isNumber() }
            jsonPath("$.utilization_volume_percent") { isNumber() }
        }
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private fun sampleRequest(overridePayout: Long? = null): String {
        val payout = overridePayout ?: 250_000L
        return """
            {
              "truck": {"id":"truck-123","max_weight_lbs":44000,"max_volume_cuft":3000},
              "orders": [
                {"id":"ord-001","payout_cents":$payout,"weight_lbs":18000,"volume_cuft":1200,
                 "origin":"Los Angeles, CA","destination":"Dallas, TX",
                 "pickup_date":"2025-12-05","delivery_date":"2025-12-09","is_hazmat":false},
                {"id":"ord-002","payout_cents":180000,"weight_lbs":12000,"volume_cuft":900,
                 "origin":"Los Angeles, CA","destination":"Dallas, TX",
                 "pickup_date":"2025-12-04","delivery_date":"2025-12-10","is_hazmat":false},
                {"id":"ord-003","payout_cents":320000,"weight_lbs":30000,"volume_cuft":1800,
                 "origin":"Los Angeles, CA","destination":"Dallas, TX",
                 "pickup_date":"2025-12-06","delivery_date":"2025-12-08","is_hazmat":true}
              ]
            }
        """.trimIndent()
    }
}
