# Chuti AI Module

This module provides AI-powered bot capabilities for Chuti using Large Language Models (LLMs) via Ollama and Langchain4j.

## Overview

The AI module enables intelligent computer players that understand Chuti game rules, make strategic decisions based on match position, and play competitively against human players or other bots.

### Key Features

- **Custom LLM with Baked-In Rules**: Uses a custom Ollama model (`chuti-llama3.2:1b`) with Chuti game rules embedded in the system prompt for zero per-request token cost
- **SystemMessage Fallback**: Supports standard models with rules sent via SystemMessage for development and testing
- **Optimized Prompts**: Context-only prompts (300 tokens vs 1000 tokens previously) - 70% reduction
- **Strategic Decision-Making**: Considers match position, player scores, and risk management
- **Legal Move Validation**: All LLM decisions are validated against legal moves; invalid moves fall back to DumbBot
- **Graceful Degradation**: Always falls back to DumbBot on LLM errors or timeouts

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                   NewAIBot / AIChutiBot              │
│  (decides when to use LLM vs auto-decisions)        │
└─────────────────┬───────────────────────────────────┘
                  │
         ┌────────┴────────┐
         │                 │
    ┌────▼─────┐    ┌─────▼──────┐
    │ LLMService│    │  DumbBot   │
    │           │    │ (fallback) │
    └────┬─────┘    └────────────┘
         │
    ┌────▼───────────────────────────────┐
    │         Ollama (HTTP API)          │
    │  ┌──────────────────────────────┐  │
    │  │  chuti-llama3.2:1b (custom)  │  │
    │  │  OR llama3.2:1b (standard)   │  │
    │  └──────────────────────────────┘  │
    └────────────────────────────────────┘
```

### Components

- **LLMService** (`ai/src/main/scala/ai/LLMService.scala`): Interface to Ollama via Langchain4j
- **OllamaConfig** (`ai/src/main/scala/ai/OllamaConfig.scala`): Configuration for model, temperature, timeout, etc.
- **PromptBuilder** (`server/src/main/scala/chuti/bots/llm/PromptBuilder.scala`): Builds minimal context-only prompts
- **MoveValidator** (`server/src/main/scala/chuti/bots/llm/MoveValidator.scala`): Validates moves and generates legal move lists
- **AIChutiBot** / **NewAIBot** (`server/src/main/scala/chuti/bots/`): Bot implementations with LLM integration
- **DumbChutiBot** (`server/src/main/scala/chuti/bots/DumbChutiBot.scala`): Heuristic-based fallback bot

## Setup

### Prerequisites

1. **Install Ollama**: Download from [ollama.ai](https://ollama.ai)
2. **Ensure Ollama is running**: `ollama --version`

### Option 1: Custom Model (Recommended for Production)

Build the custom `chuti-llama3.2:1b` model with game rules baked in:

```bash
# From project root
./scripts/setup-chuti-llm.sh
```

This will:
- Pull the base `llama3.2:1b` model if needed
- Create `chuti-llama3.2:1b` with embedded game rules
- Save to Ollama model library

**Configuration** (already set in `application.conf`):
```hocon
ai.ollama {
  modelName = "chuti-llama3.2:1b"  # Custom model
  useSystemMessage = false          # Rules baked in
  customModel = true                # Indicates custom model
}
```

**Benefits:**
- Zero token cost for rules (sent once at model creation)
- Faster inference (no system message overhead)
- Consistent rule context across all decisions

### Option 2: Standard Model with SystemMessage (Development/Testing)

Use the standard `llama3.2:1b` model with rules sent per request:

**Configuration** (via environment variables or config file):
```bash
export CHUTI_OLLAMA_MODEL="llama3.2:1b"
export CHUTI_OLLAMA_USE_SYSTEM_MESSAGE="true"
export CHUTI_OLLAMA_CUSTOM_MODEL="false"
```

Or in `application.conf`:
```hocon
ai.ollama {
  modelName = "llama3.2:1b"
  useSystemMessage = true
  customModel = false
}
```

**Benefits:**
- No custom model rebuild needed
- Easier to iterate on rule changes
- Works with any Ollama model

## Configuration

### Full Configuration Options

```hocon
ai {
  ollama {
    # Ollama server URL
    baseUrl = "http://localhost:11434"
    baseUrl = ${?CHUTI_OLLAMA_URL}

    # Model name (custom or standard)
    modelName = "chuti-llama3.2:1b"
    modelName = ${?CHUTI_OLLAMA_MODEL}

    # Temperature (0.0-1.0, higher = more creative)
    temperature = 0.7

    # Max tokens in response
    maxTokens = 500

    # Request timeout
    timeout = "2 minute"

    # Send rules via SystemMessage (fallback mode)
    useSystemMessage = false
    useSystemMessage = ${?CHUTI_OLLAMA_USE_SYSTEM_MESSAGE}

    # Indicates custom model (rules baked in)
    customModel = true
    customModel = ${?CHUTI_OLLAMA_CUSTOM_MODEL}
  }
}
```

### Environment Variables

- `CHUTI_OLLAMA_URL`: Ollama server URL (default: `http://localhost:11434`)
- `CHUTI_OLLAMA_MODEL`: Model name (default: `chuti-llama3.2:1b`)
- `CHUTI_OLLAMA_USE_SYSTEM_MESSAGE`: Use SystemMessage for rules (default: `false`)
- `CHUTI_OLLAMA_CUSTOM_MODEL`: Custom model flag (default: `true`)

