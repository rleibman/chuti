#!/bin/bash
set -e

echo "========================================="
echo "Setting up Chuti LLM model"
echo "========================================="
echo ""

# Check if Ollama is installed
if ! command -v ollama &> /dev/null; then
    echo "❌ Error: Ollama not found."
    echo "Please install Ollama from https://ollama.ai"
    exit 1
fi

echo "✅ Ollama found: $(ollama --version)"
echo ""

# Check if base model exists
echo "Checking for base model llama3.2:1b..."
if ! ollama list | grep -q "llama3.2:1b"; then
    echo "📥 Base model not found. Pulling llama3.2:1b..."
    echo "This may take a few minutes depending on your connection..."
    ollama pull llama3.2:1b
    echo "✅ Base model pulled successfully"
else
    echo "✅ Base model llama3.2:1b already exists"
fi
echo ""

# Navigate to project root
cd "$(dirname "$0")/.."
PROJECT_ROOT=$(pwd)

# Verify Modelfile exists
if [ ! -f "ai/Modelfile.chuti" ]; then
    echo "❌ Error: Modelfile not found at ai/Modelfile.chuti"
    echo "Please ensure the project structure is correct."
    exit 1
fi

echo "📦 Building custom model chuti-llama3.2:1b with game rules..."
echo "This will create a model with Chuti game rules embedded in the system prompt."
echo ""

# Build custom model
ollama create chuti-llama3.2:1b -f ai/Modelfile.chuti

echo ""
echo "========================================="
echo "✅ Custom Chuti model created successfully!"
echo "========================================="
echo ""
echo "Model: chuti-llama3.2:1b"
echo "Location: Ollama model library"
echo ""
echo "To use this model, update your application.conf:"
echo "  ai.ollama.modelName = \"chuti-llama3.2:1b\""
echo "  ai.ollama.useSystemMessage = false"
echo ""
echo "Or set environment variables:"
echo "  export CHUTI_OLLAMA_MODEL=\"chuti-llama3.2:1b\""
echo "  export CHUTI_OLLAMA_USE_SYSTEM_MESSAGE=\"false\""
echo ""
echo "To test the model directly:"
echo "  ollama run chuti-llama3.2:1b"
echo ""
echo "To view all models:"
echo "  ollama list"
echo ""
