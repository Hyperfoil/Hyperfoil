---
title: Benchmarking LLM Conversation APIs
description: How to benchmark multi-turn LLM conversations with Server-Sent Events streaming
categories: [HowTo]
tags: [how-to, guides, llm, sse, streaming, conversations]
weight: 2
---

Hyperfoil supports benchmarking modern Large Language Model (LLM) APIs that use Server-Sent Events (SSE) for streaming responses. This guide shows you how to build realistic multi-turn conversation scenarios to measure the performance and reliability of LLM-based services.

## Overview

LLM conversation APIs typically stream responses in real-time using Server-Sent Events, where tokens are sent incrementally as the model generates them. To benchmark these effectively, you need to:

1. **Extract streamed content** from SSE events using the `sseJsonExtractor` handler
2. **Escape extracted content** safely for JSON embedding using `jsonStringEscaper`
3. **Use extracted content** in follow-up requests to simulate real multi-turn conversations

This approach allows you to create realistic benchmarks that capture how your LLM service handles conversation context and streaming performance.

## Starting with a curl Example

Let's start with the basics. When you send a streaming request to an LLM API like Ollama, here's the actual curl command:

**Request:**
```bash
curl -X POST http://localhost:11434/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "granite4.2:3b",
    "messages": [{"role": "user", "content": "What is AI?"}],
    "stream": true
  }'
```

**Raw SSE Response (multiple events arriving incrementally):**
```
data: {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1704067200,"model":"granite4.2:3b","choices":[{"index":0,"delta":{"role":"assistant","content":""},"finish_reason":null}]}
data: {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1704067200,"model":"granite4.2:3b","choices":[{"index":0,"delta":{"content":" Artificial"},"finish_reason":null}]}
data: {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1704067200,"model":"granite4.2:3b","choices":[{"index":0,"delta":{"content":" Intelligence"},"finish_reason":null}]}
data: {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1704067200,"model":"granite4.2:3b","choices":[{"index":0,"delta":{"content":" is"},"finish_reason":null}]}
data: {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1704067200,"model":"granite4.2:3b","choices":[{"index":0,"delta":{"content":" the"},"finish_reason":null}]}
data: {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1704067200,"model":"granite4.2:3b","choices":[{"index":0,"delta":{"content":" simulation"},"finish_reason":null}]}
data: [DONE]
```

Each line starting with `data:` is a separate SSE event containing a JSON payload. The tokens arrive one at a time as `choices[0].delta.content` values.

## How Hyperfoil Handles LLM Streaming Responses

Now that you've seen what the raw SSE stream looks like, here's how Hyperfoil extracts and processes this data:

**Step 1: Extract content from the stream**

Hyperfoil uses the `sseJsonExtractor` handler to automatically:
- Parse the incoming SSE stream
- Extract specific JSON paths from each event payload
- Combine all chunks into a single session variable

**Step 2: Escape for safe JSON embedding**

The `jsonStringEscaper` step ensures extracted content (which may contain newlines, quotes, or backslashes) is properly escaped before embedding in JSON request bodies.

**Step 3: Reuse in follow-up requests**

The escaped content becomes available as a session variable for use in subsequent conversation turns, enabling true multi-turn benchmarking.

## Understanding the Components

### SseJsonExtractor

The `sseJsonExtractor` handler processes Server-Sent Events and extracts JSON data from them.

**Key features:**
- Automatically handles SSE stream parsing
- Extracts specific JSON paths from event payloads
- Stores extracted values in session variables for reuse

**What happens in practice:**

When Hyperfoil processes the response, it:
1. Receives the stream of SSE events
2. Extracts `choices[0].delta.content` from each event (` Artificial`, ` Intelligence`, ` is`, etc.)
3. Combines all tokens into a single variable

```
${response} = " Artificial Intelligence is the simulation..."
```

**Configuration:**
```yaml
handler:
  body:
    sseJsonExtractor:
      path: choices[0].delta.content
      toVar: assistantResponse
```

### JsonStringEscaper

The `jsonStringEscaper` step safely escapes extracted strings for embedding in JSON requests.

**Only needed for multi-turn conversations:** If you're just measuring single request/response latency, you can skip this. This step is only required when you want to reuse the extracted response content in follow-up requests, creating a conversation flow.

**Why it's needed:** LLM responses may contain special characters (newlines, quotes, backslashes) that must be properly escaped before being placed in JSON request bodies. Without escaping, malformed JSON could break subsequent requests.

**Example:**
```yaml
- jsonStringEscaper:
    fromVar: assistantResponse
    toVar: assistantResponseEscaped
```

