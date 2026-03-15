# Google Messages Android Auto Reverse Engineering

## Inputs
- Primary target APK: `com.google.android.apps.messaging`
- Version: `messages.android_20260210_00_RC00.phone_dynamic`
- Version code: `299663063`
- APK SHA-256: `cdcf56b7acba72b8d91299730b3c8b2e42e17ce6efcd08754db9249031a8cf98`
- Source path on device: `/data/app/.../com.google.android.apps.messaging.../base.apk`

- Secondary comparison APK: `com.android.mms`
- Version: `16.30.10`
- Version code: `16030010`
- APK SHA-256: `e1330fe8f62e0b6bf8f926357785bb1aa705ca145a5a27c305be9e9372df49dd`
- Source path on device: `/product/priv-app/Mms/Mms.apk`

## Identified Entry Points

### Google Messages
The Android Auto messaging entrypoint is declared directly in the manifest:

- Service: `com.google.android.apps.messaging.auto.MessagingTemplateScreenServiceModule`
- Intent action: `androidx.car.app.CarAppService`
- Category: `androidx.car.app.category.MESSAGING`
- `androidx.car.app.minCarApiLevel = 7`

This service is a real `CarAppService` implementation. Decompiled code shows:
- host validation in `mo29143b()`
- session creation in `mo29144ft()`
- lifecycle logging:
  - `CarAppService onStart`
  - `CarAppService onResume`
  - `CarAppService onStop`
  - `CarAppService onDestroy`

Session creation path:
- `MessagingTemplateScreenServiceModule.mo29144ft()`
- creates `wfh`
- `wfh.mo41532c()`
- returns a `wfc` instance

`wfc` is the actual Android Auto screen/state owner. It logs as:
- `com/google/android/apps/messaging/auto/MessagingTemplateScreen`

### System MMS
No Android Auto `CarAppService` was found in `com.android.mms`.
This app appears to be a conventional OEM messaging app with normal conversation list layers, not an Android Auto messaging reference implementation.

## Auto List Architecture

### Google Messages
Google Messages does not build the Android Auto list directly from UI screen state.
It uses a dedicated Auto screen/state object:

- `p000.wfc` -> `MessagingTemplateScreen`

Relevant fields in `wfc`:
- `AtomicReference f271693n` -> cached current template
- `AtomicBoolean f271694o` -> refresh pending flag
- `atfk f271699t` -> top conversations source
- `dazw f271686g` -> subscription handle to the data source
- `wfb f271695p` -> data change callback

Constructor behavior:
- logs `Creating a MessagingTemplateScreen and getting top convos`
- gets top conversations source via `((apdy) f271690k.mo128a()).mo10352K(10)`
- subscribes via `atfk.mo3646a(wfb)`

This is the important architectural difference from the current Telegram implementation:
- Google Messages has a dedicated Auto data source for top conversations
- the Auto screen owns a cached template
- the Auto screen is not using “screen fields + rebuild immediately on every change” as the primary model

### System MMS
System MMS shows a classic conversation list architecture:
- `ConversationListFragment`
- `ConversationListDataSource`
- `ConversationListDataTransformer`
- `ConversationListFragmentViewModel`
- `CardConversationListAdapter`

This is useful as a baseline for normal list separation, but it does not show a matching Android Auto stack.

## Update / Refresh Strategy

### Google Messages refresh model
The refresh path is two-phase:

1. Data source changes do not rebuild the template immediately.
2. They mark refresh pending and request a host invalidate.
3. On the next template request, cached/loading/current template policy decides what to return.

The callback class is:
- `p000.wfb`

Behavior:
- `wfb.onChanged()`
- logs `Conversations changed, refreshing auto UI`
- sets `wfc.f271694o = true`
- calls `wfc.m41457c()`

`m41457c()` is in base class `p000.dkh` and is effectively:
- if lifecycle is started -> `AppManager.invalidate()`

### Cached template path
`wfc.mo41455a()` is the core template retrieval method.

Important observed behavior:
- maintains cached template in `AtomicReference`
- creates a loading template first
- logs `Retrieving a car template isRefresh: %b isLoading %b`
- if loading state is first install into cache:
  - starts async refresh
  - returns loading template
- if refresh was marked pending:
  - triggers async refresh
  - still returns current cached template immediately
- when async refresh finishes:
  - cached template is replaced
  - screen invalidation path is used again

This is a critical difference from Telegram’s current approach.
Google Messages does not require each data change to synchronously rebuild the visible template before returning from the screen path.

## Unread Model And Bubble Updates

### Google Messages separates conversation unread state from message unread state
There are at least two distinct unread layers in the Google Messages codebase:

1. Conversation-level unread metadata.
2. Per-message read/unread flags used by message-list and Android Auto preview rendering.

Concrete evidence:
- `p000.apmf` (`DefaultConversationSummary`) stores `boolean isUnread`
- `p000.apqr` (`BugleConversationProperties`) exposes `isUnread` via `mo10339y()`
- database/query projection in `p000.bkjx` includes:
  - `conversations.marked_as_unread`
  - `conversations.unread_count`
  - `messages.read`

This means Google Messages does not collapse unread state into a single derived number at the Auto rendering boundary.

### Android Auto bubbles are built from real message read flags
In `p000.wfc` (`MessagingTemplateScreen`), `CarMessage` creation uses the message-level read flag directly:

- `dlvVar.f134399e = apehVar.mo1476n().toEpochMilli()`
- `dlvVar.f134400f = apehVar.mo1485w()`

So the Android Auto bubble read state is attached to each real preview message.
It is not synthesized from conversation unread count.

### Google Messages also keeps a real unread anchor in message paging
The message-list paging layer contains an `oldestUnreadMessageIdFlow`:

