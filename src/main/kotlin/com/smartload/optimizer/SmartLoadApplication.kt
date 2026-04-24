package com.smartload.optimizer

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching

@SpringBootApplication
@EnableCaching
class SmartLoadApplication

fun main(args: Array<String>) {
    runApplication<SmartLoadApplication>(*args)
}
