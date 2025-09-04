#!/bin/bash

# Kafka setup script for the Distributed Scheduler
# This script helps set up Kafka topics required for the application

KAFKA_HOME=${KAFKA_HOME:-""}
BOOTSTRAP_SERVER=${BOOTSTRAP_SERVER:-"localhost:9092"}

echo "=== Setting up Kafka for Distributed Scheduler ==="

if [ -z "$KAFKA_HOME" ]; then
    echo "⚠️ KAFKA_HOME environment variable is not set."
    echo "Please set KAFKA_HOME to your Kafka installation directory."
    echo "Example: export KAFKA_HOME=/usr/local/kafka"
    echo "Or provide the path to kafka-topics.sh script manually."
    exit 1
fi

KAFKA_TOPICS="$KAFKA_HOME/bin/kafka-topics.sh"

if [ ! -f "$KAFKA_TOPICS" ]; then
    echo "❌ kafka-topics.sh not found at: $KAFKA_TOPICS"
    echo "Please check your KAFKA_HOME path or Kafka installation."
    exit 1
fi

echo "Using Kafka topics script: $KAFKA_TOPICS"
echo "Bootstrap server: $BOOTSTRAP_SERVER"

# Test Kafka connection
echo -e "\n1. Testing Kafka connection..."
if $KAFKA_TOPICS --list --bootstrap-server $BOOTSTRAP_SERVER &> /dev/null; then
    echo "✅ Kafka is accessible"
else
    echo "❌ Cannot connect to Kafka at $BOOTSTRAP_SERVER"
    echo "Please ensure Kafka is running and accessible."
    exit 1
fi

# Create scheduler-requests topic
echo -e "\n2. Creating scheduler-requests topic..."
if $KAFKA_TOPICS --create --topic scheduler-requests \
    --bootstrap-server $BOOTSTRAP_SERVER \
    --partitions 3 \
    --replication-factor 1 2>/dev/null; then
    echo "✅ scheduler-requests topic created"
else
    echo "⚠️ scheduler-requests topic already exists or creation failed"
fi

# Create scheduler-responses topic
echo -e "\n3. Creating scheduler-responses topic..."
if $KAFKA_TOPICS --create --topic scheduler-responses \
    --bootstrap-server $BOOTSTRAP_SERVER \
    --partitions 3 \
    --replication-factor 1 2>/dev/null; then
    echo "✅ scheduler-responses topic created"
else
    echo "⚠️ scheduler-responses topic already exists or creation failed"
fi

# List all topics to verify
echo -e "\n4. Verifying topics..."
echo "Available topics:"
$KAFKA_TOPICS --list --bootstrap-server $BOOTSTRAP_SERVER

# Check if our topics exist
TOPICS_LIST=$($KAFKA_TOPICS --list --bootstrap-server $BOOTSTRAP_SERVER)
if echo "$TOPICS_LIST" | grep -q "scheduler-requests"; then
    echo "✅ scheduler-requests topic verified"
else
    echo "❌ scheduler-requests topic not found"
fi

if echo "$TOPICS_LIST" | grep -q "scheduler-responses"; then
    echo "✅ scheduler-responses topic verified"
else
    echo "❌ scheduler-responses topic not found"
fi

echo -e "\n=== Kafka setup complete ==="
echo -e "\nTo monitor messages during testing:"
echo "  Requests: $KAFKA_HOME/bin/kafka-console-consumer.sh --bootstrap-server $BOOTSTRAP_SERVER --topic scheduler-requests"
echo "  Responses: $KAFKA_HOME/bin/kafka-console-consumer.sh --bootstrap-server $BOOTSTRAP_SERVER --topic scheduler-responses"