- `p000.aedy` logs as `MessageListPagingDataProviderImpl`
- `p000.aedg` logs `getInitialMessageId - oldestUnreadMessageIdFlow emitted: %s`
- `p000.arno` returns `stream(messages).filter(new arpr()).findFirst().orElse(null)`
- `p000.arpr` checks `((apeh) obj).mo1485w()`

This indicates Google Messages derives the unread anchor from the first real unread message in the stream, not from placeholder rows or padded counts.

### Practical implication for Telegram
For Android Auto, the transferable pattern is:

1. Keep conversation-level unread metadata in the repository/domain layer.
2. Keep per-message read state on real preview messages.
3. Build `CarMessage.isRead` only from real message state.
4. Never fabricate unread preview messages to force badge behavior.
5. If the host needs preview bubbles, provide only real previewable messages.
6. If aggregate unread count exists in Telegram domain state, do not misuse it as a per-message flag.

## Scroll Stability Strategy
The reverse-engineered evidence supports this conclusion:

### What Google Messages appears to do
- subscribe to a dedicated top-conversations source
- keep a cached template
- separate “data changed” from “template rebuild”
- return an existing template while refresh is pending
- rebuild conversation items asynchronously from dedicated conversation models

### What it does not appear to rely on
- no evidence that Java `equals()` on `ListTemplate` or `ItemList` is used as the primary scroll-preservation mechanism
- no evidence that host-side redraw suppression depends on model equality
- no need for reflection-based template-id reuse to establish the main refresh policy

### Why this matters
In `androidx.car.app`, `Screen.invalidate()` causes the host to request a new template.
Framework inspection showed no public logic where app-side `equals()` prevents that round trip.
So the production lever is not “override equals on the list”.
The production lever is:
- reduce synchronous full rebuilds
- keep a cached template
- treat refresh as dirty state, not immediate rebuild
- decouple data-source change notification from template construction

## Comparison With System MMS

| Area | Google Messages | System MMS | Telegram Auto current |
|---|---|---|---|
| Android Auto entrypoint | Yes, `MessagingTemplateScreenServiceModule` | No clear car-app service | Yes |
| Dedicated Auto model | Yes, `MessagingTemplateScreen` + top conversation source | No evidence | Partial, but screen-driven |
| Cached template | Yes | N/A | No |
| Dirty refresh flag | Yes, `AtomicBoolean isRefresh` | N/A | No |
| Loading vs current template separation | Yes | N/A | Minimal |
| Conversation source | dedicated top-conversations provider | classic data source / viewmodel stack | direct repository snapshot into screen |
| Rebuild timing | async after invalidate | N/A | synchronous in response to listener changes |
| Likely scroll-preserving mechanism | fewer rebuilds + cached template + top-source ownership | classic app list separation | still too eager to invalidate/rebuild |

## Mapping To Telegram

### What Google Messages does
- CarAppService creates a dedicated Auto screen/session object.
- That object subscribes to a top-conversations provider.
- Data changes mark refresh pending.
- The screen returns a cached or loading template first.
- Actual conversation item reconstruction is asynchronous.
- The screen owns the current template, not just current raw dialogs.
- Conversation summaries carry conversation-level unread state separately from preview messages.
- Auto bubble unread state is derived from real message read flags, not aggregate unread count.

### What Telegram currently does differently
- [ChatListScreen.java](/Users/rbnkv/Projects/Telegram/TMessagesProj/src/main/java/org/telegram/messenger/auto/ChatListScreen.java) still treats repository change as a signal to compare a signature and call `invalidate()`.
- [AutoDialogsRepository.java](/Users/rbnkv/Projects/Telegram/TMessagesProj/src/main/java/org/telegram/messenger/auto/AutoDialogsRepository.java) still computes list state directly for rendering rather than owning a cached template lifecycle.
- [AutoConversationItemFactory.java](/Users/rbnkv/Projects/Telegram/TMessagesProj/src/main/java/org/telegram/messenger/auto/AutoConversationItemFactory.java) still participates in direct render-path building rather than feeding a template cache.
- Voice recorder state still contributes directly to screen invalidation.

### Recommended Telegram Changes
1. Move from “snapshot signature -> invalidate -> rebuild template immediately” to “dirty flag + cached template”.
2. Introduce a dedicated Auto template controller owned by session, not by `ChatListScreen`.
3. Let repository publish semantic change events:
   - initial load required
   - presentation changed
   - structure changed
   - reorder pending
4. Maintain:
   - `currentTemplate`
   - `isLoading`
   - `refreshPending`
   - `structurePending`
5. On repository change:
   - do not synchronously rebuild template in the listener
   - set pending flags
   - request invalidate
6. On `onGetTemplate()`:
   - if no cached template -> return loading template and kick async build
   - if refresh pending -> return current cached template, start async rebuild if not already running
   - once async build completes -> atomically replace cached template and invalidate again
7. Keep frozen-order policy from previous work, but move it below the template cache layer.
8. Remove direct recorder-driven `invalidate()` and route recorder changes through the same dirty-template policy.

## Recommended Telegram Changes
The production fix should be based on this exact principle:

- `equals()` is not the fix
- reflection is not the fix
- “update one item in place” is not a real public car-app API capability
- cached template + dirty refresh + async rebuild is the transferable pattern

The Telegram implementation should therefore target:
- dedicated Auto template controller
- cached last good `ListTemplate` / `TabTemplate`
- async build of `ConversationItem`s from repository state
- repository change coalescing
- deferred structural reorder while user is away from top
- no immediate full template reconstruction inside screen listeners
- separate conversation unread metadata from preview-message unread flags
- compute preview unread bubbles from real unread-tail messages only
