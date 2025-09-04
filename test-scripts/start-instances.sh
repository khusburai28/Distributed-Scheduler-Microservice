#!/bin/bash

# Script to start multiple instances of the scheduler
# Usage: ./start-instances.sh

JAR_FILE="../target/distributed-scheduler-1.0.0.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "❌ JAR file not found: $JAR_FILE"
    echo "Please run 'mvn clean package' first"
    exit 1
fi

echo "=== Starting Distributed Scheduler Instances ==="

# Start MS1 on port 8080
echo "Starting MS1 on port 8080..."
java -Dscheduler.server.port=8080 \
     -Dscheduler.instance.id=MS1 \
     -jar "$JAR_FILE" > logs/ms1.log 2>&1 &

MS1_PID=$!
echo "MS1 started with PID: $MS1_PID"

# Wait a bit before starting the second instance
sleep 5

# Start MS2 on port 8081
echo "Starting MS2 on port 8081..."
java -Dscheduler.server.port=8081 \
     -Dscheduler.instance.id=MS2 \
     -jar "$JAR_FILE" > logs/ms2.log 2>&1 &

MS2_PID=$!
echo "MS2 started with PID: $MS2_PID"

# Save PIDs for easy cleanup
echo "$MS1_PID" > ms1.pid
echo "$MS2_PID" > ms2.pid

echo -e "\n=== Both instances started ==="
echo "MS1: http://localhost:8080/sch (PID: $MS1_PID)"
echo "MS2: http://localhost:8081/sch (PID: $MS2_PID)"
echo -e "\nLogs:"
echo "MS1 logs: tail -f logs/ms1.log"
echo "MS2 logs: tail -f logs/ms2.log"
echo -e "\nTo stop instances: ./stop-instances.sh"

# Wait for both instances to start
echo -e "\nWaiting for instances to start..."
sleep 10

# Check if instances are responding
echo "Checking MS1 status..."
MS1_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/sch/jobs)
if [ "$MS1_STATUS" == "200" ]; then
    echo "✅ MS1 is running and responding"
else
    echo "❌ MS1 is not responding (HTTP $MS1_STATUS)"
fi

echo "Checking MS2 status..."
MS2_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8081/sch/jobs)
if [ "$MS2_STATUS" == "200" ]; then
    echo "✅ MS2 is running and responding"
else
    echo "❌ MS2 is not responding (HTTP $MS2_STATUS)"
fi

echo -e "\n=== Ready for testing ==="