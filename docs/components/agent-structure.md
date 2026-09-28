# Agent structure

How the agent works through a request: plans, tasks, chains of thought and data parts.

## Plan

The agent's plan and the step it is on.

![Plan](../assets/components/plan.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `Plan(title, description, steps)` |
| AI Elements | `Plan` |

## Task

A task and the files it touched.

![Task](../assets/components/task.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `Task(title, steps)` |
| AI Elements | `Task` |

## Chain of thought

The steps of the agent's reasoning, with their sources.

![Chain of thought](../assets/components/chain-of-thought.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `ChainOfThought(steps)` |
| AI Elements | `ChainOfThought` |

## Data parts (data-plan, data-task)

Custom data parts: known names render as elements, others as data.

![Data parts (data-plan, data-task)](../assets/components/data-parts.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `DataPartView(part)` |
