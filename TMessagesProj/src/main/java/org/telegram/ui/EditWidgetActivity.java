/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2021.
 */


package org.telegram.ui;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ChatsWidgetProvider;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.ContactsWidgetProvider;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.GroupCreateUserCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackgroundGradientDrawable;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.ForegroundColorSpanThemable;
import org.telegram.ui.Components.InviteMembersBottomSheet;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.Components.RecyclerListView;

import java.io.File;
import java.util.ArrayList;

public class EditWidgetActivity extends BaseFragment {

    private ListAdapter listAdapter;
    private RecyclerListView listView;
    private ItemTouchHelper itemTouchHelper;
    private FrameLayout widgetPreview;

    private ImageView previewImageView;

    private ArrayList<Long> selectedDialogs = new ArrayList<>();

    private WidgetPreviewCell widgetPreviewCell;

    private int previewRow;
    private int selectChatsRow;
    private int chatsStartRow;
    private int chatsEndRow;
    private int infoRow;
    private int rowCount;

    private int widgetType;
    private int currentWidgetId;

    private EditWidgetActivityDelegate delegate;

    public final static int TYPE_CHATS = 0;
    public final static int TYPE_CONTACTS = 1;

    private final static int done_item = 1;

    public interface EditWidgetActivityDelegate {
        void didSelectDialogs(ArrayList<Long> dialogs);
    }

    public class TouchHelperCallback extends ItemTouchHelper.Callback {

        private boolean moved;

        @Override
        public boolean isLongPressDragEnabled() {
            return false;
        }

        @Override
        public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            if (viewHolder.getItemViewType() != 3) {
                return makeMovementFlags(0, 0);
            }
            return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
        }

