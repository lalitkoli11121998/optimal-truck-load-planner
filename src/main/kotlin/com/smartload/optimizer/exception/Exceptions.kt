package com.smartload.optimizer.exception

/** Thrown when business-logic validation fails (maps to HTTP 400). */
class ValidationException(message: String) : RuntimeException(message)
