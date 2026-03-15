# Google Messages Android Auto Evidence

## APK Inputs

### Google Messages
- Package: `com.google.android.apps.messaging`
- Version code: `299663063`
- Version name: `messages.android_20260210_00_RC00.phone_dynamic`
- APK SHA-256: `cdcf56b7acba72b8d91299730b3c8b2e42e17ce6efcd08754db9249031a8cf98`
- Device APK path: `/data/app/.../com.google.android.apps.messaging.../base.apk`

### System MMS
- Package: `com.android.mms`
- Version code: `16030010`
- Version name: `16.30.10`
- APK SHA-256: `e1330fe8f62e0b6bf8f926357785bb1aa705ca145a5a27c305be9e9372df49dd`
- Device APK path: `/product/priv-app/Mms/Mms.apk`

## Manifest Evidence

### Google Messages Android Auto service
Manifest extracted with `aapt2 dump xmltree`.

Relevant block:
- `androidx.car.app.minCarApiLevel = 7`
- service name: `com.google.android.apps.messaging.auto.MessagingTemplateScreenServiceModule`
- intent action: `androidx.car.app.CarAppService`
- intent category: `androidx.car.app.category.MESSAGING`

This proves Google Messages ships a dedicated car-app messaging service.

### System MMS
No `androidx.car.app.CarAppService` declaration was found in the manifest.

## Decompiled Class Evidence

### `com.google.android.apps.messaging.auto.MessagingTemplateScreenServiceModule`
Observed methods and behavior:
- `mo29143b()` builds host validator
- `mo29144ft()` creates session via `new wfh(this)`
- lifecycle logs:
  - `CarAppService onStart`
  - `CarAppService onResume`
  - `CarAppService onStop`
  - `CarAppService onDestroy`

Important note:
- release host validation uses `R.array.hosts_allowlist_sample`
- this mirrors the AndroidX sample host allowlist pattern

### `p000.wfh`
Observed behavior:
- extends session/screen factory base
- `mo41532c()` logs `CarAppService onCreateScreen`
- creates `new wfc(...)`
- stores it back into service field

This identifies `wfc` as the actual Android Auto screen/state object.

### `p000.wfc`
String tag identifies it as:
- `com/google/android/apps/messaging/auto/MessagingTemplateScreen`

Key fields:
- cached template: `AtomicReference f271693n`
- refresh pending flag: `AtomicBoolean f271694o`
- top conversation source: `atfk f271699t`
- source subscription handle: `dazw f271686g`
- callback: `wfb f271695p`

Constructor evidence:
- log: `Creating a MessagingTemplateScreen and getting top convos`
- gets source with `((apdy) ...).mo10352K(10)`
- subscribes via `mo3646a(wfbVar)`

Template retrieval evidence in `mo41455a()`:
- logs `Retrieving a car template isRefresh: %b isLoading %b`
- creates loading template when cache is empty
- refreshes asynchronously rather than rebuilding inline for every change
- returns cached/loading/current template path depending on state

List building evidence in `m89874f()`:
- top conversations are transformed into `ConversationItem`
- each conversation loads:
  - conversation properties
  - message list
  - optional avatar/icon
- failures are tolerated:
  - `Failed to retrieve Conversationitem`
  - skipped broken items do not abort whole template build
- empty list path logs:
  - `No messages to display for Android Auto.`
- per-message Auto unread evidence:
  - `dlvVar.f134399e = apehVar.mo1476n().toEpochMilli()`
  - `dlvVar.f134400f = apehVar.mo1485w()`

This shows Android Auto `CarMessage` read state is assigned from each real message model, not from conversation unread count.

### `p000.wfb`
This is the data change callback.

Observed behavior:
- log: `Conversations changed, refreshing auto UI`
- sets `wfc.f271694o = true`
- calls `wfc.m41457c()`

This proves Google Messages uses a dirty-refresh flag rather than synchronous rebuild inside the callback.

### `p000.dkh`
Observed behavior:
- `m41457c()` calls `AppManager.invalidate()` when lifecycle is started

This confirms the callback chain:
- data changed
- mark refresh pending
- request host invalidate
- later `getTemplate()` resolves whether to return cached/loading/current template