        @Override
        public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder source, RecyclerView.ViewHolder target) {
            if (source.getItemViewType() != target.getItemViewType()) {
                return false;
            }
            int p1 = source.getAdapterPosition();
            int p2 = target.getAdapterPosition();
            if (listAdapter.swapElements(p1, p2)) {
                ((GroupCreateUserCell) source.itemView).setDrawDivider(p2 != chatsEndRow - 1);
                ((GroupCreateUserCell) target.itemView).setDrawDivider(p1 != chatsEndRow - 1);
                moved = true;
            }
            return true;
        }

        @Override
        public void onChildDraw(Canvas c, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
        }

        @Override
        public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
            if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
                listView.cancelClickRunnables(false);
                viewHolder.itemView.setPressed(true);
            } else if (moved) {
                if (widgetPreviewCell != null) {
                    widgetPreviewCell.updateDialogs();
                }
                moved = false;
            }
            super.onSelectedChanged(viewHolder, actionState);
        }

        @Override
        public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {

        }

        @Override
        public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            super.clearView(recyclerView, viewHolder);
            viewHolder.itemView.setPressed(false);
        }
    }

    public class WidgetPreviewCell extends FrameLayout {

        private BackgroundGradientDrawable.Disposable backgroundGradientDisposable;
        private BackgroundGradientDrawable.Disposable oldBackgroundGradientDisposable;

        private Drawable backgroundDrawable;
        private Drawable oldBackgroundDrawable;
        private Drawable shadowDrawable;

        private Paint roundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private RectF bitmapRect = new RectF();
        private ViewGroup[] cells = new ViewGroup[2];

        public WidgetPreviewCell(Context context) {
            super(context);

            setWillNotDraw(false);
            setPadding(0, AndroidUtilities.dp(24), 0, AndroidUtilities.dp(24));

            LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(LinearLayout.VERTICAL);
            addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

            ChatActionCell chatActionCell = new ChatActionCell(context);
            chatActionCell.setCustomText(LocaleController.getString(R.string.WidgetPreview));
            linearLayout.addView(chatActionCell, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 0, 0, 4));

            LinearLayout widgetPreview = new LinearLayout(context);
            widgetPreview.setOrientation(LinearLayout.VERTICAL);
            widgetPreview.setBackgroundResource(R.drawable.widget_bg);
            linearLayout.addView(widgetPreview, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 10, 0, 10, 0));

            previewImageView = new ImageView(context);

            if (widgetType == TYPE_CHATS) {
                for (int a = 0; a < 2; a++) {
                    cells[a] = (ViewGroup) getParentActivity().getLayoutInflater().inflate(R.layout.shortcut_widget_item, null);
                    widgetPreview.addView(cells[a], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                }
                widgetPreview.addView(previewImageView, LayoutHelper.createLinear(218, 160, Gravity.CENTER));
                previewImageView.setImageResource(R.drawable.chats_widget_preview);
            } else if (widgetType == TYPE_CONTACTS) {
                for (int a = 0; a < 2; a++) {
                    cells[a] = (ViewGroup) getParentActivity().getLayoutInflater().inflate(R.layout.contacts_widget_item, null);
                    widgetPreview.addView(cells[a], LayoutHelper.createLinear(160, LayoutHelper.WRAP_CONTENT));
                }
                widgetPreview.addView(previewImageView, LayoutHelper.createLinear(160, 160, Gravity.CENTER));
                previewImageView.setImageResource(R.drawable.contacts_widget_preview);
            }
            updateDialogs();

            shadowDrawable = Theme.getThemedDrawableByKey(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow);
        }
        
        public void updateDialogs() {
            if (widgetType == TYPE_CHATS) {
                for (int a = 0; a < 2; a++) {
                    TLRPC.Dialog dialog;
                    if (selectedDialogs.isEmpty()) {
                        dialog = a < getMessagesController().dialogsServerOnly.size() ? getMessagesController().dialogsServerOnly.get(a) : null;
                    } else {
                        if (a < selectedDialogs.size()) {
                            dialog = getMessagesController().dialogs_dict.get(selectedDialogs.get(a));
                            if (dialog == null) {
                                dialog = new TLRPC.TL_dialog();
                                dialog.id = selectedDialogs.get(a);
                            }
                        } else {
                            dialog = null;
                        }
                    }
                    if (dialog == null) {
                        cells[a].setVisibility(GONE);
                        continue;
                    }
                    cells[a].setVisibility(VISIBLE);
                    String name = "";

                    TLRPC.FileLocation photoPath = null;
                    TLRPC.User user = null;
                    TLRPC.Chat chat = null;
                    if (DialogObject.isUserDialog(dialog.id)) {
                        user = getMessagesController().getUser(dialog.id);
                        if (user != null) {
                            if (UserObject.isUserSelf(user)) {
                                name = LocaleController.getString(R.string.SavedMessages);
                            } else if (UserObject.isReplyUser(user)) {
                                name = LocaleController.getString(R.string.RepliesTitle);
                            } else if (UserObject.isDeleted(user)) {
                                name = LocaleController.getString(R.string.HiddenName);
                            } else {
                                name = ContactsController.formatName(user.first_name, user.last_name);
                            }
                            if (!UserObject.isReplyUser(user) && !UserObject.isUserSelf(user) && user.photo != null && user.photo.photo_small != null && user.photo.photo_small.volume_id != 0 && user.photo.photo_small.local_id != 0) {
                                photoPath = user.photo.photo_small;
                            }
                        }
                    } else {
                        chat = getMessagesController().getChat(-dialog.id);
                        if (chat != null) {
                            name = chat.title;
                            if (chat.photo != null && chat.photo.photo_small != null && chat.photo.photo_small.volume_id != 0 && chat.photo.photo_small.local_id != 0) {
                                photoPath = chat.photo.photo_small;
                            }
                        }
                    }
                    ((TextView) cells[a].findViewById(R.id.shortcut_widget_item_text)).setText(name);

                    try {
                        Bitmap bitmap = null;
                        if (photoPath != null) {
                            File path = getFileLoader().getPathToAttach(photoPath, true);
                            bitmap = BitmapFactory.decodeFile(path.toString());
                        }

                        int size = AndroidUtilities.dp(48);
                        Bitmap result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                        result.eraseColor(Color.TRANSPARENT);
                        Canvas canvas = new Canvas(result);
                        if (bitmap == null) {
                            AvatarDrawable avatarDrawable;
                            if (user != null) {
                                avatarDrawable = new AvatarDrawable(user);
                                if (UserObject.isReplyUser(user)) {
                                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_REPLIES);
                                } else if (UserObject.isUserSelf(user)) {
                                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
                                }
                            } else {
                                avatarDrawable = new AvatarDrawable(chat);
                            }
                            avatarDrawable.setBounds(0, 0, size, size);
                            avatarDrawable.draw(canvas);
                        } else {
                            BitmapShader shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                            if (roundPaint == null) {
                                roundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                                bitmapRect = new RectF();
                            }
                            float scale = size / (float) bitmap.getWidth();
                            canvas.save();
                            canvas.scale(scale, scale);
                            roundPaint.setShader(shader);
                            bitmapRect.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
                            canvas.drawRoundRect(bitmapRect, bitmap.getWidth(), bitmap.getHeight(), roundPaint);
                            canvas.restore();
                        }
                        canvas.setBitmap(null);
                        ((ImageView) cells[a].findViewById(R.id.shortcut_widget_item_avatar)).setImageBitmap(result);
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }

                    ArrayList<MessageObject> messages = getMessagesController().dialogMessage.get(dialog.id);
                    MessageObject message = messages != null && messages.size() > 0 ? messages.get(0) : null;
                    if (message != null) {
                        TLRPC.User fromUser = null;
                        TLRPC.Chat fromChat = null;
                        long fromId = message.getFromChatId();
                        if (fromId > 0) {
                            fromUser = getMessagesController().getUser(fromId);
                        } else {
                            fromChat = getMessagesController().getChat(-fromId);
                        }
                        CharSequence messageString;
                        CharSequence messageNameString;
                        int textColor = getContext().getResources().getColor(R.color.widget_text);
                        if (message.messageOwner instanceof TLRPC.TL_messageService) {
                            if (ChatObject.isChannel(chat) && (message.messageOwner.action instanceof TLRPC.TL_messageActionHistoryClear ||
                                    message.messageOwner.action instanceof TLRPC.TL_messageActionChannelMigrateFrom)) {
                                messageString = "";
                            } else {
                                messageString = message.messageText;
                            }
                            textColor = getContext().getResources().getColor(R.color.widget_action_text);
                        } else {
                            boolean needEmoji = true;
                            if (chat != null && chat.id > 0 && fromChat == null && (!ChatObject.isChannel(chat) || ChatObject.isMegagroup(chat))) {
                                if (message.isOutOwner()) {
                                    messageNameString = LocaleController.getString(R.string.FromYou);
                                } else if (fromUser != null) {
                                    messageNameString = UserObject.getFirstName(fromUser).replace("\n", "");
                                } else {
                                    messageNameString = "DELETED";
                                }
                                SpannableStringBuilder stringBuilder;
                                String messageFormat = "%2$s: \u2068%1$s\u2069";
                                if (message.caption != null) {
                                    String mess = message.caption.toString();
                                    if (mess.length() > 150) {
                                        mess = mess.substring(0, 150);
                                    }
                                    String emoji;
                                    if (message.isVideo()) {
                                        emoji = "\uD83D\uDCF9 ";
                                    } else if (message.isVoice()) {
                                        emoji = "\uD83C\uDFA4 ";
                                    } else if (message.isMusic()) {
                                        emoji = "\uD83C\uDFA7 ";
                                    } else if (message.isPhoto()) {
                                        emoji = "\uD83D\uDDBC ";
                                    } else {
                                        emoji = "\uD83D\uDCCE ";
                                    }
                                    stringBuilder = SpannableStringBuilder.valueOf(String.format(messageFormat, emoji + mess.replace('\n', ' '), messageNameString));
                                } else if (message.messageOwner.media != null && !message.isMediaEmpty()) {
                                    textColor = getContext().getResources().getColor(R.color.widget_action_text);
                                    String innerMessage;
                                    if (message.messageOwner.media instanceof TLRPC.TL_messageMediaPoll) {
                                        TLRPC.TL_messageMediaPoll mediaPoll = (TLRPC.TL_messageMediaPoll) message.messageOwner.media;
                                        if (Build.VERSION.SDK_INT >= 18) {
                                            innerMessage = String.format("\uD83D\uDCCA \u2068%s\u2069", mediaPoll.poll.question.text);
                                        } else {
                                            innerMessage = String.format("\uD83D\uDCCA %s", mediaPoll.poll.question.text);
                                        }
                                    } else if (message.messageOwner.media instanceof TLRPC.TL_messageMediaGame) {
                                        if (Build.VERSION.SDK_INT >= 18) {
                                            innerMessage = String.format("\uD83C\uDFAE \u2068%s\u2069", message.messageOwner.media.game.title);
                                        } else {
                                            innerMessage = String.format("\uD83C\uDFAE %s", message.messageOwner.media.game.title);
                                        }
                                    } else if (message.type == MessageObject.TYPE_MUSIC) {
                                        if (Build.VERSION.SDK_INT >= 18) {
                                            innerMessage = String.format("\uD83C\uDFA7 \u2068%s - %s\u2069", message.getMusicAuthor(), message.getMusicTitle());
                                        } else {
                                            innerMessage = String.format("\uD83C\uDFA7 %s - %s", message.getMusicAuthor(), message.getMusicTitle());
                                        }
                                    } else {
                                        innerMessage = message.messageText.toString();
                                    }
                                    innerMessage = innerMessage.replace('\n', ' ');
                                    stringBuilder = SpannableStringBuilder.valueOf(String.format(messageFormat, innerMessage, messageNameString));
                                    try {
                                        stringBuilder.setSpan(new ForegroundColorSpanThemable(Theme.key_chats_attachMessage), messageNameString.length() + 2, stringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                    } catch (Exception e) {
                                        FileLog.e(e);
                                    }
                                } else if (message.messageOwner.message != null) {
                                    String mess = message.messageOwner.message;
                                    if (mess.length() > 150) {
                                        mess = mess.substring(0, 150);
                                    }
                                    mess = mess.replace('\n', ' ').trim();
                                    stringBuilder = SpannableStringBuilder.valueOf(String.format(messageFormat, mess, messageNameString));
                                } else {
                                    stringBuilder = SpannableStringBuilder.valueOf("");
                                }
                                try {
                                    stringBuilder.setSpan(new ForegroundColorSpanThemable(Theme.key_chats_nameMessage), 0, messageNameString.length() + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                } catch (Exception e) {
                                    FileLog.e(e);
                                }
                                messageString = stringBuilder;
                            } else {
                                if (message.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto && message.messageOwner.media.photo instanceof TLRPC.TL_photoEmpty && message.messageOwner.media.ttl_seconds != 0) {
                                    messageString = LocaleController.getString(R.string.AttachPhotoExpired);
                                } else if (message.messageOwner.media instanceof TLRPC.TL_messageMediaDocument && message.messageOwner.media.document instanceof TLRPC.TL_documentEmpty && message.messageOwner.media.ttl_seconds != 0) {
                                    messageString = LocaleController.getString(R.string.AttachVideoExpired);
                                } else if (message.caption != null) {
                                    String emoji;
                                    if (message.isVideo()) {
                                        emoji = "\uD83D\uDCF9 ";
                                    } else if (message.isVoice()) {
                                        emoji = "\uD83C\uDFA4 ";
                                    } else if (message.isMusic()) {
                                        emoji = "\uD83C\uDFA7 ";
                                    } else if (message.isPhoto()) {
                                        emoji = "\uD83D\uDDBC ";
                                    } else {
                                        emoji = "\uD83D\uDCCE ";
                                    }
                                    messageString = emoji + message.caption;
                                } else {
                                    if (message.messageOwner.media instanceof TLRPC.TL_messageMediaPoll) {
                                        TLRPC.TL_messageMediaPoll mediaPoll = (TLRPC.TL_messageMediaPoll) message.messageOwner.media;
                                        messageString = "\uD83D\uDCCA " + mediaPoll.poll.question.text;
                                    } else if (message.messageOwner.media instanceof TLRPC.TL_messageMediaGame) {
                                        messageString = "\uD83C\uDFAE " + message.messageOwner.media.game.title;
                                    } else if (message.type == MessageObject.TYPE_MUSIC) {
                                        messageString = String.format("\uD83C\uDFA7 %s - %s", message.getMusicAuthor(), message.getMusicTitle());
                                    } else {
                                        messageString = message.messageText;
                                        AndroidUtilities.highlightText(messageString, message.highlightedWords, null);
                                    }
                                    if (message.messageOwner.media != null && !message.isMediaEmpty()) {
                                        textColor = getContext().getResources().getColor(R.color.widget_action_text);
                                    }
                                }
                            }
                        }

                        ((TextView) cells[a].findViewById(R.id.shortcut_widget_item_time)).setText(LocaleController.stringForMessageListDate(message.messageOwner.date));
                        ((TextView) cells[a].findViewById(R.id.shortcut_widget_item_message)).setText(messageString.toString());
                        ((TextView) cells[a].findViewById(R.id.shortcut_widget_item_message)).setTextColor(textColor);
                    } else {
                        if (dialog.last_message_date != 0) {
                            ((TextView) cells[a].findViewById(R.id.shortcut_widget_item_time)).setText(LocaleController.stringForMessageListDate(dialog.last_message_date));
                        } else {
                            ((TextView) cells[a].findViewById(R.id.shortcut_widget_item_time)).setText("");
                        }
                        ((TextView) cells[a].findViewById(R.id.shortcut_widget_item_message)).setText("");
                    }
                    if (dialog.unread_count > 0) {
                        ((TextView) cells[a].findViewById(R.id.shortcut_widget_item_badge)).setText(String.format("%d", dialog.unread_count));
                        cells[a].findViewById(R.id.shortcut_widget_item_badge).setVisibility(VISIBLE);
                        if (getMessagesController().isDialogMuted(dialog.id, 0)) {
                            cells[a].findViewById(R.id.shortcut_widget_item_badge).setBackgroundResource(R.drawable.widget_counter_muted);
                        } else {
                            cells[a].findViewById(R.id.shortcut_widget_item_badge).setBackgroundResource(R.drawable.widget_counter);
                        }
                    } else {
                        cells[a].findViewById(R.id.shortcut_widget_item_badge).setVisibility(GONE);
                    }
                }
                cells[0].findViewById(R.id.shortcut_widget_item_divider).setVisibility(cells[1].getVisibility());
                cells[1].findViewById(R.id.shortcut_widget_item_divider).setVisibility(GONE);
            } else if (widgetType == TYPE_CONTACTS) {
                for (int position = 0; position < 2; position++) {
                    for (int a = 0; a < 2; a++) {
                        int num = position * 2 + a;
                        TLRPC.Dialog dialog;
                        if (selectedDialogs.isEmpty()) {
                            if (num < getMediaDataController().hints.size()) {
                                long userId = getMediaDataController().hints.get(num).peer.user_id;
                                dialog = getMessagesController().dialogs_dict.get(userId);
                                if (dialog == null) {
                                    dialog = new TLRPC.TL_dialog();
                                    dialog.id = userId;
                                }
                            } else {
                                dialog = null;
                            }
                        } else {
                            if (num < selectedDialogs.size()) {
                                dialog = getMessagesController().dialogs_dict.get(selectedDialogs.get(num));
                                if (dialog == null) {
                                    dialog = new TLRPC.TL_dialog();
                                    dialog.id = selectedDialogs.get(num);
                                }
                            } else {
                                dialog = null;
                            }
                        }
                        if (dialog == null) {
                            cells[position].findViewById(a == 0 ? R.id.contacts_widget_item1 : R.id.contacts_widget_item2).setVisibility(INVISIBLE);
                            if (num == 0 || num == 2) {
                                cells[position].setVisibility(GONE);
                            }
                            continue;
                        }
                        cells[position].findViewById(a == 0 ? R.id.contacts_widget_item1 : R.id.contacts_widget_item2).setVisibility(VISIBLE);
                        if (num == 0 || num == 2) {
                            cells[position].setVisibility(VISIBLE);
                        }

                        String name;

                        TLRPC.FileLocation photoPath = null;
                        TLRPC.User user = null;
                        TLRPC.Chat chat = null;
                        if (DialogObject.isUserDialog(dialog.id)) {
                            user = getMessagesController().getUser(dialog.id);
                            if (UserObject.isUserSelf(user)) {
                                name = LocaleController.getString(R.string.SavedMessages);
                            } else if (UserObject.isReplyUser(user)) {
                                name = LocaleController.getString(R.string.RepliesTitle);
                            } else if (UserObject.isDeleted(user)) {
                                name = LocaleController.getString(R.string.HiddenName);
                            } else {
                                name = UserObject.getFirstName(user);
                            }
                            if (!UserObject.isReplyUser(user) && !UserObject.isUserSelf(user) && user != null && user.photo != null && user.photo.photo_small != null && user.photo.photo_small.volume_id != 0 && user.photo.photo_small.local_id != 0) {
                                photoPath = user.photo.photo_small;
                            }
                        } else {
                            chat = getMessagesController().getChat(-dialog.id);
                            name = chat.title;
                            if (chat.photo != null && chat.photo.photo_small != null && chat.photo.photo_small.volume_id != 0 && chat.photo.photo_small.local_id != 0) {
                                photoPath = chat.photo.photo_small;
                            }
                        }
                        ((TextView) cells[position].findViewById(a == 0 ? R.id.contacts_widget_item_text1 : R.id.contacts_widget_item_text2)).setText(name);
                        try {
                            Bitmap bitmap = null;
                            if (photoPath != null) {
                                File path = getFileLoader().getPathToAttach(photoPath, true);
                                bitmap = BitmapFactory.decodeFile(path.toString());
                            }

                            int size = AndroidUtilities.dp(48);
                            Bitmap result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                            result.eraseColor(Color.TRANSPARENT);
                            Canvas canvas = new Canvas(result);
                            if (bitmap == null) {
                                AvatarDrawable avatarDrawable;
                                if (user != null) {
                                    avatarDrawable = new AvatarDrawable(user);
                                    if (UserObject.isReplyUser(user)) {
                                        avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_REPLIES);
                                    } else if (UserObject.isUserSelf(user)) {
                                        avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
                                    }
                                } else {
                                    avatarDrawable = new AvatarDrawable(chat);
                                }
                                avatarDrawable.setBounds(0, 0, size, size);
                                avatarDrawable.draw(canvas);
                            } else {
                                BitmapShader shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                                float scale = size / (float) bitmap.getWidth();
                                canvas.save();
                                canvas.scale(scale, scale);
                                roundPaint.setShader(shader);
                                bitmapRect.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
                                canvas.drawRoundRect(bitmapRect, bitmap.getWidth(), bitmap.getHeight(), roundPaint);
                                canvas.restore();
                            }
                            canvas.setBitmap(null);
                            ((ImageView) cells[position].findViewById(a == 0 ? R.id.contacts_widget_item_avatar1 : R.id.contacts_widget_item_avatar2)).setImageBitmap(result);
                        } catch (Throwable e) {
                            FileLog.e(e);
                        }

                        if (dialog.unread_count > 0) {
                            String count;
                            if (dialog.unread_count > 99) {
                                count = String.format("%d+", 99);
                            } else {
                                count = String.format("%d", dialog.unread_count);
                            }
                            ((TextView) cells[position].findViewById(a == 0 ? R.id.contacts_widget_item_badge1 : R.id.contacts_widget_item_badge2)).setText(count);
                            cells[position].findViewById(a == 0 ? R.id.contacts_widget_item_badge_bg1 : R.id.contacts_widget_item_badge_bg2).setVisibility(VISIBLE);
                        } else {
                            cells[position].findViewById(a == 0 ? R.id.contacts_widget_item_badge_bg1 : R.id.contacts_widget_item_badge_bg2).setVisibility(GONE);
                        }
                    }
                }
            }
            if (cells[0].getVisibility() == VISIBLE) {
                previewImageView.setVisibility(GONE);
            } else {
                previewImageView.setVisibility(VISIBLE);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(264), MeasureSpec.EXACTLY));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            Drawable newDrawable = Theme.getCachedWallpaperNonBlocking();
            if (newDrawable != backgroundDrawable && newDrawable != null) {
                if (Theme.isAnimatingColor()) {
                    oldBackgroundDrawable = backgroundDrawable;
                    oldBackgroundGradientDisposable = backgroundGradientDisposable;
                } else if (backgroundGradientDisposable != null) {
                    backgroundGradientDisposable.dispose();
                    backgroundGradientDisposable = null;
                }
                backgroundDrawable = newDrawable;
            }
            float themeAnimationValue = parentLayout.getThemeAnimationValue();
            for (int a = 0; a < 2; a++) {
                Drawable drawable = a == 0 ? oldBackgroundDrawable : backgroundDrawable;
                if (drawable == null) {
                    continue;
                }
                if (a == 1 && oldBackgroundDrawable != null && parentLayout != null) {
                    drawable.setAlpha((int) (255 * themeAnimationValue));
                } else {
                    drawable.setAlpha(255);
                }
                if (drawable instanceof ColorDrawable || drawable instanceof GradientDrawable || drawable instanceof MotionBackgroundDrawable) {
                    drawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
                    if (drawable instanceof BackgroundGradientDrawable) {
                        final BackgroundGradientDrawable backgroundGradientDrawable = (BackgroundGradientDrawable) drawable;
                        backgroundGradientDisposable = backgroundGradientDrawable.drawExactBoundsSize(canvas, this);
                    } else {
                        drawable.draw(canvas);
                    }
                } else if (drawable instanceof BitmapDrawable) {
                    BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
                    if (bitmapDrawable.getTileModeX() == Shader.TileMode.REPEAT) {
                        canvas.save();
                        float scale = 2.0f / AndroidUtilities.density;
                        canvas.scale(scale, scale);
                        drawable.setBounds(0, 0, (int) Math.ceil(getMeasuredWidth() / scale), (int) Math.ceil(getMeasuredHeight() / scale));
                    } else {
                        int viewHeight = getMeasuredHeight();
                        float scaleX = (float) getMeasuredWidth() / (float) drawable.getIntrinsicWidth();
                        float scaleY = (float) (viewHeight) / (float) drawable.getIntrinsicHeight();
                        float scale = Math.max(scaleX, scaleY);
                        int width = (int) Math.ceil(drawable.getIntrinsicWidth() * scale);
                        int height = (int) Math.ceil(drawable.getIntrinsicHeight() * scale);
                        int x = (getMeasuredWidth() - width) / 2;
                        int y = (viewHeight - height) / 2;
                        canvas.save();
                        canvas.clipRect(0, 0, width, getMeasuredHeight());
                        drawable.setBounds(x, y, x + width, y + height);
                    }
                    drawable.draw(canvas);
                    canvas.restore();
                }
                if (a == 0 && oldBackgroundDrawable != null && themeAnimationValue >= 1.0f) {
                    if (oldBackgroundGradientDisposable != null) {
                        oldBackgroundGradientDisposable.dispose();
                        oldBackgroundGradientDisposable = null;
                    }
                    oldBackgroundDrawable = null;
                    invalidate();
                }
            }
            shadowDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
            shadowDrawable.draw(canvas);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (backgroundGradientDisposable != null) {
                backgroundGradientDisposable.dispose();
                backgroundGradientDisposable = null;
            }
            if (oldBackgroundGradientDisposable != null) {
                oldBackgroundGradientDisposable.dispose();
                oldBackgroundGradientDisposable = null;
            }
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            return false;
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            return false;
        }

        @Override
        protected void dispatchSetPressed(boolean pressed) {

        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return false;
        }
    }

    public EditWidgetActivity(int type, int widgetId) {
        super();
        widgetType = type;
        currentWidgetId = widgetId;
        ArrayList<TLRPC.User> users = new ArrayList<>();
        ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        getMessagesStorage().getWidgetDialogIds(currentWidgetId, widgetType, selectedDialogs, users, chats, true);
        getMessagesController().putUsers(users, true);
        getMessagesController().putChats(chats, true);
        updateRows();
    }

    @Override
    public boolean onFragmentCreate() {
        DialogsActivity.loadDialogs(AccountInstance.getInstance(currentAccount));
        getMediaDataController().loadHints(true);
        return super.onFragmentCreate();
    }

    private void updateRows() {
        rowCount = 0;
        previewRow = rowCount++;
        selectChatsRow = rowCount++;
        if (selectedDialogs.isEmpty()) {
            chatsStartRow = -1;
            chatsEndRow = -1;
        } else {
            chatsStartRow = rowCount;
            rowCount += selectedDialogs.size();
            chatsEndRow = rowCount;
        }
        infoRow = rowCount++;

        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    public void setDelegate(EditWidgetActivityDelegate editWidgetActivityDelegate) {
        delegate = editWidgetActivityDelegate;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        if (AndroidUtilities.isTablet()) {
            actionBar.setOccupyStatusBar(false);
        }

        if (widgetType == TYPE_CHATS) {
            actionBar.setTitle(LocaleController.getString(R.string.WidgetChats));
        } else {
            actionBar.setTitle(LocaleController.getString(R.string.WidgetShortcuts));
        }
        ActionBarMenu menu = actionBar.createMenu();
        menu.addItem(done_item, LocaleController.getString(R.string.Done).toUpperCase());

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (delegate == null) {
                        finishActivity();
                        return;
                    }
                    finishFragment();
                } else if (id == done_item) {
                    if (getParentActivity() == null) {
                        return;
                    }

                    ArrayList<MessagesStorage.TopicKey> topicKeys = new ArrayList<>();
                    for (int i = 0; i < selectedDialogs.size(); i++) {
                        topicKeys.add(MessagesStorage.TopicKey.of(selectedDialogs.get(i), 0));
                    }
                    getMessagesStorage().putWidgetDialogs(currentWidgetId, topicKeys);

                    SharedPreferences preferences = getParentActivity().getSharedPreferences("shortcut_widget", Activity.MODE_PRIVATE);
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putInt("account" + currentWidgetId, currentAccount);
                    editor.putInt("type" + currentWidgetId, widgetType);
                    editor.commit();

                    AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(getParentActivity());
                    if (widgetType == TYPE_CHATS) {
                        ChatsWidgetProvider.updateWidget(getParentActivity(), appWidgetManager, currentWidgetId);
                    } else {
                        ContactsWidgetProvider.updateWidget(getParentActivity(), appWidgetManager, currentWidgetId);
                    }
                    if (delegate != null) {
                        delegate.didSelectDialogs(selectedDialogs);
                    } else {
                        finishActivity();
                    }
                }
            }
        });

        listAdapter = new ListAdapter(context);

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = frameLayout;

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        listView.setAdapter(listAdapter);
        ((DefaultItemAnimator) listView.getItemAnimator()).setDelayAnimations(false);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        itemTouchHelper = new ItemTouchHelper(new TouchHelperCallback());
        itemTouchHelper.attachToRecyclerView(listView);
        listView.setOnItemClickListener((view, position) -> {
            if (position == selectChatsRow) {
                InviteMembersBottomSheet bottomSheet = new InviteMembersBottomSheet(context, currentAccount, null, 0, EditWidgetActivity.this, null);
                bottomSheet.setDelegate(dids -> {
                    selectedDialogs.clear();
                    selectedDialogs.addAll(dids);
                    updateRows();
                    if (widgetPreviewCell != null) {
                        widgetPreviewCell.updateDialogs();
                    }
                }, selectedDialogs);
                bottomSheet.setSelectedContacts(selectedDialogs);
                showDialog(bottomSheet);
            }
        });
        listView.setOnItemLongClickListener(new RecyclerListView.OnItemLongClickListenerExtended() {

            private Rect rect = new Rect();

            @Override
            public boolean onItemClick(View view, int position, float x, float y) {
                if (getParentActivity() == null || !(view instanceof GroupCreateUserCell)) {
                    return false;
                }
                ImageView imageView = (ImageView) view.getTag(R.id.object_tag);
                imageView.getHitRect(rect);
                if (!rect.contains((int) x, (int) y)) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    CharSequence[] items = new CharSequence[]{LocaleController.getString(R.string.Delete)};
                    builder.setItems(items, (dialogInterface, i) -> {
                        if (i == 0) {
                            selectedDialogs.remove(position - chatsStartRow);
                            updateRows();
                            if (widgetPreviewCell != null) {
                                widgetPreviewCell.updateDialogs();
                            }
                        }
                    });
                    showDialog(builder.create());
                    return true;
                }
                return false;
            }

            @Override
            public void onMove(float dx, float dy) {

            }

            @Override
            public void onLongClickRelease() {

            }
        });

        return fragmentView;
    }

    private void finishActivity() {
        if (getParentActivity() == null) {
            return;
        }
        getParentActivity().finish();
        AndroidUtilities.runOnUIThread(this::removeSelfFromStack, 1000);
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type == 1 || type == 3;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new TextInfoPrivacyCell(mContext);
                    view.setBackgroundDrawable(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    break;
                case 1:
                    view = new TextCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 2:
                    view = widgetPreviewCell = new WidgetPreviewCell(mContext);
                    break;
                case 3:
                default:
                    GroupCreateUserCell cell = new GroupCreateUserCell(mContext, 0, 0, false);
                    ImageView sortImageView = new ImageView(mContext);
                    sortImageView.setImageResource(R.drawable.list_reorder);
                    sortImageView.setScaleType(ImageView.ScaleType.CENTER);
                    cell.setTag(R.id.object_tag, sortImageView);
                    cell.addView(sortImageView, LayoutHelper.createFrame(40, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT), 10, 0, 10, 0));
                    sortImageView.setOnTouchListener((v, event) -> {
                        if (event.getAction() == MotionEvent.ACTION_DOWN) {
                            itemTouchHelper.startDrag(listView.getChildViewHolder(cell));
                        }
                        return false;
                    });
                    sortImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_pinnedIcon), PorterDuff.Mode.MULTIPLY));
                    view = cell;
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == infoRow) {
                        SpannableStringBuilder builder = new SpannableStringBuilder();
                        if (widgetType == TYPE_CHATS) {
                            builder.append(LocaleController.getString(R.string.EditWidgetChatsInfo));
                        } else if (widgetType == TYPE_CONTACTS) {
                            builder.append(LocaleController.getString(R.string.EditWidgetContactsInfo));
                        }
                        if (SharedConfig.passcodeHash.length() > 0) {
                            builder.append("\n\n").append(AndroidUtilities.replaceTags(LocaleController.getString(R.string.WidgetPasscode2)));
                        }
                        cell.setText(builder);
                    }
                    break;
                }
                case 1: {
                    TextCell cell = (TextCell) holder.itemView;
                    cell.setColors(-1, Theme.key_windowBackgroundWhiteBlueText4);
                    Drawable drawable1 = mContext.getResources().getDrawable(R.drawable.poll_add_circle);
                    Drawable drawable2 = mContext.getResources().getDrawable(R.drawable.poll_add_plus);
                    drawable1.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_switchTrackChecked), PorterDuff.Mode.MULTIPLY));
                    drawable2.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_checkboxCheck), PorterDuff.Mode.MULTIPLY));
                    CombinedDrawable combinedDrawable = new CombinedDrawable(drawable1, drawable2);
                    cell.setTextAndIcon(LocaleController.getString(R.string.SelectChats), combinedDrawable, chatsStartRow != -1);
                    cell.getImageView().setPadding(0, AndroidUtilities.dp(7), 0, 0);
                    break;
                }
                case 3: {
                    GroupCreateUserCell cell = (GroupCreateUserCell) holder.itemView;
                    long did = selectedDialogs.get(position - chatsStartRow);
                    if (DialogObject.isUserDialog(did)) {
                        TLRPC.User user = getMessagesController().getUser(did);
                        cell.setObject(user, null, null, position != chatsEndRow - 1);
                    } else {
                        TLRPC.Chat chat = getMessagesController().getChat(-did);
                        cell.setObject(chat, null, null, position != chatsEndRow - 1);
                    }
                }
            }
        }

        @Override
        public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            if (type == 3 || type == 1) {
                holder.itemView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == previewRow) {
                return 2;
            } else if (position == selectChatsRow) {
                return 1;
            } else if (position == infoRow) {
                return 0;
            }
            return 3;
        }

        public boolean swapElements(int fromIndex, int toIndex) {
            int idx1 = fromIndex - chatsStartRow;
            int idx2 = toIndex - chatsStartRow;
            int count = chatsEndRow - chatsStartRow;
            if (idx1 < 0 || idx2 < 0 || idx1 >= count || idx2 >= count) {
                return false;
            }
            Long did1 = selectedDialogs.get(idx1);
            Long did2 = selectedDialogs.get(idx2);
            selectedDialogs.set(idx1, did2);
            selectedDialogs.set(idx2, did1);
            notifyItemMoved(fromIndex, toIndex);
            return true;
        }
    }

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        return false;
    }

    @Override
    public boolean onBackPressed() {
        if (delegate == null) {
            finishActivity();
            return false;
        } else {
            return super.onBackPressed();
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUBACKGROUND, null, null, null, null, Theme.key_actionBarDefaultSubmenuBackground));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM, null, null, null, null, Theme.key_actionBarDefaultSubmenuItem));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM | ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_actionBarDefaultSubmenuItemIcon));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueText4));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueText4));


        return themeDescriptions;
    }
}
