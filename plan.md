# URL Shortener Application — Implementation & Deployment Plan

## Problem Statement
Build a scalable URL shortener with a React frontend and Java Spring Boot backend microservices, deployed on Azure Kubernetes Service (AKS). The system uses Base62 hashing (idempotent — same URL always produces the same 6-char code) with Azure Table Storage for persistence.

## Architecture Overview

```
Internet (Browser Users + Script/API Users)
    │
    ▼
┌──────────────────────────────────────────────────────────────────┐
│  Azure VNet                                                      │
│                                                                  │
│  ┌────────────────────────────────┐  ┌────────────────────────┐ │
│  │  Frontend Subnet                │  │ Backend Subnet         │ │
│  │  NSG: Allow 80/443 inbound     │  │ NSG: Allow from        │ │
│  │       Deny all other inbound   │  │      frontend-subnet   │ │
│  │                                 │  │      + API clients     │ │
│  │  NGINX Ingress → React SPA     │  │      Deny all other    │ │
│  │  (UI users)                     │  │                        │ │
│  │                                 │  │  Spring Cloud GW       │ │
│  │  NGINX Ingress → Gateway       │  │  (API key validation)  │ │
│  │  (script/API users: /api/*)    │  │        │               │ │
│  │                                 │  │        ▼               │ │
│  └────────────────────────────────┘  │  URL Shortener Svc     │ │
│                                      │  (+ future svcs)       │ │
│                                      │        │               │ │
│                                      │   ┌────┴────┐          │ │
│                                      │   ▼         ▼          │ │
│                                      │ Redis    Azure Table   │ │
│                                      │ Cache    Storage       │ │
│                                      │ (fast)   (persistent)  │ │
│                                      │          (Priv.Endpt)  │ │
│                                      └────────────────────────┘ │
└──────────────────────────────────────────────────────────────────┘
```

### NSG (Network Security Group) Rules

| NSG | Rule | Direction | Priority | Source | Destination | Port | Action |
|-----|------|-----------|----------|--------|-------------|------|--------|
| **frontend-nsg** | Allow-HTTP | Inbound | 100 | Internet | Frontend Subnet | 80 | Allow |
| **frontend-nsg** | Allow-HTTPS | Inbound | 110 | Internet | Frontend Subnet | 443 | Allow |
| **frontend-nsg** | Deny-All-Inbound | Inbound | 4096 | Any | Frontend Subnet | Any | Deny |
| **backend-nsg** | Allow-From-Frontend | Inbound | 100 | Frontend Subnet | Backend Subnet | 8080 | Allow |
| **backend-nsg** | Allow-AKS-Internal | Inbound | 200 | VNet | Backend Subnet | 10250,443 | Allow |
| **backend-nsg** | Deny-All-Inbound | Inbound | 4096 | Any | Backend Subnet | Any | Deny |
| **backend-nsg** | Allow-TableStorage | Outbound | 100 | Backend Subnet | Storage.EastUS | 443 | Allow |

## Tech Stack
| Layer | Technology |
|-------|-----------|
| Frontend | React (Vite), NGINX |
| API Gateway | Spring Cloud Gateway |
| Backend Service | Java 17+, Spring Boot 3.x |
| Cache | Redis (Azure Cache for Redis or self-hosted on AKS) |
| Storage | Azure Table Storage |
| Containerization | Docker, Docker Hub |
| Orchestration | Azure Kubernetes Service (AKS) |
| IaC | Terraform |
| CI/CD | GitHub Actions |
| Networking | Azure VNet with 2 subnets |

## Monorepo Structure

```
url-shortener/
├── frontend/                    # React app (Vite)
│   ├── src/
│   ├── public/
│   ├── Dockerfile
│   ├── nginx.conf
│   └── package.json
├── backend/
│   ├── gateway/                 # Spring Cloud Gateway
│   │   ├── src/
│   │   ├── Dockerfile
│   │   └── pom.xml
│   ├── url-service/             # URL shortener microservice
│   │   ├── src/
│   │   ├── Dockerfile
│   │   └── pom.xml
│   └── pom.xml                  # Parent POM
├── infra/                       # Terraform IaC
│   ├── main.tf
│   ├── variables.tf
│   ├── outputs.tf
│   ├── modules/
│   │   ├── vnet/
│   │   ├── aks/
│   │   └── storage/
│   └── terraform.tfvars
├── k8s/                         # Kubernetes manifests
│   ├── frontend/
│   │   ├── deployment.yaml
│   │   ├── service.yaml
│   │   └── ingress.yaml
│   ├── gateway/
│   │   ├── deployment.yaml
│   │   └── service.yaml
│   ├── url-service/
│   │   ├── deployment.yaml
│   │   └── service.yaml
│   └── namespaces.yaml
├── .github/
│   └── workflows/
│       ├── ci.yaml              # Build & test on PR
│       └── cd.yaml              # Deploy to AKS on merge to main
├── docker-compose.yaml          # Local development
└── README.md
```

## User Interaction Flows

### Flow 1: Shorten a URL

```
┌─────────┐         ┌──────────┐        ┌─────────────┐       ┌──────────────┐       ┌───────────────┐
│  User   │         │  React   │        │   NGINX     │       │ Spring Cloud │       │  URL Service  │
│ Browser │         │ Frontend │        │  Ingress    │       │   Gateway    │       │               │
└────┬────┘         └────┬─────┘        └──────┬──────┘       └──────┬───────┘       └───────┬───────┘
     │                   │                     │                     │                       │
     │  1. Enter long URL│                     │                     │                       │
     │  & click "Shorten"│                     │                     │                       │
     │──────────────────>│                     │                     │                       │
     │                   │                     │                     │                       │
     │                   │  2. POST /api/shorten                     │                       │
     │                   │  {url: "https://..."}                     │                       │
     │                   │────────────────────>│                     │                       │
     │                   │                     │                     │                       │
     │                   │                     │  3. Route to gateway │                       │
     │                   │                     │────────────────────>│                       │
     │                   │                     │                     │                       │
     │                   │                     │                     │  4. Forward to service │
     │                   │                     │                     │─────────────────────>│
     │                   │                     │                     │                       │
     │                   │                     │                     │                       │  5. Hash URL
     │                   │                     │                     │                       │  → Base62 code
     │                   │                     │                     │                       │  → Store in
     │                   │                     │                     │                       │    Azure Table
     │                   │                     │                     │                       │
     │                   │                     │                     │  6. Response           │
     │                   │                     │                     │<─────────────────────│
     │                   │                     │                     │                       │
     │                   │                     │  7. Response         │                       │
     │                   │                     │<────────────────────│                       │
     │                   │                     │                     │                       │
     │                   │  8. JSON Response    │                     │                       │
     │                   │<────────────────────│                     │                       │
     │                   │                     │                     │                       │
     │  9. Display short │                     │                     │                       │
     │  URL + copy btn   │                     │                     │                       │
     │<──────────────────│                     │                     │                       │
     │                   │                     │                     │                       │
```

