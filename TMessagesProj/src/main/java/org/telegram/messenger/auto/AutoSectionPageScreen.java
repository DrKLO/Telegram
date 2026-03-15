package org.telegram.messenger.auto;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.CarIcon;
import androidx.car.app.model.ListTemplate;
import androidx.car.app.model.Template;
import androidx.core.graphics.drawable.IconCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

final class AutoSectionPageScreen extends Screen {

    private final AutoPrimarySection section;
    private final AutoDialogsRepository dialogsRepository;
    private final AutoConversationItemFactory conversationItemFactory;
    private final AutoConversationItemFactory.ViewportListener viewportListener;
    private final AutoTemplateController templateController;
    private final int offset;
    private final int pageSize;

    AutoSectionPageScreen(@NonNull CarContext carContext,
                          @NonNull AutoPrimarySection section,
                          @NonNull AutoDialogsRepository dialogsRepository,
                          @NonNull AutoConversationItemFactory conversationItemFactory,
                          int offset,
                          int pageSize) {
        super(carContext);
        this.section = section;
        this.dialogsRepository = dialogsRepository;
        this.conversationItemFactory = conversationItemFactory;
        this.offset = Math.max(0, offset);
        this.pageSize = Math.max(1, pageSize);
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
            if (section.listKey.equals(listKey)) {
                templateController.onVisibleModelChanged(listKey, getTemplateVersion());
            }
        };
        dialogsRepository.addListener(repositoryListener);
        templateController.onForceRebuild(section.listKey, getTemplateVersion());
        getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onDestroy(@NonNull LifecycleOwner owner) {
                dialogsRepository.removeListener(repositoryListener);
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
        return new ListTemplate.Builder()
                .setHeaderAction(Action.BACK)
                .setTitle(section.title)
                .setLoading(true)
                .build();
    }

    @NonNull
    private Template buildContentTemplate() {
        AutoDialogsRepository.AutoListSnapshot snapshot = dialogsRepository.getPageSnapshot(section.listKey, offset, pageSize);
        ListTemplate.Builder builder = new ListTemplate.Builder()
                .setHeaderAction(Action.BACK)
                .setTitle(section.title)
                .setSingleList(conversationItemFactory.buildItemList(
                        this,
                        section.listKey,
                        section.renderMode,
                        snapshot,
                        getEmptyMessage(),
                        viewportListener,
                        this::onLoadMore));
        ActionStrip actionStrip = buildPageActionStrip(snapshot);
        if (actionStrip != null) {
            builder.setActionStrip(actionStrip);
        }
        return builder.build();
    }

    private long getTemplateVersion() {
        return dialogsRepository.getPageSnapshot(section.listKey, offset, pageSize).viewModelVersion;
    }

    private void onLoadMore(@NonNull String listKey) {
        AutoDialogsRepository.AutoListSnapshot snapshot = dialogsRepository.getPageSnapshot(listKey, offset, pageSize);
        getScreenManager().push(new AutoSectionPageScreen(
                getCarContext(),
                section,
                dialogsRepository,
                conversationItemFactory,
                offset + snapshot.dialogs.size(),
                pageSize));
    }

    private ActionStrip buildPageActionStrip(@NonNull AutoDialogsRepository.AutoListSnapshot snapshot) {
        ActionStrip.Builder builder = new ActionStrip.Builder();
        boolean hasPrevious = offset > 0;
        boolean hasNext = snapshot.hasMore;
        if (!hasPrevious && !hasNext) {
            return null;
        }
        if (hasPrevious) {
            builder.addAction(new Action.Builder()
                    .setIcon(new CarIcon.Builder(
                            IconCompat.createWithResource(getCarContext(), android.R.drawable.ic_media_previous))
                            .build())
                    .setOnClickListener(() -> getScreenManager().pop())
                    .build());
        }
        if (hasNext) {
            builder.addAction(new Action.Builder()
                    .setIcon(new CarIcon.Builder(
                            IconCompat.createWithResource(getCarContext(), android.R.drawable.ic_media_next))
                            .build())
                    .setOnClickListener(() -> onLoadMore(section.listKey))
                    .build());
        }
        return builder.build();
    }

    @NonNull
    private String getEmptyMessage() {
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
}
