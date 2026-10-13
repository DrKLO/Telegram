package org.telegram.ui.multiaccount;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.multiaccount.MultiAccountController;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.Forum.ForumUtilities;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.ContactsActivity;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.TopicsFragment;

import java.util.ArrayList;

/** Shared presentation helpers of the unified multi-account interface. */
public final class MultiAccountUi {

    public interface AccountCallback {
        void onAccountSelected(int account);
    }

    private MultiAccountUi() {
    }

    /** Whether rows should show which account they belong to. */
    public static boolean showAccountBadges() {
        return MultiAccountController.isActive();
    }

    public static String getAccountName(int account) {
        TLRPC.User user = UserConfig.getInstance(account).getCurrentUser();
        String name = user != null ? UserObject.getUserName(user) : null;
        if (TextUtils.isEmpty(name)) {
            name = "#" + (MultiAccountController.getAccountOrdinal(account) + 1);
        }
        return name;
    }

    public static String getAccountShortName(int account) {
        TLRPC.User user = UserConfig.getInstance(account).getCurrentUser();
        String name = user != null ? UserObject.getFirstName(user) : null;
        if (TextUtils.isEmpty(name)) {
            name = getAccountName(account);
        }
        return name;
    }

    /** Text appended to the composer hint so the sending identity is visible before typing. */
    public static CharSequence decorateComposerHint(int account, CharSequence hint) {
        if (!showAccountBadges()) {
            return hint;
        }
        return LocaleController.formatString(R.string.MultiAccountMessageAs, getAccountShortName(account));
    }

    public static BackupImageView createAccountAvatar(Context context, int account, int sizeDp) {
        BackupImageView avatar = new BackupImageView(context);
        avatar.setRoundRadius(dp(sizeDp / 2f));
        avatar.getImageReceiver().setCurrentAccount(account);
        TLRPC.User user = UserConfig.getInstance(account).getCurrentUser();
        AvatarDrawable drawable = new AvatarDrawable();
        drawable.setInfo(account, user);
        avatar.setForUserOrChat(user, drawable);
        return avatar;
    }

