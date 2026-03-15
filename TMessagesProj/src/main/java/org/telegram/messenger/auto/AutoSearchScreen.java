package org.telegram.messenger.auto;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.CarIcon;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.Row;
import androidx.car.app.model.SearchTemplate;
import androidx.car.app.model.Template;
import androidx.core.graphics.drawable.IconCompat;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.DialogObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

final class AutoSearchScreen extends Screen {

    private static final int MAX_RESULTS = 30;

    private final AccountInstance accountInstance;
    private final AutoDialogsRepository dialogsRepository;
    private final AutoVoiceRecorderController voiceRecorderController;
    private final AutoAvatarProvider avatarProvider;

    private String query = "";

    AutoSearchScreen(@NonNull CarContext carContext,
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
        SearchTemplate.Builder builder = new SearchTemplate.Builder(new SearchTemplate.SearchCallback() {
            @Override
            public void onSearchTextChanged(@NonNull String searchText) {
                query = searchText;
                invalidate();
            }

            @Override
            public void onSearchSubmitted(@NonNull String searchText) {
                query = searchText;
                invalidate();
            }
        });
        builder.setHeaderAction(Action.BACK)
                .setSearchHint("Search chats")
                .setShowKeyboardByDefault(true);

        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isEmpty()) {
            builder.setLoading(false);
            builder.setItemList(new ItemList.Builder()
                    .setNoItemsMessage("Type to search chats")
                    .build());
            return builder.build();
        }

        ArrayList<TLRPC.Dialog> results = dialogsRepository.searchTargets(normalizedQuery, MAX_RESULTS);
        ItemList.Builder listBuilder = new ItemList.Builder();
        for (int i = 0; i < results.size(); i++) {
            TLRPC.Dialog dialog = results.get(i);
            String title = avatarProvider.resolveDialogName(dialog.id);
            if (title == null || title.isEmpty()) {
                continue;
            }
            Row.Builder rowBuilder = new Row.Builder()
                    .setTitle(title)
                    .setBrowsable(true)
                    .setOnClickListener(() -> openDialog(dialog, title));
            CarIcon avatar = avatarProvider.getDialogIcon(dialog.id);
            if (avatar != null) {
                rowBuilder.setImage(avatar);
            }
            listBuilder.addItem(rowBuilder.build());
        }
        if (results.isEmpty()) {
            listBuilder.setNoItemsMessage("No matches");
        }
        builder.setItemList(listBuilder.build());
        return builder.build();
    }

    private void openDialog(@NonNull TLRPC.Dialog dialog, @NonNull String title) {
        if (DialogObject.isChatDialog(dialog.id)) {
            TLRPC.Chat chat = accountInstance.getMessagesController().getChat(-dialog.id);
            if (chat != null && org.telegram.messenger.ChatObject.isChannel(chat) && !chat.megagroup) {
                getScreenManager().push(new AutoChannelPreviewScreen(
                        getCarContext(),
                        accountInstance,
                        dialog.id,
                        dialog.top_message,
                        title,
                        title,
                        avatarProvider.getDialogIcon(dialog.id)));
                return;
            }
        }
        getScreenManager().push(new AutoComposeActionScreen(
                getCarContext(),
                dialog.id,
                title,
                accountInstance,
                voiceRecorderController,
                avatarProvider,
                2));
    }
}
