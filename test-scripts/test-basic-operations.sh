#!/bin/bash

# Basic Operations Test Script for Distributed Scheduler
# Usage: ./test-basic-operations.sh [port] [instance-name]

PORT=${1:-8080}
INSTANCE=${2:-MS1}
BASE_URL="http://localhost:$PORT/sch"

echo "=== Testing Basic Operations on $INSTANCE (Port: $PORT) ==="

# Test 1: Create Job
echo -e "\n1. Creating job..."
RESPONSE=$(curl -s -X POST $BASE_URL/create \
  -H "Content-Type: application/json" \
  -d "{
    \"jobId\": \"basic-test-job-$INSTANCE\",
    \"jobName\": \"Basic Test Job\",
    \"jobGroup\": \"basic-test\",
    \"scheduleTime\": \"$(date -d '+1 hour' '+%Y-%m-%dT%H:%M:%S')\",
    \"description\": \"Basic test job for $INSTANCE\",
    \"recurring\": false
  }")

echo "$RESPONSE" | jq .

# Test 2: Check Job Status
echo -e "\n2. Checking job status..."
curl -s "$BASE_URL/status/basic-test-job-$INSTANCE" | jq .

# Test 3: Get All Jobs
echo -e "\n3. Getting all jobs..."
curl -s "$BASE_URL/jobs" | jq .

# Test 4: Reschedule Job
echo -e "\n4. Rescheduling job..."
curl -s -X POST $BASE_URL/reschedule \
  -H "Content-Type: application/json" \
  -d "{
    \"jobId\": \"basic-test-job-$INSTANCE\",
    \"jobGroup\": \"basic-test\",
    \"newScheduleTime\": \"$(date -d '+2 hours' '+%Y-%m-%dT%H:%M:%S')\"
  }" | jq .

# Test 5: Cancel Job
echo -e "\n5. Cancelling job..."
curl -s -X POST $BASE_URL/cancel \
  -H "Content-Type: application/json" \
  -d "{
    \"jobId\": \"basic-test-job-$INSTANCE\",
    \"jobGroup\": \"basic-test\"
  }" | jq .

# Test 6: Verify Job Cancellation
echo -e "\n6. Verifying job cancellation..."
curl -s "$BASE_URL/jobs" | jq .

echo -e "\n=== Basic Operations Test Complete for $INSTANCE ==="