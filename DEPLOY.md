# Manual Deployment

## 1. Clone repository

```bash
git clone https://github.com/SolveGO/solvego-backend.git
cd solvego-backend
````

## 2. Create `.env`

```bash
cp .env.example .env
```

Edit the values in the `.env` file to match your environment.

```env
DB_USERNAME=solvego
DB_PASSWORD=change-me
MYSQL_ROOT_PASSWORD=change-me
JWT_SECRET=change-me-to-a-long-random-secret-key
```

## 3. Start containers

```bash
docker compose up -d --build
```

## 4. Check container status

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

Swagger UI:

```text
http://localhost:8080/swagger-ui/index.html
```

## 6. Stop containers

```bash
docker compose down
```

## 7. Reset database volume

The following command also deletes the MySQL data volume.

```bash
docker compose down -v
```
