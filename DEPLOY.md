# Manual Deployment

## 1. Clone repository

```bash
git clone <repository-url>
cd solvego
```

## 2. Create `.env`

```bash
cp .env.example .env
```

Edit `.env` values.

Example:

```env
DB_USERNAME=solvego
DB_PASSWORD=change-me
MYSQL_ROOT_PASSWORD=change-me
JWT_SECRET=change-me-to-a-long-random-secret-key
```

## 3. Run Docker Compose

```bash
docker compose up --build --detach
```

## 4. Check containers

```bash
docker compose ps
```

Expected:

```text
solvego-app   Up
solvego-db    Up (healthy)
```

## 5. Health check

```bash
curl http://localhost:8080/actuator/health
```

Expected response:

```json
{"status":"UP"}
```

## 6. Stop containers

```bash
docker compose down
```

## 7. Reset database volume

This deletes the Docker MySQL data volume.

```bash
docker compose down --volumes
```