### Flow 2: Redirect from Short URL

```
┌─────────┐        ┌──────────┐       ┌─────────────┐       ┌──────────────┐       ┌───────────────┐
│  User   │        │  NGINX   │       │ Spring Cloud │       │  URL Service │       │ Azure Table   │
│ Browser │        │ Ingress  │       │   Gateway    │       │              │       │   Storage     │
└────┬────┘        └────┬─────┘       └──────┬───────┘       └──────┬───────┘       └───────┬───────┘
     │                  │                    │                      │                       │
     │ 1. Visit         │                    │                      │                       │
     │ ourdomain/abc123 │                    │                      │                       │
     │─────────────────>│                    │                      │                       │
     │                  │                    │                      │                       │
     │                  │ 2. Route GET       │                      │                       │
     │                  │ /api/r/abc123      │                      │                       │
     │                  │───────────────────>│                      │                       │
     │                  │                    │                      │                       │
     │                  │                    │ 3. Forward            │                       │
     │                  │                    │─────────────────────>│                       │
     │                  │                    │                      │                       │
     │                  │                    │                      │ 4. Lookup code         │
     │                  │                    │                      │ PK="ab", RK="abc123"  │
     │                  │                    │                      │─────────────────────>│
     │                  │                    │                      │                       │
     │                  │                    │                      │ 5. Return long URL     │
     │                  │                    │                      │<─────────────────────│
     │                  │                    │                      │                       │
     │                  │                    │ 6. 302 Redirect       │                       │
     │                  │                    │ Location: long URL    │                       │
     │                  │                    │<─────────────────────│                       │
     │                  │                    │                      │                       │
     │                  │ 7. 302 Redirect    │                      │                       │
     │                  │<───────────────────│                      │                       │
     │                  │                    │                      │                       │
     │ 8. Browser auto  │                    │                      │                       │
     │ redirects to     │                    │                      │                       │
     │ long URL         │                    │                      │                       │
     │<─────────────────│                    │                      │                       │
```

### Flow 3: Invalid/Expired Short URL

```
User visits ourdomain/xyz999
  → Gateway routes to URL Service
  → Service looks up "xyz999" in Azure Table → NOT FOUND
  → Returns 404 response
  → Frontend shows "URL not found" error page
```

---

## API Specification

### Base URL
- **Production**: `https://ourdomain/api`
- **Local Dev**: `http://localhost:8080/api`

---

### `POST /api/shorten` — Create Short URL

**Description**: Accepts a long URL and returns a shortened version using Base62 encoding.

**Request**:
```http
POST /api/shorten HTTP/1.1
Content-Type: application/json

{
  "url": "https://www.example.com/very/long/path?query=param&another=value"
}
```

**Request Body Schema**:
| Field | Type   | Required | Validation |
|-------|--------|----------|------------|
| url   | string | Yes      | Must be a valid URL (http/https), max 2048 chars |

**Success Response** (`200 OK`):
```json
{
  "code": "abc123",
  "shortUrl": "https://ourdomain/abc123",
  "originalUrl": "https://www.example.com/very/long/path?query=param&another=value",
  "createdAt": "2026-06-14T12:00:00Z",
  "isNew": true
}
```

**Response Schema**:
| Field       | Type    | Description |
|-------------|---------|-------------|
| code        | string  | The 6-character Base62 code |
| shortUrl    | string  | Full short URL ready to share |
| originalUrl | string  | The original long URL |
| createdAt   | string  | ISO 8601 timestamp |
| isNew       | boolean | `true` if newly created, `false` if URL was already shortened |

**Error Responses**:

`400 Bad Request` — Invalid URL:
```json
{
  "error": "INVALID_URL",
  "message": "The provided URL is not valid. Must be a valid HTTP or HTTPS URL.",
  "timestamp": "2026-06-14T12:00:00Z"
}
```

`400 Bad Request` — URL too long:
```json
{
  "error": "URL_TOO_LONG",
  "message": "URL exceeds maximum length of 2048 characters.",
  "timestamp": "2026-06-14T12:00:00Z"
}
```

`429 Too Many Requests` — Rate limited:
```json
{
  "error": "RATE_LIMITED",
  "message": "Too many requests. Please try again later.",
  "retryAfter": 60,
  "timestamp": "2026-06-14T12:00:00Z"
}
```

`500 Internal Server Error`:
```json
{
  "error": "INTERNAL_ERROR",
  "message": "An unexpected error occurred. Please try again.",
  "timestamp": "2026-06-14T12:00:00Z"
}
```

---

### `GET /api/r/{code}` — Redirect to Original URL

**Description**: Looks up the 6-char code and returns a 302 redirect to the original long URL.

**Request**:
```http
GET /api/r/abc123 HTTP/1.1
```

**Path Parameters**:
| Parameter | Type   | Description |
|-----------|--------|-------------|
| code      | string | 6-character Base62 code (a-z, A-Z, 0-9) |

**Success Response** (`302 Found`):
```http
HTTP/1.1 302 Found
Location: https://www.example.com/very/long/path?query=param&another=value
Cache-Control: no-cache
```

**Error Responses**:

`404 Not Found`:
```json
{
  "error": "CODE_NOT_FOUND",
  "message": "Short URL 'abc123' does not exist.",
  "timestamp": "2026-06-14T12:00:00Z"
}
```

`400 Bad Request` — Invalid code format:
```json
{
  "error": "INVALID_CODE",
  "message": "Code must be exactly 6 alphanumeric characters.",
  "timestamp": "2026-06-14T12:00:00Z"
}
```

---

### `GET /api/health` — Health Check

**Request**:
```http
GET /api/health HTTP/1.1
```

**Response** (`200 OK`):
```json
{
  "status": "UP",
  "services": {
    "urlService": "UP",
    "tableStorage": "UP"
  },
  "timestamp": "2026-06-14T12:00:00Z"
}
```

---

### Gateway Routes Configuration

| Route Pattern | Target Service | Method |
|---------------|---------------|--------|
| `/api/shorten` | url-service | POST |
| `/api/r/**` | url-service | GET |
| `/api/health` | url-service | GET |

---

## Programmatic / Script Access (API-First Design)

Users who want to integrate with the URL shortener from scripts, CI/CD pipelines, or other applications can call the API directly without needing the React frontend.

### Authentication for API Users

The Gateway enforces API key authentication for programmatic access:

| Header | Required | Description |
|--------|----------|-------------|
| `X-API-Key` | Yes (for script users) | API key issued to registered developers |

### How We Differentiate UI Users vs API Clients

The Spring Cloud Gateway uses a **GatewayFilter chain** to distinguish between the two:

