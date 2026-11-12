# Spring AI

Reference: https://docs.spring.io/spring-ai/reference/ (2.0.1 here, the version Initializr picks for Boot 4.1.2)

## ChatModel and ChatClient
- `ChatModel.call(Prompt)` is the low-level API; `ChatClient` is the fluent one with advisors.
- In 2.0 the chat model itself no longer executes tools: it returns the tool calls, and `ToolCallingAdvisor` in
  `ChatClient` runs the loop. `TriageService` uses `ChatModel` and `ToolCallingManager` directly to cap the loop
  at 3 calls and keep a journal of results.
- Options in a `Prompt` replace the configured defaults instead of being merged. Start from
  `chatModel.getDefaultOptions().mutate()`, otherwise the model name from `application.yml` is lost
  ("model cannot be null or empty"). Found on the first real run, test `keepsConfiguredModelOptionsInEveryRequest`.
- Config moved from `spring.ai.ollama.chat.options.*` to `spring.ai.ollama.chat.*` (`model`, `num-ctx`, `think`,
  `keep-alive`); the old keys are deprecated.

## Advisors
- Interceptors around a ChatClient call: memory, RAG (`QuestionAnswerAdvisor`), logging, tool loop.
- Not used: search is a tool the model calls when it needs it, not something added to every request.

## Structured output
- `BeanOutputConverter<T>`: schema from the type, format instructions for the prompt, `convert` to parse.
  Cleans markdown fences and think tags before parsing.

## Tools
- `@Tool` / `@ToolParam` on methods, `ToolCallbacks.from(object)` turns an instance into callbacks.
- Tool instances are created per ticket here (`new OrderTools(lookup, journal)`), so each carries its own journal.
- Return values are serialised to JSON; `@JsonInclude(NON_NULL)` keeps empty fields out of the model's context.

## Fakes in tests
- `ScriptedChatModel` (test sources) answers from a script: text, a tool call, or an exception. Tests check our
  code: tool loop limit, parse retry, handoff. Model quality is the eval's job.
- `FakeEmbeddingModel` puts texts on axes by keyword, so a test decides which texts are "similar".

## Timeouts and retries
- Ollama goes through Boot's `RestClient.Builder`, so `spring.http.clients` timeouts apply to it:
  connect 2 s, read 120 s. The order-service client sets its own 1 s / 2 s.
- `spring.ai.retry.*`: retries on network errors and 5xx, 3 attempts. Default is 10 attempts, too many for a
  call that takes a minute.
- Circuit breaker `model` (Resilience4j) around every call. While it is open the worker leaves tickets in the
  queue; it needs `automatic-transition-from-open-to-half-open-enabled`, because nobody calls the model while open.
