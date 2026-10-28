#!/usr/bin/env bash
# ==============================================================================
# FitFlow Stage 4 Live Endpoint Walkthrough Script (Bash)
# ==============================================================================
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
echo "=== FitFlow Stage 4 Live Walkthrough ==="

# 1. Health check
echo "1. Checking application health..."
curl -s "${BASE_URL}/actuator/health" | grep -q '"status":"UP"'
echo "Health: UP"

# 2. Authenticate Admin and Trainer
echo "2. Authenticating Admin and Trainer Alex..."
ADMIN_TOKEN=$(curl -s -X POST "${BASE_URL}/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"dev@fitflow.local","password":"FitFlowDev2026!"}' | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)

ALEX_RESP=$(curl -s -X POST "${BASE_URL}/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"trainer.alex@fitflow.local","password":"FitFlowDev2026!"}')
ALEX_TOKEN=$(echo "$ALEX_RESP" | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
ALEX_ID=$(echo "$ALEX_RESP" | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)

echo "Admin and Trainer Alex authenticated successfully."

# 3. Configure Trainer Availability
echo "3. Configuring Trainer Alex Availability..."
curl -s -X PATCH "${BASE_URL}/api/v1/trainer/me" \
  -H "Authorization: Bearer ${ALEX_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "availability": [
      {"dayOfWeek": 1, "start": "06:00", "end": "22:00"},
      {"dayOfWeek": 2, "start": "06:00", "end": "22:00"},
      {"dayOfWeek": 3, "start": "06:00", "end": "22:00"},
      {"dayOfWeek": 4, "start": "06:00", "end": "22:00"},
      {"dayOfWeek": 5, "start": "06:00", "end": "22:00"},
      {"dayOfWeek": 6, "start": "06:00", "end": "22:00"},
      {"dayOfWeek": 7, "start": "06:00", "end": "22:00"}
    ]
  }' > /dev/null

echo "Trainer Alex availability configured."

# 4. Admin creates Class
echo "4. Admin creates Fitness Class..."
RANDOM_TAG=$((1000 + RANDOM % 9000))
CLASS_RESP=$(curl -s -X POST "${BASE_URL}/api/v1/admin/classes" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{
    \"name\": \"Live Stage 4 HIIT Flow ${RANDOM_TAG}\",
    \"description\": \"High intensity interval training with mobility recovery\",
    \"category\": \"HIIT\",
    \"durationMinutes\": 60,
    \"intensity\": \"HIGH\",
    \"impactLevel\": \"HIGH\",
    \"metValue\": 8.5,
    \"contraindicationTags\": [\"high_impact\", \"high_cardio\"],
    \"minAge\": 16,
    \"defaultCapacity\": 20
  }")
CLASS_ID=$(echo "$CLASS_RESP" | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)
echo "Class created with id: ${CLASS_ID}"

# 5. Admin creates 1-capacity session
echo "5. Admin creates 1-Capacity Session for Waitlist Demo..."
STARTS_AT=$(date -u -d "+3 days" "+%Y-%m-%dT10:00:00Z" 2>/dev/null || date -u -v+3d "+%Y-%m-%dT10:00:00Z")
SESSION_RESP=$(curl -s -X POST "${BASE_URL}/api/v1/admin/sessions" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{
    \"classId\": ${CLASS_ID},
    \"trainerId\": ${ALEX_ID},
    \"startsAt\": \"${STARTS_AT}\",
    \"capacity\": 1,
    \"room\": \"VIP Studio ${RANDOM_TAG}\"
  }")
SESSION_ID=$(echo "$SESSION_RESP" | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)
echo "Session created with id: ${SESSION_ID}"

# 6. Register 2 Members
echo "6. Registering and authenticating Members..."
M1_EMAIL="m1.${RANDOM_TAG}@fitflow.local"
M2_EMAIL="m2.${RANDOM_TAG}@fitflow.local"
curl -s -X POST "${BASE_URL}/api/v1/auth/register" -H "Content-Type: application/json" \
  -d "{\"email\":\"${M1_EMAIL}\",\"password\":\"FitFlowMember2026!\",\"fullName\":\"Member One\",\"dateOfBirth\":\"1998-01-01\"}" > /dev/null
curl -s -X POST "${BASE_URL}/api/v1/auth/register" -H "Content-Type: application/json" \
  -d "{\"email\":\"${M2_EMAIL}\",\"password\":\"FitFlowMember2026!\",\"fullName\":\"Member Two\",\"dateOfBirth\":\"1997-02-02\"}" > /dev/null

M1_TOKEN=$(curl -s -X POST "${BASE_URL}/api/v1/auth/login" -H "Content-Type: application/json" \
  -d "{\"email\":\"${M1_EMAIL}\",\"password\":\"FitFlowMember2026!\"}" | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
M2_TOKEN=$(curl -s -X POST "${BASE_URL}/api/v1/auth/login" -H "Content-Type: application/json" \
  -d "{\"email\":\"${M2_EMAIL}\",\"password\":\"FitFlowMember2026!\"}" | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)

# 7. Member 1 books session -> 201
echo "7. Member 1 books session (Expect 201 CONFIRMED)..."
BOOK1_RESP=$(curl -s -X POST "${BASE_URL}/api/v1/bookings" \
  -H "Authorization: Bearer ${M1_TOKEN}" \
  -H "Idempotency-Key: key-live-m1-${RANDOM_TAG}" \
  -H "Content-Type: application/json" \
  -d "{\"sessionId\": ${SESSION_ID}}")
BOOK1_ID=$(echo "$BOOK1_RESP" | grep -o '"bookingId":[0-9]*' | cut -d':' -f2)
echo "Member 1 Booking: ${BOOK1_RESP}"

# 8. Member 2 books session -> 202 WAITLISTED
echo "8. Member 2 books session (Expect 202 WAITLISTED)..."
BOOK2_RESP=$(curl -s -w "\nHTTP_STATUS:%{http_code}\n" -X POST "${BASE_URL}/api/v1/bookings" \
  -H "Authorization: Bearer ${M2_TOKEN}" \
  -H "Idempotency-Key: key-live-m2-${RANDOM_TAG}" \
  -H "Content-Type: application/json" \
  -d "{\"sessionId\": ${SESSION_ID}}")
echo "Member 2 Booking: ${BOOK2_RESP}"

# 9. Member 1 cancels booking
echo "9. Member 1 cancels booking (Triggers waitlist promotion)..."
curl -s -X DELETE "${BASE_URL}/api/v1/bookings/${BOOK1_ID}" \
  -H "Authorization: Bearer ${M1_TOKEN}"
echo "Booking cancelled."

# 10. Verify Member 2 promoted
echo "10. Verifying Member 2 promoted..."
M2_BOOKINGS=$(curl -s -X GET "${BASE_URL}/api/v1/me/bookings" -H "Authorization: Bearer ${M2_TOKEN}")
echo "Member 2 bookings: ${M2_BOOKINGS}"

echo "=== Live Walkthrough Script Ready ==="
