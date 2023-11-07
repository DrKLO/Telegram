package org.telegram.ui;

import static android.content.DialogInterface.BUTTON_NEGATIVE;
import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.getPath;
import static org.telegram.ui.Components.Premium.LimitReachedBottomSheet.TYPE_BOOSTS_FOR_COLOR;
import static org.telegram.ui.Components.Premium.LimitReachedBottomSheet.TYPE_BOOSTS_FOR_USERS;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.icu.util.Measure;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.ThemePreviewMessagesCell;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.LimitReachedBottomSheet;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SimpleThemeDescription;
import org.telegram.ui.Components.Text;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;
import java.util.List;

public class PeerColorActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private final boolean isChannel;
    private final long dialogId;

    private FrameLayout contentView;
    private RecyclerListView listView;
    private RecyclerView.Adapter listAdapter;
    private FrameLayout buttonContainer;
    private ButtonWithCounterView button;
    private PeerColorGrid peerColorPicker;

    private int selectedColor;
    private long selectedEmoji;
    private ThemePreviewMessagesCell messagesCellPreview;
    private SetReplyIconCell setReplyIconCell;

    private CharSequence buttonLocked, buttonUnlocked;

    int previewRow;
    int colorPickerRow;
    int infoRow;
    int iconRow;
    int info2Row;
    int buttonRow;

    int rowCount;

    private static final int VIEW_TYPE_MESSAGE = 0;
    private static final int VIEW_TYPE_COLOR_PICKER = 1;
    private static final int VIEW_TYPE_INFO = 2;
    private static final int VIEW_TYPE_ICON = 3;
    private static final int VIEW_TYPE_BUTTONPAD = 5;

    public PeerColorActivity(long dialogId) {
        super();

        this.dialogId = dialogId;
        this.isChannel = dialogId != 0;
    }

    private BaseFragment bulletinFragment;
    public PeerColorActivity setOnApplied(BaseFragment bulletinFragment) {
        this.bulletinFragment = bulletinFragment;
        return this;
    }

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addObserver(this, NotificationCenter.currentUserPremiumStatusChanged);
        Bulletin.addDelegate(this, new Bulletin.Delegate() {
            @Override
            public int getBottomOffset(int tag) {
                return dp(62);
            }

            @Override
            public boolean clipWithGradient(int tag) {
                return true;
            }
        });
        getMediaDataController().loadReplyIcons();
        if (MessagesController.getInstance(currentAccount).peerColors == null && BuildVars.DEBUG_PRIVATE_VERSION) {
            MessagesController.getInstance(currentAccount).loadAppConfig(true);
        }
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        actionBar.setTitle(LocaleController.getString(isChannel ? R.string.ChannelColorTitle : R.string.UserColorTitle));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (!isChannel && hasUnsavedChanged() && getUserConfig().isPremium()) {
                        showUnsavedAlert();
                        return;
                    }
                    finishFragment();
                }
            }
        });

        if (dialogId < 0) {
            TLRPC.Chat chat = getMessagesController().getChat(-dialogId);
            if ((chat.flags2 & 32) != 0) {
                selectedEmoji = chat.background_emoji_id;
            }
            if ((chat.flags2 & 64) != 0) {
                selectedColor = chat.color;
            } else {
                selectedColor = (int) (chat.id % 7);
            }
        } else {
            TLRPC.User user = getUserConfig().getCurrentUser();
            if ((user.flags2 & 64) != 0) {
                selectedEmoji = user.background_emoji_id;
            }
            if ((user.flags2 & 128) != 0) {
                selectedColor = user.color;
            } else {
                selectedColor = (int) (user.id % 7);
            }
        }

        FrameLayout frameLayout = new FrameLayout(context);

        listView = new RecyclerListView(context) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                super.onMeasure(widthSpec, heightSpec);
                updateButtonY();
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                super.onLayout(changed, l, t, r, b);
                updateButtonY();
            }
        };
        ((DefaultItemAnimator) listView.getItemAnimator()).setSupportsChangeAnimations(false);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(listAdapter = new RecyclerListView.SelectionAdapter() {
            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return holder.getItemViewType() == VIEW_TYPE_ICON;
            }

            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view;
                switch (viewType) {
                    case VIEW_TYPE_MESSAGE:
                        ThemePreviewMessagesCell messagesCell = messagesCellPreview = new ThemePreviewMessagesCell(context, parentLayout, ThemePreviewMessagesCell.TYPE_PEER_COLOR, dialogId);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                            messagesCell.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                        }
                        messagesCell.fragment = PeerColorActivity.this;
                        view = messagesCell;
                        break;
                    default:
                    case VIEW_TYPE_INFO:
                        TextInfoPrivacyCell cell = new TextInfoPrivacyCell(context);
                        view = cell;
                        break;
                    case VIEW_TYPE_COLOR_PICKER:
                        PeerColorGrid colorPicker = peerColorPicker = new PeerColorGrid(context, currentAccount);
                        colorPicker.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                        colorPicker.setSelected(selectedColor);
                        colorPicker.setOnColorClick(colorId -> {
                            selectedColor = colorId;
                            colorPicker.setSelected(colorId);
                            updateMessages();
                            if (setReplyIconCell != null) {
                                setReplyIconCell.invalidate();
                            }
                        });
                        view = colorPicker;
                        break;
                    case VIEW_TYPE_BUTTONPAD:
                        view = new View(context) {
                            @Override
                            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(14 + 48 + 14), MeasureSpec.EXACTLY));
                            }
                        };
                        break;
                    case VIEW_TYPE_ICON:
                        SetReplyIconCell setcell = setReplyIconCell = new SetReplyIconCell(context);
                        setcell.update(false);
                        view = setcell;
                        break;
                    case 4:
                        view = new View(context) {
                            @Override
                            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                                super.onMeasure(
                                        MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                                        MeasureSpec.makeMeasureSpec(dp(16), MeasureSpec.EXACTLY)
                                );
                            }
                        };
                        view.setBackground(Theme.getThemedDrawableByKey(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                        break;
                }
                return new RecyclerListView.Holder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                switch (getItemViewType(position)) {
                    case VIEW_TYPE_INFO:
                        TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                        if (position == infoRow) {
                            cell.setText(LocaleController.getString(isChannel ? R.string.ChannelColorHint : R.string.UserColorHint));
                            cell.setBackground(Theme.getThemedDrawableByKey(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                        } else if (position == info2Row) {
                            cell.setText(LocaleController.getString(isChannel ? R.string.ChannelReplyIconHint : R.string.UserReplyIconHint));
                            cell.setBackground(Theme.getThemedDrawableByKey(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                        }
                }
            }

            @Override
            public int getItemCount() {
                return rowCount;
            }

            @Override
            public int getItemViewType(int position) {
                if (position == previewRow) {
                    return VIEW_TYPE_MESSAGE;
                }
                if (position == infoRow || position == info2Row) {
                    return VIEW_TYPE_INFO;
                }
                if (position == colorPickerRow) {
                    return VIEW_TYPE_COLOR_PICKER;
                }
                if (position == iconRow) {
                    return VIEW_TYPE_ICON;
                }
                if (position == buttonRow) {
                    return VIEW_TYPE_BUTTONPAD;
                }
                if (position == getItemCount() - 1) {
                    return 4;
                }
                return VIEW_TYPE_INFO;
            }
        });
        listView.setOnItemClickListener((view, position) -> {
            if (view instanceof SetReplyIconCell) {
                showSelectStatusDialog((SetReplyIconCell) view);
            }
        });
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        buttonContainer = new FrameLayout(context);
        buttonContainer.setPadding(dp(14), dp(14), dp(14), dp(14));
        buttonContainer.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));

        SpannableStringBuilder buttonLock = new SpannableStringBuilder("l");
        buttonLock.setSpan(new ColoredImageSpan(R.drawable.msg_mini_lock2), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        buttonUnlocked = LocaleController.getString(isChannel ? R.string.ChannelColorApply : R.string.UserColorApplyIcon);
        buttonLocked = new SpannableStringBuilder(buttonLock).append(" ").append(buttonUnlocked);

        button = new ButtonWithCounterView(context, getResourceProvider());
        button.text.setHacks(true, true, true);
        button.setText(isChannel ? buttonUnlocked : (!getUserConfig().isPremium() ? buttonLocked : buttonUnlocked), false);
        button.setOnClickListener(v -> buttonClick());
        buttonContainer.addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48));

        frameLayout.addView(buttonContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                updateButtonY();
            }
        });

        fragmentView = contentView = frameLayout;

        updateColors();
        updateRows();

        return contentView;
    }

    private void updateButtonY() {
        if (buttonContainer == null) {
            return;
        }
        final int lastPosition = listAdapter.getItemCount() - 1;
        boolean foundLastPosition = false;
        int maxTop = 0;
        for (int i = 0; i < listView.getChildCount(); ++i) {
            View child = listView.getChildAt(i);
            final int position = listView.getChildAdapterPosition(child);
            if (position != RecyclerListView.NO_POSITION && position <= lastPosition) {
                maxTop = Math.max(maxTop, child.getTop());
                if (position == lastPosition) {
                    foundLastPosition = true;
                }
            }
        }
        if (!foundLastPosition) {
            maxTop = listView.getMeasuredHeight();
        }
        buttonContainer.setTranslationY(Math.max(0, maxTop - (listView.getMeasuredHeight() - dp(14 + 48 + 14))));
    }

    private void showBoostLimit(boolean error) {
        getMessagesController().getBoostsController().getBoostsStats(dialogId, boostsStatus -> {
            if (error || boostsStatus.level < getMessagesController().channelColorLevelMin) {
                getMessagesController().getBoostsController().userCanBoostChannel(dialogId, boostsStatus, canApplyBoost -> {
                    if (getContext() == null) {
                        return;
                    }
                    LimitReachedBottomSheet limitReachedBottomSheet = new LimitReachedBottomSheet(this, getContext(), TYPE_BOOSTS_FOR_COLOR, currentAccount, getResourceProvider());
                    limitReachedBottomSheet.setCanApplyBoost(canApplyBoost);

                    limitReachedBottomSheet.setBoostsStats(boostsStatus, true);
                    limitReachedBottomSheet.setDialogId(dialogId);
                    limitReachedBottomSheet.showStatisticButtonInLink(() -> {
                        TLRPC.Chat chat = getMessagesController().getChat(-dialogId);
                        Bundle args = new Bundle();
                        args.putLong("chat_id", -dialogId);
                        args.putBoolean("is_megagroup", chat.megagroup);
                        args.putBoolean("start_from_boosts", true);
                        TLRPC.ChatFull chatInfo = getMessagesController().getChatFull(-dialogId);
                        if (chatInfo == null || !chatInfo.can_view_stats) {
                            args.putBoolean("only_boosts", true);
                        };
                        StatisticActivity fragment = new StatisticActivity(args);
                        presentFragment(fragment);
                    });
                    showDialog(limitReachedBottomSheet);
                    AndroidUtilities.runOnUIThread(() -> button.setLoading(false), 300);
                });
            } else {
                apply();
            }
        });
    }

    @Override
    public boolean onBackPressed() {
        if (!isChannel && hasUnsavedChanged() && getUserConfig().isPremium()) {
            showUnsavedAlert();
            return false;
        }
        return super.onBackPressed();
    }

    public boolean hasUnsavedChanged() {
        if (isChannel) {
            final TLRPC.Chat chat = getMessagesController().getChat(-dialogId);
            if (chat == null) {
                return false;
            }
            if (selectedColor == chat.color && selectedEmoji == ((chat.flags2 & 64) == 0 ? 0 : chat.background_emoji_id)) {
                return false;
            }
            return true;
        } else {
            final TLRPC.User me = getUserConfig().getCurrentUser();
            if (selectedColor == me.color && selectedEmoji == ((me.flags2 & 64) == 0 ? 0 : me.background_emoji_id)) {
                return false;
            }
            return true;
        }
    }

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        if (!isChannel && hasUnsavedChanged() && getUserConfig().isPremium()) {
            return false;
        }
        return super.isSwipeBackEnabled(event);
    }

    private void showUnsavedAlert() {
        if (getVisibleDialog() != null) {
            return;
        }
        AlertDialog alertDialog = new AlertDialog.Builder(getContext(), getResourceProvider())
            .setTitle(LocaleController.getString(isChannel ? R.string.ChannelColorUnsaved : R.string.UserColorUnsaved))
            .setMessage(LocaleController.getString(isChannel ? R.string.ChannelColorUnsavedMessage : R.string.UserColorUnsavedMessage))
            .setNegativeButton(LocaleController.getString(R.string.Dismiss), (di, w) -> {
                finishFragment();
            })
            .setPositiveButton(LocaleController.getString(R.string.ApplyTheme), (di, w) -> {
                buttonClick();
            })
            .create();
        showDialog(alertDialog);
        ((TextView) alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE)).setTextColor(getThemedColor(Theme.key_text_RedBold));
    }

    private void buttonClick() {
        if (button.isLoading()) {
            return;
        }
        if (isChannel) {
            button.setLoading(true);
            showBoostLimit(false);
            return;
        } else {
            if (!getUserConfig().isPremium()) {
                Bulletin bulletin = BulletinFactory.of(this).createSimpleBulletin(R.raw.star_premium_2, AndroidUtilities.premiumText(LocaleController.getString(R.string.UserColorApplyPremium), () -> {
                    presentFragment(new PremiumPreviewFragment("name_color"));
                }));
                bulletin.getLayout().setPadding(dp(8 + 6), dp(8), dp(8 + 6), dp(8));
                bulletin.show();

                BotWebViewVibrationEffect.APP_ERROR.vibrate();
                return;
            }
        }

        apply();
        finishFragment();
        showBulletin();
    }

    private boolean applying;
    private void apply() {
        if (applying || peerColorPicker == null || !isChannel && !getUserConfig().isPremium()) {
            return;
        }

        if (isChannel) {
            final TLRPC.Chat chat = getMessagesController().getChat(-dialogId);
            if (chat == null) {
                return;
            }
            if (selectedColor == chat.color && selectedEmoji == ((chat.flags2 & 64) == 0 ? 0 : chat.background_emoji_id)) {
                return;
            }
            TLRPC.TL_channels_updateColor req = new TLRPC.TL_channels_updateColor();
            req.channel = getMessagesController().getInputChannel(-dialogId);
            if (req.channel == null) {
                return;
            }
            chat.flags2 |= 64;
            req.color = chat.color = selectedColor;
            if (selectedEmoji != 0) {
                chat.flags2 |= 32;
                chat.background_emoji_id = selectedEmoji;

                req.flags |= 1;
                req.background_emoji_id = selectedEmoji;
            } else {
                chat.flags2 &= ~32;
                chat.background_emoji_id = 0;
            }
            button.setLoading(true);
            getMessagesController().putChat(chat, false);
            getUserConfig().saveConfig(true);
            getConnectionsManager().sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                applying = false;
                if (err != null && "BOOSTS_REQUIRED".equals(err.text)) {
                    showBoostLimit(true);
                } else {
                    finishFragment();
                    showBulletin();
                }
            }));
        } else {
            final TLRPC.User me = getUserConfig().getCurrentUser();
            if (selectedColor == me.color && selectedEmoji == ((me.flags2 & 64) == 0 ? 0 : me.background_emoji_id)) {
                return;
            }
            TLRPC.TL_account_updateColor req = new TLRPC.TL_account_updateColor();
            me.flags2 |= 128;
            req.color = me.color = selectedColor;
            if (selectedEmoji != 0) {
                me.flags2 |= 64;
                me.background_emoji_id = selectedEmoji;

                req.flags |= 1;
                req.background_emoji_id = selectedEmoji;
            } else {
                me.flags2 &= ~64;
                me.background_emoji_id = 0;
            }
            getMessagesController().putUser(me, false);
            getUserConfig().saveConfig(true);
            getConnectionsManager().sendRequest(req, null);
        }
        applying = true;
        getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_EMOJI_STATUS);
    }

    private void showBulletin() {
        if (bulletinFragment != null) {
            BulletinFactory.of(bulletinFragment).createSimpleBulletin(PeerColorDrawable.from(currentAccount, selectedColor), LocaleController.getString(isChannel ? R.string.ChannelColorApplied : R.string.UserColorApplied)).show();
            bulletinFragment = null;
        }
    }

    private void updateMessages() {
        if (messagesCellPreview != null) {
            ChatMessageCell[] cells = messagesCellPreview.getCells();
            for (int i = 0; i < cells.length; ++i) {
                if (cells[i] != null) {
                    MessageObject msg = cells[i].getMessageObject();
                    if (msg != null) {
                        if (peerColorPicker != null) {
                            msg.overrideLinkColor = peerColorPicker.getColorId();
                        }
                        msg.overrideLinkEmoji = selectedEmoji;
                        cells[i].setAvatar(msg);
                        cells[i].invalidate();
                    }
                }
            }
        }
    }

    @Override
    public void onFragmentClosed() {
        super.onFragmentClosed();
        Bulletin.removeDelegate(this);
    }

    private class SetReplyIconCell extends FrameLayout {

        private TextView textView;
        private Text offText;
        private AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable imageDrawable;

        public SetReplyIconCell(Context context) {
            super(context);

            setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));

            textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            textView.setText(LocaleController.getString(isChannel ? R.string.ChannelReplyIcon : R.string.UserReplyIcon));
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL, 20, 0, 48, 0));

            imageDrawable = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(this, false, dp(24), AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_STATIC);
        }

        public void update(boolean animated) {
            if (selectedEmoji != 0) {
                imageDrawable.set(selectedEmoji, animated);
                offText = null;
            } else {
                imageDrawable.set((Drawable) null, animated);
                if (offText == null) {
                    offText = new Text(LocaleController.getString(isChannel ? R.string.ChannelReplyIconOff : R.string.UserReplyIconOff), 16);
                }
            }
        }

        public void updateImageBounds() {
            imageDrawable.setBounds(
                getWidth() - imageDrawable.getIntrinsicWidth() - dp(21),
                (getHeight() - imageDrawable.getIntrinsicHeight()) / 2,
                getWidth() - dp(21),
                (getHeight() + imageDrawable.getIntrinsicHeight()) / 2
            );
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            updateImageBounds();
            imageDrawable.setColor(getColor());
            if (offText != null) {
                offText.draw(canvas, getMeasuredWidth() - offText.getWidth() - dp(19), getMeasuredHeight() / 2f, getThemedColor(Theme.key_windowBackgroundWhiteBlueText4), 1f);
            } else {
                imageDrawable.draw(canvas);
            }
        }

        public int getColor() {
            if (selectedColor < 7) {
                return getThemedColor(Theme.keys_avatar_nameInMessage[selectedColor]);
            } else {
                MessagesController.PeerColors peerColors = MessagesController.getInstance(currentAccount).peerColors;
                if (peerColors != null) {
                    MessagesController.PeerColor color = peerColors.getColor(selectedColor);
                    if (color != null) {
                        return color.getColor1();
                    }
                }
            }
            return getThemedColor(Theme.keys_avatar_nameInMessage[0]);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(50), MeasureSpec.EXACTLY)
            );
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            imageDrawable.detach();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            imageDrawable.attach();
        }
    }

    private SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow selectAnimatedEmojiDialog;
    public void showSelectStatusDialog(SetReplyIconCell cell) {
        if (selectAnimatedEmojiDialog != null || cell == null) {
            return;
        }
        final SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow[] popup = new SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow[1];
        int xoff = 0, yoff = 0;

        AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable scrimDrawable = null;
        View scrimDrawableParent = null;
        final int popupHeight = (int) Math.min(AndroidUtilities.dp(410 - 16 - 64), AndroidUtilities.displaySize.y * .75f);
        final int popupWidth = (int) Math.min(dp(340 - 16), AndroidUtilities.displaySize.x * .95f);
        if (cell != null) {
            scrimDrawable = cell.imageDrawable;
            scrimDrawableParent = cell;
            if (cell.imageDrawable != null) {
                cell.imageDrawable.play();
                cell.updateImageBounds();
                AndroidUtilities.rectTmp2.set(cell.imageDrawable.getBounds());
                yoff = -AndroidUtilities.rectTmp2.centerY() + dp(12) - popupHeight;
                xoff = AndroidUtilities.rectTmp2.centerX() - (AndroidUtilities.displaySize.x - popupWidth);
            }
        }
        SelectAnimatedEmojiDialog popupLayout = new SelectAnimatedEmojiDialog(this, getContext(), true, xoff, SelectAnimatedEmojiDialog.TYPE_SET_REPLY_ICON, true, getResourceProvider(), 24, cell.getColor()) {
            @Override
            protected void onEmojiSelected(View emojiView, Long documentId, TLRPC.Document document, Integer until) {
                selectedEmoji = documentId == null ? 0 : documentId;
                if (cell != null) {
                    cell.update(true);
                }
                updateMessages();
                if (popup[0] != null) {
                    selectAnimatedEmojiDialog = null;
                    popup[0].dismiss();
                }
            }

            @Override
            protected float getScrimDrawableTranslationY() {
                return 0;
            }
        };
        popupLayout.useAccentForPlus = true;
        popupLayout.setSelected(selectedEmoji == 0 ? null : selectedEmoji);
        popupLayout.setSaveState(3);
        popupLayout.setScrimDrawable(scrimDrawable, scrimDrawableParent);
        popup[0] = selectAnimatedEmojiDialog = new SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow(popupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
            @Override
            public void dismiss() {
                super.dismiss();
                selectAnimatedEmojiDialog = null;
            }
        };
        popup[0].showAsDropDown(cell, 0, yoff, Gravity.TOP | Gravity.RIGHT);
        popup[0].dimBehind();
    }

    private void updateRows() {
        rowCount = 0;
        previewRow = rowCount++;
        colorPickerRow = rowCount++;
        iconRow = rowCount++;
        infoRow = rowCount++;
        buttonRow = rowCount++;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        getNotificationCenter().removeObserver(this, NotificationCenter.currentUserPremiumStatusChanged);
    }

    private List<TLRPC.TL_availableReaction> getAvailableReactions() {
        return getMediaDataController().getReactionsList();
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        return SimpleThemeDescription.createThemeDescriptions(this::updateColors,
                Theme.key_windowBackgroundWhite,
                Theme.key_windowBackgroundWhiteBlackText,
                Theme.key_windowBackgroundWhiteGrayText2,
                Theme.key_listSelector,
                Theme.key_windowBackgroundGray,
                Theme.key_windowBackgroundWhiteGrayText4,
                Theme.key_text_RedRegular,
                Theme.key_windowBackgroundChecked,
                Theme.key_windowBackgroundCheckText,
                Theme.key_switchTrackBlue,
                Theme.key_switchTrackBlueChecked,
                Theme.key_switchTrackBlueThumb,
                Theme.key_switchTrackBlueThumbChecked
        );
    }

    @SuppressLint("NotifyDataSetChanged")
    private void updateColors() {
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        listAdapter.notifyDataSetChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (account != currentAccount) return;
        if (id == NotificationCenter.currentUserPremiumStatusChanged) {
//            updateRows();
//            listAdapter.notifyDataSetChanged();

            if (button != null) {
                button.setText(isChannel ? buttonUnlocked : (!getUserConfig().isPremium() ? buttonLocked : buttonUnlocked), true);
            }
        }
    }

    private static class PeerColorPicker extends RecyclerListView {
        private final Theme.ResourcesProvider resourcesProvider;
        public final LinearLayoutManager layoutManager;
        public final Adapter adapter;
        private final int currentAccount;

        private static final int[] order = { // key_avatar_nameInMessageRed, key_avatar_nameInMessageOrange, key_avatar_nameInMessageViolet, key_avatar_nameInMessageGreen, key_avatar_nameInMessageCyan, key_avatar_nameInMessageBlue, key_avatar_nameInMessagePink
            5, // blue
            3, // green
            1, // orange
            0, // red
            2, // violet
            4, // cyan
            6  // pink
        };

        @Override
        public boolean onInterceptTouchEvent(MotionEvent e) {
            if (getParent() != null && getParent().getParent() != null) {
                getParent().getParent().requestDisallowInterceptTouchEvent(canScrollHorizontally(-1) || canScrollHorizontally(1));
                getParent().requestDisallowInterceptTouchEvent(true);
            }
            return super.onInterceptTouchEvent(e);
        }

        @Override
        public Integer getSelectorColor(int position) {
            return 0;
        }

        public PeerColorPicker(Context context, final int currentAccount, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.currentAccount = currentAccount;
            this.resourcesProvider = resourcesProvider;
            
            setPadding(dp(8), dp(8), dp(8), dp(8));
            setClipToPadding(false);

            setAdapter(adapter = new SelectionAdapter() {
                @Override
                public boolean isEnabled(ViewHolder holder) {
                    return true;
                }

                @NonNull
                @Override
                public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                    return new Holder(new ColorCell(context));
                }

                @Override
                public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
                    ColorCell cell = (ColorCell) holder.itemView;
                    cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
                    cell.setSelected(position == selectedPosition, false);
                    if (position >= 0 && position < Theme.keys_avatar_nameInMessage.length) {
                        cell.set(
                            Theme.getColor(Theme.keys_avatar_nameInMessage[order[position]], resourcesProvider)
                        );
                    } else {
                        position -= Theme.keys_avatar_nameInMessage.length;
                        MessagesController.PeerColors peerColors = MessagesController.getInstance(currentAccount).peerColors;
                        if (peerColors != null && position >= 0 && position < peerColors.colors.size()) {
                            cell.set(peerColors.colors.get(position));
                        }
                    }
                }

                @Override
                public int getItemCount() {
                    MessagesController.PeerColors peerColors = MessagesController.getInstance(currentAccount).peerColors;
                    return 7 + (peerColors == null ? 0 : peerColors.colors.size());
                }
            });
            layoutManager = new LinearLayoutManager(context);
            layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
            setLayoutManager(layoutManager);
        }

        private int selectedPosition;
        public void setSelected(int color) {
            setSelectedPosition(toPosition(color));
        }

        public void setSelectedPosition(int position) {
            if (position != selectedPosition) {
                selectedPosition = position;
                AndroidUtilities.forEachViews(this, child -> ((ColorCell) child).setSelected(getChildAdapterPosition(child) == selectedPosition, true));
            }
        }

        public int getColorId() {
            return toColorId(selectedPosition);
        }

        public int toPosition(final int colorId) {
            if (colorId >= 0 && colorId < Theme.keys_avatar_nameInMessage.length) {
                for (int i = 0; i < order.length; ++i) {
                    if (order[i] == colorId) {
                        return i;
                    }
                }
            }
            MessagesController.PeerColors peerColors = MessagesController.getInstance(currentAccount).peerColors;
            if (peerColors == null) {
                return 0;
            }
            for (int i = 0; i < peerColors.colors.size(); ++i) {
                if (peerColors.colors.get(i).id == colorId) {
                    return 7 + i;
                }
            }
            return 0;
        }

        public int toColorId(int position) {
            if (position >= 0 && position < 7) {
                return order[position];
            }
            position -= 7;
            MessagesController.PeerColors peerColors = MessagesController.getInstance(currentAccount).peerColors;
            if (peerColors == null || position < 0 || position >= peerColors.colors.size()) {
                return 0;
            }
            return peerColors.colors.get(position).id;
        }

        private static class ColorCell extends View {
            private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint paint1 = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint paint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint paint3 = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Path circlePath = new Path();
            private final Path color2Path = new Path();
            private boolean hasColor2, hasColor3;

            private final ButtonBounce bounce = new ButtonBounce(this);

            public ColorCell(Context context) {
                super(context);
                backgroundPaint.setStyle(Paint.Style.STROKE);
            }

            public void setBackgroundColor(int backgroundColor) {
                backgroundPaint.setColor(backgroundColor);
            }

            public void set(int color) {
                hasColor2 = hasColor3 = false;
                paint1.setColor(color);
            }

            public void set(int color1, int color2) {
                hasColor2 = true;
                hasColor3 = false;
                paint1.setColor(color1);
                paint2.setColor(color2);
            }

            public void set(MessagesController.PeerColor color) {
                if (Theme.isCurrentThemeDark() && color.hasColor2() && !color.hasColor3()) {
                    paint1.setColor(color.getColor2());
                    paint2.setColor(color.getColor1());
                } else {
                    paint1.setColor(color.getColor1());
                    paint2.setColor(color.getColor2());
                }
                paint3.setColor(color.getColor3());
                hasColor2 = color.hasColor2();
                hasColor3 = color.hasColor3();
            }

            private boolean selected;
            private final AnimatedFloat selectedT = new AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
            public void setSelected(boolean selected, boolean animated) {
                this.selected = selected;
                if (!animated) {
                    selectedT.set(selected, true);
                }
                invalidate();
            }

            private static final int VIEW_SIZE_DP = 56;
            private static final int CIRCLE_RADIUS_DP = 20;

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                setMeasuredDimension(dp(VIEW_SIZE_DP), dp(VIEW_SIZE_DP));

                circlePath.rewind();
                circlePath.addCircle(getMeasuredWidth() / 2f, getMeasuredHeight() / 2f, dp(CIRCLE_RADIUS_DP), Path.Direction.CW);

                color2Path.rewind();
                color2Path.moveTo(getMeasuredWidth(), 0);
                color2Path.lineTo(getMeasuredWidth(), getMeasuredHeight());
                color2Path.lineTo(0, getMeasuredHeight());
                color2Path.close();
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                canvas.save();
                final float s = bounce.getScale(.05f);
                canvas.scale(s, s, getMeasuredWidth() / 2f, getMeasuredHeight() / 2f);

                canvas.save();
                canvas.clipPath(circlePath);
                canvas.drawPaint(paint1);
                if (hasColor2) {
                    canvas.drawPath(color2Path, paint2);
                }
                canvas.restore();

                if (hasColor3) {
                    canvas.save();
                    AndroidUtilities.rectTmp.set(
                        (getMeasuredWidth() - dp(12.4f)) / 2f,
                        (getMeasuredHeight() - dp(12.4f)) / 2f,
                        (getMeasuredWidth() + dp(12.4f)) / 2f,
                        (getMeasuredHeight() + dp(12.4f)) / 2f
                    );
                    canvas.rotate(45f, getMeasuredWidth() / 2f, getMeasuredHeight() / 2f);
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(2.33f), dp(2.33f), paint3);
                    canvas.restore();
                }

                final float selectT = selectedT.set(selected);

                if (selectT > 0) {
                    backgroundPaint.setStrokeWidth(dpf2(2));
                    canvas.drawCircle(
                        getMeasuredWidth() / 2f, getMeasuredHeight() / 2f,
                        AndroidUtilities.lerp(
                            dp(CIRCLE_RADIUS_DP) + backgroundPaint.getStrokeWidth() * .5f,
                            dp(CIRCLE_RADIUS_DP) - backgroundPaint.getStrokeWidth() * 2f,
                            selectT
                        ),
                        backgroundPaint
                    );
                }

                canvas.restore();
            }

            @Override
            public void setPressed(boolean pressed) {
                super.setPressed(pressed);
                bounce.setPressed(pressed);
            }
        }
    }

    public static class ChangeNameColorCell extends View {
        private final boolean isChannel;
        private final Theme.ResourcesProvider resourcesProvider;

        private final Drawable drawable;
        private final Text buttonText;

        private final Paint userTextBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Text userText;
        private int userTextColorKey = -1;
        private boolean needDivider;

        public ChangeNameColorCell(boolean isChannel, Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.isChannel = isChannel;
            this.resourcesProvider = resourcesProvider;

            drawable = context.getResources().getDrawable(R.drawable.msg_palette).mutate();
            drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourcesProvider), PorterDuff.Mode.SRC_IN));
            buttonText = new Text(LocaleController.getString(isChannel ? R.string.ChangeChannelNameColor : R.string.ChangeUserNameColor), 16);
            updateColors();
        }

        public void updateColors() {
            drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(isChannel ? Theme.key_windowBackgroundWhiteGrayIcon : Theme.key_windowBackgroundWhiteBlueText4, resourcesProvider), PorterDuff.Mode.SRC_IN));
            buttonText.setColor(Theme.getColor(isChannel ? Theme.key_windowBackgroundWhiteBlackText : Theme.key_windowBackgroundWhiteBlueText4, resourcesProvider));

            if (userText != null && userTextBackgroundPaint != null && userTextColorKey != -1) {
                final int color = Theme.getColor(userTextColorKey, resourcesProvider);
                userText.setColor(color);
                userTextBackgroundPaint.setColor(Theme.multAlpha(color, .10f));
            }
        }

        public void set(TLRPC.Chat chat, boolean divider) {
            if (chat == null) {
                return;
            }
            needDivider = divider;
            CharSequence text = chat.title;
            text = Emoji.replaceEmoji(text, Theme.chat_msgTextPaint.getFontMetricsInt(), false);
            userText = new Text(text, 13, AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            final int color;
            int colorId = chat != null && (chat.flags2 & 64) != 0 ? chat.color : (int) (chat.id % 7);
            if (colorId < 7) {
                color = Theme.getColor(userTextColorKey = Theme.keys_avatar_nameInMessage[colorId], resourcesProvider);
            } else {
                MessagesController.PeerColors peerColors = MessagesController.getInstance(UserConfig.selectedAccount).peerColors;
                MessagesController.PeerColor peerColor = peerColors == null ? null : peerColors.getColor(colorId);
                if (peerColor != null) {
                    userTextColorKey = -1;
                    color = peerColor.getColor1();
                } else {
                    color = Theme.getColor(userTextColorKey = Theme.keys_avatar_nameInMessage[0], resourcesProvider);
                }
            }
            userText.setColor(color);
            userTextBackgroundPaint.setColor(Theme.multAlpha(color, .10f));
        }

        public void set(TLRPC.User user) {
            if (user == null) {
                return;
            }
            String name = user.first_name == null ? "" : user.first_name.trim();
            int index = name.indexOf(" ");
            if (index > 0) {
                name = name.substring(0, index);
            }
            CharSequence text = name;
            text = Emoji.replaceEmoji(text, Theme.chat_msgTextPaint.getFontMetricsInt(), false);
            userText = new Text(text, 13, AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            final int color;
            int colorId = user != null && (user.flags2 & 128) != 0 ? user.color : (int) (user.id % 7);
            if (colorId < 7) {
                color = Theme.getColor(userTextColorKey = Theme.keys_avatar_nameInMessage[colorId], resourcesProvider);
            } else {
                MessagesController.PeerColors peerColors = MessagesController.getInstance(UserConfig.selectedAccount).peerColors;
                MessagesController.PeerColor peerColor = peerColors == null ? null : peerColors.getColor(colorId);
                if (peerColor != null) {
                    userTextColorKey = -1;
                    color = peerColor.getColor1();
                } else {
                    color = Theme.getColor(userTextColorKey = Theme.keys_avatar_nameInMessage[0], resourcesProvider);
                }
            }
            userText.setColor(color);
            userTextBackgroundPaint.setColor(Theme.multAlpha(color, .10f));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(50) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));
        }

        private int rtl(int x) {
            return LocaleController.isRTL ? getMeasuredWidth() - x : x;
        }
        private float rtl(float x) {
            return LocaleController.isRTL ? getMeasuredWidth() - x : x;
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            drawable.setBounds(
                rtl(dp(64) / 2) - drawable.getIntrinsicWidth() / 2,
                getMeasuredHeight() / 2 - drawable.getIntrinsicHeight() / 2,
                rtl(dp(64) / 2) + drawable.getIntrinsicWidth() / 2,
                getMeasuredHeight() / 2 + drawable.getIntrinsicHeight() / 2
            );
            drawable.draw(canvas);
            buttonText
                    .ellipsize(getMeasuredWidth() - dp(64 + 7 + 100))
                    .draw(canvas, LocaleController.isRTL ? getMeasuredWidth() - buttonText.getWidth() - dp(64 + 7) : dp(64 + 7), getMeasuredHeight() / 2f);

            if (userText != null) {
                final int maxWidth = (int) (getMeasuredWidth() - dp(64 + 7 + 15 + 9 + 9 + 12) - Math.min(buttonText.getWidth(), getMeasuredWidth() - dp(64 + 100)));
                final int w = (int) Math.min(userText.getWidth(), maxWidth);

                AndroidUtilities.rectTmp.set(
                        LocaleController.isRTL ? dp(15) : getMeasuredWidth() - dp(15 + 9 + 9) - w,
                        (getMeasuredHeight() - dp(22)) / 2f,
                        LocaleController.isRTL ? dp(15 + 9 + 9) + w : getMeasuredWidth() - dp(15),
                        (getMeasuredHeight() + dp(22)) / 2f
                );
                canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(12), dp(12), userTextBackgroundPaint);

                userText
                        .ellipsize(maxWidth)
                        .draw(canvas, LocaleController.isRTL ? dp(15 + 9) : getMeasuredWidth() - dp(15 + 9) - w, getMeasuredHeight() / 2f);
            }

            if (needDivider) {
                Paint paint = resourcesProvider != null ? resourcesProvider.getPaint(Theme.key_paint_divider) : null;
                if (paint == null) {
                    paint = Theme.dividerPaint;
                }
                canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(64), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(64) : 0), getMeasuredHeight() - 1, paint);
            }
        }
    }

    public static class PeerColorGrid extends View {

        public class ColorButton {
            private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint paint1 = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint paint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint paint3 = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Path circlePath = new Path();
            private final Path color2Path = new Path();
            private boolean hasColor2, hasColor3;

            private final ButtonBounce bounce = new ButtonBounce(PeerColorGrid.this);

            public ColorButton() {
                backgroundPaint.setStyle(Paint.Style.STROKE);
            }

            public void setBackgroundColor(int backgroundColor) {
                backgroundPaint.setColor(backgroundColor);
            }

            public void set(int color) {
                hasColor2 = hasColor3 = false;
                paint1.setColor(color);
            }

            public void set(int color1, int color2) {
                hasColor2 = true;
                hasColor3 = false;
                paint1.setColor(color1);
                paint2.setColor(color2);
            }

            public void set(MessagesController.PeerColor color) {
                if (color == null) {
                    return;
                }
                if (Theme.isCurrentThemeDark() && color.hasColor2() && !color.hasColor3()) {
                    paint1.setColor(color.getColor2());
                    paint2.setColor(color.getColor1());
                } else {
                    paint1.setColor(color.getColor1());
                    paint2.setColor(color.getColor2());
                }
                paint3.setColor(color.getColor3());
                hasColor2 = color.hasColor2();
                hasColor3 = color.hasColor3();
            }

            private boolean selected;
            private final AnimatedFloat selectedT = new AnimatedFloat(PeerColorGrid.this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
            public void setSelected(boolean selected, boolean animated) {
                this.selected = selected;
                if (!animated) {
                    selectedT.set(selected, true);
                }
                invalidate();
            }

            private static final int VIEW_SIZE_DP = 56;
            private static final int CIRCLE_RADIUS_DP = 20;

            public int id;
            private final RectF bounds = new RectF();
            public final RectF clickBounds = new RectF();
            public void layout(int id, RectF bounds) {
                this.id = id;
                this.bounds.set(bounds);
            }
            public void layoutClickBounds(RectF bounds) {
                this.clickBounds.set(bounds);
            }

            protected void draw(Canvas canvas) {
                canvas.save();
                final float s = bounce.getScale(.05f);
                canvas.scale(s, s, bounds.centerX(), bounds.centerY());

                canvas.save();
                circlePath.rewind();
                circlePath.addCircle(bounds.centerX(), bounds.centerY(), Math.min(bounds.height() / 2f, bounds.width() / 2f), Path.Direction.CW);
                canvas.clipPath(circlePath);
                canvas.drawPaint(paint1);
                if (hasColor2) {
                    color2Path.rewind();
                    color2Path.moveTo(bounds.right, bounds.top);
                    color2Path.lineTo(bounds.right, bounds.bottom);
                    color2Path.lineTo(bounds.left, bounds.bottom);
                    color2Path.close();
                    canvas.drawPath(color2Path, paint2);
                }
                canvas.restore();

                if (hasColor3) {
                    canvas.save();
                    final float color3Size = (bounds.width() * .315f);
                    AndroidUtilities.rectTmp.set(
                        bounds.centerX() - color3Size / 2f,
                        bounds.centerY() - color3Size / 2f,
                        bounds.centerX() + color3Size / 2f,
                        bounds.centerY() + color3Size / 2f
                    );
                    canvas.rotate(45f, bounds.centerX(), bounds.centerY());
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(2.33f), dp(2.33f), paint3);
                    canvas.restore();
                }

                final float selectT = selectedT.set(selected);

                if (selectT > 0) {
                    backgroundPaint.setStrokeWidth(dpf2(2));
                    canvas.drawCircle(
                        bounds.centerX(), bounds.centerY(),
                        Math.min(bounds.height() / 2f, bounds.width() / 2f) + backgroundPaint.getStrokeWidth() * AndroidUtilities.lerp(.5f, -2f, selectT),
                        backgroundPaint
                    );
                }

                canvas.restore();
            }

            private boolean pressed;
            public boolean isPressed() {
                return pressed;
            }

            public void setPressed(boolean pressed) {
                bounce.setPressed(this.pressed = pressed);
            }
        }

        private int currentAccount;

        private ColorButton[] buttons;

        public PeerColorGrid(Context context, int currentAccount) {
            super(context);
            this.currentAccount = currentAccount;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            final int width = MeasureSpec.getSize(widthMeasureSpec);

            final MessagesController.PeerColors peerColors = MessagesController.getInstance(currentAccount).peerColors;
            final int colorsCount = 7 + (peerColors == null ? 0 : peerColors.colors.size());
            final int columns = 7;

            final float iconSize = Math.min(dp(38 + 16), width / (columns + (columns + 1) * .28947f));
            final float horizontalSeparator = iconSize * .28947f;
            final float verticalSeparator = iconSize * .315789474f;

            final int rows = colorsCount / columns;
            final int height = (int) (iconSize * rows + verticalSeparator * (rows + 1));

            setMeasuredDimension(width, height);

            if (buttons == null || buttons.length != colorsCount) {
                buttons = new ColorButton[colorsCount];
                for (int i = 0; i < colorsCount; ++i) {
                    buttons[i] = new ColorButton();
                    buttons[i].setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    if (i < 7) {
                        buttons[i].set(Theme.getColor(Theme.keys_avatar_nameInMessage[i]));
                    } else if (peerColors != null) {
                        buttons[i].set(peerColors.getColor(i));
                    }
                }
            }
            final float itemsWidth = iconSize * columns + horizontalSeparator * (columns + 1);
            final float startX = (width - itemsWidth) / 2f + horizontalSeparator;
            if (buttons != null) {
                float x = startX, y = verticalSeparator;
                for (int i = 0; i < buttons.length; ++i) {
                    AndroidUtilities.rectTmp.set(x, y, x + iconSize, y + iconSize);
                    buttons[i].layout(i, AndroidUtilities.rectTmp);
                    AndroidUtilities.rectTmp.inset(-horizontalSeparator / 2, -verticalSeparator / 2);
                    buttons[i].layoutClickBounds(AndroidUtilities.rectTmp);
                    buttons[i].setSelected(i == selectedColorId, false);

                    if (i % columns == (columns - 1)) {
                        x = startX;
                        y += iconSize + verticalSeparator;
                    } else {
                        x += iconSize + horizontalSeparator;
                    }
                }
            }
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            if (buttons != null) {
                for (int i = 0; i < buttons.length; ++i) {
                    buttons[i].draw(canvas);
                }
            }
            canvas.drawRect(dp(21), getMeasuredHeight() - 1, getMeasuredWidth() - dp(21), getMeasuredHeight(), Theme.dividerPaint);
        }

        private int selectedColorId = 0;
        public void setSelected(int colorId) {
            selectedColorId = colorId;
            if (buttons != null) {
                for (int i = 0; i < buttons.length; ++i) {
                    buttons[i].setSelected(i == colorId, true);
                }
            }
        }
        public int getColorId() {
            return selectedColorId;
        }

        private Utilities.Callback<Integer> onColorClick;
        public void setOnColorClick(Utilities.Callback<Integer> onColorClick) {
            this.onColorClick = onColorClick;
        }

        private ColorButton pressedButton;
        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            ColorButton button = null;
            if (buttons != null) {
                for (int i = 0; i < buttons.length; ++i) {
                    if (buttons[i].clickBounds.contains(event.getX(), event.getY())) {
                        button = buttons[i];
                        break;
                    }
                }
            }
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                pressedButton = button;
                if (button != null) {
                    button.setPressed(true);
                }
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
            } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                if (pressedButton != button) {
                    if (pressedButton != null) {
                        pressedButton.setPressed(false);
                    }
                    if (button != null) {
                        button.setPressed(true);
                    }
                    if (pressedButton != null && button != null) {
                        if (onColorClick != null) {
                            onColorClick.run(button.id);
                        }
                    }
                    pressedButton = button;
                }
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                if (event.getAction() == MotionEvent.ACTION_UP && pressedButton != null) {
                    if (onColorClick != null) {
                        onColorClick.run(pressedButton.id);
                    }
                }
                if (buttons != null) {
                    for (int i = 0; i < buttons.length; ++i) {
                        buttons[i].setPressed(false);
                    }
                }
                pressedButton = null;
            }
            return true;
        }
    }

    public static class PeerColorDrawable extends Drawable {

        public static PeerColorDrawable from(int currentAccount, int colorId) {
            if (colorId < 7) {
                return new PeerColorDrawable(Theme.getColor(Theme.keys_avatar_nameInMessage[colorId]), Theme.getColor(Theme.keys_avatar_nameInMessage[colorId]), Theme.getColor(Theme.keys_avatar_nameInMessage[colorId]));
            }
            MessagesController.PeerColors peerColors = MessagesController.getInstance(currentAccount).peerColors;
            MessagesController.PeerColor peerColor = peerColors == null ? null : peerColors.getColor(colorId);
            return from(peerColor);
        }

        public static PeerColorDrawable from(MessagesController.PeerColor peerColor) {
            if (peerColor == null) {
                return new PeerColorDrawable(0, 0, 0);
            }
            return new PeerColorDrawable(peerColor.getColor1(), peerColor.getColor2(), peerColor.getColor3());
        }

        private final int diameter = AndroidUtilities.dp(21.333f);
        private final int radius = diameter / 2;

        private final boolean hasColor3;
        private final Paint color1Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint color2Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint color3Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path color2Path = new Path();
        private final Path clipCirclePath = new Path();

        public PeerColorDrawable(int color1, int color2, int color3) {
            hasColor3 = color3 != color1;
            color1Paint.setColor(color1);
            color2Paint.setColor(color2);
            color3Paint.setColor(color3);

            clipCirclePath.addCircle(radius, radius, radius, Path.Direction.CW);
            color2Path.moveTo(diameter, 0);
            color2Path.lineTo(diameter, diameter);
            color2Path.lineTo(0, diameter);
            color2Path.close();
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            canvas.save();
            canvas.translate(getBounds().centerX() - radius, getBounds().centerY() - radius);
            canvas.clipPath(clipCirclePath);
            canvas.drawPaint(color1Paint);
            canvas.drawPath(color2Path, color2Paint);
            if (hasColor3) {
                AndroidUtilities.rectTmp.set(radius - dp(3.66f), radius - dp(3.66f), radius + dp(3.66f), radius + dp(3.66f));
                canvas.rotate(45, radius, radius);
                canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(2.33f), dp(2.33f), color3Paint);
            }
            canvas.restore();
        }

        @Override
        public void setAlpha(int alpha) {}

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {}

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }

        @Override
        public int getIntrinsicHeight() {
            return diameter;
        }

        @Override
        public int getIntrinsicWidth() {
            return diameter;
        }
    }
}