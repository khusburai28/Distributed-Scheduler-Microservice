#!/bin/bash

# Distributed Operations Test Script
# Tests cross-instance job management between MS1 and MS2

MS1_URL="http://localhost:8080/sch"
MS2_URL="http://localhost:8081/sch"

echo "=== Testing Distributed Operations ==="

# Ensure both instances are running
echo -e "\n0. Checking if both instances are running..."
MS1_STATUS=$(curl -s -o /dev/null -w "%{http_code}" $MS1_URL/jobs)
MS2_STATUS=$(curl -s -o /dev/null -w "%{http_code}" $MS2_URL/jobs)

if [ "$MS1_STATUS" != "200" ]; then
    echo "❌ MS1 is not running or not accessible at $MS1_URL"
    exit 1
fi

if [ "$MS2_STATUS" != "200" ]; then
    echo "❌ MS2 is not running or not accessible at $MS2_URL"
    exit 1
fi

echo "✅ Both instances are running"

# Test 1: Create job on MS1
echo -e "\n1. Creating job on MS1..."
RESPONSE=$(curl -s -X POST $MS1_URL/create \
  -H "Content-Type: application/json" \
  -d "{
    \"jobId\": \"distributed-job-001\",
    \"jobName\": \"Distributed Test Job\",
    \"jobGroup\": \"distributed\",
    \"scheduleTime\": \"$(date -d '+3 hours' '+%Y-%m-%dT%H:%M:%S')\",
    \"description\": \"Job created on MS1 for distributed testing\",
    \"recurring\": false
  }")

echo "$RESPONSE" | jq .

# Test 2: Verify job exists on MS1
echo -e "\n2. Verifying job exists on MS1..."
curl -s "$MS1_URL/jobs" | jq .

# Test 3: Verify job does NOT exist on MS2
echo -e "\n3. Verifying job does NOT exist on MS2..."
curl -s "$MS2_URL/jobs" | jq .

# Test 4: Reschedule job from MS2 (distributed operation)
echo -e "\n4. Rescheduling job from MS2 (distributed operation)..."
curl -s -X POST $MS2_URL/reschedule \
  -H "Content-Type: application/json" \
  -d "{
    \"jobId\": \"distributed-job-001\",
    \"jobGroup\": \"distributed\",
    \"newScheduleTime\": \"$(date -d '+4 hours' '+%Y-%m-%dT%H:%M:%S')\"
  }" | jq .

# Give some time for the distributed operation to complete
sleep 2

# Test 5: Create another job on MS2
echo -e "\n5. Creating job on MS2..."
curl -s -X POST $MS2_URL/create \
  -H "Content-Type: application/json" \
  -d "{
    \"jobId\": \"distributed-job-002\",
    \"jobName\": \"Another Distributed Job\",
    \"jobGroup\": \"distributed\",
    \"scheduleTime\": \"$(date -d '+5 hours' '+%Y-%m-%dT%H:%M:%S')\",
    \"description\": \"Job created on MS2\",
    \"recurring\": false
  }" | jq .

# Test 6: Cancel MS1's job from MS2 (distributed operation)
echo -e "\n6. Cancelling MS1's job from MS2 (distributed operation)..."
curl -s -X POST $MS2_URL/cancel \
  -H "Content-Type: application/json" \
  -d "{
    \"jobId\": \"distributed-job-001\",
    \"jobGroup\": \"distributed\"
  }" | jq .

# Give some time for the distributed operation to complete
sleep 2

# Test 7: Cancel MS2's job from MS1 (distributed operation)
echo -e "\n7. Cancelling MS2's job from MS1 (distributed operation)..."
curl -s -X POST $MS1_URL/cancel \
  -H "Content-Type: application/json" \
  -d "{
    \"jobId\": \"distributed-job-002\",
    \"jobGroup\": \"distributed\"
  }" | jq .

# Give some time for the distributed operation to complete
sleep 2

# Test 8: Verify all jobs are cancelled
echo -e "\n8. Verifying all jobs are cancelled on MS1..."
curl -s "$MS1_URL/jobs" | jq .

echo -e "\n9. Verifying all jobs are cancelled on MS2..."
curl -s "$MS2_URL/jobs" | jq .

# Test 9: Test non-existent job operation
echo -e "\n10. Testing cancellation of non-existent job..."
curl -s -X POST $MS1_URL/cancel \
  -H "Content-Type: application/json" \
  -d "{
    \"jobId\": \"non-existent-job\",
    \"jobGroup\": \"test\"
  }" | jq .

echo -e "\n=== Distributed Operations Test Complete ==="