```
Incoming Request
      │
      ▼
┌─────────────────────────────────┐
│ 1. Check Origin/Referer Header  │
│    Origin == "https://ourdomain" │──── YES ──→ UI User (skip API key check)
│    OR Referer starts with       │              └→ Apply UI rate limit (generous)
│    "https://ourdomain"          │
└──────────────┬──────────────────┘
               │ NO
               ▼
┌─────────────────────────────────┐
│ 2. Check X-API-Key Header       │
│    Header present?              │──── NO ──→ 401 Unauthorized
└──────────────┬──────────────────┘            {"error": "API_KEY_REQUIRED"}
               │ YES
               ▼
┌─────────────────────────────────┐
│ 3. Validate API Key             │
│    Lookup in ApiKeys table      │──── INVALID ──→ 403 Forbidden
│    Check: active, not expired   │                 {"error": "INVALID_API_KEY"}
└──────────────┬──────────────────┘
               │ VALID
               ▼
┌─────────────────────────────────┐
│ 4. Apply Tier Rate Limit        │
│    Read tier from ApiKeys table │──── EXCEEDED ──→ 429 Too Many Requests
│    (Free/Standard/Premium)      │
└──────────────┬──────────────────┘
               │ OK
               ▼
        Route to backend service
```

**Key implementation details:**

| Aspect | UI Users (Browser) | API Clients (Script) |
|--------|-------------------|---------------------|
| Identification | `Origin` header matches our domain | `X-API-Key` header present |
| Authentication | None (CORS protects from cross-origin abuse) | API key validated against Azure Table |
| Rate Limiting | 60 req/min per IP (generous for interactive use) | Tiered per API key (Free/Standard/Premium) |
| CORS | Allowed (same origin) | N/A (no CORS for server-to-server) |
| Response format | JSON (consumed by React) | Same JSON (consumed by scripts) |
| Abuse protection | CORS + IP-based rate limit | API key revocation + tier limits |

**API Key Storage Schema (Azure Table: `ApiKeys`):**
| Field | Type | Description |
|-------|------|-------------|
| PartitionKey | string | "apikey" (single partition for fast lookup) |
| RowKey | string | The API key value (UUID format) |
| Owner | string | Developer name or email |
| Tier | string | "free" / "standard" / "premium" |
| IsActive | boolean | Can be revoked by setting to false |
| CreatedAt | datetime | Key creation timestamp |
| LastUsedAt | datetime | Last successful request timestamp |

**Why this approach works:**
- **No token management overhead** for UI users — the browser's same-origin policy prevents CSRF
- **API clients get proper auth** — keys can be rotated, revoked, and rate-limited independently
- **Gateway handles all auth** — backend services don't need to know about authentication
- **Future-proof** — can add OAuth2/JWT later for more complex scenarios

### Usage Examples

**cURL — Shorten a URL:**
```bash
curl -X POST https://ourdomain/api/shorten \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-api-key-here" \
  -d '{"url": "https://www.example.com/very/long/path"}'
```

Response:
```json
{
  "code": "abc123",
  "shortUrl": "https://ourdomain/abc123",
  "originalUrl": "https://www.example.com/very/long/path",
  "createdAt": "2026-06-14T12:00:00Z",
  "isNew": true
}
```

**cURL — Resolve a short URL (get redirect target without following):**
```bash
curl -I https://ourdomain/api/r/abc123 \
  -H "X-API-Key: your-api-key-here"
```

Response:
```
HTTP/1.1 302 Found
Location: https://www.example.com/very/long/path
```

**Python Script:**
```python
import requests

API_BASE = "https://ourdomain/api"
API_KEY = "your-api-key-here"
headers = {"X-API-Key": API_KEY, "Content-Type": "application/json"}

# Shorten
resp = requests.post(f"{API_BASE}/shorten", json={"url": "https://example.com/long"}, headers=headers)
short_url = resp.json()["shortUrl"]
print(f"Short URL: {short_url}")

# Resolve (without following redirect)
resp = requests.get(f"{API_BASE}/r/abc123", headers=headers, allow_redirects=False)
print(f"Redirects to: {resp.headers['Location']}")
```

**PowerShell Script:**
```powershell
$headers = @{
    "X-API-Key" = "your-api-key-here"
    "Content-Type" = "application/json"
}

# Shorten
$body = @{ url = "https://example.com/very/long/path" } | ConvertTo-Json
$response = Invoke-RestMethod -Uri "https://ourdomain/api/shorten" -Method POST -Headers $headers -Body $body
Write-Host "Short URL: $($response.shortUrl)"
```

### Rate Limiting for API Users

| Tier | Requests/Minute | Requests/Day |
|------|----------------|--------------|
| Free | 10 | 1,000 |
| Standard | 100 | 50,000 |
| Premium | 1,000 | Unlimited |

Rate limit info returned in headers:
```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1718366460
```

---

## Mock UI Designs

### Screen 1: Home Page — URL Input

```
┌─────────────────────────────────────────────────────────────────────┐
│  🔗 URL Shortener                                    [About] [GitHub]│
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│                                                                     │
│              ╔═══════════════════════════════════════╗               │
│              ║        Shorten Your URLs              ║               │
│              ║   Make long links short & shareable   ║               │
│              ╚═══════════════════════════════════════╝               │
│                                                                     │
│   ┌─────────────────────────────────────────────────────┐  ┌─────┐ │
│   │ https://www.example.com/very/long/url/path...       │  │ GO! │ │
│   └─────────────────────────────────────────────────────┘  └─────┘ │
│                                                                     │
│   ℹ️  Paste any URL above and click GO to generate a short link     │
│                                                                     │
│                                                                     │
│                                                                     │
├─────────────────────────────────────────────────────────────────────┤
│  © 2026 URL Shortener  |  Powered by Azure                         │
└─────────────────────────────────────────────────────────────────────┘
```

### Screen 2: Success — Short URL Generated

```
┌─────────────────────────────────────────────────────────────────────┐
│  🔗 URL Shortener                                    [About] [GitHub]│
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│              ╔═══════════════════════════════════════╗               │
│              ║        Shorten Your URLs              ║               │
│              ║   Make long links short & shareable   ║               │
│              ╚═══════════════════════════════════════╝               │
│                                                                     │
│   ┌─────────────────────────────────────────────────────┐  ┌─────┐ │
│   │ https://www.example.com/very/long/url/path...       │  │ GO! │ │
│   └─────────────────────────────────────────────────────┘  └─────┘ │
│                                                                     │
│   ┌─────────────────────────────────────────────────────────────┐  │
│   │  ✅ Your short URL is ready!                                │  │
│   │                                                             │  │
│   │  ┌───────────────────────────────────┐  ┌──────────────┐   │  │
│   │  │ https://ourdomain/abc123          │  │ 📋 Copy Link │   │  │
│   │  └───────────────────────────────────┘  └──────────────┘   │  │
│   │                                                             │  │
│   │  Original: https://www.example.com/very/long/url/path...    │  │
│   │  Created:  June 14, 2026 at 12:00 PM                       │  │
│   └─────────────────────────────────────────────────────────────┘  │
│                                                                     │
│   [Shorten Another URL]                                             │
│                                                                     │
├─────────────────────────────────────────────────────────────────────┤
│  © 2026 URL Shortener  |  Powered by Azure                         │
└─────────────────────────────────────────────────────────────────────┘
```

