# LLM basics

## Tokens
- Models read and write tokens, not characters: pieces of words, whole short words, punctuation.
  English is about 4 characters per token; Ukrainian and Polish take more tokens for the same meaning,
  so the same ticket costs more and fills the context faster in those languages.
- Limits, speed and price are all per token. On a CPU the output side dominates: here prompt tokens are read
  at ~160/s (and almost free when the prefix is cached), new tokens come out at ~5.5/s with qwen3:8b.

## Context window
- Everything the model sees in one call: system prompt, messages, tool definitions, tool results, and the answer
  it is writing. What does not fit does not exist for the model.
- Ollama does not fail on overflow, it silently drops the start of the prompt. That is why `num-ctx` is set
  explicitly in `support-service/src/main/resources/application.yml` and checked against the real prompt size
  (~750 tokens for prompt v1 plus tool results, far below 8192).
- Tickets are cut to 2000 characters (`TicketIntake`), so one long message cannot push the instructions out.

## Hallucinations
- The model continues text plausibly, it does not look anything up. "Your order will arrive on Friday" is a
  plausible sentence whether or not it is true.
- Prompt v1 showed it directly: asked "where is my order", it answered without calling `getOrder`.
- Defence is not "tell it not to lie" but structure: give it the data through tools and check that what it cites
  came from those tools (`ResultValidator`).

## Choosing a model
- Measure on your own data, not on leaderboards: run the same eval set on two or three models of different size
  and compare quality, latency and tokens per ticket. `TriageEval` takes the model from
  `spring.ai.ollama.chat.model`, so a comparison is the same command with another `-D` value.
- What to watch with small models: tool calls with wrong arguments, replies in the wrong language, and
  reasoning text leaking into the answer when the model ignores the "no thinking" switch. A smaller model that
  writes more tokens per ticket is not faster in the end.
- Not done in this project: only qwen3:8b was evaluated.

## Prompting vs RAG vs fine-tuning
In this order, cheapest first:
1. Prompting: clearer instructions, definitions, examples. Minutes to change, easy to roll back.
2. RAG: put the relevant documents into the request. Knowledge stays in files that change without retraining
   (the shop policies here).
3. Fine-tuning: train on thousands of labelled examples. Worth it when 1 and 2 stop helping and the data exists.
   Neither is true here, and policies that change weekly do not belong in model weights.
