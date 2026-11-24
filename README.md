# MS Platform

A Spring Boot microservices platform for Learning Management System (LMS) with Kafka messaging, Redis caching, and PostgreSQL database.

## Prerequisites

### Required Software
- **Java 17+** - Required for Spring Boot application
- **Maven 3.6+** - Build tool
- **PostgreSQL** - Database server (localhost:5432)
- **Redis** - Caching server (localhost:6379)
- **Docker Desktop** - For running Kafka and Zookeeper

### Database Setup
Create PostgreSQL database:
```sql
CREATE DATABASE lms_platform;
CREATE USER postgres WITH PASSWORD '<your-db-password>';
GRANT ALL PRIVILEGES ON DATABASE lms_platform TO postgres;
```

### Environment variables
Secrets are read from the environment, never from the repository:
```bash
export DB_USERNAME=postgres
export DB_PASSWORD=<your-db-password>
export JWT_SECRET=$(openssl rand -base64 32)
export GMAIL_USERNAME=<sender-address>   # optional, for password-reset e-mails
export GMAIL_PASSWORD=<app-password>
```
A template is provided in `.env.example`.

## Infrastructure Setup

### 1. Redis Installation & Setup

#### Option A: Install Redis locally
**Windows:**
j'ai telecharge depuis ce lien https://www.memurai.com/get-memurai
```bash
# Using chocolatey
choco install redis-64

# Or download from https://github.com/microsoftarchive/redis/releases
# Start Redis server
redis-server
```

**macOS:**
```bash
# Using Homebrew
brew install redis
brew services start redis
```

**Linux (Ubuntu/Debian):**
```bash
sudo apt update
sudo apt install redis-server
sudo systemctl start redis
sudo systemctl enable redis
```

#### Option B: Redis with Docker
```bash
docker run -d --name redis -p 6379:6379 redis:7-alpine
```

### 2. Kafka Setup with Docker (Required)

The application requires Kafka for event messaging. Use the provided docker-compose configuration:

#### Step 1: Start Docker Desktop
- Open Docker Desktop application
- Wait for it to fully start (Docker whale icon in system tray should be running)

#### Step 2: Navigate to Project Directory
```bash
cd D:\DIC_2_GIT\MS_Platform
```

#### Step 3: Start Kafka Services
```bash
docker-compose up -d
```

#### Step 4: Verify Kafka is Running
```bash
docker-compose ps
```
You should see both `zookeeper` and `kafka` containers running with "Up" status.

#### Step 5: Test Kafka Connection
```bash
docker exec kafka kafka-topics --bootstrap-server localhost:9092 --list
```

#### Step 6: Stop Kafka (when needed)
```bash
docker-compose down
```

### Kafka Topics
The application will automatically create these topics on startup:
- `lms.quiz.completed` - Quiz completion events
- `lms.module.completed` - Module completion events  
- `lms.user.enrolled` - User enrollment events
- `lms.course.published` - Course publication events

## Application Configuration

### Environment Variables
Create `.env` file in project root (optional):
```env
# Database
DB_USERNAME=your-db-username
DB_PASSWORD=your-db-password

# Kafka
KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# JWT
JWT_SECRET=your-jwt-secret-key-here

# CORS
CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:8080
```

### Profiles
- **dev** - Development profile (default)
- **prod** - Production profile

## Running the Application

### Step 1: Verify Infrastructure
Ensure all services are running:
- PostgreSQL on localhost:5432
- Redis on localhost:6379
- Kafka on localhost:9092

### Step 2: Build and Run
```bash
# Build the project
mvn clean install

# Run the application
mvn spring-boot:run

# Or run with specific profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### Step 3: Verify Application
- Application will start on http://localhost:8080
- Swagger UI available at http://localhost:8080/swagger-ui.html
- Health check at http://localhost:8080/actuator/health

## Tests

```bash
./mvnw test
```
36 tests, JUnit 5:
- `AuthServiceTest`, 15 unit tests with Mockito: registration, login with rate limiting, account lock and timing-attack protection, token refresh rotation, logout blacklisting, password reset.
- `AuthControllerTest`, 19 web layer tests with `@WebMvcTest` and MockMvc: the real security configuration and JWT filter are loaded; covers validation errors, 401 without or with a revoked token, 429 and the authenticated endpoints.
- `MsPlatformApplicationTests`, integration tests with Testcontainers: the application starts against real PostgreSQL, Redis and Kafka containers, Flyway migrates the schema and Hibernate validates it. Docker must be running.

Requires JDK 25.

## Troubleshooting

### Common Issues

#### Kafka Connection Errors
**Symptoms:** "Rebootstrapping with Cluster" messages in logs

**Solutions:**
1. Ensure Kafka is running: `docker-compose ps`
2. Check Kafka logs: `docker logs kafka`
3. Wait 30-60 seconds after Kafka starts before running application
4. Restart Kafka: `docker-compose restart kafka`

#### Redis Connection Errors
**Symptoms:** Connection refused to localhost:6379

**Solutions:**
1. Verify Redis is running: `redis-cli ping`
2. Check Redis service status
3. Verify Redis port is not blocked by firewall

#### Database Connection Errors
**Symptoms:** Connection refused to PostgreSQL

**Solutions:**
1. Verify PostgreSQL is running
2. Check database exists: `psql -h localhost -U postgres -d lms_platform`
3. Verify credentials in configuration

#### Port Conflicts
**Symptoms:** "Address already in use" errors

**Solutions:**
1. Check port usage: `netstat -an | findstr :9092`
2. Stop conflicting services
3. Restart Docker containers: `docker-compose down && docker-compose up -d`

### Docker Issues
**Docker Desktop not starting:**
- Restart Docker Desktop
- Check if WSL2 is enabled on Windows
- Verify virtualization is enabled in BIOS

**Container startup failures:**
- Remove version attribute from docker-compose.yml (warning only)
- Check Docker logs: `docker logs kafka`
- Rebuild containers: `docker-compose down && docker-compose up -d --force-recreate`

## Development

### API Documentation
- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI spec: http://localhost:8080/api-docs

### Monitoring
- Health endpoints: http://localhost:8080/actuator/health
- Metrics: http://localhost:8080/actuator/metrics
- Info: http://localhost:8080/actuator/info

### Logging
Development profile shows SQL and security logs. Production profile uses WARN level.

## Production Deployment

### Environment Setup
1. Set production environment variables
2. Configure external PostgreSQL, Redis, and Kafka clusters
3. Use production profile: `-Dspring-boot.run.profiles=prod`

### Security
- Generate secure JWT secret: `openssl rand -base64 32`
- Configure proper CORS origins
- Use HTTPS in production
- Configure database credentials securely

## Architecture

### Technology Stack
- **Spring Boot 3.x** - Application framework
- **Spring Security** - Authentication and authorization
- **Spring Data JPA** - Database access
- **Spring Kafka** - Event messaging
- **PostgreSQL** - Primary database
- **Redis** - Caching layer
- **Docker** - Containerization (Kafka/Zookeeper)

### Messaging Pattern
The application uses event-driven architecture with Kafka for:
- Async communication between microservices
- Event sourcing for domain events
- Decoupled service dependencies

### Caching Strategy
Redis is used for:
- Session storage
- Query result caching
- Temporary data storage

## License

[Add your license information here]
