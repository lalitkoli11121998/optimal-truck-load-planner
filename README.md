# SmartLoad Optimization API

A stateless REST service built with **Spring Boot + Kotlin + Maven** that finds the revenue-maximising combination of freight orders for a truck, respecting weight, volume, hazmat, and route constraints.

## How to run

```bash
git clone <your-repo>
cd optimal-truck-load-planner
docker compose up --build
# Service will be available at http://localhost:8080
```

Docker builds the image from source and starts it on port 8080.  
Alternatively, skip the build and just pull the pre-built image:

```bash
docker compose up          # pulls lalit1106/testrepo:latest from Docker Hub
```

---

## CI/CD — automatic build & push

Every push to `main` / `master` triggers a GitHub Actions workflow that builds the image and pushes it to `lalit1106/testrepo:latest`.

**One-time setup** — add these two secrets in your GitHub repo → Settings → Secrets → Actions:

| Secret name | Value |
|---|---|
| `DOCKERHUB_USERNAME` | `lalit1106` |
| `DOCKERHUB_TOKEN` | your Docker Hub access token |

After that, every `git push` auto-publishes a fresh image.

---

## Manual build & push (one-off)

```bash
docker build -t lalit1106/testrepo:latest .
docker login
docker push lalit1106/testrepo:latest
```

---

## Health check

```bash
curl http://localhost:8080/healthz
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
| 200 | Optimal selection returned (may be empty if no orders fit within constraints) |
| 400 | Invalid request — bad field values, duplicate order IDs, delivery before pickup, malformed JSON |
| 413 | Order count exceeds maximum of 22 |

---

## Algorithm

**Bitmask enumeration** over all 2ⁿ subsets (n ≤ 22).

Each subset is computed in **O(1) amortised** time by extending the sub-mask that lacks its lowest set bit (`Integer.numberOfTrailingZeros`), giving an overall **O(2ⁿ)** pass using primitive `IntArray`/`LongArray` — no boxing overhead. For n = 22 this is ≈ 4 M iterations, comfortably under 800 ms on any modern JVM.

**Compatibility rules** (orders can only be combined when **all** match):

| Rule | Detail |
|------|--------|
| Same origin | Case-insensitive, whitespace-trimmed |
| Same destination | Case-insensitive, whitespace-trimmed |
| Same hazmat status | Hazmat cargo **must not** share a load with non-hazmat cargo |

**Money** is always `Long` cents — never `Float` or `Double`.

---

## Running tests locally

```bash
mvn test
```

---

## Project structure

```
src/
└── main/kotlin/com/smartload/optimizer/
    ├── SmartLoadApplication.kt
    ├── api/
    │   ├── LoadOptimizerController.kt   POST /api/v1/load-optimizer/optimize
    │   └── HealthController.kt          GET /healthz
    ├── model/
    │   ├── OptimizeRequest.kt
    │   └── OptimizeResponse.kt
    ├── service/
    │   └── OptimizerService.kt          Bitmask DP core algorithm
    └── exception/
        ├── Exceptions.kt
        └── GlobalExceptionHandler.kt
src/
└── test/kotlin/com/smartload/optimizer/
    ├── service/OptimizerServiceTest.kt
    └── api/LoadOptimizerControllerTest.kt
pom.xml
Dockerfile                               Multi-stage (Maven build → JRE runtime)
docker-compose.yml                       Pulls lalit1106/optimal-truck-load-planner:latest
sample-request.json
```