## How It Works

### 1. Decision Flow

```
User turn → AIChutiBot.decideTurn()
              │
              ├─→ Check auto-decisions (only 1 legal move, obvious play)
              │   └─→ Return immediate decision (skip LLM)
              │
              └─→ Call LLM
                   │
                   ├─→ Build minimal prompt (game state, legal moves, strategic context)
                   │
                   ├─→ LLMService.generate()
                   │    │
                   │    ├─→ Custom model: Send user message only
                   │    └─→ Standard model: Send system message (rules) + user message
                   │
                   ├─→ Parse JSON response
                   ├─→ Validate against legal moves
                   └─→ On error/timeout: Fall back to DumbBot
```

### 2. Prompt Structure

**Before refactor (1000 tokens):**
- Rule explanations (~500 tokens)
- Few-shot examples (~300 tokens)
- Strategic heuristics (~100 tokens)
- Game state + legal moves (~100 tokens)

**After refactor (300 tokens):**
- Strategic context (scores, positions) (~50 tokens)
- Game state (hand, trump, table) (~100 tokens)
- Legal moves (JSON) (~100 tokens)
- Conservative recommendation (~50 tokens)

**Rules are now in system prompt (custom model) or SystemMessage (fallback).**

### 3. Legal Move Validation

All LLM responses are validated by `MoveValidator`:
- Ensures chosen move is in legal moves list
- Validates tiles are in player's hand
- Checks game phase and rules compliance
- Rejects invalid moves → falls back to DumbBot

### 4. Fallback Strategy

If LLM fails (timeout, parsing error, invalid move):
1. Log error with details
2. Call `DumbBot.decideTurn()` for safe heuristic decision
3. Continue game without interruption

DumbBot uses conservative heuristics:
- Bid based on "de caída" (guaranteed tricks)
- Play highest trump/mula
- Follow suit with lowest tile to save strong tiles
- Always fall (caete) when able

## Testing

### Unit Tests

```bash
# Test AI bot decisions
sbt --error "server/testOnly chuti.bots.AIBotGameSpec"

# Test move validator
sbt --error "server/testOnly chuti.bots.MoveValidatorSpec"

# Test all bot specs
sbt --error "server/testOnly chuti.bots.*"
```

### Manual Testing

```bash
# Test custom model directly
ollama run chuti-llama3.2:1b "Explain the Chuti bidding rules"

# List all models
ollama list

# Check Ollama server
curl http://localhost:11434/api/tags
```

### Integration Testing

Start server and play games via web UI or API to observe bot behavior in real games.

## Performance

### Token Usage

- **Before refactor**: ~1000 tokens per decision
- **After refactor (custom model)**: ~300 tokens per decision (70% reduction)
- **After refactor (SystemMessage)**: ~800 tokens per decision (20% reduction)

### Latency

- **Custom model**: ~200ms faster per decision (no system message overhead)
- **Standard model**: Similar to before

### LLM Call Frequency

- **Auto-decisions**: ~40% of turns skip LLM (obvious plays, single legal move)
- **LLM decisions**: ~60% of turns require strategic thinking

## Troubleshooting

### Ollama Not Found

```bash
Error: Ollama not found. Please install from https://ollama.ai
```

**Solution**: Install Ollama and ensure it's in your PATH.

### Model Not Found

```bash
Error: model 'chuti-llama3.2:1b' not found
```

**Solution**: Run `./scripts/setup-chuti-llm.sh` to build the custom model.

### LLM Timeout

```
LLM timeout for bot player1, using DumbBot recommendation
```

**Solution**:
- Check Ollama is running: `ollama list`
- Increase timeout in config: `ai.ollama.timeout = "2 minutes"`
- Use smaller model: `llama3.2:1b` is already the smallest

### Invalid JSON Response

```
Failed to parse LLM response as JSON: ...
```

**Solution**: This is expected occasionally with small models. The bot automatically falls back to DumbBot. If it happens frequently (>20%), consider:
- Adjusting temperature (lower = more consistent)
- Using a larger model (e.g., `llama3.2:3b`)

### Rules Resource Not Found

```
Game rules resource not found at /chuti_game_rules.txt
```

**Solution**: Ensure `ai/src/main/resources/chuti_game_rules.txt` exists and is included in the JAR.

## Updating Game Rules

When game rules change:

1. **Update rules file**: Edit `ai/src/main/resources/chuti_game_rules.txt`

2. **Update Modelfile**: Edit `ai/Modelfile.chuti` SYSTEM block with new rules

3. **Rebuild custom model**:
   ```bash
   ollama create chuti-llama3.2:1b -f ai/Modelfile.chuti
   ```

4. **Optional - Tag version**:
   ```bash
   ollama tag chuti-llama3.2:1b chuti-llama3.2:1b-v2
   ```

5. **Restart server** to pick up changes

## Future Enhancements

### Phase 5: Training Data Collection (Planned)

- Collect game transcripts with LLM decisions and reasoning
- Store in database for analysis and fine-tuning
- Export to JSONL for supervised fine-tuning
- Build vector database for RAG-based few-shot examples
- Web UI for expert annotation of moves

### Other Possibilities

- Fine-tuning on real Chuti games for better decision quality
- Larger models (3b, 7b) for higher-stakes games
- Multi-model ensemble with weighted voting
- Game history in context window
- Real-time learning from game outcomes

## License

Copyright 2020 Roberto Leibman

Licensed under the Apache License, Version 2.0.
