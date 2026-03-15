# Telegram Android Auto Scroll Fix Spec

## Summary
Fix Android Auto dialog-list resets by copying the transferable pattern from Google Messages:

- keep a cached template
- separate `data changed` from `template rebuilt`
- rebuild asynchronously
- return cached/current/loading template rather than regenerating the visible list inline for every listener event

This spec replaces the current direct path:
- repository change
- screen compares signature
- `invalidate()`
- `onGetTemplate()` rebuilds full list immediately

with:
- repository change
- template controller marks dirty
- `invalidate()`
- `onGetTemplate()` returns cached template
- async rebuild replaces cached template
- second invalidate publishes new stable template

## Problem Statement
The current Telegram Auto implementation still visually behaves like full list replacement because:

1. [ChatListScreen.java](/Users/rbnkv/Projects/Telegram/TMessagesProj/src/main/java/org/telegram/messenger/auto/ChatListScreen.java) calls `invalidate()` directly from repository changes.
2. `onGetTemplate()` rebuilds the full `ListTemplate` / `TabTemplate` immediately from live repository state.
3. Even when order is frozen, presentation-only updates still go through the same whole-template path.
4. Voice recorder changes can still force invalidation outside a unified refresh policy.

The reverse-engineered Google Messages implementation avoids this by caching the template and treating changes as pending refresh state.

## Target Architecture

### New internal component
Add a new internal Auto component:
- `AutoTemplateController`

Recommended path:
- `TMessagesProj/src/main/java/org/telegram/messenger/auto/AutoTemplateController.java`

### Ownership
Owned by:
- [FoldogramAutoSession.java](/Users/rbnkv/Projects/Telegram/TMessagesProj/src/main/java/org/telegram/messenger/auto/FoldogramAutoSession.java)

Consumed by:
- [ChatListScreen.java](/Users/rbnkv/Projects/Telegram/TMessagesProj/src/main/java/org/telegram/messenger/auto/ChatListScreen.java)
- [FolderChatListScreen.java](/Users/rbnkv/Projects/Telegram/TMessagesProj/src/main/java/org/telegram/messenger/auto/FolderChatListScreen.java)

Dependencies:
- [AutoDialogsRepository.java](/Users/rbnkv/Projects/Telegram/TMessagesProj/src/main/java/org/telegram/messenger/auto/AutoDialogsRepository.java)
- [AutoConversationItemFactory.java](/Users/rbnkv/Projects/Telegram/TMessagesProj/src/main/java/org/telegram/messenger/auto/AutoConversationItemFactory.java)
- [AutoVoiceRecorderController.java](/Users/rbnkv/Projects/Telegram/TMessagesProj/src/main/java/org/telegram/messenger/auto/AutoVoiceRecorderController.java)

## Responsibilities of `AutoTemplateController`
`AutoTemplateController` must:

1. Keep cached templates per active screen context:
- main list template
- tab template
- folder list template

2. Track state:
- `hasTemplate`
- `isLoading`
- `refreshPending`
- `buildInFlight`
- `structurePending`
- `lastBuiltListKey`
- `lastBuiltViewSignature`

3. Decide whether `onGetTemplate()` should:
- return cached loading template
- return cached current template
- schedule async rebuild
- publish freshly built template

4. Coalesce repository and recorder changes into one refresh policy.

## Required behavior

### First load
When there is no cached template:
- create and cache a loading template immediately
- start async build
- return loading template

### Background presentation change
If current list is scrolled away from top and only presentation changes:
- do not rebuild inline inside repository listener
- mark `refreshPending = true`
- call `invalidate()`
- on next `onGetTemplate()`, return current cached template immediately
- if no build is already running, start async rebuild
- once async rebuild completes:
  - replace cached template
  - invalidate once more

### Background structural change while away from top
If repository reports frozen order differs from canonical order:
- keep current cached template
- set `structurePending = true`
- do not rebuild visible template into canonical order yet
- rebuild only when:
  - list returns to top
  - user changes tab
  - user reopens screen
  - folder screen is re-entered

### While at top
If user is at top and structural changes are allowed:
- rebuild asynchronously
- publish new template

## Public/internal interfaces to add

### `AutoTemplateController`
Required methods:
- `Template getOrBuildMainTemplate(Screen screen)`
- `Template getOrBuildTabTemplate(Screen screen, String activeTabId)`
- `Template getOrBuildFolderTemplate(Screen screen, int filterIndex, String folderName)`
- `void onRepositoryChanged()`
- `void onRecorderStateChanged()`
- `void onViewportChanged(String listKey, int start, int end)`
- `void onListHidden(String listKey)`
- `void forceSyncList(String listKey)`
- `void destroy()`

### `AutoDialogsRepository`
Keep current frozen-order logic, but add semantic change classification.

Replace coarse listener contract:
- `void onDialogsChanged()`

with:
- `void onDialogsChanged(ChangeSet changeSet)`

`ChangeSet` fields:
- `boolean mainPresentationChanged`
- `boolean mainStructureChanged`
- `Set<Integer> changedFilterIndices`
- `boolean tabsChanged`
- `boolean loadingChanged`

The implementer may represent this as a class or immutable value object, but these semantics are mandatory.

## Screen changes

