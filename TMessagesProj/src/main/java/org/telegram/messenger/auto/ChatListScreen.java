package org.telegram.messenger.auto;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.CarToast;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.CarColor;
import androidx.car.app.model.CarIcon;
import androidx.car.app.model.ListTemplate;
import androidx.car.app.model.Tab;
import androidx.car.app.model.TabContents;
import androidx.car.app.model.TabTemplate;
import androidx.car.app.model.Template;
import androidx.core.graphics.drawable.IconCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;

public class ChatListScreen extends Screen {
    private final AutoDialogsRepository dialogsRepository;
    private final AutoConversationItemFactory conversationItemFactory;
    private final AutoVoiceRecorderController voiceRecorderController;
    private final AutoAvatarProvider avatarProvider;
    private final AccountInstance accountInstance;
    private final AutoConversationItemFactory.ViewportListener viewportListener;
    private final AutoTemplateController templateController;

    private String activeTabId = AutoPrimarySection.UNREAD.tabId;

    public ChatListScreen(@NonNull CarContext carContext,
                          int currentAccount,
                          @NonNull AccountInstance accountInstance,
                          @NonNull AutoDialogsRepository dialogsRepository,
                          @NonNull AutoConversationItemFactory conversationItemFactory,
                          @NonNull AutoVoiceRecorderController voiceRecorderController,
                          @NonNull AutoAvatarProvider avatarProvider) {
        super(carContext);
        this.accountInstance = accountInstance;
        this.dialogsRepository = dialogsRepository;
        this.conversationItemFactory = conversationItemFactory;
        this.voiceRecorderController = voiceRecorderController;
        this.avatarProvider = avatarProvider;
        this.viewportListener = new AutoConversationItemFactory.ViewportListener() {
            @Override
            public void onVisibleRangeChanged(@NonNull String listKey, int startIndex, int endIndex) {
                dialogsRepository.onVisibleRangeChanged(listKey, startIndex, endIndex);
            }

            @Override
            public void onListHidden(@NonNull String listKey) {
                dialogsRepository.onListHidden(listKey);
            }
        };
        this.templateController = new AutoTemplateController(
                this,
                this::buildLoadingTemplate,
                this::buildContentTemplate
        );

        AutoDialogsRepository.Listener repositoryListener = (listKey, eventType, viewModelVersion) -> {
            if (getActiveSection().listKey.equals(listKey)) {
                templateController.onVisibleModelChanged(listKey, getActiveTemplateVersion());
            } else if (eventType == AutoDialogsRepository.EventType.TABS_CHANGED) {
                templateController.onTabsChanged(getActiveSection().listKey, getActiveTemplateVersion());
            }
        };
        AutoVoiceRecorderController.Listener recorderListener = () ->
                templateController.onVisibleModelChanged(getActiveSection().listKey, getActiveTemplateVersion());

        dialogsRepository.addListener(repositoryListener);
        voiceRecorderController.addListener(recorderListener);
        templateController.onForceRebuild(getActiveSection().listKey, getActiveTemplateVersion());
        getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onResume(@NonNull LifecycleOwner owner) {
                String pendingToast = AutoUiFeedback.consumeMainScreenToast();
                if (pendingToast != null) {
                    AndroidUtilities.runOnUIThread(
                            () -> CarToast.makeText(getCarContext(), pendingToast, CarToast.LENGTH_SHORT).show(),
                            250);
                }
            }

            @Override
            public void onDestroy(@NonNull LifecycleOwner owner) {
                dialogsRepository.removeListener(repositoryListener);
                voiceRecorderController.removeListener(recorderListener);
                templateController.destroy();
            }
        });
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        return templateController.getTemplate();
    }

    @NonNull
    private Template buildLoadingTemplate() {
        return buildTabTemplate(true);
    }

    @NonNull
    private Template buildContentTemplate() {
        if (shouldShowFullScreenLoading()) {
            return buildLoadingTemplate();
        }
        return buildTabTemplate(false);
    }

    private boolean shouldShowFullScreenLoading() {
        AutoDialogsRepository.AutoListSnapshot snapshot = getActiveSnapshot();
        return snapshot.loading && snapshot.dialogs.isEmpty();
    }

    @NonNull
    private Template buildTabTemplate(boolean forceLoading) {
        AutoPrimarySection activeSection = getActiveSection();
        TabTemplate.Builder builder = new TabTemplate.Builder(new TabTemplate.TabCallback() {
            @Override
            public void onTabSelected(@NonNull String tabId) {
                activeTabId = AutoPrimarySection.fromTabId(tabId).tabId;
                templateController.onTabsChanged(getActiveSection().listKey, getActiveTemplateVersion());
            }
        });
        builder.setHeaderAction(Action.APP_ICON);
        builder.addTab(buildTab(AutoPrimarySection.UNREAD, org.telegram.messenger.R.drawable.tabs_chats_24));
        builder.addTab(buildTab(AutoPrimarySection.PINNED, org.telegram.messenger.R.drawable.chats_pin));
        builder.addTab(buildTab(AutoPrimarySection.BOTS, org.telegram.messenger.R.drawable.filled_bot_approve_24));
        builder.addTab(buildTab(AutoPrimarySection.CHANNELS, org.telegram.messenger.R.drawable.outline_channel_24));

        AutoDialogsRepository.AutoListSnapshot snapshot = getActiveSnapshot();
        ListTemplate.Builder contentBuilder = new ListTemplate.Builder();
        if (forceLoading || (snapshot.loading && snapshot.dialogs.isEmpty())) {
            contentBuilder.setLoading(true);
        } else {
            contentBuilder.setSingleList(conversationItemFactory.buildItemList(
                    this,
                    activeSection.listKey,
                    activeSection.renderMode,
                    snapshot,
                    getEmptyMessage(activeSection),
                    viewportListener,
                    this::onLoadMore));
        }
        if (activeSection == AutoPrimarySection.UNREAD) {
            contentBuilder.addAction(new Action.Builder()
                    .setIcon(CarIcon.COMPOSE_MESSAGE)
                    .setBackgroundColor(CarColor.BLUE)
                    .setOnClickListener(() -> getScreenManager().push(
                            new AutoComposeRecipientScreen(
                                    getCarContext(),
                                    accountInstance,
                                    dialogsRepository,
                                    voiceRecorderController,
                                    avatarProvider)))
                    .build());
        }
        ListTemplate content = contentBuilder.build();

        builder.setTabContents(new TabContents.Builder(content).build());
        builder.setActiveTabContentId(activeSection.tabId);
        return builder.build();
    }

    @NonNull
    private Tab buildTab(@NonNull AutoPrimarySection section, int iconResId) {
        return new Tab.Builder()
                .setContentId(section.tabId)
                .setTitle(section.title)
                .setIcon(new CarIcon.Builder(
                        IconCompat.createWithResource(getCarContext(), iconResId))
                        .build())
                .build();
    }

    @NonNull
    private String getEmptyMessage(@NonNull AutoPrimarySection section) {
        switch (section) {
            case PINNED:
                return "No pinned chats";
            case CHANNELS:
                return "No channels";
            case BOTS:
                return "No bots";
            case UNREAD:
            default:
                return "No unread chats";
        }
    }

    @NonNull
    private AutoPrimarySection getActiveSection() {
        return AutoPrimarySection.fromTabId(activeTabId);
    }

    @NonNull
    private AutoDialogsRepository.AutoListSnapshot getActiveSnapshot() {
        switch (getActiveSection()) {
            case PINNED:
                return dialogsRepository.getPinnedSnapshot();
            case BOTS:
                return dialogsRepository.getBotsSnapshot();
            case CHANNELS:
                return dialogsRepository.getChannelsSnapshot();
            case UNREAD:
            default:
                return dialogsRepository.getUnreadSnapshot();
        }
    }

    private long getActiveTemplateVersion() {
        AutoDialogsRepository.AutoListSnapshot snapshot = getActiveSnapshot();
        long version = snapshot.viewModelVersion;
        version = version * 31 + getActiveSection().tabId.hashCode();
        version = version * 31 + conversationItemFactory.getVoiceSignature();
        return version;
    }

    private void onLoadMore(@NonNull String listKey) {
        AutoPrimarySection section = AutoPrimarySection.fromListKey(listKey);
        AutoDialogsRepository.AutoListSnapshot snapshot = getActiveSnapshot();
        getScreenManager().push(new AutoSectionPageScreen(
                getCarContext(),
                section,
                dialogsRepository,
                conversationItemFactory,
                snapshot.dialogs.size(),
                snapshot.dialogs.size()));
    }
}