### Screen 3: Error — Invalid URL

```
┌─────────────────────────────────────────────────────────────────────┐
│  🔗 URL Shortener                                    [About] [GitHub]│
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│              ╔═══════════════════════════════════════╗               │
│              ║        Shorten Your URLs              ║               │
│              ║   Make long links short & shareable   ║               │
│              ╚═══════════════════════════════════════╝               │
│                                                                     │
│   ┌─────────────────────────────────────────────────────┐  ┌─────┐ │
│   │ not-a-valid-url                                     │  │ GO! │ │
│   └─────────────────────────────────────────────────────┘  └─────┘ │
│   ⚠️  Please enter a valid URL starting with http:// or https://    │
│                                                                     │
│                                                                     │
├─────────────────────────────────────────────────────────────────────┤
│  © 2026 URL Shortener  |  Powered by Azure                         │
└─────────────────────────────────────────────────────────────────────┘
```

### Screen 4: 404 — Short URL Not Found (Redirect Failure)

```
┌─────────────────────────────────────────────────────────────────────┐
│  🔗 URL Shortener                                    [About] [GitHub]│
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│                                                                     │
│                         ╔════════════════╗                           │
│                         ║      404       ║                           │
│                         ║   Not Found    ║                           │
│                         ╚════════════════╝                           │
│                                                                     │
│           The short URL you visited does not exist                   │
│           or may have been removed.                                  │
│                                                                     │
│                    [← Go to Homepage]                                │
│                                                                     │
│                                                                     │
├─────────────────────────────────────────────────────────────────────┤
│  © 2026 URL Shortener  |  Powered by Azure                         │
└─────────────────────────────────────────────────────────────────────┘
```

### Screen 5: Loading State

