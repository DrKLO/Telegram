package org.telegram.messenger.auto;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.ListTemplate;
import androidx.car.app.model.Template;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

public class FolderChatListScreen extends Screen {

    private final String folderName;
    private final int filterIndex;
    private final AutoDialogsRepository dialogsRepository;
    private final AutoConversationItemFactory conversationItemFactory;
    private final AutoVoiceRecorderController voiceRecorderController;
    private final AutoConversationItemFactory.ViewportListener viewportListener;
    private final AutoTemplateController templateController;

    public FolderChatListScreen(@NonNull CarContext carContext,
                                @NonNull String folderName,
                                int filterIndex,
                                @NonNull AutoDialogsRepository dialogsRepository,
                                @NonNull AutoConversationItemFactory conversationItemFactory,
                                @NonNull AutoVoiceRecorderController voiceRecorderController) {
        super(carContext);
        this.folderName = folderName;
        this.filterIndex = filterIndex;
        this.dialogsRepository = dialogsRepository;
        this.conversationItemFactory = conversationItemFactory;
        this.voiceRecorderController = voiceRecorderController;
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
            if (AutoDialogsRepository.getFilterListKey(this.filterIndex).equals(listKey)) {
                templateController.onVisibleModelChanged(listKey, getTemplateVersion());
            }
        };
        AutoVoiceRecorderController.Listener recorderListener = () ->
                templateController.onVisibleModelChanged(AutoDialogsRepository.getFilterListKey(this.filterIndex), getTemplateVersion());
        dialogsRepository.addListener(repositoryListener);
        voiceRecorderController.addListener(recorderListener);
        templateController.onForceRebuild(AutoDialogsRepository.getFilterListKey(this.filterIndex), getTemplateVersion());
        getLifecycle().addObserver(new DefaultLifecycleObserver() {
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
        return new ListTemplate.Builder()
                .setHeaderAction(Action.BACK)
                .setTitle(folderName)
                .setLoading(true)
                .build();
    }

    @NonNull
    private Template buildContentTemplate() {
        AutoDialogsRepository.AutoListSnapshot snapshot = dialogsRepository.getFilterSnapshot(filterIndex);
        return new ListTemplate.Builder()
                .setHeaderAction(Action.BACK)
                .setTitle(folderName)
                .setSingleList(conversationItemFactory.buildItemList(
                        this,
                        AutoDialogsRepository.getFilterListKey(filterIndex),
                        AutoListRenderMode.FILTER_STANDARD,
                        snapshot,
                        "No chats",
                        viewportListener,
                        null))
                .build();
    }

    private long getTemplateVersion() {
        return dialogsRepository.getFilterSnapshot(filterIndex).viewModelVersion * 31
                + conversationItemFactory.getVoiceSignature();
    }
}
