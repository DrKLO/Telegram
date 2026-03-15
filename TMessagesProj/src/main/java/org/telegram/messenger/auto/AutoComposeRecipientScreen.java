package org.telegram.messenger.auto;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.ListTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;

import org.telegram.messenger.AccountInstance;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

final class AutoComposeRecipientScreen extends Screen {

    private static final int MAX_RECIPIENTS = 30;

    private final AccountInstance accountInstance;
    private final AutoDialogsRepository dialogsRepository;
    private final AutoVoiceRecorderController voiceRecorderController;
    private final AutoAvatarProvider avatarProvider;

    AutoComposeRecipientScreen(@NonNull CarContext carContext,
                               @NonNull AccountInstance accountInstance,
                               @NonNull AutoDialogsRepository dialogsRepository,
                               @NonNull AutoVoiceRecorderController voiceRecorderController,
                               @NonNull AutoAvatarProvider avatarProvider) {
        super(carContext);
        this.accountInstance = accountInstance;
        this.dialogsRepository = dialogsRepository;
        this.voiceRecorderController = voiceRecorderController;
        this.avatarProvider = avatarProvider;
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        ArrayList<TLRPC.Dialog> targets = dialogsRepository.getComposeTargets(MAX_RECIPIENTS);
        ItemList.Builder listBuilder = new ItemList.Builder();
        for (int i = 0; i < targets.size(); i++) {
            TLRPC.Dialog dialog = targets.get(i);
            String title = avatarProvider.resolveDialogName(dialog.id);
            if (title == null || title.isEmpty()) {
                continue;
            }
            Row.Builder rowBuilder = new Row.Builder()
                    .setTitle(title)
                    .setBrowsable(true)
                    .setOnClickListener(() -> getScreenManager().push(
                            new AutoComposeActionScreen(
                                    getCarContext(),
                                    dialog.id,
                                    title,
                                    accountInstance,
                                    voiceRecorderController,
                                    avatarProvider,
                                    2)));
            if (avatarProvider.getDialogIcon(dialog.id) != null) {
                rowBuilder.setImage(avatarProvider.getDialogIcon(dialog.id));
            }
            listBuilder.addItem(rowBuilder.build());
        }
        if (targets.isEmpty()) {
            listBuilder.setNoItemsMessage("No writable chats");
        }
        return new ListTemplate.Builder()
                .setHeaderAction(Action.BACK)
                .setTitle("New message")
                .setSingleList(listBuilder.build())
                .build();
    }
}
