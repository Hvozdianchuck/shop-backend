# Workflows and agents

Reference: https://www.anthropic.com/engineering/building-effective-agents

- Chaining: fixed steps, output of one is input of the next. Easy to test step by step.
- Routing: classify first, then handle each class its own way. This service is routing: the model sets `intent`,
  code decides what happens (`HandoffPolicy`).
- Agent: the model decides in a loop which tools to call and when to stop. Flexible, but runs differ, cost and
  latency are open-ended, and it is hard to test.

Why a workflow here: the steps are known (read, classify, maybe look up order or policy, answer). The model gets
tools, but `TriageService` drives the loop itself and stops after 3 tool calls, then asks for the answer without
tools. An agent would add nothing but variance.

When an agent is worth it: the path really depends on what is found on the way (research, debugging, multi-step
tasks over many systems), and a person checks the result anyway.

## MCP
- Model Context Protocol: a standard way to expose tools and data to any AI client (IDE, chat app, agent).
  Server lists tools/resources, client calls them over stdio or HTTP. https://modelcontextprotocol.io
- Spring AI has MCP client and server starters; `getOrder` could be published as an MCP tool for other clients.
- Not used here: one service, two tools, no other clients. MCP pays off when the same tools serve several clients.
