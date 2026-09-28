# Conversation

The chat itself: the message list, messages, the composer and what surrounds them.

## Conversation

The scrolling message list: follows the stream, stops when you scroll up, jumps back to the latest.

![Conversation](../assets/components/conversation.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `Conversation(state, onRegenerate, onToolApproval, …)` |
| AI Elements | `Conversation` |

## Messages

A user or assistant message with its parts and actions (copy, read aloud, regenerate, …).

![Messages](../assets/components/messages.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `MessageItem(message, onRegenerate)` |
| AI Elements | `Message` |

## Prompt input

The composer: text, attachments, dictation, send and stop, queueing while the agent works.

![Prompt input](../assets/components/prompt-input.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `PromptInput(value, onValueChange, onSubmit, onStop, busy)` |
| AI Elements | `PromptInput` |

## Suggestions

Tappable prompt suggestions.

![Suggestions](../assets/components/suggestions.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `Suggestions(suggestions, onSelect)` |
| AI Elements | `Suggestion` |

## Empty state

The start of a conversation: a greeting and suggestions.

![Empty state](../assets/components/empty-state.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `ChatEmptyState(title, subtitle, suggestions, onSelect)` |

## Branch

Switch between regenerated versions of a reply.

![Branch](../assets/components/branch.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `BranchSelector(index, count, onSelect)` |
| AI Elements | `Branch` |

## Checkpoint

Restore the conversation to an earlier point, after confirming.

![Checkpoint](../assets/components/checkpoint.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `Checkpoint(onRestore)` |
| AI Elements | `Checkpoint` |

## Queue

Messages waiting to be sent while the agent is busy.

![Queue](../assets/components/queue.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `Queue(items, paused, onRemove, onSendNow)` |
| AI Elements | `Queue` |

## Open in chat

Continue a prompt in another chat app.

![Open in chat](../assets/components/open-in-chat.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `OpenInChat(prompt)` |
| AI Elements | `OpenInChat` |

## Model selector

Pick a model, with its provider, capabilities and context window.

![Model selector](../assets/components/model-selector.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `ModelSelector(models, selectedId, onSelect)` |
| AI Elements | `ModelSelector` |

## Context (token usage)

Tokens used by the conversation, against the model's context window.

![Context (token usage)](../assets/components/context.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `ContextUsage(usage, contextWindow)` |
| AI Elements | `Context` |

## Loading indicators

Material 3 Expressive loading indicators while a reply starts.

![Loading indicators](../assets/components/loading.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `LoadingIndicator(), ContainedLoadingIndicator()` |
| AI Elements | `Loader` |

## Shimmer

Text with a moving highlight, for work in progress.

![Shimmer](../assets/components/shimmer.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `ShimmerText(text, active)` |
| AI Elements | `Shimmer` |