After escaping, `${assistantResponseEscaped}` is safe to embed in your next request's JSON body.

## Building a Multi-Turn Conversation Benchmark

Here's a complete example that benchmarks a three-turn conversation with an LLM. The full benchmark is available as a reference file:

**[ollama-conversation.hf.yaml](/benchmarks/ollama-conversation.hf.yaml)**

This benchmark demonstrates:

1. **Turn 1** — Initial question to the LLM with a system prompt
   - Extracts the assistant's response using `sseJsonExtractor`
   - Escapes the response with `jsonStringEscaper`
   
2. **Turn 2** — Follow-up question that includes the first response
   - Embeds the escaped content from Turn 1 using `${firstResponseContentEscaped}`
   - Extracts and escapes the second response
   
3. **Turn 3** — Another follow-up that includes both previous responses
   - Embeds both escaped responses in the conversation history
   - Extracts the final response

The key pattern: **extract → escape → embed in next request**. Each turn stores its extracted response in a variable, escapes it, and then includes it in the next request's `messages` array.

## Key Patterns and Best Practices

### 1. Always Escape Extracted Content

When you extract content from an SSE stream and plan to embed it in a JSON request body, always use `jsonStringEscaper`:

```yaml
# Extract from streaming response
- httpRequest:
    POST: /api/stream
    handler:
      body:
        sseJsonExtractor:
          path: message.content
          toVar: extracted

# Escape before using in JSON
- jsonStringEscaper:
    fromVar: extracted
    toVar: escapedForJson

# Use in next request
- httpRequest:
    POST: /api/process
    body: |
      {"text": "${escapedForJson}"}
```

### 2. Use `sync: true` for Request-Response Sequencing

When building conversation flows, set `sync: true` on HTTP requests to ensure they complete in order:

```yaml
- httpRequest:
    POST: /v1/chat/completions
    body: |
      {
        "messages": [{"role": "user", "content": "${userInput}"}],
        "stream": true
      }
    sync: true  # Wait for this response before moving to next step
    handler:
      body:
        sseJsonExtractor:
          path: choices[0].delta.content
          toVar: response
```

### 3. Extract the Right JSON Path

Different LLM APIs structure their SSE payloads differently. Identify the correct path to your content:

**Ollama / OpenAI-compatible format:**
```yaml
path: choices[0].delta.content
```

To find the correct path, examine a single SSE event from your API. For Ollama, each event looks like:
```json
{
  "choices": [
    {
      "index": 0,
      "delta": {
        "content": "token"
      },
      "finish_reason": null
    }
  ]
}
```

So the path `choices[0].delta.content` points to the `"token"` value.

### 4. Structure Multi-User Scenarios

For load testing multiple concurrent conversations:

```yaml
phases:
- conversation:
    increasingLoad:
      initialUsersPerSec: 1
      targetUsersPerSec: 10
      duration: 5m
      scenario:
      - multi-turn:
          # Your conversation steps here
```

## Common Mistakes

### ❌ Forgetting to Escape

Directly embedding extracted content without escaping can break JSON:
```yaml
# Wrong - may produce invalid JSON
body: |
  {"message": "${extractedContent}"}

# Right - always escape first
body: |
  {"message": "${escapedContent}"}
```

### ❌ Wrong JSON Path

Using an incorrect path results in null extractions:
```yaml
# Wrong - path doesn't exist
sseJsonExtractor:
  path: response[0].message

# Right - verify the actual structure
sseJsonExtractor:
  path: choices[0].delta.content
```

### ❌ Not Waiting for Responses

Sending follow-up requests before the previous response completes:
```yaml
# Wrong - no sync
- httpRequest:
    POST: /api/first
    handler: ...

# Missing sync or wait mechanism

- httpRequest:
    POST: /api/second
    body: |
      {"previous": "${firstResponse}"}

# Right - use sync: true
- httpRequest:
    POST: /api/first
    sync: true
    handler: ...

- httpRequest:
    POST: /api/second
    sync: true
```

## Testing Your Benchmark

Before running at scale, test with a single user to validate:

1. **Extract paths are correct** — Check the logs to ensure content is being extracted
2. **Escaping works** — Verify escaped content contains valid JSON-safe characters
3. **Conversation flow** — Confirm follow-up requests use previous responses correctly
4. **Response times** — Measure realistic latencies with your target model

## See Also

- [Session Variables](/docs/concepts/session/) — How variables are stored and used
- [HTTP Handlers](/docs/reference/http/#handlers) — Complete handler documentation
- [Scenario Structure](/docs/concepts/scenario/) — Building complex test scenarios
