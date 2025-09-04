#!/bin/bash

# Complete test suite for the Distributed Scheduler
# This script runs all tests in sequence

echo "=== Distributed Scheduler Complete Test Suite ==="

# Check if jq is available for JSON formatting
if ! command -v jq &> /dev/null; then
    echo "⚠️ jq is not installed. Installing jq for better JSON formatting..."
    # Try to install jq (this works on many systems)
    if command -v brew &> /dev/null; then
        brew install jq
    elif command -v apt-get &> /dev/null; then
        sudo apt-get install -y jq
    else
        echo "Please install jq manually for better output formatting"
    fi
fi

# Create logs directory
mkdir -p logs

echo -e "\n1. Building the application..."
cd ..
mvn clean package -q
BUILD_STATUS=$?

if [ $BUILD_STATUS -ne 0 ]; then
    echo "❌ Build failed. Please fix compilation errors and try again."
    exit 1
fi

echo "✅ Build successful"

cd test-scripts

echo -e "\n2. Starting instances..."
./start-instances.sh

# Wait for instances to be fully ready
sleep 15

echo -e "\n3. Running basic operations test on MS1..."
./test-basic-operations.sh 8080 MS1

echo -e "\n4. Running basic operations test on MS2..."
./test-basic-operations.sh 8081 MS2

echo -e "\n5. Running distributed operations test..."
./test-distributed-operations.sh

echo -e "\n6. Stopping instances..."
./stop-instances.sh

echo -e "\n=== Test Suite Complete ==="
echo -e "\nCheck the following log files for detailed information:"
echo "- Application logs: logs/scheduler.log"
echo "- MS1 logs: logs/ms1.log"  
echo "- MS2 logs: logs/ms2.log"