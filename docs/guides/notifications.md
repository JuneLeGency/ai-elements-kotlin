# Run notifications and Live Updates

An agent's run can take minutes. Users should be able to leave the app and still see how the run is
going, and know when the reply is ready. AI Elements follows a run with Android's own notification
features:

- **While it runs:** one ongoing notification that follows the run. On Android 16 and later it is a
  [Live Update](https://developer.android.com/develop/ui/views/notifications/live-update), a
  `ProgressStyle` notification promoted to the status bar chip and the lock screen (Android's
  counterpart of iOS Live Activities and the Dynamic Island). It shows:
    - what the agent is doing ("Running command · ./gradlew test");
    - the plan as progress segments;
    - the elapsed time;
    - a short text in the chip ("2/5", "Step 3", "Review");
    - a **Stop** action.

    Earlier Android versions show the same content as an ordinary progress notification.
- **When it waits for the user:** the notification says so ("Waiting for your approval · Save
  note"), alerts once, and offers **Review**, which opens the chat. Approvals are deliberately not
  answered from the notification: the user sees what the call would do before deciding.
- **When it ends:** if the user is elsewhere, a "reply ready" notification shows the start of the
  reply, or why the run stopped.

## The progress of a run

`AgentProgress.of(chatState)`, in `ai-elements-ui`, says where the latest turn stands. It reads the model only, so it
works the same for every protocol and for on-device agents.

| Phase | When |
|---|---|
| `THINKING` | the request is sent, or the model is reasoning |
| `WORKING` | a tool call is running (`tool` is the step) |
| `WRITING` | the reply's text is streaming |
| `NEEDS_APPROVAL` / `NEEDS_INPUT` | a call waits for approval, or the agent asked the user something |
| `DONE` / `FAILED` | the turn ended, or stopped with an error |

`plan` holds the agent's plan, from a `plan` data part or `plan` in shared state (Pydantic AI
Harness `Planning`, AG-UI state). `steps` counts the tool calls, and `reply` is the text so far. Use
it for your own surfaces too, such as a widget or a watch tile. `ToolPart.activityLabel(resources)`
gives the step's text outside composition.

## Notifications

`AgentRunNotifications`, in the optional `ai-elements-notifications` artifact, builds the
notifications from an `AgentProgress`:

```kotlin
val notifications = AgentRunNotifications(context).also { it.ensureChannel() }
val progress = AgentProgress.of(controller.state.value) ?: return
notifications.post(RUN_ID, notifications.running(progress, title = "Fix the build", contentIntent = openChat, stopIntent = stop))
// …when the run ends and the app is in the background:
notifications.post(DONE_ID, notifications.finished(progress, title = "Fix the build", contentIntent = openChat))
```

The artifact's manifest brings `POST_NOTIFICATIONS` and `POST_PROMOTED_NOTIFICATIONS` (for the Live
Update). Apps that do not add it get neither. The app asks for `POST_NOTIFICATIONS` at runtime, in
context: the demo asks the first time the user sends a message.

## Keeping the run going in the background

A run is work the user started and expects to finish. When the app goes to the background, Android
(and vendor battery managers, which freeze background processes) may stop it. The standard answer is
a foreground service, which also shows the run's notification. The demo's `AgentRunService`:

- starts when a run starts, while the app is in front, as Android requires;
- uses the `dataSync` foreground service type;
- updates the notification as the chat's state changes;
- when the run ends, removes the notification, leaves "reply ready" if the user is elsewhere, and
  stops.

The service is an app decision (its type, and how long runs may take), so it lives in the demo;
the library provides the progress and the notifications.

## Vendor surfaces

Android 16's Live Updates are the platform standard, and vendor skins show promoted notifications in
their own status-bar surfaces. Some skins also have a private API for their island, for example
Xiaomi HyperOS focus notifications. The library does not use private APIs; add them in your app if
you need them.
