# FitFlow Local Setup Guide

This guide describes local setup, environment configuration, database bootstrapping, and operational commands for running the FitFlow backend monolith on **Windows (PowerShell)** and Unix-like environments.

---

## 1. Prerequisites & Services

The FitFlow backend runs against real local services:
- **Java**: OpenJDK 17 LTS (or 21)
- **Maven**: 3.9+
- **MySQL**: 8.0+ running on port `3306`
- **PostgreSQL**: 16/17 running on port `5432`
- **RabbitMQ**: 3.12+ running on port `5672` (management UI on `15672`)

### Windows Service Management
On Windows, MySQL, PostgreSQL, and RabbitMQ typically run as Windows Services:
```powershell
# Check service statuses
Get-Service MySQL80, postgresql-x64-17, RabbitMQ

# Start services if stopped
Start-Service MySQL80
Start-Service postgresql-x64-17
Start-Service RabbitMQ

# Enable RabbitMQ Management Plugin (if not already enabled)
& "C:\Program Files\RabbitMQ Server\rabbitmq_server-*\sbin\rabbitmq-plugins.bat" enable rabbitmq_management
```

---

## 2. Database Initialization

Execute the SQL setup scripts to create the application and test schemas with restricted user privileges:

### MySQL
```powershell
# Run from repository root
Get-Content scripts/setup-local-db.mysql.sql | mysql -u root -p
```

### PostgreSQL
Ensure `psql` is in your PATH (e.g. `C:\Program Files\PostgreSQL\17\bin`):
```powershell
# Run from repository root
& "C:\Program Files\PostgreSQL\17\bin\psql.exe" -U postgres -f scripts/setup-local-db.pg.sql
```

---

## 3. Environment Variables Configuration

Create a `.env` file in the project root by copying `.env.example`:
```powershell
Copy-Item .env.example .env
```

### Loading Environment Variables in Shell

#### PowerShell:
```powershell
# Read and load .env into current session
Get-Content .env | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
        $parts = $line.Split("=", 2)
        [Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1].Trim())
    }
}
```

#### Bash:
```bash
export $(grep -v '^#' .env | xargs)
```

---

## 4. Running the Application

### Start in Development Mode:
```powershell
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### Running Verification Suite:
```powershell
# PowerShell
.\scripts\verify.ps1

# Bash
./scripts/verify.sh
```

---

## 5. Development Credentials & Endpoints

### Default Synthetic Dev Accounts
The dev runner seeds the following accounts on first boot:
- **Default Password**: `FitFlowDev2026!`
- **Admin**: `dev@fitflow.local` (Roles: `ADMIN`, Permission: `SUPER_ADMIN`, `must_change_password=true`)
- **Trainer 1**: `trainer.alex@fitflow.local` (Specialties: Strength, Mobility)
- **Trainer 2**: `trainer.sam@fitflow.local` (Specialties: Yoga, Pilates)

### Operational Endpoints (Dev Profile)
- **Actuator Health**: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)
- **Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI JSON**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- **Dev Mail Viewer (GreenMail)**: [http://localhost:8080/dev/mail](http://localhost:8080/dev/mail)
- **RabbitMQ Management Console**: [http://localhost:15672](http://localhost:15672) (guest / guest)

---

## 6. Troubleshooting

### Port in Use (e.g. 8080, 3306, 5432)
Check which PID holds the port:
```powershell
netstat -ano | findstr :8080
taskkill /PID <PID> /F
```

### Authentication Failed / Wrong Password
- If MySQL user `fitflow_app` fails to authenticate, re-run `setup-local-db.mysql.sql` using `mysql -u root -p`.
- Verify database password in `.env` matches the password specified in `setup-local-db.*.sql`.

### Flyway Checksum Mismatch
- **Rule R11**: Never edit an already-applied Flyway migration script. Always add a new versioned file (e.g., `V2__xxx.sql`).
- In local development only, if you need a clean slate:
  ```sql
  -- In MySQL
  DROP DATABASE fitflow;
  DROP DATABASE fitflow_test;
  -- Then re-run setup-local-db.mysql.sql
  ```