    /** One row of an account chooser: avatar, name and an optional "selected" ring. */
    public static View createAccountRow(Context context, int account, boolean selected, Theme.ResourcesProvider resourcesProvider) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), 0, 0));

        FrameLayout avatarContainer = new FrameLayout(context) {
            private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void dispatchDraw(@NonNull Canvas canvas) {
                if (selected) {
                    ringPaint.setStyle(Paint.Style.STROKE);
                    ringPaint.setStrokeWidth(dp(1.33f));
                    ringPaint.setColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider));
                    canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, dp(16), ringPaint);
                }
                super.dispatchDraw(canvas);
            }
        };
        row.addView(avatarContainer, LayoutHelper.createLinear(34, 34, Gravity.CENTER_VERTICAL, 12, 0, 0, 0));
        BackupImageView avatar = createAccountAvatar(context, account, 32);
        if (selected) {
            avatar.setScaleX(0.833f);
            avatar.setScaleY(0.833f);
        }
        avatarContainer.addView(avatar, LayoutHelper.createFrame(32, 32, Gravity.CENTER));

        TextView name = new TextView(context);
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        name.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        name.setText(getAccountName(account));
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(name, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL, 13, 0, 14, 0));
        row.setContentDescription(getAccountName(account));
        return row;
    }

    /**
     * Lets the user choose the sending account. With a single account the callback runs immediately.
     * {@code preselected} gets the selection ring (usually the primary account).
     */
    public static void chooseAccount(BaseFragment fragment, View anchor, CharSequence title, int preselected, AccountCallback callback) {
        ArrayList<Integer> accounts = MultiAccountController.getActiveAccounts();
        if (accounts.size() <= 1) {
            callback.onAccountSelected(accounts.isEmpty() ? fragment.getCurrentAccount() : accounts.get(0));
            return;
        }
        Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        ItemOptions options = ItemOptions.makeOptions(fragment, anchor);
        if (title != null) {
            options.addText(title, 13);
        }
        for (int account : accounts) {
            View row = createAccountRow(context, account, account == preselected, fragment.getResourceProvider());
            row.setOnClickListener(v -> {
                options.dismiss();
                callback.onAccountSelected(account);
            });
            options.addView(row, LayoutHelper.createLinear(240, 48));
        }
        options.setDimAlpha(0);
        options.show();
    }

    /** Opens "New message" for an explicitly chosen sending account. */
    public static void openNewMessage(BaseFragment from, int account) {
        Bundle args = new Bundle();
        args.putBoolean("destroyAfterSelect", true);
        ContactsActivity contacts = createInAccount(account, () -> new ContactsActivity(args));
        presentInAccount(from, contacts);
    }

    /**
     * Opens a conversation of {@code account}. Everything opened from it (profile, media, files,
     * replies) keeps using that account because routing follows the visible screen.
     */
    public static boolean openDialog(BaseFragment from, int account, long dialogId, int messageId) {
        if (!UserConfig.isValidAccount(account) || dialogId == 0) {
            return false;
        }
        MessagesController controller = MessagesController.getInstance(account);
        TLRPC.Dialog dialog = controller.dialogs_dict.get(dialogId);

        if (dialog instanceof TLRPC.TL_dialogCommunity) {
            Bundle args = new Bundle();
            args.putLong("community_id", dialog.community_id);
            return presentInAccount(from, createInAccount(account, () -> new DialogsActivity(args)));
        }

        Bundle args = new Bundle();
        if (DialogObject.isEncryptedDialog(dialogId)) {
            args.putInt("enc_id", DialogObject.getEncryptedChatId(dialogId));
        } else if (DialogObject.isUserDialog(dialogId)) {
            args.putLong("user_id", dialogId);
        } else {
            long did = dialogId;
            if (messageId != 0) {
                TLRPC.Chat chat = controller.getChat(-did);
                if (chat != null && chat.migrated_to != null) {
                    args.putLong("migrated_to", did);
                    did = -chat.migrated_to.channel_id;
                }
            }
            args.putLong("chat_id", -did);
        }
        if (messageId != 0) {
            args.putInt("message_id", messageId);
        }

        int previousRoute = UserConfig.selectedAccount;
        MultiAccountController.getInstance().route(account);
        if (!controller.checkCanOpenChat(args, from)) {
            MultiAccountController.getInstance().route(previousRoute);
            return false;
        }

        BaseFragment target;
        TLRPC.Chat chat = DialogObject.isChatDialog(dialogId) ? controller.getChat(-dialogId) : null;
        if (chat != null && messageId == 0 && (chat.monoforum || chat.forum)) {
            if (chat.monoforum) {
                args.putInt("chatMode", ChatActivity.MODE_SUGGESTIONS);
                args.putBoolean("isSubscriberSuggestions", !ChatObject.canManageMonoForum(account, chat));
                target = new ChatActivity(args);
            } else if (ChatObject.areTabsEnabled(chat)) {
                ChatActivity chatActivity = new ChatActivity(args);
                ForumUtilities.applyTopic(chatActivity, MessagesStorage.TopicKey.of(-chat.id, controller.getForumLastTopicId(chat.id)));
                target = chatActivity;
            } else if (dialog != null && dialog.view_forum_as_messages) {
                target = new ChatActivity(args);
            } else {
                target = new TopicsFragment(args);
            }
        } else if (chat != null && ChatObject.isCommunity(chat)) {
            Bundle communityArgs = new Bundle();
            communityArgs.putLong("community_id", chat.id);
            target = new DialogsActivity(communityArgs);
        } else {
            target = new ChatActivity(args);
        }
        target.setCurrentAccount(account);
        if (!from.presentFragment(target)) {
            // on tablets a chat may be shown in the side pane although the call reports false
            if (target.getParentLayout() == null) {
                MultiAccountController.getInstance().route(previousRoute);
                return false;
            }
        }
        return true;
    }

    private interface FragmentFactory<T extends BaseFragment> {
        T create();
    }

    private static <T extends BaseFragment> T createInAccount(int account, FragmentFactory<T> factory) {
        final Object[] holder = new Object[1];
        MultiAccountController.getInstance().runInAccount(account, () -> holder[0] = factory.create());
        @SuppressWarnings("unchecked") T fragment = (T) holder[0];
        fragment.setCurrentAccount(account);
        return fragment;
    }

    private static boolean presentInAccount(BaseFragment from, BaseFragment fragment) {
        int previousRoute = UserConfig.selectedAccount;
        if (!from.presentFragment(fragment) && fragment.getParentLayout() == null) {
            MultiAccountController.getInstance().route(previousRoute);
            return false;
        }
        return true;
    }
}
