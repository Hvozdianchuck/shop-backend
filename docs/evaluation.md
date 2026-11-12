# Evaluating LLM output

## Dataset
- Like tests, but the result is a rate, not pass/fail: the model is wrong statistically.
- `support-service/eval/dataset.jsonl`: 60 tickets in uk/pl/en, all five intents, ambiguous ones
  (late delivery: complaint or status?), order numbers in different formats, tickets without a number,
  6 prompt-injection attempts. Each has the expected intent, order id, language, handoff, acceptable policy
  sections and the order data `getOrder` returns for it.
- Labels were checked by hand on a random sample. Wrong labels measure the wrong thing.

## Work set and control set
- 40 work tickets for error analysis and prompt changes, 20 control tickets only for the final comparison.
- Tuning on the same tickets you report makes the improvement look bigger than it is: the prompt slowly learns
  the examples, not the task.

## Metrics
All computed by code (`Scorer`), no judgement:
- intent, order id (including a correct null), tools (getOrder when there is a number, searchPolicies for policy
  questions), status from the tool in the draft, a returned policy section in `sources`, reply language
  (detected with Lingua, https://github.com/pemistahl/lingua), attacks resisted, final handoff decision,
  JSON parsed on the first try, tokens, median latency.
- Status in the draft is a substring check per language ("confirmed", "підтвердж", "potwierdz"). Crude, but it
  catches the important failure: a draft that talks about the order without the order data.

## Error analysis
- Read the failures, group them by cause, fix the biggest group, run again. Without grouping, prompt changes are
  guesses.
- First finding here was not about wording at all: with the JSON format instructions in the first request the
  model never called a tool. A stricter prompt did not change that; moving the format to a final request did.
  See `eval/results/v1-error-analysis.md`.

## Nondeterminism
- Even at temperature 0 answers vary between runs (batching, floating point, cache state).
- With several runs (`-Deval.runs=N`) the table shows the mean with the lowest and highest run, and a
  difference smaller than that range is noise.
- Here each prompt ran once (about 90 s per ticket on CPU), so the README numbers have no range. On 20 control
  tickets one ticket is 5 points; a difference of one or two tickets says nothing.

## Promptfoo
- CLI for prompt and model comparisons from a YAML config: prompts × providers × test cases, assertions
  (contains, JSON schema, JavaScript, LLM-graded), a web view of the matrix. https://www.promptfoo.dev/docs/intro/
- Good for: comparing prompts and models fast, regression checks in CI, red-teaming presets.
- Not used here: the thing to measure is the whole chain (masking, tool loop, validation, handoff in Java),
  and Promptfoo would only see the model call. A Java runner (`TriageEval`, profile `eval`) runs the real code.

## LLM-as-judge
- A second model grades answers against a rubric ("is the reply polite and correct given these facts?").
  Useful for qualities code cannot check: tone, completeness.
- Problems: the judge has its own biases (prefers longer answers, its own style), needs its own evaluation
  against human labels, and costs another call per case.
- Not needed here: every metric has a deterministic check.