```
┌─────────────────────────────────────────────────────────────────────┐
│  🔗 URL Shortener                                    [About] [GitHub]│
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│   ┌─────────────────────────────────────────────────────┐  ┌─────┐ │
│   │ https://www.example.com/very/long/url/path...       │  │ ... │ │
│   └─────────────────────────────────────────────────────┘  └─────┘ │
│                                                                     │
│                    ⏳ Generating short URL...                        │
│                    ━━━━━━━━━━━━━░░░░░░░░░░░                         │
│                                                                     │
├─────────────────────────────────────────────────────────────────────┤
│  © 2026 URL Shortener  |  Powered by Azure                         │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Implementation Phases

### Phase 1: Project Scaffolding & Local Development Setup
- Initialize monorepo with folder structure
- Set up React frontend with Vite (URL input form, result display)
- Set up Spring Boot parent POM with modules (gateway, url-service)
- Create docker-compose.yaml for local dev (Azurite for Table Storage emulation + Redis container)

### Phase 2: Backend — URL Shortener Service
- Implement Base62 encoding algorithm (MurmurHash3 → Base62 → 6 chars)
- REST API endpoints:
  - `POST /api/shorten` — accepts `{ "url": "https://..." }`, returns `{ "code": "abc123", "shortUrl": "ourdomain/abc123" }`
  - `GET /api/{code}` — returns 302 redirect to the original URL
- **Redis Cache layer** (read-through cache before Azure Table Storage):
  - On `GET /api/r/{code}`: check Redis first → if cache miss, fetch from Table Storage → populate cache
  - On `POST /api/shorten`: write to Table Storage → write to Redis cache
  - TTL: 24 hours (configurable), with cache-aside pattern
  - Key format: `url:{code}` → value: long URL string
  - API key validation cache: `apikey:{key}` → value: tier/active JSON (TTL: 5 min)
- Azure Table Storage integration (PartitionKey = first 2 chars of code, RowKey = code)
- Collision handling: verify stored URL matches on hash collision
- Unit & integration tests

### Phase 3: Backend — Spring Cloud Gateway
- Route configuration: `/api/**` → url-service
- **API key validation filter** (for script/programmatic users via `X-API-Key` header)
- Origin-based bypass for React frontend requests (no API key needed from UI)
- Rate limiting (tiered: Free/Standard/Premium), CORS, request logging filters
- Rate limit response headers (`X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`)
- Health check endpoints
- Designed for easy addition of future microservice routes

### Phase 4: Frontend — React Application
- URL input form with validation
- Submit button → calls gateway `/api/shorten`
- Display shortened URL with copy-to-clipboard
- Error handling and loading states
- Responsive design (TailwindCSS or similar)

### Phase 5: Dockerization
- Frontend Dockerfile (multi-stage: build with Node → serve with NGINX)
- Gateway Dockerfile (multi-stage: build with Maven → run with JRE)
- URL Service Dockerfile (multi-stage: build with Maven → run with JRE)
- docker-compose.yaml for full local stack
- Push images to Docker Hub

### Phase 6: Terraform — Azure Infrastructure
- **Resource Group**
- **VNet** with 2 subnets:
  - `frontend-subnet` (for frontend pods + NGINX ingress)
  - `backend-subnet` (for gateway + microservice pods + Redis)
- **NSGs (Network Security Groups)**:
  - `frontend-nsg`: Allow HTTP/HTTPS inbound from Internet, deny all other
  - `backend-nsg`: Allow inbound only from frontend-subnet on port 8080, deny all other
  - Associate NSGs to respective subnets
- **AKS Cluster**:
  - Azure CNI networking (for subnet assignment)
  - System node pool + user node pools
  - Node pool per subnet using `vnetSubnetID`
- **Azure Cache for Redis** (Standard tier, C1 size):
  - Deployed in backend subnet (Private Endpoint)
  - TLS enabled, minimum TLS 1.2
  - 1 GB cache size (scalable)
  - Or alternatively: Redis pod on AKS (cheaper for dev, less HA for production)
- **Storage Account** with Table Storage
  - Private endpoint in backend subnet
- **Managed Identity** for AKS to access Table Storage and Redis

### Phase 7: Kubernetes Deployment
- Namespaces: `frontend`, `backend`
- **Frontend**:
  - Deployment (React NGINX container)
  - ClusterIP Service
  - NGINX Ingress Controller (installed via Helm)
  - Ingress resource for external access
- **Backend**:
  - Gateway Deployment + ClusterIP Service
  - URL Service Deployment + ClusterIP Service
  - Internal communication: frontend ingress routes `/api/**` → gateway service in backend namespace
- **HPA** (Horizontal Pod Autoscaler) on url-service
- **ConfigMaps/Secrets** for Azure Storage connection strings
- **Network Policies**: restrict cross-subnet traffic (frontend can only reach gateway)

### Phase 8: CI/CD — GitHub Actions
- **CI Pipeline** (on PR):
  - Build & test backend (Maven)
  - Build & test frontend (npm)
  - Lint Dockerfiles
  - Lint Terraform (`terraform validate`, `terraform plan`)
- **CD Pipeline** (on merge to main):
  - Build Docker images with commit SHA tags
  - Push to Docker Hub
  - Update Kubernetes manifests with new image tags
  - Apply to AKS using `kubectl`
  - Run smoke tests

### Secrets Management for GitHub Actions

All secrets are stored in **GitHub Repository Secrets** (Settings → Secrets and variables → Actions).

#### Required Secrets

| Secret Name | Purpose | Where It's Used |
|-------------|---------|-----------------|
| `DOCKERHUB_USERNAME` | Docker Hub login | CI/CD — push images |
| `DOCKERHUB_TOKEN` | Docker Hub access token (NOT password) | CI/CD — push images |
| `AZURE_CREDENTIALS` | Azure Service Principal JSON | CD — deploy to AKS, access Storage |
| `AZURE_SUBSCRIPTION_ID` | Azure subscription ID | Terraform apply |
| `AZURE_TENANT_ID` | Azure AD tenant ID | Terraform apply |
| `AZURE_CLIENT_ID` | Service Principal app ID | Terraform apply |
| `AZURE_CLIENT_SECRET` | Service Principal secret | Terraform apply |
| `AKS_CLUSTER_NAME` | Name of AKS cluster | CD — kubectl context |
| `AKS_RESOURCE_GROUP` | Resource group containing AKS | CD — kubectl context |
| `AZURE_STORAGE_CONNECTION_STRING` | Table Storage connection | K8s Secret creation |

#### How to Create Azure Service Principal

```bash
# Create SP with Contributor role scoped to resource group
az ad sp create-for-rbac \
  --name "url-shortener-github-actions" \
  --role Contributor \
  --scopes /subscriptions/{sub-id}/resourceGroups/{rg-name} \
  --sdk-auth

# Output JSON → store as AZURE_CREDENTIALS secret
```

#### How to Create Docker Hub Access Token

1. Docker Hub → Account Settings → Security → New Access Token
2. Name: `github-actions-url-shortener`
3. Permissions: Read & Write
4. Copy token → store as `DOCKERHUB_TOKEN` secret

#### GitHub Actions Workflow — Secret Usage

**CI Pipeline (`.github/workflows/ci.yaml`):**
```yaml
name: CI
on:
  pull_request:
    branches: [main]

jobs:
  build-backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Build & Test
        run: cd backend && mvn clean verify

  build-frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '20'
      - name: Install & Test
        run: cd frontend && npm ci && npm test && npm run build

  lint-terraform:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: hashicorp/setup-terraform@v3
      - run: cd infra && terraform init -backend=false && terraform validate
```

**CD Pipeline (`.github/workflows/cd.yaml`):**
```yaml
name: CD
on:
  push:
    branches: [main]

env:
  DOCKER_REGISTRY: docker.io
  IMAGE_PREFIX: ${{ secrets.DOCKERHUB_USERNAME }}/url-shortener

jobs:
  build-and-push:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        service: [frontend, gateway, url-service]
    steps:
      - uses: actions/checkout@v4

      - name: Login to Docker Hub
        uses: docker/login-action@v3
        with:
          username: ${{ secrets.DOCKERHUB_USERNAME }}
          password: ${{ secrets.DOCKERHUB_TOKEN }}    # ← Access token, not password

      - name: Build & Push Docker Image
        uses: docker/build-push-action@v5
        with:
          context: ./${{ matrix.service == 'url-service' && 'backend/url-service' || (matrix.service == 'gateway' && 'backend/gateway' || 'frontend') }}
          push: true
          tags: |
            ${{ env.IMAGE_PREFIX }}-${{ matrix.service }}:${{ github.sha }}
            ${{ env.IMAGE_PREFIX }}-${{ matrix.service }}:latest

  deploy:
    needs: build-and-push
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Azure Login
        uses: azure/login@v2
        with:
          creds: ${{ secrets.AZURE_CREDENTIALS }}     # ← Service Principal JSON

      - name: Set AKS Context
        uses: azure/aks-set-context@v4
        with:
          cluster-name: ${{ secrets.AKS_CLUSTER_NAME }}
          resource-group: ${{ secrets.AKS_RESOURCE_GROUP }}

      - name: Update K8s Secrets
        run: |
          kubectl create secret generic azure-storage \
            --from-literal=connection-string="${{ secrets.AZURE_STORAGE_CONNECTION_STRING }}" \
            --namespace=backend \
            --dry-run=client -o yaml | kubectl apply -f -

      - name: Deploy to AKS
        run: |
          # Update image tags in manifests
          cd k8s
          sed -i "s|IMAGE_TAG|${{ github.sha }}|g" */deployment.yaml
          kubectl apply -f namespaces.yaml
          kubectl apply -f frontend/
          kubectl apply -f gateway/
          kubectl apply -f url-service/

      - name: Smoke Test
        run: |
          # Wait for rollout
          kubectl rollout status deployment/url-service -n backend --timeout=120s
          kubectl rollout status deployment/gateway -n backend --timeout=120s
          kubectl rollout status deployment/frontend -n frontend --timeout=120s
          # Health check
          INGRESS_IP=$(kubectl get ingress -n frontend -o jsonpath='{.items[0].status.loadBalancer.ingress[0].ip}')
          curl -f "http://${INGRESS_IP}/api/health" || exit 1
