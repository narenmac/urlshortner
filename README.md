# URL Shortener

A scalable URL shortener application deployed on Azure Kubernetes Service (AKS).

## Architecture

- **Frontend**: React (Vite) + TailwindCSS served by NGINX
- **API Gateway**: Spring Cloud Gateway (routing, API key auth for script users)
- **Backend Service**: Spring Boot (URL shortening with MurmurHash3 → Base62 encoding)
- **Storage**: Azure Table Storage (persistent store for URL mappings)
- **Infrastructure**: Terraform (AKS, VNet, NSGs, Storage)
- **CI/CD**: GitHub Actions → Docker Hub → AKS
- **Networking**: NGINX Ingress Controller + ExternalName service for cross-namespace routing

## Project Structure

```
url-shortener/
├── frontend/                  # React app (Vite + TailwindCSS)
│   ├── src/components/        # UrlForm, ResultDisplay, ErrorDisplay
│   ├── src/services/          # API client
│   ├── Dockerfile             # Multi-stage: Node build → NGINX serve
│   └── nginx.conf             # SPA routing + API proxy (for K8s)
├── backend/
│   ├── gateway/               # Spring Cloud Gateway (API routing + auth)
│   ├── url-service/           # URL shortener microservice
│   └── pom.xml                # Parent POM (multi-module Maven)
├── infra/                     # Terraform (VNet, NSGs, AKS, Storage)
│   └── modules/               # vnet, aks, storage, redis
├── k8s/                       # Kubernetes manifests
│   ├── frontend/              # Deployment, Service, Ingress, ExternalName
│   ├── gateway/               # Deployment, Service
│   ├── url-service/           # Deployment, Service, HPA, PDB
│   └── namespaces.yaml
├── .github/workflows/
│   ├── ci.yaml                # Build & test on PR
│   └── cd.yaml                # Build → Push → Deploy on merge to main
└── docker-compose.yaml        # Local dev (Azurite for Table Storage)
```

## Local Development

### Prerequisites
- Docker & Docker Compose
- Node.js 20+
- Java 17+
- Maven 3.9+

### Quick Start

```bash
# Start all services locally
docker-compose up -d

# Frontend only (with hot reload)
cd frontend && npm install && npm run dev

# Backend only
cd backend && mvn spring-boot:run -pl url-service
```

### Local Services
| Service | URL |
|---------|-----|
| Frontend | http://localhost:3000 |
| Gateway | http://localhost:8080 |
| URL Service | http://localhost:8081 |
| Azurite (Table) | localhost:10002 |

## Deployment to Azure

### Prerequisites
1. Azure account with a resource group (`url-shortener-rg`)
2. AKS cluster created (manually or via Terraform)
3. Azure Storage Account with Table Storage
4. Docker Hub account
5. NGINX Ingress Controller installed on AKS

### GitHub Secrets Required

| Secret | Description |
|--------|-------------|
| `DOCKERHUB_USERNAME` | Docker Hub username |
| `DOCKERHUB_TOKEN` | Docker Hub access token (Security → New Access Token) |
| `AZURE_CREDENTIALS` | Service Principal JSON (`az ad sp create-for-rbac --sdk-auth`) |
| `AKS_CLUSTER_NAME` | AKS cluster name (e.g., `url-shortener-aks`) |
| `AKS_RESOURCE_GROUP` | Resource group name (e.g., `url-shortener-rg`) |
| `AZURE_STORAGE_CONNECTION_STRING` | Storage Account → Access keys → Connection string |

### Install NGINX Ingress Controller (one-time)

```bash
az aks get-credentials -g url-shortener-rg -n url-shortener-aks
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm repo update
helm install nginx-ingress ingress-nginx/ingress-nginx -n ingress-nginx --create-namespace
```

### Deploy via CI/CD
Push to `main` branch → GitHub Actions automatically builds, pushes images, and deploys to AKS.

### Manual Deploy
```bash
az aks get-credentials -g url-shortener-rg -n url-shortener-aks
kubectl apply -f k8s/namespaces.yaml
kubectl apply -f k8s/frontend/
kubectl apply -f k8s/gateway/
kubectl apply -f k8s/url-service/
```

## API Usage

### Shorten URL
```bash
curl -X POST http://<EXTERNAL-IP>/api/shorten \
  -H "Content-Type: application/json" \
  -d '{"url": "https://example.com/very/long/url"}'
```

Response:
```json
{
  "code": "7xJyf5",
  "shortUrl": "http://<EXTERNAL-IP>/api/r/7xJyf5",
  "originalUrl": "https://example.com/very/long/url",
  "createdAt": "2026-06-15T12:00:00Z",
  "isNew": true
}
```

### Redirect
```bash
curl -L http://<EXTERNAL-IP>/api/r/7xJyf5
# → 302 redirects to original URL
```

### Health Check
```bash
curl http://<EXTERNAL-IP>/api/health
```

## Testing After Deployment

1. Get External IP: Azure Portal → AKS → Services and ingresses → LoadBalancer IP
2. Open `http://<EXTERNAL-IP>/` in browser for the React UI
3. Paste a long URL → click GO → get short URL
4. Visit the short URL → browser redirects to original

## Key Design Decisions

- **Base62 encoding is idempotent**: Same URL always produces the same 6-char code (MurmurHash3)
- **No Redis** (for free-tier): Goes directly to Azure Table Storage. Add Redis later for caching.
- **ExternalName service**: Routes `/api/*` from frontend namespace to gateway in backend namespace
- **API key filter**: UI users (same-origin) pass through; script users need `X-API-Key` header
- **Auto-detected base URL**: CD pipeline reads Ingress IP and sets `APP_BASE_URL` dynamically
