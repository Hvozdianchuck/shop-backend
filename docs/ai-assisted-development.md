# AI-assisted development

General notes on coding agents such as Claude Code (https://docs.claude.com/en/docs/claude-code/overview):
tools that read the repository, run commands and edit files in a loop.

## Giving a task
- Say what done looks like: which tests must pass, which command must succeed, what must not change.
- Small steps with a check after each beat one big request. Ask for a plan first when the change is large,
  and correct the plan, not the code.
- Point to the existing code to copy the style from, otherwise the agent invents its own.

## Project instructions
- A `CLAUDE.md` (or `AGENTS.md`) in the repo root is read at the start of every session: build and test
  commands, ports, conventions, things not to touch. https://docs.claude.com/en/docs/claude-code/memory
- Keep it short and concrete. Rules the agent keeps breaking belong there; general advice does not.
- Hooks and permission settings enforce what instructions can only ask for (for example, block pushes).

## Checking generated code
- Read the diff like a colleague's pull request. Generated code is often plausible and wrong in the same way
  model answers are: an API that existed in an older version, a test that passes without testing anything.
- Run the tests yourself; break the code on purpose once to see the test fail.
- Watch for extra: abstractions nobody asked for, broad try/catch, comments that repeat the code, new
  dependencies.
- Anything the agent reports as done (numbers, outputs) should come from a command you can rerun.
