# URL Shortener

A scalable URL shortener application deployed on Azure Kubernetes Service (AKS).

## Architecture

- **Frontend**: React (Vite) + TailwindCSS served by NGINX
- **API Gateway**: Spring Cloud Gateway (routing, rate limiting, API key auth)
- **Backend Service**: Spring Boot (URL shortening with Base62 encoding)
- **Cache**: Redis (Azure Cache for Redis)
- **Storage**: Azure Table Storage
- **Infrastructure**: Terraform (AKS, VNet, NSGs, Storage, Redis)
- **CI/CD**: GitHub Actions → Docker Hub → AKS

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
| Redis | localhost:6379 |
| Azurite (Table) | localhost:10002 |

## Deployment

### Infrastructure
```bash
cd infra
terraform init
terraform plan
terraform apply
```

### Manual Deploy
```bash
# Build and push images
docker build -t youruser/url-shortener-frontend ./frontend
docker push youruser/url-shortener-frontend

# Apply K8s manifests
kubectl apply -f k8s/
```

## API Usage

### Shorten URL
```bash
curl -X POST http://localhost:8080/api/shorten \
  -H "Content-Type: application/json" \
  -d '{"url": "https://example.com/long-url"}'
```

### Redirect
```bash
curl -L http://localhost:8080/api/r/abc123
```
# urlshortner
