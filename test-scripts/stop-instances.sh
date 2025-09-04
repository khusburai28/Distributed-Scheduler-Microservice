#!/bin/bash

# Script to stop all running scheduler instances

echo "=== Stopping Distributed Scheduler Instances ==="

# Stop MS1
if [ -f "ms1.pid" ]; then
    MS1_PID=$(cat ms1.pid)
    echo "Stopping MS1 (PID: $MS1_PID)..."
    if kill $MS1_PID 2>/dev/null; then
        echo "✅ MS1 stopped successfully"
    else
        echo "⚠️ MS1 process not found or already stopped"
    fi
    rm -f ms1.pid
else
    echo "⚠️ MS1 PID file not found"
fi

# Stop MS2
if [ -f "ms2.pid" ]; then
    MS2_PID=$(cat ms2.pid)
    echo "Stopping MS2 (PID: $MS2_PID)..."
    if kill $MS2_PID 2>/dev/null; then
        echo "✅ MS2 stopped successfully"
    else
        echo "⚠️ MS2 process not found or already stopped"
    fi
    rm -f ms2.pid
else
    echo "⚠️ MS2 PID file not found"
fi

# Alternative: Kill any running scheduler processes
echo -e "\nKilling any remaining scheduler processes..."
pkill -f "distributed-scheduler" && echo "✅ Killed remaining processes" || echo "ℹ️ No additional processes found"

echo -e "\n=== All instances stopped ==="