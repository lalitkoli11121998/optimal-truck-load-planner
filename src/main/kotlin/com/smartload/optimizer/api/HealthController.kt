package com.smartload.optimizer.api

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController {

    @GetMapping("/healthz")
    fun health() = mapOf("status" to "ok")
}
