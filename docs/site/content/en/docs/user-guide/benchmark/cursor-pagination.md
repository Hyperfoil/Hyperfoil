---
title: Cursor-based pagination
description: How to walk through all pages of a cursor-paginated API with a separate loop sequence
categories: [Guide, Benchmark]
tags: [guides, benchmark, pagination, cursor, sequences]
weight: 9
---

# Cursor-based pagination

This guide shows the recommended Hyperfoil pattern for cursor-based pagination using two
sequences: one for session initialisation as the first request, and a second that loops
over the remaining pages.

## Why two sequences?

The scenario uses two separate sequences with different responsibilities:

- `walkPages` runs the session setup, selects the CSV row, and sends the first request
  without a cursor. It runs once for each virtual user.
- `pageLoop` sends requests using `afterCursor` and repeats them until the API returns
  the final page.

`restartSequence` restarts the **current sequence** from its first step. Because the
first step in `pageLoop` is already the cursor-based request, restarting it fetches the
next page. The setup and first request remain in `walkPages`, so they are not repeated.

## Complete YAML

```yaml
name: cursor-pagination
http:
  host: http://localhost:8080    # replace with your service
phases:
  - listPages:
      always:
        users: 20
        duration: 90s
        scenario:
          initialSequences:
            # ── session init: runs once per virtual user ──────────────────────
            - walkPages:
              - randomCsvRow:
                  file: conversation-ids.csv
                  columns:
                    0: conversationId
                    1: ownerID

              # first page — no cursor
              - httpRequest:
                  GET: /v1/conversations?mode=all&limit=20
                  headers:
                    X-API-Key: ${apiKey}
                    X-User-ID: ${ownerID}
                  metric: list-conversations-page
                  handler:
                    body:
                      json:
                        query: .afterCursor
                        toVar: afterCursor
                    onCompletion:
                      conditional:
                        stringCondition:
                          fromVar: afterCursor
                          isSet: true
                          length:
                            greaterThan: 0
                        actions:
                          - newSequence: pageLoop

          sequences:
            # ── loop: runs repeatedly until the last page ─────────────────────
            - pageLoop:
              - httpRequest:
                  GET: /v1/conversations?mode=all&limit=20&afterCursor=${urlencode:afterCursor}
                  headers:
                    X-API-Key: ${apiKey}
                    X-User-ID: ${ownerID}
                  metric: list-conversations-page
                  handler:
                    body:
                      json:
                        query: .afterCursor
                        toVar: afterCursor

              # stop when the response no longer contains afterCursor
              - breakSequence:
                  stringCondition:
                    fromVar: afterCursor
                    isSet: false

              # cursor is present — fetch the next page
              - restartSequence
```

## How the loop terminates

Each iteration of `pageLoop` follows the same three-step flow:

1. The request uses the current value of `afterCursor` to fetch a page.
2. The response handler extracts the next `afterCursor` value.
3. Hyperfoil checks whether the variable is set:
   - If the API returned another cursor, `breakSequence` does not stop the sequence and
     `restartSequence` starts the next iteration of `pageLoop`.
   - If the API omitted `afterCursor`, `breakSequence` ends `pageLoop` and no further
     request is made.

```text
response contains afterCursor
  -> afterCursor is set
  -> breakSequence does not stop pageLoop
  -> restartSequence requests the next page

response omits afterCursor
  -> afterCursor is unset
  -> breakSequence stops pageLoop
  -> no restart and no next request
```

The `httpRequest` is synchronous by default, so the `breakSequence` check runs only after
the response body has been processed. This ensures that the decision is based on the
cursor returned by the current page.

## Metrics

Both requests share the metric name `list-conversations-page`, so Hyperfoil
aggregates all page requests into a single histogram.