```

#### Security Best Practices

| Practice | Implementation |
|----------|---------------|
| Never commit secrets | All secrets in GitHub Settings, never in code |
| Use access tokens not passwords | Docker Hub token with minimal permissions |
| Scope Service Principal | Contributor role scoped to specific resource group only |
| Rotate secrets regularly | Set calendar reminder to rotate SP secret & Docker token quarterly |
| Audit secret access | GitHub audit log shows who accessed/changed secrets |
| Use OIDC (advanced) | Future: replace SP secret with GitHub OIDC federation for passwordless Azure auth |

#### Terraform Backend State (also uses secrets)

Terraform state is stored in Azure Blob Storage (separate from application storage):
```hcl
# infra/backend.tf
terraform {
  backend "azurerm" {
    resource_group_name  = "tfstate-rg"
    storage_account_name = "tfstateurlshortener"
    container_name       = "tfstate"
    key                  = "url-shortener.tfstate"
  }
}
```
The CD pipeline authenticates to this backend using the same `AZURE_CREDENTIALS` service principal.

## Key Design Decisions

1. **Base62 Encoding (Idempotent)**: `MurmurHash3(url) → take first 6 Base62 chars`. Same URL always yields same code. On collision (different URL, same hash), append incrementing suffix.

2. **Azure Table Storage Schema**:
   - Table: `UrlMappings`
   - PartitionKey: first 2 chars of code (distributes load)
   - RowKey: full 6-char code
   - Properties: `LongUrl`, `CreatedAt`, `HitCount`

3. **Subnet Isolation**: AKS with Azure CNI allows node pools in different subnets. NetworkPolicies enforce that only the gateway is reachable from the frontend subnet.

4. **Spring Cloud Gateway**: Lightweight, Java-native API gateway running as a pod in the backend subnet. Routes, rate-limits, and provides a single entry point for all backend microservices.

## Azure Resources Summary

| Resource | Purpose |
|----------|---------|
| Resource Group | Logical container |
| VNet + 2 Subnets | Network isolation |
| NSG (frontend-nsg) | Allow HTTP/HTTPS inbound, deny all other |
| NSG (backend-nsg) | Allow only from frontend subnet, deny all other |
| AKS Cluster | Container orchestration |
| Azure Cache for Redis | Fast lookup cache for URL mappings & API keys |
| Storage Account (Table) | URL mapping + API keys persistence |
| Public IP | Ingress external IP |
| Managed Identity | AKS → Storage + Redis access |

## Redis Caching Strategy

### Lookup Flow (GET /api/r/{code})

```
Request: GET /api/r/abc123
         │
         ▼
┌─────────────────────┐
│ Check Redis Cache    │
│ Key: "url:abc123"   │
└────────┬────────────┘
         │
    ┌────┴────┐
    │  HIT?   │
    └────┬────┘
     YES │         NO
    ┌────┘         └────┐
    ▼                   ▼
┌──────────┐    ┌──────────────────┐
│ Return   │    │ Query Azure Table │
│ cached   │    │ PK="ab" RK="abc" │
│ long URL │    └────────┬─────────┘
│ (< 1ms)  │             │
└──────────┘        ┌────┴────┐
                    │  FOUND? │
                    └────┬────┘
                 YES │        NO
                ┌────┘        └────┐
                ▼                   ▼
        ┌──────────────┐    ┌──────────┐
        │ Write to     │    │ Return   │
        │ Redis Cache  │    │ 404      │
        │ TTL: 24h     │    └──────────┘
        │ Return URL   │
        └──────────────┘
```

### Write Flow (POST /api/shorten)

```
Request: POST /api/shorten {"url": "https://..."}
         │
         ▼
┌─────────────────────────┐
│ Generate Base62 code    │
│ (MurmurHash3 → 6 chars) │
└────────┬────────────────┘
         │
         ▼
┌─────────────────────────┐
│ Write to Azure Table    │  ← Source of truth (persistent)
│ PK="ab" RK="abc123"    │
└────────┬────────────────┘
         │
         ▼
┌─────────────────────────┐
│ Write to Redis Cache    │  ← For fast subsequent reads
│ Key: "url:abc123"       │
│ Value: long URL         │
│ TTL: 24 hours           │
└────────┬────────────────┘
         │
         ▼
┌─────────────────────────┐
│ Return response to user │
└─────────────────────────┘
```

### Redis Key Schema

| Key Pattern | Value | TTL | Purpose |
|-------------|-------|-----|---------|
| `url:{code}` | Long URL string | 24h | URL mapping cache |
| `apikey:{key}` | `{"tier":"free","active":true}` | 5 min | API key validation cache |
| `ratelimit:{key}:{window}` | Request count (integer) | 1 min | Rate limit counters |

### Cache Invalidation
- **TTL-based expiry**: Keys auto-expire after TTL (no manual invalidation needed for URL mappings since they're immutable)
- **API key changes**: When a key is revoked/updated, delete `apikey:{key}` from Redis
- **Cache warming**: On service startup, optionally preload top-N most accessed URLs

### Performance Expectations
| Operation | Without Redis | With Redis (cache hit) |
|-----------|--------------|----------------------|
| URL redirect | ~20-50ms (Table Storage) | ~1-3ms |
| API key validation | ~20-50ms per request | ~1-3ms (cached 5 min) |
| Shorten URL | ~30-60ms | ~30-60ms (write-through, no improvement) |

## Scaling Architecture

### Scaling Strategy Overview

```
═══════════════════════════════════════════════════════════════════════════
                    SCALING ARCHITECTURE — PICTORIAL VIEW
═══════════════════════════════════════════════════════════════════════════

                         ╔══════════════════════╗
                         ║   INTERNET TRAFFIC   ║
                         ║  100 → 10,000+ RPS   ║
                         ╚══════════╦═══════════╝
                                    ║
                         ╔══════════╩═══════════════════════════════════╗
                         ║   Azure Load Balancer (L4 — TCP only)       ║
                         ║                                              ║
                         ║   • Auto-created by AKS when NGINX Ingress  ║
                         ║     Service type = LoadBalancer              ║
                         ║   • Provides the PUBLIC IP address           ║
                         ║   • Forwards raw TCP (ports 80/443) to      ║
                         ║     NGINX Ingress Controller pods            ║
                         ║   • Does NOT inspect HTTP — no URL routing  ║
                         ║   • Health-probes NGINX pods, removes        ║
                         ║     unhealthy ones from rotation             ║
                         ║                                              ║
                         ║   Why needed: AKS pods don't have public    ║
                         ║   IPs. Azure LB bridges Internet → cluster. ║
                         ╚══════════╦═══════════════════════════════════╝
                                    ║
═══════════════════════╦════════════╩══════════════╦════════════════════════
   FRONTEND SUBNET     ║                           ║     BACKEND SUBNET
                       ║                           ║
