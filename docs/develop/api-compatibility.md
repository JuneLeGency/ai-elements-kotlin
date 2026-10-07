# API stability and compatibility

Starting with the first public release, unmarked public Kotlin declarations in the published
artifacts are supported APIs, including 0.x releases. Upgrades must preserve existing call sites,
compiled clients and documented behavior. Patch and minor releases do not remove or rename APIs.
We retain deprecated entry points as forwarding implementations; a major version is not automatic
permission to delete them. Experimental APIs are identified by a `RequiresOptIn` annotation before
publication, not retroactively after consumers have adopted them.

## What is stable

| Surface | Contract |
|---|---|
| `ai-elements-chat` | Message/part models, controller, backend, events and approval contracts |
| `ai-elements-core` | Protocol clients, provider backends, capabilities, tools, auth and configuration |
| `ai-elements-ui` | Public elements, state factories, theme and renderer extension points |
| Optional integrations and harness artifacts | Public declarations, with the same policy; external service behavior follows its versioned protocol |
| `@ExperimentalNativeMermaidApi` | Native Mermaid is opt-in; its API and layout may change in a minor release |
| `internal`, private declarations, demo/server implementation | Not a supported library interface |

Material 3 and Compose dependencies currently include alpha versions. This does not exempt our
own stable APIs from compatibility review. The supported consumer toolchain is listed in
[Installation](../getting-started/installation.md); raising it or minSdk needs release review.
The library is Android-only, not a Kotlin Multiplatform library.

## The reviewed model decisions

We retain the public data classes (`Message`, `ToolPart`, `ChatState`, etc.) for immutable snapshots
and ergonomic `copy` operations. Their constructor order, default-call signatures, `copy` and
`componentN` functions are part of the supported contract. Do not append even a defaulted constructor
property and assume it is compatible. Add separately designed extension types or methods, or retain
all old signatures and verify old compiled consumers before changing them.

The public sealed event/part hierarchies and enum values are also frozen for source compatibility:
adding a branch can break a consumer's exhaustive `when`. Protocol additions should map to existing
neutral models and the documented `DataPart`/metadata extension points where their semantics fit.
A new fundamental model needs an explicit API design and migration review, not an automatic new
sealed subtype. Do not serialize these internal mappings as a custom wire protocol.

Serialized `Message` values are suitable for app-owned local storage. Keep old field names,
`SerialName` values and defaults readable. Apps own storage schema versions and migrations; these
Kotlin serializers are not a promise of a public network format. Add old-record fixtures when
changing persisted models.

## Reviewing a change

1. Run `./gradlew apiCheck`. Each library has a checked-in `api/<artifact>.api` baseline.
2. For an intentional addition, inspect the generated diff, compatibility of named arguments,
   defaults, return types, overload resolution and implementers of interfaces. Document it in
   CHANGELOG and the relevant guide, then run `./gradlew apiDump` and review that diff.
3. Retain replaced functions with `@Deprecated(message, replaceWith = ...)` at WARNING level and
   forward to the replacement. Do not automatically escalate to ERROR/HIDDEN: that breaks source
   compatibility even when the binary symbol remains.
4. Run `tools/check-published-consumer.sh` and affected behavioral tests. For an evolution of an
   already published signature, also run the old compiled consumer against the new artifacts.
   Updating a baseline alone is not evidence that a breaking change is acceptable.

The checker uses JetBrains' official Binary Compatibility Validator on `classes.jar` extracted
from AGP's public release AAR artifact. This bridges AGP 9 built-in Kotlin discovery without a
custom ABI parser. It checks all published library modules, including optional ones. It cannot
prove behavioral compatibility or all Kotlin source compatibility, so human review is required.

[JetBrains compatibility guidance](https://kotlinlang.org/docs/api-guidelines-backward-compatibility.html)
explains default arguments, data classes, return types and deprecation in detail.
