# SmartLoad Optimization API

## How to run

```bash
git clone https://github.com/lalitkoli11121998/optimal-truck-load-planner.git
cd optimal-truck-load-planner
docker compose up --build
# → Service will be available at http://localhost:8080
```

## Health check

```bash
curl http://localhost:8080/actuator/health
```

## Example request

```bash
curl -X POST http://localhost:8080/api/v1/load-optimizer/optimize \
  -H "Content-Type: application/json" \
  -d @sample-request.json
```

### Expected response

```json
{
  "truck_id": "truck-123",
  "selected_order_ids": ["ord-001", "ord-002"],
  "total_payout_cents": 430000,
  "total_weight_lbs": 30000,
  "total_volume_cuft": 2100,
  "utilization_weight_percent": 68.18,
  "utilization_volume_percent": 70.0
}
```

---

## API reference

### `POST /api/v1/load-optimizer/optimize`

| Status | Meaning |
|--------|---------|
| `200` | Optimal selection returned (empty list if no orders fit) |
| `400` | Invalid request — bad fields, duplicate IDs, delivery before pickup, malformed JSON |
| `413` | Order count exceeds maximum of 22 |

---

## Algorithm & design decisions

**Stack:** Kotlin + Spring Boot 3 + Maven

### Optimisation — Bitmask DP O(2ⁿ)

Enumerates all 2ⁿ subsets (n ≤ 22). Each subset is built in **O(1) amortised** time by extending the sub-mask that lacks its lowest set bit (`Integer.numberOfTrailingZeros`). Uses primitive `IntArray`/`LongArray` — no boxing overhead over the 4 M entry tables. Handles n = 22 well under 800 ms.

### Compatibility rules

| Rule | Detail |
|------|--------|
| Same origin | Case-insensitive, trimmed |
| Same destination | Case-insensitive, trimmed |
| Hazmat isolation | Hazmat orders **cannot** share a load with non-hazmat cargo |

### Money

All monetary values are `Long` cents — never `Float` or `Double`.

### Caching / memoisation

Results are cached with **Caffeine** (`@Cacheable("optimize-results")`), keyed on the full request via Kotlin data-class `hashCode`/`equals`. Repeated identical payloads skip the O(2ⁿ) computation entirely. Cache is bounded to 1 000 entries and expires after 1 hour.

---

## Running tests

```bash
mvn test
```

---

## Project structure

```
src/main/kotlin/com/smartload/optimizer/
├── SmartLoadApplication.kt          Entry point + @EnableCaching
├── api/
│   ├── LoadOptimizerController.kt   POST /api/v1/load-optimizer/optimize
│   └── HealthController.kt          GET /healthz
├── model/
│   ├── OptimizeRequest.kt           Truck · Order · OptimizeRequest
│   └── OptimizeResponse.kt
├── service/
│   └── OptimizerService.kt          Bitmask DP + Caffeine cache
└── exception/
    ├── Exceptions.kt
    └── GlobalExceptionHandler.kt    400 / 413 error mapping

src/test/kotlin/com/smartload/optimizer/
├── service/OptimizerServiceTest.kt
└── api/LoadOptimizerControllerTest.kt
```