## Dex/String Evidence

Confirmed strings from Google Messages APK:
- `CarAppService onCreateScreen`
- `CarAppService onStart`
- `CarAppService onResume`
- `CarAppService onStop`
- `CarAppService onDestroy`
- `Creating a MessagingTemplateScreen and getting top convos`
- `Retrieving a car template isRefresh: %b isLoading %b`
- `Failed to load conversations for Auto`
- `Failed to retrieve Conversationitem`
- `No messages to display for Android Auto.`
- `Draft projections to update %s`
- `Skipping draft diff persistence. No projections to update.`
- `createListTemplateFromWithConversationItems`
- `MessageListProjectionSpec.kt`
- `ProjectionSpecComposeScreenClearcutLogger.kt`
- `ConversationPropertiesProjection(archiveStatus=`
- `ConversationPropertiesProjection(kind=`
- `isUnread`
- `markedAsUnread`
- `unreadBadgeVisibility`
- `unreadMessagesCount`
- `MarkAsUnreadManagerImpl.java`
- `UpdateUnreadCounterHandler.java`
- `MessageListPagingDataProviderImpl$oldestUnreadMessageIdFlow$2`
- `getInitialMessageId - oldestUnreadMessageIdFlow emitted: %s`

These strings support the interpretation that:
- Google Messages has a projection-oriented architecture around conversation rendering
- template construction is not naïve one-shot screen rebuilding
- unread state exists in both conversation-level projection/domain models and message-level flows

## Unread Evidence

### Conversation-level unread state
`p000.apmf` is `DefaultConversationSummary`.

Observed fields and methods:
- stores `boolean f32697d`
- `mo10730i()` returns that boolean
- `toString()` includes `isUnread=...`

This is direct evidence that the top-conversation summary model already carries conversation-level unread state.

`p000.apqr` is `BugleConversationProperties`.

Observed API:
- `mo10339y()` -> `isUnread`
- `toString()` prints `isUnread: ...`

This is separate from the Auto `CarMessage` rendering path.

### Database/query evidence
`p000.bkjx` includes the following columns in the conversation query projection:
- `conversations.marked_as_unread`
- `conversations.unread_count`
- `messages.read`

This proves Google Messages stores both:
- aggregate conversation unread metadata
- per-message read state

### Real unread anchor in the message list
`p000.aedy` logs as `MessageListPagingDataProviderImpl`.

Observed evidence:
- `p000.aedg` logs `getInitialMessageId - oldestUnreadMessageIdFlow emitted: %s`
- `p000.aedo` logs timeout handling for `oldestUnreadMessageIdFlow`
- `p000.arno` returns the first message matching `new arpr()`
- `p000.arpr` is `return ((apeh) obj).mo1485w();`

This shows Google Messages computes the unread anchor from real message objects and their read flag, not by inventing placeholder messages.

### Conclusion for Telegram
Google Messages’ model is:
- conversation unread state belongs to conversation summary/properties and counters
- Android Auto bubble unread state belongs to real preview messages

The evidence does not support:
- padding fake unread messages to influence host badges
- treating `dialog.unread_count > 0` as “all incoming preview messages are unread”
- using aggregate unread count as a substitute for per-message unread state

## System MMS Baseline Evidence

Relevant discovered classes/strings:
- `ConversationListFragment`
- `ConversationListDataSource`
- `ConversationListDataTransformer`
- `ConversationListFragmentViewModel`
- `CardConversationListAdapter`
- `ActionServiceImpl`
- `BackgroundWorkerService`

Interpretation:
- system MMS uses a conventional data-source / transformer / viewmodel split for its normal list
- no matching Android Auto `CarAppService` was found

## `equals()` Conclusion
Framework-side inspection of `androidx.car.app` earlier showed:
- `Screen.invalidate()` requests a new template from the host
- public refresh flow does not use app-defined `equals()` as the main mechanism to suppress redraw

Combined with Google Messages evidence:
- no evidence was found that Google Messages relies on `equals()` for list stability
- the observed mechanism is cached template + dirty flag + deferred rebuild

So the evidence supports:
- `equals()` is not the right primary fix for Telegram Auto scroll reset