### [ChatListScreen.java](/Users/rbnkv/Projects/Telegram/TMessagesProj/src/main/java/org/telegram/messenger/auto/ChatListScreen.java)
Must stop owning refresh policy.

Required changes:
- remove direct `conversationItemFactory.buildItemList(...)` from `onGetTemplate()`
- delegate template retrieval to `AutoTemplateController`
- keep tab selection state only
- on tab select:
  - notify controller
  - force sync selected list
  - request invalidate once

Must remove:
- direct signature-based decision as the primary refresh mechanism
- direct recorder listener `this::invalidate`

### [FolderChatListScreen.java](/Users/rbnkv/Projects/Telegram/TMessagesProj/src/main/java/org/telegram/messenger/auto/FolderChatListScreen.java)
Required changes:
- use `AutoTemplateController` for folder template retrieval
- no direct list rebuild in `onGetTemplate()`
- no direct recorder-driven invalidate

## Template build pipeline

### Build inputs
Async build should consume:
- frozen `AutoListSnapshot`
- active list key
- active tab state
- current voice recorder state snapshot

### Build output
Async build produces immutable:
- `ListTemplate`
- or `TabTemplate`

This object is stored in controller cache and reused until replaced.

### Important rule
The async build must work from snapshot input captured at build start.
It must not re-read mutable screen fields during the middle of build.

## Voice recorder integration
[AutoVoiceRecorderController.java](/Users/rbnkv/Projects/Telegram/TMessagesProj/src/main/java/org/telegram/messenger/auto/AutoVoiceRecorderController.java) must not directly force screen redraw anymore.

Required behavior:
- recorder changes notify `AutoTemplateController`
- controller marks presentation dirty
- controller coalesces recorder updates with other pending refreshes

This removes one remaining unconditional full refresh path.

## Why `equals()` is not the fix
Do not implement a custom equality-based solution as the primary mechanism.

Reasons:
1. `androidx.car.app` public refresh path is still `invalidate()` -> host asks for template.
2. No evidence in framework or Google Messages suggests app-defined `equals()` is the redraw suppression mechanism.
3. Google Messages uses cached template + pending refresh, which is a stronger and directly evidenced pattern.

`equals()` on internal snapshots can still be used for local change detection inside repository/controller, but not as the host-facing strategy.

## Required file changes

### New file
- `TMessagesProj/src/main/java/org/telegram/messenger/auto/AutoTemplateController.java`

### Existing files to refactor
- [FoldogramAutoSession.java](/Users/rbnkv/Projects/Telegram/TMessagesProj/src/main/java/org/telegram/messenger/auto/FoldogramAutoSession.java)
- [ChatListScreen.java](/Users/rbnkv/Projects/Telegram/TMessagesProj/src/main/java/org/telegram/messenger/auto/ChatListScreen.java)
- [FolderChatListScreen.java](/Users/rbnkv/Projects/Telegram/TMessagesProj/src/main/java/org/telegram/messenger/auto/FolderChatListScreen.java)
- [AutoDialogsRepository.java](/Users/rbnkv/Projects/Telegram/TMessagesProj/src/main/java/org/telegram/messenger/auto/AutoDialogsRepository.java)
- [AutoConversationItemFactory.java](/Users/rbnkv/Projects/Telegram/TMessagesProj/src/main/java/org/telegram/messenger/auto/AutoConversationItemFactory.java)
- [AutoVoiceRecorderController.java](/Users/rbnkv/Projects/Telegram/TMessagesProj/src/main/java/org/telegram/messenger/auto/AutoVoiceRecorderController.java)

## Test cases and scenarios

### Core list behavior
1. Open dialog list, scroll down, receive new message in another chat:
- no jump to top
- no immediate reorder

2. Open dialog list, scroll down, visible row unread state changes:
- no jump to top
- updated state appears after controller refresh cycle

3. Open dialog list, stay at top, receive new message in another chat:
- reorder may apply
- no repeated refresh storm

4. Stay away from top, accumulate multiple incoming changes:
- no repeated visible reset
- one eventual sync when returning to top

### Tab behavior
5. Switch tabs with pending reorder:
- selected tab syncs once
- content is correct

6. Overflow “More” folder list:
- background main-list changes do not cause spurious template churn there

### Recorder behavior
7. Start/stop recording while list is open:
- no unconditional direct screen invalidation path
- template refresh goes through controller

### Failure behavior
8. Async build fails:
- keep last good cached template
- do not crash
- schedule retry on next meaningful change

## Acceptance criteria
The fix is complete only when:

1. `ChatListScreen` no longer directly rebuilds the visible template from live repository state on every listener event.
2. There is a dedicated `AutoTemplateController`.
3. The controller returns cached template while refresh is pending.
4. Repository changes are semantic and coalesced.
5. Recorder state changes no longer directly call `invalidate()` on screens.
6. Manual Android Auto verification shows the list no longer visually resets to top on ordinary background updates.

## Assumptions and defaults
- Google Messages is the authoritative Android Auto reference, not system MMS.
- `equals()` is not the production fix for host redraw behavior.
- Frozen-order logic remains useful, but it must sit under a template-cache layer.
- The implementation should prefer stability over instant reorder while the user is browsing away from top.
