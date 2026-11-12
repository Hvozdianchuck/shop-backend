# LLM APIs

Ollama API: https://github.com/ollama/ollama/blob/main/docs/api.md

## System instructions
- Separate role from the user message. The system message holds our rules; the customer text goes in a user
  message, wrapped in `<customer_message>` tags (see [llm-security.md](llm-security.md)).
- Prompts live in files with versions: `support-service/src/main/resources/prompts/triage-v1.st` etc.
  The version is stored with every ticket and every eval result, so a number can always be traced to a prompt.

## Structured output
- Ask for JSON that matches a schema, parse it into a type, reject what does not fit.
- Spring AI `BeanOutputConverter<TriageResult>` generates the JSON schema from the record and the format
  instructions that go into the prompt (`{format}` in the template).
- Providers can also enforce the schema while generating (Ollama `format`, OpenAI/Anthropic structured outputs).
  Not used here: with Ollama, a forced format also constrains tool-call output, so the model could not call tools.
- When parsing fails: one retry with the parser error in the message, then the ticket goes to a person.
  Small models often write `""` where the schema says `null`; `TriageResult` normalises that.

## Tool calling
- The model does not run anything. It answers "call `getOrder` with this argument"; our code runs it and sends
  the result back as a tool message; the model continues.
- Tools are described by name, description and a JSON schema of the arguments. In Spring AI: `@Tool` and
  `@ToolParam` on methods (`OrderTools`, `PolicyTools`).
- Only read-only tools here. A tool that changes state is a tool an attacker can talk the model into calling.
- Tool results are data the model reads, so keep them small: `getOrder` returns status, date and item count,
  not the whole order.

## Tokens and cost
- Every response reports input and output tokens. `TriageService` sums them over all calls of a ticket and stores
  them on the ticket; Spring AI also publishes `gen_ai.client.token.usage` to Micrometer.
- One ticket is 1 to 5 model calls; the system prompt is sent each time, so a longer prompt costs on every call.
- Measured with prompt v3: about 4000 input and 270 output tokens per ticket (average over 40). v1 in one phase
  was 751 / 146. With a hosted model, cost = tokens × the provider's price per million input and output
  tokens; input dominates here because the prompt and tool results are resent on every call.
