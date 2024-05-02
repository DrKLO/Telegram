package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.ApplicationErrorReport;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.collection.LongSparseArray;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.AvatarsDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.ClickableAnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LoadingDrawable;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Stories.StoriesController;
import org.telegram.ui.Stories.StoriesListPlaceProvider;

import java.util.ArrayList;

public class ProfileChannelCell extends FrameLayout {

    private final Theme.ResourcesProvider resourcesProvider;

    private final TextView headerView;
    private final AnimatedTextView subscribersView;

    public final DialogCell dialogCell;

    public ProfileChannelCell(BaseFragment fragment) {
        super(fragment.getContext());
        final Context context = fragment.getContext();
        this.resourcesProvider = fragment.getResourceProvider();

        LinearLayout headerLayout = new LinearLayout(context);
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        addView(headerLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP, 22, 16.6f, 22, 0));

        headerView = new TextView(context);
        headerView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        headerView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        headerView.setText(LocaleController.getString(R.string.ProfileChannel));
        headerLayout.addView(headerView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));

        subscribersView = new ClickableAnimatedTextView(context);
        subscribersView.getDrawable().setHacks(true, true, true);
        subscribersView.setAnimationProperties(.3f, 0, 165, CubicBezierInterpolator.EASE_OUT_QUINT);
        subscribersView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        subscribersView.setTextSize(dp(11));
        subscribersView.setPadding(dp(4.33f), 0, dp(4.33f), 0);
        subscribersView.setGravity(Gravity.LEFT);
        headerLayout.addView(subscribersView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 17, Gravity.LEFT | Gravity.TOP, 4, 2, 4, 0));

        dialogCell = new DialogCell(null, context, false, true, UserConfig.selectedAccount, resourcesProvider);
        dialogCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        dialogCell.setDialogCellDelegate(new DialogCell.DialogCellDelegate() {
            @Override
            public void onButtonClicked(DialogCell dialogCell) {

            }

            @Override
            public void onButtonLongPress(DialogCell dialogCell) {

            }

            @Override
            public boolean canClickButtonInside() {
                return true;
            }

            @Override
            public void openStory(DialogCell dialogCell, Runnable onDone) {
                if (fragment.getMessagesController().getStoriesController().hasStories(dialogCell.getDialogId())) {
                    fragment.getOrCreateStoryViewer().doOnAnimationReady(onDone);
                    fragment.getOrCreateStoryViewer().open(fragment.getContext(), dialogCell.getDialogId(), StoriesListPlaceProvider.of(ProfileChannelCell.this));
                    return;
                }
            }

            @Override
            public void showChatPreview(DialogCell dialogCell) {

            }

            @Override
            public void openHiddenStories() {
                StoriesController storiesController = fragment.getMessagesController().getStoriesController();
                if (storiesController.getHiddenList().isEmpty()) {
                    return;
                }
                boolean unreadOnly = storiesController.getUnreadState(DialogObject.getPeerDialogId(storiesController.getHiddenList().get(0).peer)) != StoriesController.STATE_READ;
                ArrayList<Long> peerIds = new ArrayList<>();
                for (int i = 0; i < storiesController.getHiddenList().size(); i++) {
                    long dialogId = DialogObject.getPeerDialogId(storiesController.getHiddenList().get(i).peer);
                    if (!unreadOnly || storiesController.getUnreadState(dialogId) != StoriesController.STATE_READ) {
                        peerIds.add(dialogId);
                    }
                }

                fragment.getOrCreateStoryViewer().open(context, null, peerIds, 0, null, null, StoriesListPlaceProvider.of(ProfileChannelCell.this), false);
            }
        });
        dialogCell.avatarStart = 15;
        dialogCell.messagePaddingStart = 83;
        addView(dialogCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.BOTTOM));

        updateColors();

        setWillNotDraw(false);

        loadingDrawable = new LoadingDrawable();
        loadingDrawable.setColors(
            Theme.multAlpha(Theme.getColor(Theme.key_listSelector, resourcesProvider), 1.25f),
            Theme.multAlpha(Theme.getColor(Theme.key_listSelector, resourcesProvider), .8f)
        );
        loadingDrawable.setRadiiDp(8);
    }

    private boolean loading;
    private AnimatedFloat loadingAlpha = new AnimatedFloat(320, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final LoadingDrawable loadingDrawable;

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        float loading = loadingAlpha.set(this.loading);
        if (loading > 0) {
            loadingDrawable.setAlpha((int) (0xFF * loading));

            AndroidUtilities.rectTmp.set(
                dialogCell.getX() + dp(dialogCell.messagePaddingStart + 6),
                dialogCell.getY() + dp(38),
                dialogCell.getX() + dp(dialogCell.messagePaddingStart + 6) + getWidth() * .5f,
                dialogCell.getY() + dp(38 + 8.33f)
            );
            loadingDrawable.setBounds(AndroidUtilities.rectTmp);
            loadingDrawable.draw(canvas);

            AndroidUtilities.rectTmp.set(
                    dialogCell.getX() + dp(dialogCell.messagePaddingStart + 6),
                    dialogCell.getY() + dp(38 + 18),
                    dialogCell.getX() + dp(dialogCell.messagePaddingStart + 6) + getWidth() * .36f,
                    dialogCell.getY() + dp(38 + 18 + 8.33f)
            );
            loadingDrawable.setBounds(AndroidUtilities.rectTmp);
            loadingDrawable.draw(canvas);

            AndroidUtilities.rectTmp.set(
                    dialogCell.getX() + dialogCell.getWidth() - dp(16) - dp(43),
                    dialogCell.getY() + dp(12),
                    dialogCell.getX() + dialogCell.getWidth() - dp(16),
                    dialogCell.getY() + dp(12 + 8.33f)
            );
            loadingDrawable.setBounds(AndroidUtilities.rectTmp);
            loadingDrawable.draw(canvas);

            invalidate();
        }
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return loadingDrawable == who || super.verifyDrawable(who);
    }

    private boolean set = false;

    public void set(TLRPC.Chat channel, MessageObject messageObject) {
        final boolean animated = set;
        final boolean subscribersShown = channel == null || channel.participants_count > 0;
        subscribersView.cancelAnimation();
        subscribersView.setPivotX(0);
        if (animated) {
            subscribersView.animate().alpha(subscribersShown ? 1f : 0f).scaleX(subscribersShown ? 1f : .8f).scaleY(subscribersShown ? 1f : .8f).setDuration(420).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
        } else {
            subscribersView.setAlpha(subscribersShown ? 1f : 0f);
            subscribersView.setScaleX(subscribersShown ? 1f : 0f);
            subscribersView.setScaleY(subscribersShown ? 1f : 0f);
        }

        if (channel != null) {
            int[] result = new int[1];
            boolean ignoreShort = AndroidUtilities.isAccessibilityScreenReaderEnabled();
            String shortNumber = ignoreShort ? String.valueOf(result[0] = channel.participants_count) : LocaleController.formatShortNumber(channel.participants_count, result);
            subscribersView.setText(LocaleController.formatPluralString("Subscribers", result[0]).replace(String.format("%d", result[0]), shortNumber), true);

            if (loading = (messageObject == null)) {
                dialogCell.setDialog(-channel.id, null, 0, false, animated);
            } else {
                dialogCell.setDialog(-channel.id, messageObject, messageObject.messageOwner.date, false, animated);
            }
        }

        if (!animated) {
            loadingAlpha.set(loading, true);
        }
        invalidate();

        set = true;
    }


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(115.66f), MeasureSpec.EXACTLY));
    }


    public static class ChannelMessageFetcher {

        public final int currentAccount;
        public boolean loading, loaded, error;
        public MessageObject messageObject;

        public ChannelMessageFetcher(int currentAccount) {
            this.currentAccount = currentAccount;
        }

        private int searchId;
        public long channel_id;
        public int message_id;

        public void fetch(TLRPC.UserFull userInfo) {
            if (userInfo == null || (userInfo.flags2 & 64) == 0) {
                searchId++;
                loaded = true;
                messageObject = null;
                done(false);
                return;
            }
            fetch(userInfo.personal_channel_id, userInfo.personal_channel_message);
        }

        public void fetch(long channel_id, int message_id) {
            if (loaded || loading) {
                if (this.channel_id != channel_id || this.message_id != message_id) {
                    loaded = false;
                    messageObject = null;
                } else {
                    return;
                }
            }
            final int thisSearchId = ++this.searchId;
            loading = true;

            this.channel_id = channel_id;
            this.message_id = message_id;

            final long selfId = UserConfig.getInstance(currentAccount).getClientUserId();
            MessagesStorage storage = MessagesStorage.getInstance(currentAccount);
            storage.getStorageQueue().postRunnable(() -> {
                TLRPC.Message message = null;
                ArrayList<TLRPC.User> users = new ArrayList<>();
                ArrayList<TLRPC.Chat> chats = new ArrayList<>();
                SQLiteCursor cursor = null;
                try {
                    if (message_id <= 0) {
                        cursor = storage.getDatabase().queryFinalized("SELECT data, mid FROM messages_v2 WHERE uid = ? ORDER BY mid DESC LIMTI 1", -channel_id);
                    } else {
                        cursor = storage.getDatabase().queryFinalized("SELECT data, mid FROM messages_v2 WHERE uid = ? AND mid = ? LIMIT 1", -channel_id, message_id);
                    }
                    ArrayList<Long> usersToLoad = new ArrayList<>();
                    ArrayList<Long> chatsToLoad = new ArrayList<>();
                    if (cursor.next()) {
                        NativeByteBuffer data = cursor.byteBufferValue(0);
                        if (data != null) {
                            message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                            message.readAttachPath(data, selfId);
                            data.reuse();
                            message.id = cursor.intValue(1);
                            message.dialog_id = -channel_id;
                            MessagesStorage.addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad, null);
                        }
                    }
                    cursor.dispose();

                    if (message != null) {

                        if (!usersToLoad.isEmpty()) {
                            storage.getUsersInternal(TextUtils.join(",", usersToLoad), users);
                        }
                        if (!chatsToLoad.isEmpty()) {
                            storage.getChatsInternal(TextUtils.join(",", chatsToLoad), chats);
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                } finally {
                    if (cursor != null) {
                        cursor.dispose();
                    }
                }
                final TLRPC.Message finalMessage = message;
                AndroidUtilities.runOnUIThread(() -> {
                    if (thisSearchId != searchId) return;
                    MessageObject messageObject1 = null;
                    if (finalMessage != null) {
                        messageObject1 = new MessageObject(currentAccount, finalMessage, true, true);
                    }

                    if (messageObject1 != null) {
                        this.messageObject = messageObject1;
                        done(false);
                        return;
                    }

                    TLRPC.TL_channels_getMessages req = new TLRPC.TL_channels_getMessages();
                    req.channel = MessagesController.getInstance(currentAccount).getInputChannel(channel_id);
                    req.id.add(message_id);
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, err) -> AndroidUtilities.runOnUIThread(() -> {
                        if (response instanceof TLRPC.messages_Messages) {
                            TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                            MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                            MessagesController.getInstance(currentAccount).putChats(res.chats, false);
                            storage.putUsersAndChats(res.users, res.chats, true, true);
                            storage.putMessages(res, -channel_id, -1, 0, false, 0, 0);

                            if (thisSearchId != searchId) return;

                            TLRPC.Message message1 = null;
                            for (TLRPC.Message m : res.messages) {
                                if (m.id == message_id) {
                                    message1 = m;
                                    break;
                                }
                            }
                            if (message1 != null) {
                                if (message1 instanceof TLRPC.TL_messageEmpty) {
                                    this.messageObject = null;
                                } else {
                                    this.messageObject = new MessageObject(currentAccount, message1, true, true);
                                }
                                done(false);
                            }
                        } else {
                            if (thisSearchId != searchId) return;
                            done(true);
                        }
                    }));
                });
            });
        }

        private ArrayList<Runnable> callbacks = new ArrayList<>();
        public void subscribe(Runnable callback) {
            if (loaded) {
                callback.run();
            } else {
                callbacks.add(callback);
            }
        }
        private void done(boolean error) {
            loading = false;
            loaded = true;
            this.error = error;
            for (Runnable callback : callbacks) callback.run();
            callbacks.clear();
        }
    }

    public int processColor(int color) {
        return color;
    }

    public void updateColors() {
        final int headerColor = processColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader, resourcesProvider));
        subscribersView.setTextColor(headerColor);
        subscribersView.setBackground(Theme.createRoundRectDrawable(dp(4.5f), dp(4.5f), Theme.multAlpha(headerColor, .1f)));
        headerView.setTextColor(headerColor);
//        titleView.setTextColor(Theme.getColor(Theme.key_chats_name, resourcesProvider));
//        dateView.setTextColor(Theme.getColor(Theme.key_chats_date, resourcesProvider));
    }

}