┌──────────────────────╨───────────────────────────╨───────────────────────┐
│                                                                          │
│  ┌─── NGINX INGRESS CONTROLLER (Layer 7 — HTTP routing) ────────────┐   │
│  │                                                                   │   │
│  │   This is a SEPARATE NGINX instance (K8s Ingress Controller).     │   │
│  │   It does NOT serve React files — it only ROUTES traffic:         │   │
│  │                                                                   │   │
│  │   • /*     → Frontend Pods (which have their OWN embedded NGINX)  │   │
│  │   • /api/* → Gateway Pods                                         │   │
│  │                                                                   │   │
│  │   Features: SSL termination, connection pooling, load balancing   │   │
│  └───────────────────────────────────────────────────────────────────┘   │
│           │                                        │                     │
│           ▼                                        ▼                     │
│  ┌──────────────────────────────┐    ┌───────────────────────────────┐  │
│  │  FRONTEND PODS (HPA)         │    │    GATEWAY PODS (HPA)         │  │
│  │                               │    │                               │  │
│  │  Each pod = NGINX + React SPA │    │  ┌───┐ ┌───┐       ┌───┐    │  │
│  │  (NGINX serves static files)  │    │  │ G │ │ G │  ...  │ G │    │  │
│  │                               │    │  │ 1 │ │ 2 │       │10 │    │  │
│  │  ┌─────────────────────┐     │    │  └───┘ └───┘       └───┘    │  │
│  │  │ Pod 1               │     │    │                               │  │
│  │  │ ┌───────────────┐   │     │    │  Min: 2  Max: 10             │  │
│  │  │ │ NGINX (port80)│   │     │    │  Scale: CPU > 70%            │  │
│  │  │ │  serves →     │   │     │    └──────────────┬────────────────┘  │
│  │  │ │ index.html    │   │     │                   │                   │
│  │  │ │ bundle.js     │   │     │                   ▼                   │
│  │  │ │ styles.css    │   │     │    ┌───────────────────────────────┐  │
│  │  │ └───────────────┘   │     │    │   URL SERVICE PODS (HPA)      │  │
│  │  └─────────────────────┘     │    │                               │  │
│  │                               │    │  ┌───┐ ┌───┐ ┌───┐    ┌───┐ │  │
│  │  ┌─────────────────────┐     │    │  │ U │ │ U │ │ U │... │ U │ │  │
│  │  │ Pod 2  (same image) │     │    │  │ 1 │ │ 2 │ │ 3 │    │20 │ │  │
│  │  └─────────────────────┘     │    │  └───┘ └───┘ └───┘    └───┘ │  │
│  │          ...                  │    │                               │  │
│  │  ┌─────────────────────┐     │    │  Min: 2  Max: 20             │  │
│  │  │ Pod 5  (same image) │     │    │  Scale: CPU>60% OR RPS>500   │  │
│  │  └─────────────────────┘     │    └──────────────┬────────────────┘  │
│  │                               │                   │                   │
│  │  Min: 2  Max: 5              │         ┌─────────┴─────────┐        │
│  │  Scale: CPU > 80%            │         ▼                   ▼        │
│  └──────────────────────────────┘  ┌──────────────┐   ┌──────────────┐ │
│                                    │    REDIS     │   │ AZURE TABLE  │ │
│                                    │   CACHE      │   │  STORAGE     │ │
│                                    │ C1 → Cluster │   │ Auto-scales  │ │
│                                    └──────────────┘   └──────────────┘ │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘

═══════════════════════════════════════════════════════════════════════════
   THREE NETWORKING LAYERS — EXPLAINED
═══════════════════════════════════════════════════════════════════════════

   ┌────────────────────────────────────────────────────────────────────┐
   │                                                                    │
   │  LAYER 1: Azure Load Balancer (L4 — TCP)                          │
   │  ────────────────────────────────────────                          │
   │  • Auto-provisioned by AKS (you don't create it manually)         │
   │  • Purpose: Gives your cluster a PUBLIC IP reachable from Internet │
   │  • Operates at TCP level — cannot read URLs or HTTP headers        │
   │  • Simply forwards port 80/443 traffic to NGINX Ingress pods      │
   │  • Also health-checks NGINX pods and removes failed ones          │
   │  • Without this: your cluster has NO public entry point            │
   │                                                                    │
   │  LAYER 2: NGINX Ingress Controller (L7 — HTTP)                    │
   │  ──────────────────────────────────────────────                    │
   │  • Deployed as its own pods (managed by Helm chart)                │
   │  • Operates at HTTP level — reads URLs, headers, cookies           │
   │  • Routes based on URL path rules (/* → frontend, /api/* → GW)    │
   │  • Handles: SSL termination, CORS, connection pooling             │
   │  • Does NOT serve any static files                                 │
   │  • Comparable to: Azure App Gateway, AWS ALB, Traefik              │
   │                                                                    │
   │  LAYER 3: NGINX inside Frontend Pods (static file server)          │
   │  ──────────────────────────────────────────────────                │
   │  • Built into the frontend Docker image (multi-stage build)        │
   │  • Serves React's index.html, bundle.js, CSS, images              │
   │  • Lightweight — just serves files, no routing logic              │
   │  • One NGINX per frontend pod                                      │
   │  • Comparable to: Apache httpd, Python http.server                 │
   │                                                                    │
   │  COMPLETE FLOW:                                                    │
   │  User visits https://ourdomain/                                    │
   │    → DNS resolves to Azure LB public IP                            │
   │      → Azure LB forwards TCP to NGINX Ingress pod (L4)            │
   │        → NGINX Ingress reads URL path "/" (L7)                     │
   │          → Routes to a Frontend Pod                                │
   │            → Frontend Pod's NGINX serves index.html + bundle.js    │
   │              → React app loads in browser                          │
   │                → React calls /api/shorten                          │
   │                  → Azure LB → NGINX Ingress → routes /api/* → GW  │
   │                    → Gateway → URL Service → Redis/Table           │
   │                                                                    │
   └────────────────────────────────────────────────────────────────────┘

═══════════════════════════════════════════════════════════════════════════
              AKS CLUSTER AUTOSCALER (Layer 3: Node Scaling)
═══════════════════════════════════════════════════════════════════════════

  Frontend Node Pool                    Backend Node Pool
  ┌─────────────────────┐              ┌─────────────────────────────────┐
  │                     │              │                                 │
  │  ┌────┐ ┌────┐     │              │  ┌────┐ ┌────┐ ┌────┐         │
  │  │Node│ │Node│ ... │              │  │Node│ │Node│ │Node│ ...     │
  │  │ 1  │ │ 2  │     │              │  │ 1  │ │ 2  │ │ 3  │         │
  │  │D2sv3 │D2sv3│     │              │  │D4sv3 │D4sv3 │D4sv3│         │
  │  └────┘ └────┘     │              │  └────┘ └────┘ └────┘         │
  │                     │              │                                 │
  │  Min: 2  Max: 5    │              │  Min: 2  Max: 10               │
  │  VM: D2s_v3        │              │  VM: D4s_v3                    │
  │  (2 vCPU, 8 GB)    │              │  (4 vCPU, 16 GB)              │
  └─────────────────────┘              └─────────────────────────────────┘

═══════════════════════════════════════════════════════════════════════════
                    SCALING TRIGGERS & RESPONSES
═══════════════════════════════════════════════════════════════════════════

  TRIGGER                    ACTION                       TIME
  ─────────────────────────────────────────────────────────────────
  CPU > 60% (url-svc)  ───► HPA adds pods              ~30 seconds
  CPU > 70% (gateway)  ───► HPA adds pods              ~30 seconds
  Pods unschedulable    ───► Cluster adds nodes         ~3-5 minutes
  RPS > 500/pod         ───► HPA adds url-svc pods     ~30 seconds
  Cache hit ratio < 80% ───► Scale Redis tier           Manual/alert
  Nodes < 50% utilized  ───► Cluster removes nodes     ~10 minutes
  CPU < 40% (sustained) ───► HPA removes pods          ~5 minutes


═══════════════════════════════════════════════════════════════════════════
                    SCALING PROGRESSION BY LOAD
═══════════════════════════════════════════════════════════════════════════

  Load       Frontend    Gateway    URL Svc    Nodes(BE)  Redis       Cost
  ────────── ─────────── ────────── ────────── ────────── ─────────── ──────
  Idle        2 pods      2 pods     2 pods     2          C1 (1GB)   $300
              │           │          │          │          │
  500 RPS     2 pods      2 pods     3 pods     2          C1 (1GB)   $320
              │           │          │          │          │
  2,000 RPS   3 pods      4 pods     8 pods     4          C2 (2.5GB) $500
              │           │          │          │          │
  5,000 RPS   4 pods      6 pods    12 pods     6          C3 (6GB)   $800
              │           │          │          │          │
  10,000 RPS  5 pods     10 pods    20 pods    10          P1 Cluster $1500
              │           │          │          │          │
              ▼           ▼          ▼          ▼          ▼
         [MAX REACHED — add more node pools or upgrade VM sizes]
```

### Horizontal Pod Autoscaler (HPA) Configuration

| Service | Min Replicas | Max Replicas | Scale Metric | Target |
|---------|-------------|-------------|--------------|--------|
| url-service | 2 | 20 | CPU utilization | 60% |
| url-service | 2 | 20 | Requests/sec (custom) | 500 RPS/pod |
| gateway | 2 | 10 | CPU utilization | 70% |
| frontend | 2 | 5 | CPU utilization | 80% |

**HPA YAML example (url-service):**
```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: url-service-hpa
  namespace: backend
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: url-service
  minReplicas: 2
  maxReplicas: 20
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 60
    - type: Pods
      pods:
        metric:
          name: http_requests_per_second
        target:
          type: AverageValue
          averageValue: "500"
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 30
      policies:
        - type: Percent
          value: 100          # Can double pods in one step
          periodSeconds: 30
    scaleDown:
      stabilizationWindowSeconds: 300   # Wait 5 min before scaling down
      policies:
        - type: Percent
          value: 25           # Remove max 25% pods per step
          periodSeconds: 60
```

### AKS Cluster Autoscaler

```yaml
# Terraform: AKS node pool with autoscaling
resource "azurerm_kubernetes_cluster_node_pool" "backend" {
  name                  = "backend"
  kubernetes_cluster_id = azurerm_kubernetes_cluster.aks.id
  vm_size              = "Standard_D4s_v3"    # 4 vCPU, 16 GB RAM
  vnet_subnet_id       = azurerm_subnet.backend.id

  enable_auto_scaling  = true
  min_count           = 2
  max_count           = 10
  node_count          = 3                     # Initial count

  node_labels = {
    "tier" = "backend"
  }
}

resource "azurerm_kubernetes_cluster_node_pool" "frontend" {
  name                  = "frontend"
  kubernetes_cluster_id = azurerm_kubernetes_cluster.aks.id
  vm_size              = "Standard_D2s_v3"    # 2 vCPU, 8 GB RAM
  vnet_subnet_id       = azurerm_subnet.frontend.id

  enable_auto_scaling  = true
  min_count           = 2
  max_count           = 5
  node_count          = 2

  node_labels = {
    "tier" = "frontend"
  }
}
```

### Redis Scaling Path

| Stage | Configuration | Capacity | Use Case |
|-------|--------------|----------|----------|
| Dev/Test | Redis pod on AKS | 256 MB | Local development |
| Production (start) | Azure Cache C1 Standard | 1 GB, 1 replica | < 5,000 RPS |
| Production (medium) | Azure Cache C3 Standard | 6 GB, 1 replica | < 20,000 RPS |
| Production (high) | Azure Cache P1 Premium + Clustering | 6 GB × N shards | > 20,000 RPS |

### Scaling Scenarios

**Scenario 1: Normal traffic (100-500 RPS)**
```
Frontend: 2 pods, Gateway: 2 pods, URL Service: 2 pods
Nodes: 2 backend + 2 frontend
Redis: Azure Cache C1 (1 GB)
Estimated cost: ~$300/month
```

**Scenario 2: Growth spike (1,000-5,000 RPS)**
```
Frontend: 3 pods, Gateway: 4 pods, URL Service: 8 pods
Nodes: 4 backend + 2 frontend (autoscaled)
Redis: Azure Cache C2 (2.5 GB)
HPA triggers: CPU > 60%, auto-scales pods within 30 seconds
Estimated cost: ~$600/month
```

**Scenario 3: Viral event (10,000+ RPS)**
```
Frontend: 5 pods, Gateway: 10 pods, URL Service: 20 pods
Nodes: 8 backend + 4 frontend (autoscaled)
Redis: Azure Cache P1 Premium with 3 shards
Cluster autoscaler adds nodes in ~3-5 minutes
Estimated cost: ~$1,500/month
```

### Pod Resource Requests & Limits

```yaml
# url-service deployment (per pod)
resources:
  requests:
    cpu: "250m"
    memory: "512Mi"
  limits:
    cpu: "1000m"
    memory: "1Gi"

# gateway deployment (per pod)
resources:
  requests:
    cpu: "200m"
    memory: "384Mi"
  limits:
    cpu: "500m"
    memory: "768Mi"

# frontend deployment (per pod)
resources:
  requests:
    cpu: "100m"
    memory: "128Mi"
  limits:
    cpu: "250m"
    memory: "256Mi"
```

### Pod Disruption Budgets (PDB)

Ensure availability during node upgrades and scaling events:

```yaml
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: url-service-pdb
  namespace: backend
spec:
  minAvailable: 1       # At least 1 pod always running
  selector:
    matchLabels:
      app: url-service
```

### Key Scaling Design Decisions

| Decision | Rationale |
|----------|-----------|
| Min 2 replicas everywhere | Zero-downtime deploys + fault tolerance |
| CPU + custom metrics for HPA | CPU catches compute load; RPS catches I/O-bound load |
| Aggressive scale-up, conservative scale-down | Handle spikes fast, avoid flapping |
| Separate node pools per subnet | Independent scaling, cost isolation, security boundary |
| Redis before Table Storage | 90%+ reads served from cache, Table Storage never becomes bottleneck |
| Stateless services | Any pod can handle any request; horizontal scaling is trivial |

---

## Notes & Considerations
- **Custom Domain**: Plan assumes `ourdomain` will be configured via DNS A-record pointing to the Ingress public IP
- **TLS**: Use cert-manager with Let's Encrypt for HTTPS
- **Monitoring**: Can add Azure Monitor / Prometheus + Grafana in a future phase
- **Cost**: Azure Table Storage is very cheap; Azure Cache for Redis Standard C1 ~$40/mo; AKS cost depends on node size/count
- **Scalability**: HPA on url-service handles load spikes; Redis handles read burst; Azure Table Storage scales automatically
- **Redis HA**: Azure Cache for Redis Standard tier provides replication; use Premium for clustering at scale
