#!/bin/bash
# Test Ollama LLM service for Chuti bot

set -e

OLLAMA_URL="${CHUTI_OLLAMA_URL:-http://localhost:11434}"
MODEL_NAME="${CHUTI_OLLAMA_MODEL:-chuti-llama3.2:1b}"

echo "🔍 Testing Ollama service at $OLLAMA_URL"
echo "📦 Using model: $MODEL_NAME"
echo ""

# Check if Ollama is running
echo "1️⃣  Checking if Ollama is responding..."
if curl -s "$OLLAMA_URL/api/tags" > /dev/null 2>&1; then
    echo "   ✅ Ollama service is running"
else
    echo "   ❌ Ollama service is NOT responding at $OLLAMA_URL"
    echo "   Make sure Ollama is running: ollama serve"
    exit 1
fi

# List available models
echo ""
echo "2️⃣  Available models:"
curl -s "$OLLAMA_URL/api/tags" | jq -r '.models[] | "   - \(.name) (size: \(.size / 1024 / 1024 | floor)MB)"' 2>/dev/null || echo "   (Install jq for formatted output)"

# Check if our model exists
echo ""
echo "3️⃣  Checking if $MODEL_NAME is available..."
if curl -s "$OLLAMA_URL/api/tags" | grep -q "\"$MODEL_NAME\""; then
    echo "   ✅ Model $MODEL_NAME is available"
else
    echo "   ⚠️  Model $MODEL_NAME NOT found"
    echo "   Pull it with: ollama pull $MODEL_NAME"
    exit 1
fi

# Test a simple query
echo ""
echo "4️⃣  Testing simple query to $MODEL_NAME..."
RESPONSE=$(curl -s "$OLLAMA_URL/api/generate" \
  -H "Content-Type: application/json" \
  -d "{
    \"model\": \"$MODEL_NAME\",
    \"prompt\": \"Respond with ONLY valid JSON: {\\\"test\\\": \\\"success\\\", \\\"reasoning\\\": \\\"This is a test\\\"}\",
    \"stream\": false,
    \"format\": \"json\"
  }")

if echo "$RESPONSE" | grep -q "success"; then
    echo "   ✅ Model responded successfully"
    echo "   Response preview:"
    echo "$RESPONSE" | jq -r '.response' 2>/dev/null | head -c 200 || echo "$RESPONSE" | head -c 200
    echo ""
else
    echo "   ❌ Model did not respond as expected"
    echo "   Response: $RESPONSE"
    exit 1
fi

# Check response time
echo ""
echo "5️⃣  Testing response time (game-like query)..."
START=$(date +%s%N)
curl -s "$OLLAMA_URL/api/generate" \
  -H "Content-Type: application/json" \
  -d "{
    \"model\": \"$MODEL_NAME\",
    \"prompt\": \"You are playing a domino game. Your hand: [3:3, 3:2, 4:3, 5:3]. Choose which tile to play. Respond with ONLY JSON: {\\\"type\\\": \\\"pide\\\", \\\"ficha\\\": \\\"3:3\\\", \\\"reasoning\\\": \\\"Playing strongest tile\\\"}\",
    \"stream\": false,
    \"format\": \"json\"
  }" > /dev/null
END=$(date +%s%N)
DURATION=$(( (END - START) / 1000000 ))

echo "   ⏱️  Response time: ${DURATION}ms"

if [ $DURATION -lt 5000 ]; then
    echo "   ✅ Response time is good (< 5 seconds)"
elif [ $DURATION -lt 15000 ]; then
    echo "   ⚠️  Response time is slow (5-15 seconds)"
else
    echo "   ❌ Response time is very slow (> 15 seconds)"
    echo "   Consider using a faster model or checking system resources"
fi

echo ""
echo "✅ All tests passed! Ollama is ready for Chuti bots."
echo ""
echo "To monitor LLM calls in real-time:"
echo "  - Check server logs for 🤖 emoji markers"
echo "  - Watch Ollama logs: journalctl -u ollama -f (if using systemd)"
echo "  - Or: tail -f /var/log/ollama.log (if configured)"
