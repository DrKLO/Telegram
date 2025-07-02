package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.Components.Premium.LimitReachedBottomSheet.TYPE_BOOSTS_FOR_CUSTOM_EMOJI_PACK;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ChatThemeController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.ResultCallback;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.EmojiThemes;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeColors;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.ThemePreviewMessagesCell;
import org.telegram.ui.Cells.ThemesHorizontalListCell;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.ChatThemeBottomSheet;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.Easings;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.LimitReachedBottomSheet;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.Text;
import org.telegram.ui.Components.ThemeSmallPreviewView;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.ui.Stories.recorder.PreviewView;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ChannelColorActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    public final long dialogId;
    public int currentLevel;
    public TL_stories.TL_premium_boostsStatus boostsStatus;
    protected boolean isGroup;

    public int currentReplyColor, selectedReplyColor;
    public long currentReplyEmoji, selectedReplyEmoji;
    public int currentProfileColor, selectedProfileColor;
    public long currentProfileEmoji, selectedProfileEmoji;
    public TLRPC.EmojiStatus currentStatusEmoji, selectedStatusEmoji;
    public TLRPC.WallPaper currentWallpaper, selectedWallpaper;
    public TLRPC.WallPaper galleryWallpaper;

    public Drawable backgroundDrawable;

    public int minLevelRequired() {
        int lvl = 0;
        if (currentReplyColor != selectedReplyColor) {
            MessagesController.PeerColors peerColors = getMessagesController().peerColors;
            MessagesController.PeerColor peerColor = peerColors == null ? null : peerColors.getColor(selectedReplyColor);
            if (peerColor != null) {
                lvl = Math.max(lvl, peerColor.getLvl(isGroup));
            }
        }
        if (currentReplyEmoji != selectedReplyEmoji) {
            lvl = Math.max(lvl, getMessagesController().channelBgIconLevelMin);
        }
        if (currentProfileColor != selectedProfileColor) {
            MessagesController.PeerColors peerColors = getMessagesController().profilePeerColors;
            MessagesController.PeerColor peerColor = peerColors == null ? null : peerColors.getColor(selectedProfileColor);
            if (peerColor != null) {
                lvl = Math.max(lvl, peerColor.getLvl(isGroup));
            }
        }
        if (currentProfileEmoji != selectedProfileEmoji) {
            lvl = Math.max(lvl, getProfileIconLevelMin());
        }
        if (!DialogObject.emojiStatusesEqual(currentStatusEmoji, selectedStatusEmoji)) {
            lvl = Math.max(lvl, getEmojiStatusLevelMin());
        }
        if (!ChatThemeController.wallpaperEquals(currentWallpaper, selectedWallpaper)) {
            lvl = Math.max(lvl, getWallpaperLevelMin());
        }
        return lvl;
    }

    protected int getProfileIconLevelMin() {
        return getMessagesController().channelProfileIconLevelMin;
    }

    protected int getCustomWallpaperLevelMin() {
        return getMessagesController().channelCustomWallpaperLevelMin;
    }

    protected int getWallpaperLevelMin() {
        return getMessagesController().channelWallpaperLevelMin;
    }

    protected int getEmojiStatusLevelMin() {
        return getMessagesController().channelEmojiStatusLevelMin;
    }

    protected int getEmojiStickersLevelMin() {
        return 0;
    }

    private SpannableStringBuilder lock;
    public void updateButton(boolean animated) {
        if (button == null || boostsStatus == null) {
            return;
        }
        int minLevel = minLevelRequired();
        if (currentLevel >= minLevel) {
            button.setSubText(null, animated);
        } else {
            if (lock == null) {
                lock = new SpannableStringBuilder("l");
                ColoredImageSpan coloredImageSpan = new ColoredImageSpan(R.drawable.mini_switch_lock);
                coloredImageSpan.setTopOffset(1);
                lock.setSpan(coloredImageSpan, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            SpannableStringBuilder buttonLockedText = new SpannableStringBuilder();
            buttonLockedText.append(lock).append(LocaleController.formatPluralString("BoostLevelRequired", minLevel));
            button.setSubText(buttonLockedText, animated);
        }
    }

    public class ThemeDelegate implements Theme.ResourcesProvider {
        @Override
        public int getColor(int key) {
            int index = currentColors.indexOfKey(key);
            if (index >= 0) {
                return currentColors.valueAt(index);
            }
            if (parentResourcesProvider != null) {
                return parentResourcesProvider.getColor(key);
            }
            return Theme.getColor(key);
        }

        @Override
        public Drawable getDrawable(String drawableKey) {
            if (drawableKey.equals(Theme.key_drawable_msgIn)) {
                return msgInDrawable;
            }
            if (drawableKey.equals(Theme.key_drawable_msgInSelected)) {
                return msgInDrawableSelected;
            }
            if (drawableKey.equals(Theme.key_drawable_msgOut)) {
                return msgOutDrawable;
            }
            if (drawableKey.equals(Theme.key_drawable_msgOutSelected)) {
                return msgOutDrawableSelected;
            }
            if (drawableKey.equals(Theme.key_drawable_msgOutCheckRead)) {
                msgOutCheckReadDrawable.setColorFilter(getColor(Theme.key_chat_outSentCheckRead), PorterDuff.Mode.MULTIPLY);
                return msgOutCheckReadDrawable;
            }
            if (drawableKey.equals(Theme.key_drawable_msgOutHalfCheck)) {
                msgOutHalfCheckDrawable.setColorFilter(getColor(Theme.key_chat_outSentCheckRead), PorterDuff.Mode.MULTIPLY);
                return msgOutHalfCheckDrawable;
            }
            if (parentResourcesProvider != null) {
                return parentResourcesProvider.getDrawable(drawableKey);
            }
            return Theme.getThemeDrawable(drawableKey);
        }

        @Override
        public Paint getPaint(String paintKey) {
            if (paintKey.equals(Theme.key_paint_divider)) {
                return dividerPaint;
            }
            return Theme.ResourcesProvider.super.getPaint(paintKey);
        }

        @Override
        public boolean isDark() {
            return isDark;
        }

        public void toggle() {
            isDark = !isDark;
            updateThemeColors();
            updateColors();
        }
    }

    @Override
    public boolean onFragmentCreate() {
        getMediaDataController().loadRestrictedStatusEmojis();
        getNotificationCenter().addObserver(this, NotificationCenter.boostByChannelCreated);
        getNotificationCenter().addObserver(this, NotificationCenter.chatWasBoostedByUser);
        getNotificationCenter().addObserver(this, NotificationCenter.dialogDeleted);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        getNotificationCenter().removeObserver(this, NotificationCenter.boostByChannelCreated);
        getNotificationCenter().removeObserver(this, NotificationCenter.chatWasBoostedByUser);
        getNotificationCenter().removeObserver(this, NotificationCenter.dialogDeleted);
    }

    public ChannelColorActivity(long dialogId) {
        super();
        this.dialogId = dialogId;

        TLRPC.Chat chat = getMessagesController().getChat(-dialogId);
        if (chat != null) {
            currentLevel = chat.level;
        }
        MessagesController.getInstance(currentAccount).getBoostsController().getBoostsStats(dialogId, boostsStatus -> {
            this.boostsStatus = boostsStatus;
            if (boostsStatus != null) {
                this.currentLevel = boostsStatus.level;
                if (chat != null) {
                    chat.flags |= 1024;
                    chat.level = currentLevel;
                }
            }
            updateButton(true);
            if (button != null) {
                button.setLoading(false);
            }
        });

        resourceProvider = new ThemeDelegate();
        msgInDrawable = new Theme.MessageDrawable(Theme.MessageDrawable.TYPE_TEXT, false, false, resourceProvider);
        msgInDrawableSelected = new Theme.MessageDrawable(Theme.MessageDrawable.TYPE_TEXT, false, true, resourceProvider);
        msgOutDrawable = new Theme.MessageDrawable(Theme.MessageDrawable.TYPE_TEXT, true, false, resourceProvider);
        msgOutDrawableSelected = new Theme.MessageDrawable(Theme.MessageDrawable.TYPE_TEXT, true, true, resourceProvider);
    }

    @Override
    public void setResourceProvider(Theme.ResourcesProvider resourceProvider) {
        parentResourcesProvider = resourceProvider;
    }

    private boolean isDark = Theme.isCurrentThemeDark();
    private RLottieDrawable sunDrawable;
    private ActionBarMenuItem dayNightItem;

    protected RecyclerListView listView;
    protected Adapter adapter;
    protected GridLayoutManager layoutManager;
    protected FrameLayout buttonContainer;
    protected ButtonWithCounterView button;

    protected void createListView() {
        listView = new RecyclerListView(getContext(), resourceProvider);
    }

    protected void openBoostDialog(int type) {

    }

    @Override
    public View createView(Context context) {
        TLRPC.Chat chat = getMessagesController().getChat(-dialogId);
        if (chat != null) {
            currentReplyColor = selectedReplyColor = ChatObject.getColorId(chat);
            currentReplyEmoji = selectedReplyEmoji = ChatObject.getEmojiId(chat);
            currentProfileColor = selectedProfileColor = ChatObject.getProfileColorId(chat);
            currentProfileEmoji = selectedProfileEmoji = ChatObject.getProfileEmojiId(chat);
            currentStatusEmoji = selectedStatusEmoji = chat.emoji_status;
        }
        TLRPC.ChatFull chatFull = getMessagesController().getChatFull(-dialogId);
        if (chatFull != null) {
            currentWallpaper = selectedWallpaper = chatFull.wallpaper;
            if (ChatThemeController.isNotEmoticonWallpaper(currentWallpaper)) {
                galleryWallpaper = currentWallpaper;
            }
        }

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString(R.string.ChannelColorTitle2));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (currentLevel >= minLevelRequired() && hasUnsavedChanged()) {
                        showUnsavedAlert();
                        return;
                    }
                    finishFragment();
                } else if (id == 1) {
                    toggleTheme();
                }
            }
        });

        sunDrawable = new RLottieDrawable(R.raw.sun, "" + R.raw.sun, dp(28), dp(28), true, null);
        sunDrawable.setPlayInDirectionOfCustomEndFrame(true);
        if (!isDark) {
            sunDrawable.setCustomEndFrame(0);
            sunDrawable.setCurrentFrame(0);
        } else {
            sunDrawable.setCurrentFrame(35);
            sunDrawable.setCustomEndFrame(36);
        }
        sunDrawable.beginApplyLayerColors();
        int color = Theme.getColor(Theme.key_chats_menuName, resourceProvider);
        sunDrawable.setLayerColor("Sunny.**", color);
        sunDrawable.setLayerColor("Path 6.**", color);
        sunDrawable.setLayerColor("Path.**", color);
        sunDrawable.setLayerColor("Path 5.**", color);
        dayNightItem = actionBar.createMenu().addItem(1, sunDrawable);

        FrameLayout contentView = new FrameLayout(context);

        updateRows();
        createListView();
        listView.setAdapter(adapter = new Adapter());
        layoutManager = new GridLayoutManager(context, 3);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 0, 0, 0, 68));
        listView.setOnItemClickListener((view, position) -> {
            if (view instanceof EmojiCell) {
                if (position == packStickerRow) {
                    if (chatFull == null) return;
                    GroupStickersActivity fragment = new GroupStickersActivity(-dialogId);
                    fragment.setInfo(chatFull);
                    presentFragment(fragment);
                    return;
                }
                long selectedEmojiId = 0;
                if (position == replyEmojiRow) {
                    selectedEmojiId = selectedReplyEmoji;
                } else if (position == profileEmojiRow) {
                    selectedEmojiId = selectedProfileEmoji;
                } else if (position == statusEmojiRow) {
                    if (selectedStatusEmoji instanceof TLRPC.TL_emojiStatusCollectible) {
                        selectedEmojiId = ((TLRPC.TL_emojiStatusCollectible) selectedStatusEmoji).collectible_id;
                    } else {
                        selectedEmojiId = DialogObject.getEmojiStatusDocumentId(selectedStatusEmoji);
                    }
                }
                if (position == packEmojiRow) {
                    final int requiredLvl = getEmojiStickersLevelMin();
                    if (boostsStatus != null && boostsStatus.level < requiredLvl) {
                        openBoostDialog(TYPE_BOOSTS_FOR_CUSTOM_EMOJI_PACK);
                        return;
                    }
                    GroupStickersActivity fragment = new GroupStickersActivity(-dialogId, true);
                    fragment.setInfo(chatFull);
                    presentFragment(fragment);
                    return;
                }
                final EmojiCell cell = (EmojiCell) view;
                showSelectStatusDialog(cell, selectedEmojiId, position == statusEmojiRow, (documentId, until, gift) -> {
                    if (position == replyEmojiRow) {
                        selectedReplyEmoji = documentId;
                        updateMessagesPreview(true);
                    } else if (position == profileEmojiRow) {
                        selectedProfileEmoji = documentId;
                        updateProfilePreview(true);
                    } else if (position == statusEmojiRow) {
                        if (documentId == 0) {
                            selectedStatusEmoji = null;
                        } else if (gift != null) {
                            final TLRPC.TL_emojiStatusCollectible status = MessagesController.emojiStatusCollectibleFromGift(gift);
                            if (until != null) {
                                status.flags |= 1;
                                status.until = until;
                            }
                            selectedStatusEmoji = status;
                            selectedProfileColor = -1;
                            selectedProfileEmoji = 0;
                        } else {
                            final TLRPC.TL_emojiStatus status = new TLRPC.TL_emojiStatus();
                            status.document_id = documentId;
                            if (until != null) {
                                status.flags |= 1;
                                status.until = until;
                            }
                            selectedStatusEmoji = status;
                        }
                        updateProfilePreview(true);
                    }
                    updateButton(true);
                    ((EmojiCell) view).setEmoji(documentId, gift != null, true);
                }, selectedStatusEmoji instanceof TLRPC.TL_emojiStatusCollectible ? Theme.getColor(Theme.key_windowBackgroundWhiteBlueIcon, resourceProvider) : cell.getColor());
            } else if (position == removeProfileColorRow) {
                selectedProfileColor = -1;
                selectedProfileEmoji = 0;
                if (selectedStatusEmoji instanceof TLRPC.TL_emojiStatusCollectible) {
                    selectedStatusEmoji = null;
                }
                updateProfilePreview(true);
                updateButton(true);
                updateRows();
            } else if (position == wallpaperRow) {
                ChatThemeBottomSheet.openGalleryForBackground(getParentActivity(), this, dialogId, resourceProvider, wallpaper -> {
                    this.currentWallpaper = wallpaper;
                    this.selectedWallpaper = wallpaper;
                    this.galleryWallpaper = wallpaper;
                    updateButton(false);
                    updateMessagesPreview(false);
                    AndroidUtilities.runOnUIThread(() -> BulletinFactory.of(this).createSimpleBulletin(R.raw.done, LocaleController.getString(R.string.ChannelWallpaperUpdated)).show(), 350);
                }, new ThemePreviewActivity.DayNightSwitchDelegate() {
                    @Override
                    public boolean isDark() {
                        return ChannelColorActivity.this.resourceProvider != null ? ChannelColorActivity.this.resourceProvider.isDark() : Theme.isCurrentThemeDark();
                    }

                    @Override
                    public void switchDayNight(boolean animated) {
                        if (resourceProvider instanceof ChannelColorActivity.ThemeDelegate) {
                            ((ChannelColorActivity.ThemeDelegate) resourceProvider).toggle();
                        }
                        setForceDark(isDark(), false);
                        updateColors();
                    }

                    @Override
                    public boolean supportsAnimation() {
                        return false;
                    }
                }, boostsStatus);
            }
        });
        DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setDurations(350);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setSupportsChangeAnimations(false);
        listView.setItemAnimator(itemAnimator);

        button = new ButtonWithCounterView(context, resourceProvider);
        button.setText(LocaleController.getString(R.string.ApplyChanges), false);
        button.setOnClickListener(v -> buttonClick());
        updateButton(false);

        buttonContainer = new FrameLayout(context);
        buttonContainer.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        buttonContainer.addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM, 10, 10, 10, 10));
        contentView.addView(buttonContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 68, Gravity.BOTTOM));

        Bulletin.addDelegate(this, new Bulletin.Delegate() {
            @Override
            public int getBottomOffset(int tag) {
                return buttonContainer.getMeasuredHeight();
            }
        });
        return fragmentView = contentView;
    }

    public boolean seesLoading() {
        if (listView == null) return false;
        for (int i = 0; i < listView.getChildCount(); ++i) {
            View child = listView.getChildAt(i);
            if (child instanceof FlickerLoadingView) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onBackPressed() {
        if (currentLevel >= minLevelRequired() && hasUnsavedChanged()) {
            showUnsavedAlert();
            return false;
        }
        return super.onBackPressed();
    }

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        return !hasUnsavedChanged() || currentLevel < minLevelRequired();
    }

    private void buttonClick() {
        if (boostsStatus == null || button.isLoading()) {
            return;
        }
        if (currentLevel < minLevelRequired()) {
            button.setLoading(true);
            showLimit();
            return;
        }

        int[] reqCount = new int[] { 0 };
        int[] reqReceivedCount = new int[] { 0 };
        boolean[] receivedError = new boolean[] { false };
        Utilities.Callback<TLRPC.TL_error> whenRequestDone = error -> AndroidUtilities.runOnUIThread(() -> {
            if (receivedError[0] || reqReceivedCount[0] >= reqCount[0]) return;
            if (error != null) {
                receivedError[0] = true;
                if ("BOOSTS_REQUIRED".equals(error.text)) {
                    showLimit();
                } else {
                    button.setLoading(false);
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.error, LocaleController.formatString(R.string.UnknownErrorCode, error.text)).show();
                }
                return;
            }
            reqReceivedCount[0]++;
            if (reqReceivedCount[0] == reqCount[0]) {
                finishFragment();
                showBulletin();
                button.setLoading(false);
            }
        });

        TLRPC.Chat channel = getMessagesController().getChat(-dialogId);
        if (channel == null) {
            FileLog.e("channel is null in ChannelColorAcitivity");
            BulletinFactory.of(this).createSimpleBulletin(R.raw.error, LocaleController.getString(R.string.UnknownError)).show();
            return;
        }

        button.setLoading(true);

        if (currentReplyColor != selectedReplyColor || currentReplyEmoji != selectedReplyEmoji) {
            TLRPC.TL_channels_updateColor req = new TLRPC.TL_channels_updateColor();
            req.channel = getMessagesController().getInputChannel(-dialogId);
            req.for_profile = false;

            if (channel.color == null) {
                channel.color = new TLRPC.TL_peerColor();
                channel.flags2 |= 128;
            }
            req.flags |= 4;
            req.color = selectedReplyColor;
            channel.color.flags |= 1;
            channel.color.color = selectedReplyColor;

            if (selectedReplyEmoji != 0) {
                req.flags |= 1;
                req.background_emoji_id = selectedReplyEmoji;
                channel.color.flags |= 2;
                channel.color.background_emoji_id = selectedReplyEmoji;
            } else {
                channel.color.flags &=~ 2;
                channel.color.background_emoji_id = 0;
            }

            reqCount[0]++;
            getConnectionsManager().sendRequest(req, (res, err) -> {
                if (res instanceof TLRPC.Updates) {
                    getMessagesController().processUpdates((TLRPC.Updates) res, false);
                }
                if (whenRequestDone != null) {
                    whenRequestDone.run(err);
                }
            });
        }

        if (currentProfileColor != selectedProfileColor || currentProfileEmoji != selectedProfileEmoji) {
            TLRPC.TL_channels_updateColor req = new TLRPC.TL_channels_updateColor();
            req.channel = getMessagesController().getInputChannel(-dialogId);
            req.for_profile = true;

            if (channel.profile_color == null) {
                channel.profile_color = new TLRPC.TL_peerColor();
                channel.flags2 |= 256;
            }
            if (selectedProfileColor >= 0) {
                req.flags |= 4;
                req.color = selectedProfileColor;
                channel.profile_color.flags |= 1;
                channel.profile_color.color = selectedProfileColor;
            } else {
                channel.profile_color.flags &=~ 1;
            }

            if (selectedProfileEmoji != 0) {
                req.flags |= 1;
                req.background_emoji_id = selectedProfileEmoji;
                channel.profile_color.flags |= 2;
                channel.profile_color.background_emoji_id = selectedProfileEmoji;
            } else {
                channel.profile_color.flags &=~ 2;
                channel.profile_color.background_emoji_id = 0;
            }

            reqCount[0]++;
            getConnectionsManager().sendRequest(req, (res, err) -> {
                if (res instanceof TLRPC.Updates) {
                    getMessagesController().processUpdates((TLRPC.Updates) res, false);
                }
                if (whenRequestDone != null) {
                    whenRequestDone.run(err);
                }
            });
        }

        if (!ChatThemeController.wallpaperEquals(currentWallpaper, selectedWallpaper)) {
            TLRPC.TL_messages_setChatWallPaper req = new TLRPC.TL_messages_setChatWallPaper();
            req.peer = getMessagesController().getInputPeer(dialogId);
            if (selectedWallpaper != null) {
                if (!TextUtils.isEmpty(ChatThemeController.getWallpaperEmoticon(selectedWallpaper))) {
                    req.flags |= 1;
                    req.wallpaper = new TLRPC.TL_inputWallPaperNoFile();
                    ((TLRPC.TL_inputWallPaperNoFile) req.wallpaper).id = 0;

                    req.flags |= 4;
                    req.settings = new TLRPC.TL_wallPaperSettings();
                    req.settings.flags |= 128;
                    req.settings.emoticon = ChatThemeController.getWallpaperEmoticon(selectedWallpaper);
                } else {
                    req.flags |= 1;
                    if (selectedWallpaper instanceof TLRPC.TL_wallPaper) {
                        TLRPC.TL_inputWallPaper wallPaper = new TLRPC.TL_inputWallPaper();
                        wallPaper.id = selectedWallpaper.id;
                        wallPaper.access_hash = selectedWallpaper.access_hash;
                        req.wallpaper = wallPaper;
                    } else if (selectedWallpaper instanceof TLRPC.TL_wallPaperNoFile) {
                        TLRPC.TL_inputWallPaperNoFile wallPaperNoFile = new TLRPC.TL_inputWallPaperNoFile();
                        wallPaperNoFile.id = selectedWallpaper.id;
                        req.wallpaper = wallPaperNoFile;
                    }
                }
            }

            reqCount[0]++;
            getConnectionsManager().sendRequest(req, (res, err) -> {
                if (res instanceof TLRPC.Updates) {
                    getMessagesController().processUpdates((TLRPC.Updates) res, false);
                }
                if (whenRequestDone != null) {
                    whenRequestDone.run(err);
                }
            });
            TLRPC.ChatFull chatFull1 = getMessagesController().getChatFull(-dialogId);
            ChatThemeController.getInstance(currentAccount).saveChatWallpaper(dialogId, selectedWallpaper);
            if (chatFull1 != null) {
                if (selectedWallpaper == null) {
                    chatFull1.flags2 &=~ 128;
                    chatFull1.wallpaper = null;
                } else {
                    chatFull1.flags2 |= 128;
                    chatFull1.wallpaper = selectedWallpaper;
                }
                getMessagesController().putChatFull(chatFull1);
                getNotificationCenter().postNotificationName(NotificationCenter.chatInfoDidLoad, chatFull1, 0, false, false);
            }
        }

        if (!DialogObject.emojiStatusesEqual(currentStatusEmoji, selectedStatusEmoji)) {
            TLRPC.TL_channels_updateEmojiStatus req = new TLRPC.TL_channels_updateEmojiStatus();
            req.channel = getMessagesController().getInputChannel(-dialogId);
            if (selectedStatusEmoji == null || selectedStatusEmoji instanceof TLRPC.TL_emojiStatusEmpty) {
                req.emoji_status = new TLRPC.TL_emojiStatusEmpty();
                channel.emoji_status = new TLRPC.TL_emojiStatusEmpty();
                channel.flags2 &=~ 512;
            } else if (selectedStatusEmoji instanceof TLRPC.TL_emojiStatusCollectible) {
                final TLRPC.TL_emojiStatusCollectible valueStatus = (TLRPC.TL_emojiStatusCollectible) selectedStatusEmoji;
                final TLRPC.TL_inputEmojiStatusCollectible status = new TLRPC.TL_inputEmojiStatusCollectible();
                status.collectible_id = valueStatus.collectible_id;
                status.flags = valueStatus.flags;
                status.until = valueStatus.until;
                req.emoji_status = status;
                channel.emoji_status = selectedStatusEmoji;
                channel.flags |= 512;
            } else {
                req.emoji_status = selectedStatusEmoji;
                channel.emoji_status = selectedStatusEmoji;
                channel.flags |= 512;
            }

            getMessagesController().updateEmojiStatusUntilUpdate(dialogId, selectedStatusEmoji);

            reqCount[0]++;
            getConnectionsManager().sendRequest(req, (res, err) -> {
                if (res instanceof TLRPC.Updates) {
                    getMessagesController().processUpdates((TLRPC.Updates) res, false);
                }
                if (whenRequestDone != null) {
                    whenRequestDone.run(err);
                }
            });
        }

        if (reqCount[0] == 0) {
            finishFragment();
            button.setLoading(false);
        } else {
            getMessagesController().putChat(channel, false);
            getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_EMOJI_STATUS);
        }
    }

    private void showLimit() {
        getMessagesController().getBoostsController().userCanBoostChannel(dialogId, boostsStatus, canApplyBoost -> {
            int type = LimitReachedBottomSheet.TYPE_BOOSTS_FOR_COLOR;
            int lvl = 0;
            if (currentReplyColor != selectedReplyColor) {
                MessagesController.PeerColors peerColors = getMessagesController().peerColors;
                MessagesController.PeerColor peerColor = peerColors == null ? null : peerColors.getColor(selectedReplyColor);
                if (peerColor != null && peerColor.getLvl(isGroup) > currentLevel) {
                    type = LimitReachedBottomSheet.TYPE_BOOSTS_FOR_COLOR;
                    lvl = peerColor.getLvl(isGroup);
                }
            }
            if (currentProfileColor != selectedProfileColor) {
                MessagesController.PeerColors peerColors = getMessagesController().profilePeerColors;
                MessagesController.PeerColor peerColor = peerColors == null ? null : peerColors.getColor(selectedProfileColor);
                if (peerColor != null && peerColor.getLvl(isGroup) > currentLevel) {
                    type = LimitReachedBottomSheet.TYPE_BOOSTS_FOR_PROFILE_COLOR;
                    lvl = peerColor.getLvl(isGroup);
                }
            }
            if (currentReplyEmoji != selectedReplyEmoji && getMessagesController().channelBgIconLevelMin > currentLevel) {
                type = LimitReachedBottomSheet.TYPE_BOOSTS_FOR_REPLY_ICON;
            }
            if (currentProfileEmoji != selectedProfileEmoji && getProfileIconLevelMin() > currentLevel) {
                type = LimitReachedBottomSheet.TYPE_BOOSTS_FOR_PROFILE_ICON;
            }
            if (!DialogObject.emojiStatusesEqual(currentStatusEmoji, selectedStatusEmoji) && getEmojiStatusLevelMin() > currentLevel) {
                if (selectedStatusEmoji instanceof TLRPC.TL_emojiStatusCollectible) {
                    type = LimitReachedBottomSheet.TYPE_BOOSTS_FOR_WEAR_COLLECTIBLE;
                } else {
                    type = LimitReachedBottomSheet.TYPE_BOOSTS_FOR_EMOJI_STATUS;
                }
            }
            if (!ChatThemeController.wallpaperEquals(currentWallpaper, selectedWallpaper)) {
                if (!TextUtils.isEmpty(ChatThemeController.getWallpaperEmoticon(selectedWallpaper))) {
                    type = LimitReachedBottomSheet.TYPE_BOOSTS_FOR_WALLPAPER;
                } else {
                    type = LimitReachedBottomSheet.TYPE_BOOSTS_FOR_CUSTOM_WALLPAPER;
                }
            }
            final int level = lvl;
            if (getContext() == null || getParentActivity() == null) return;
            LimitReachedBottomSheet limitReachedBottomSheet = new LimitReachedBottomSheet(this, getContext(), type, currentAccount, getResourceProvider()) {
                @Override
                protected int channelColorLevelMin() {
                    return level;
                }
            };
            limitReachedBottomSheet.setCanApplyBoost(canApplyBoost);
            limitReachedBottomSheet.setBoostsStats(boostsStatus, true);
            limitReachedBottomSheet.setDialogId(dialogId);
            TLRPC.Chat channel = getMessagesController().getChat(-dialogId);
            if (channel != null) {
                limitReachedBottomSheet.showStatisticButtonInLink(() -> {
                    presentFragment(StatisticActivity.create(channel));
                });
            }
            showDialog(limitReachedBottomSheet);
            button.setLoading(false);
        });
    }

    private void showUnsavedAlert() {
        if (getVisibleDialog() != null) {
            return;
        }
        AlertDialog alertDialog = new AlertDialog.Builder(getContext(), getResourceProvider())
                .setTitle(LocaleController.getString(R.string.ChannelColorUnsaved))
                .setMessage(LocaleController.getString(R.string.ChannelColorUnsavedMessage))
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

    private SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow selectAnimatedEmojiDialog;
    public void showSelectStatusDialog(EmojiCell cell, long documentId, boolean emojiStatus, Utilities.Callback3<Long, Integer, TL_stars.TL_starGiftUnique> onSet, int accentColor) {
        if (selectAnimatedEmojiDialog != null || cell == null) {
            return;
        }
        final SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow[] popup = new SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow[1];
        int xoff = 0, yoff = 0;

        final boolean down = cell.getTop() + cell.getHeight() > listView.getMeasuredHeight() / 2f;
        AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable scrimDrawable = null;
        View scrimDrawableParent = null;
        final int popupHeight = (int) Math.min(AndroidUtilities.dp(410 - 16 - 64), AndroidUtilities.displaySize.y * .75f);
        final int popupWidth = (int) Math.min(dp(340 - 16), AndroidUtilities.displaySize.x * .95f);
        if (cell != null) {
            cell.imageDrawable.removeOldDrawable();
            scrimDrawable = cell.imageDrawable;
            scrimDrawableParent = cell;
            if (cell.imageDrawable != null) {
                cell.imageDrawable.play();
                cell.updateImageBounds();
                AndroidUtilities.rectTmp2.set(cell.imageDrawable.getBounds());
                if (down) {
                    yoff = -AndroidUtilities.rectTmp2.centerY() + dp(12) - popupHeight;
                } else {
                    yoff = -(cell.getHeight() - AndroidUtilities.rectTmp2.centerY()) - AndroidUtilities.dp(16);
                }
                xoff = AndroidUtilities.rectTmp2.centerX() - (AndroidUtilities.displaySize.x - popupWidth);
            }
        }
        int type;
        if (emojiStatus) {
            type = down ? SelectAnimatedEmojiDialog.TYPE_EMOJI_STATUS_CHANNEL_TOP : SelectAnimatedEmojiDialog.TYPE_EMOJI_STATUS_CHANNEL;
        } else {
            type = down ? SelectAnimatedEmojiDialog.TYPE_SET_REPLY_ICON : SelectAnimatedEmojiDialog.TYPE_SET_REPLY_ICON_BOTTOM;
        }
        SelectAnimatedEmojiDialog popupLayout = new SelectAnimatedEmojiDialog(ChannelColorActivity.this, getContext(), true, xoff, type, true, getResourceProvider(), down ? 24 : 16, accentColor) {
            @Override
            protected void onEmojiSelected(View emojiView, Long documentId, TLRPC.Document document, TL_stars.TL_starGiftUnique gift, Integer until) {
                if (onSet != null) {
                    onSet.run(documentId == null ? 0 : documentId, until, gift);
                }
                if (popup[0] != null) {
                    selectAnimatedEmojiDialog = null;
                    popup[0].dismiss();
                }
            }

            @Override
            public long getDialogId() {
                return dialogId;
            }

            @Override
            protected float getScrimDrawableTranslationY() {
                return 0;
            }
        };
        popupLayout.useAccentForPlus = true;
        popupLayout.setSelected(documentId == 0 ? null : documentId);
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

    private static final int VIEW_TYPE_MESSAGE_PREVIEW = 0;
    private static final int VIEW_TYPE_PROFILE_PREVIEW = 1;
    private static final int VIEW_TYPE_WALLPAPER_THEMES = 2;
    private static final int VIEW_TYPE_COLOR_REPLY_GRID = 3;
    private static final int VIEW_TYPE_COLOR_PROFILE_GRID = 4;
    private static final int VIEW_TYPE_BUTTON = 5;
    private static final int VIEW_TYPE_BUTTON_EMOJI = 6;
    private static final int VIEW_TYPE_SHADOW = 7;
    private static final int VIEW_TYPE_HEADER = 8;
    private static final int VIEW_TYPE_GIFT = 9;
    private static final int VIEW_TYPE_GIFT_FLICKER = 10;

    protected int rowsCount = 0;

    protected int messagesPreviewRow;
    protected int replyColorListRow;
    protected int replyEmojiRow;
    protected int replyHintRow;

    protected int wallpaperThemesRow;
    protected int wallpaperRow;
    protected int wallpaperHintRow;

    protected int profilePreviewRow;
    protected int profileColorGridRow;
    protected int profileEmojiRow;
    protected int profileHintRow;

    protected int removeProfileColorRow;
    protected int removeProfileColorShadowRow;

    protected int statusEmojiRow;
    protected int statusHintRow;

    protected int packEmojiRow;
    protected int packEmojiHintRow;

    protected int packStickerRow;
    protected int packStickerHintRow;

    protected void updateRows() {
        rowsCount = 0;
        messagesPreviewRow = rowsCount++;
        replyColorListRow = rowsCount++;
        replyEmojiRow = rowsCount++;
        replyHintRow = rowsCount++;
        wallpaperThemesRow = rowsCount++;
        wallpaperRow = rowsCount++;
        wallpaperHintRow = rowsCount++;
        profilePreviewRow = rowsCount++;
        profileColorGridRow = rowsCount++;
        profileEmojiRow = rowsCount++;
        if (selectedProfileEmoji != 0 || selectedProfileColor >= 0 || selectedStatusEmoji instanceof TLRPC.TL_emojiStatusCollectible) {
            boolean wasButton = removeProfileColorRow >= 0;
            removeProfileColorRow = rowsCount++;
            if (!wasButton && adapter != null) {
                adapter.notifyItemInserted(removeProfileColorRow);
                adapter.notifyItemChanged(profileEmojiRow);
            }
        } else {
            int wasIndex = removeProfileColorRow;
            removeProfileColorRow = -1;
            if (wasIndex >= 0 && adapter != null) {
                adapter.notifyItemRemoved(wasIndex);
                adapter.notifyItemChanged(profileEmojiRow);
            }
        }
        profileHintRow = rowsCount++;
        statusEmojiRow = rowsCount++;
        statusHintRow = rowsCount++;
    }

    protected int getProfileInfoStrRes() {
        return R.string.ChannelProfileInfo;
    }

    protected int getEmojiStatusStrRes() {
        return R.string.ChannelEmojiStatus;
    }

    protected int getEmojiPackStrRes() {
        return 0;
    }

    protected int getEmojiPackInfoStrRes() {
        return 0;
    }

    protected int getStickerPackStrRes() {
        return 0;
    }

    protected int getStickerPackInfoStrRes() {
        return 0;
    }

    protected int getEmojiStatusInfoStrRes() {
        return R.string.ChannelEmojiStatusInfo;
    }

    protected int getWallpaperStrRes() {
        return R.string.ChannelWallpaper;
    }

    protected int getWallpaper2InfoStrRes() {
        return R.string.ChannelWallpaper2Info;
    }

    protected int getMessagePreviewType() {
        return ThemePreviewMessagesCell.TYPE_PEER_COLOR;
    }

    private String getThemeChooserEmoticon() {
        String emoticon = ChatThemeController.getWallpaperEmoticon(selectedWallpaper);
        if (emoticon == null && selectedWallpaper == null && galleryWallpaper != null) {
            return EmojiThemes.REMOVED_EMOJI;
        }
        return emoticon;
    }

    protected class Adapter extends RecyclerListView.SelectionAdapter {

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            if (viewType == VIEW_TYPE_MESSAGE_PREVIEW) {
                ThemePreviewMessagesCell messagesCell = new ThemePreviewMessagesCell(getContext(), parentLayout, getMessagePreviewType(), dialogId, resourceProvider);
                messagesCell.customAnimation = true;
                messagesCell.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                messagesCell.fragment = ChannelColorActivity.this;
                messagesCell.setOverrideBackground(backgroundDrawable = PreviewView.getBackgroundDrawable(backgroundDrawable, currentAccount, selectedWallpaper, isDark));
                view = messagesCell;
            } else if (viewType == VIEW_TYPE_WALLPAPER_THEMES) {
                ThemeChooser themesWallpaper = new ThemeChooser(getContext(), false, currentAccount, resourceProvider);
                themesWallpaper.setWithRemovedStub(true);
                themesWallpaper.setSelectedEmoticon(getThemeChooserEmoticon(), false);
                themesWallpaper.setGalleryWallpaper(galleryWallpaper);
                themesWallpaper.setOnEmoticonSelected(emoticon -> {
                    if (emoticon == null) {
                        selectedWallpaper = galleryWallpaper;
                    } else if (emoticon.equals(EmojiThemes.REMOVED_EMOJI)) {
                        selectedWallpaper = null;
                    } else {
                        selectedWallpaper = new TLRPC.TL_wallPaperNoFile();
                        selectedWallpaper.id = 0;
                        selectedWallpaper.flags |= 4;
                        selectedWallpaper.settings = new TLRPC.TL_wallPaperSettings();
                        selectedWallpaper.settings.emoticon = emoticon;
                    }
                    updateButton(true);
                    updateMessagesPreview(true);
                });
                themesWallpaper.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                view = themesWallpaper;
            } else if (viewType == VIEW_TYPE_BUTTON) {
                TextCell textCell = new TextCell(getContext(), getResourceProvider());
                textCell.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                view = textCell;
            } else if (viewType == VIEW_TYPE_BUTTON_EMOJI) {
                EmojiCell emojiCell = new EmojiCell(getContext(), resourceProvider);
                emojiCell.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                view = emojiCell;
            } else if (viewType == VIEW_TYPE_COLOR_REPLY_GRID) {
                PeerColorPicker listCell = new PeerColorPicker(getContext(), currentAccount, resourceProvider);
                listCell.listView.setOnItemClickListener((view2, position) -> {
                    selectedReplyColor = listCell.toColorId(position);
                    updateButton(true);
                    updateMessagesPreview(true);
                    updateProfilePreview(true);

                    if (view2.getLeft() < listCell.listView.getPaddingLeft() + dp(24)) {
                        listCell.listView.smoothScrollBy((int) -(listCell.listView.getPaddingLeft() + dp(48) - view2.getLeft()), 0);
                    } else if (view2.getLeft() + view2.getWidth() > listCell.listView.getMeasuredWidth() - listCell.listView.getPaddingRight() - dp(24)) {
                        listCell.listView.smoothScrollBy((int) (view2.getLeft() + view2.getWidth() - (listCell.listView.getMeasuredWidth() - listCell.listView.getPaddingRight() - dp(48))), 0);
                    }
                });
                listCell.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                view = listCell;
//                PeerColorActivity.PeerColorGrid gridCell = new PeerColorActivity.PeerColorGrid(getContext(), PeerColorActivity.PAGE_NAME, currentAccount, resourceProvider);
//                gridCell.setOnColorClick(color -> {
//                    selectedReplyColor = color;
//                    updateButton(true);
//                    updateMessagesPreview(true);
//                    updateProfilePreview(true);
//                });
//                gridCell.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
//                view = gridCell;
            } else if (viewType == VIEW_TYPE_COLOR_PROFILE_GRID) {
                PeerColorActivity.PeerColorGrid gridCell = new PeerColorActivity.PeerColorGrid(getContext(), PeerColorActivity.PAGE_PROFILE, currentAccount, resourceProvider);
                gridCell.setDivider(false);
                gridCell.setOnColorClick(color -> {
                    selectedProfileColor = color;
                    if (selectedStatusEmoji instanceof TLRPC.TL_emojiStatusCollectible) {
                        selectedStatusEmoji = null;
                    }
                    updateButton(true);
                    updateProfilePreview(true);
                });
                gridCell.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                view = gridCell;
            } else if (viewType == VIEW_TYPE_PROFILE_PREVIEW) {
                view = new ProfilePreview(getContext());
            } else if (viewType == VIEW_TYPE_HEADER) {
                HeaderCell headerCell = new HeaderCell(getContext(), resourceProvider);
                headerCell.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                view = headerCell;
            } else if (viewType == VIEW_TYPE_GIFT) {
                PeerColorActivity.GiftCell giftCell = new PeerColorActivity.GiftCell(getContext(), false, resourceProvider);
                view = giftCell;
            } else if (viewType == VIEW_TYPE_GIFT_FLICKER) {
                FlickerLoadingView flickerLoadingView = new FlickerLoadingView(getContext(), resourceProvider);
                flickerLoadingView.setIsSingleCell(true);
                flickerLoadingView.setViewType(FlickerLoadingView.STAR_GIFT_SELECT);
                view = flickerLoadingView;
            } else {
                view = new TextInfoPrivacyCell(getContext());
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case VIEW_TYPE_BUTTON:
                    TextCell textCell = (TextCell) holder.itemView;
                    if (position == removeProfileColorRow) {
                        textCell.setText(LocaleController.getString(R.string.ChannelProfileColorReset), false);
                    } else {
                        textCell.setText(LocaleController.getString(getWallpaperStrRes()), false);
                        if (currentLevel < getCustomWallpaperLevelMin()) {
                            textCell.setLockLevel(false, getCustomWallpaperLevelMin());
                        } else {
                            textCell.setLockLevel(false, 0);
                        }
                    }
                    break;
                case VIEW_TYPE_BUTTON_EMOJI:
                    EmojiCell emojiCell = (EmojiCell) holder.itemView;
                    emojiCell.setDivider(false);
                    if (position == replyEmojiRow) {
                        emojiCell.setAdaptiveEmojiColor(currentAccount, selectedReplyColor, true);
                        emojiCell.setText(LocaleController.getString(R.string.ChannelReplyLogo));
                        if (currentLevel < getMessagesController().channelBgIconLevelMin) {
                            emojiCell.setLockLevel(getMessagesController().channelBgIconLevelMin);
                        } else {
                            emojiCell.setLockLevel(0);
                        }
                        emojiCell.setEmoji(selectedReplyEmoji, false, false);
                    } else if (position == profileEmojiRow) {
                        emojiCell.setAdaptiveEmojiColor(currentAccount, selectedProfileColor, false);
                        emojiCell.setText(LocaleController.getString(R.string.ChannelProfileLogo));
                        emojiCell.setDivider(removeProfileColorRow >= 0);
                        if (currentLevel < getProfileIconLevelMin()) {
                            emojiCell.setLockLevel(getProfileIconLevelMin());
                        } else {
                            emojiCell.setLockLevel(0);
                        }
                        emojiCell.setEmoji(selectedProfileEmoji, false, false);
                    } else if (position == statusEmojiRow) {
                        emojiCell.setAdaptiveEmojiColor(currentAccount, selectedProfileColor, false);
                        emojiCell.setText(LocaleController.getString(getEmojiStatusStrRes()));
                        if (currentLevel < getEmojiStatusLevelMin()) {
                            emojiCell.setLockLevel(getEmojiStatusLevelMin());
                        } else {
                            emojiCell.setLockLevel(0);
                        }
                        emojiCell.setEmoji(DialogObject.getEmojiStatusDocumentId(selectedStatusEmoji), DialogObject.isEmojiStatusCollectible(selectedStatusEmoji), false);
                    } else if (position == packEmojiRow) {
                        emojiCell.setAdaptiveEmojiColor(currentAccount, selectedProfileColor, false);
                        emojiCell.setText(LocaleController.getString(getEmojiPackStrRes()));
                        if (currentLevel < getEmojiStickersLevelMin()) {
                            emojiCell.setLockLevel(getEmojiStickersLevelMin());
                        } else {
                            emojiCell.setLockLevel(0);
                        }
                        TLRPC.ChatFull chatFull = getMessagesController().getChatFull(-dialogId);
                        if (chatFull != null && chatFull.emojiset != null) {
                            emojiCell.setEmoji(getEmojiSetThumbId(chatFull.emojiset), false, false);
                        } else {
                            emojiCell.setEmoji(0, false, false);
                        }
                    } else if (position == packStickerRow) {
                        emojiCell.setText(LocaleController.getString(getStickerPackStrRes()));
                        emojiCell.setLockLevel(0);
                        TLRPC.ChatFull chatFull = getMessagesController().getChatFull(-dialogId);
                        if (chatFull != null && chatFull.stickerset != null) {
                            emojiCell.setEmoji(getEmojiSetThumb(chatFull.stickerset), false, false);
                        } else {
                            emojiCell.setEmoji(0, false, false);
                        }
                    }
                    break;
                case VIEW_TYPE_SHADOW:
                    TextInfoPrivacyCell infoCell = (TextInfoPrivacyCell) holder.itemView;
                    infoCell.setFixedSize(0);
                    if (position == replyHintRow) {
                        infoCell.setText(LocaleController.getString(R.string.ChannelReplyInfo));
                    } else if (position == wallpaperHintRow) {
                        infoCell.setText(LocaleController.getString(getWallpaper2InfoStrRes()));
                    } else if (position == profileHintRow) {
                        infoCell.setText(LocaleController.getString(getProfileInfoStrRes()));
                    } else if (position == statusHintRow) {
                        infoCell.setText(LocaleController.getString(getEmojiStatusInfoStrRes()));
                    } else if (position == packEmojiHintRow) {
                        infoCell.setText(LocaleController.getString(getEmojiPackInfoStrRes()));
                    } else if (position == packStickerHintRow) {
                        infoCell.setText(LocaleController.getString(getStickerPackInfoStrRes()));
                    } else if (position == removeProfileColorShadowRow) {
                        infoCell.setText("");
                        infoCell.setFixedSize(12);
                    }
                    infoCell.setBackground(Theme.getThemedDrawableByKey(getContext(), position == statusHintRow ? R.drawable.greydivider_bottom : R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow, resourceProvider));
                    break;
                case VIEW_TYPE_PROFILE_PREVIEW:
                    ProfilePreview profilePreview = (ProfilePreview) holder.itemView;
                    profilePreview.backgroundView.setColor(currentAccount, selectedProfileColor, false);
                    profilePreview.profileView.setColor(selectedProfileColor, false);
                    profilePreview.profileView.setEmoji(selectedProfileEmoji, false, false);
                    profilePreview.profileView.setForum(isForum());
                    profilePreview.profileView.setStatusEmoji(DialogObject.getEmojiStatusDocumentId(selectedStatusEmoji), false, false);
                    profilePreview.profileView.overrideAvatarColor(selectedReplyColor);
                    break;
                case VIEW_TYPE_COLOR_PROFILE_GRID:
                    ((PeerColorActivity.PeerColorGrid) holder.itemView).setSelected(selectedProfileColor, false);
                    break;
                case VIEW_TYPE_COLOR_REPLY_GRID:
//                    ((PeerColorActivity.PeerColorGrid) holder.itemView).setSelected(selectedReplyColor, false);
                    ((PeerColorPicker) holder.itemView).setSelected(selectedReplyColor, false);
                    break;
            }
        }

        @Override
        public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
            if (holder.itemView instanceof ProfilePreview) {
                ProfilePreview profilePreview = (ProfilePreview) holder.itemView;
                if (selectedStatusEmoji instanceof TLRPC.TL_emojiStatusCollectible) {
                    profilePreview.profileView.setColor(MessagesController.PeerColor.fromCollectible(selectedStatusEmoji), false);
                    profilePreview.profileView.setEmoji(((TLRPC.TL_emojiStatusCollectible) selectedStatusEmoji).pattern_document_id, true, false);
                } else {
                    profilePreview.profileView.setColor(selectedProfileColor, false);
                    profilePreview.profileView.setEmoji(selectedProfileEmoji, false, false);
                }
                profilePreview.profileView.setStatusEmoji(DialogObject.getEmojiStatusDocumentId(selectedStatusEmoji), DialogObject.isEmojiStatusCollectible(selectedStatusEmoji), false);
                profilePreview.profileView.setForum(isForum());
                profilePreview.profileView.overrideAvatarColor(selectedReplyColor);
            } else if (holder.itemView instanceof ThemePreviewMessagesCell) {
                ThemePreviewMessagesCell messagesCell = (ThemePreviewMessagesCell) holder.itemView;
                messagesCell.setOverrideBackground(backgroundDrawable);
            } else {
                updateColors(holder.itemView);
            }
            super.onViewAttachedToWindow(holder);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == messagesPreviewRow) {
                return VIEW_TYPE_MESSAGE_PREVIEW;
            } else if (position == wallpaperThemesRow) {
                return VIEW_TYPE_WALLPAPER_THEMES;
            } else if (position == profilePreviewRow) {
                return VIEW_TYPE_PROFILE_PREVIEW;
            } else if (position == replyColorListRow) {
                return VIEW_TYPE_COLOR_REPLY_GRID;
            } else if (position == profileColorGridRow) {
                return VIEW_TYPE_COLOR_PROFILE_GRID;
            } else if (position == replyEmojiRow || position == profileEmojiRow || position == statusEmojiRow || position == packEmojiRow || position == packStickerRow) {
                return VIEW_TYPE_BUTTON_EMOJI;
            } else if (position == wallpaperRow || position == removeProfileColorRow) {
                return VIEW_TYPE_BUTTON;
            } else {
                return VIEW_TYPE_SHADOW;
            }
        }

        @Override
        public int getItemCount() {
            return rowsCount;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            final int viewType = holder.getItemViewType();
            return viewType == VIEW_TYPE_BUTTON || viewType == VIEW_TYPE_BUTTON_EMOJI;
        }

    }

    public void updateMessagesPreview(boolean animated) {
        View messagesPreview = findChildAt(messagesPreviewRow);
        View colorPicker = findChildAt(replyColorListRow);
        View emojiPicker = findChildAt(replyEmojiRow);
        View wallpaperPicker = findChildAt(wallpaperThemesRow);

        if (messagesPreview instanceof ThemePreviewMessagesCell) {
            ThemePreviewMessagesCell messagesCellPreview = (ThemePreviewMessagesCell) messagesPreview;
            ChatMessageCell[] cells = messagesCellPreview.getCells();
            for (int i = 0; i < cells.length; ++i) {
                if (cells[i] != null) {
                    MessageObject msg = cells[i].getMessageObject();
                    if (msg != null) {
                        msg.overrideLinkColor = selectedReplyColor;
                        msg.overrideLinkEmoji = selectedReplyEmoji;
                        cells[i].setAvatar(msg);
                        cells[i].invalidate();
                    }
                }
            }
            messagesCellPreview.setOverrideBackground(backgroundDrawable = PreviewView.getBackgroundDrawable(backgroundDrawable, currentAccount, selectedWallpaper, isDark));
        }
        if (colorPicker instanceof PeerColorActivity.PeerColorGrid) {
            ((PeerColorActivity.PeerColorGrid) colorPicker).setSelected(selectedReplyColor, animated);
        } else if (colorPicker instanceof PeerColorPicker) {
            ((PeerColorPicker) colorPicker).setSelected(selectedReplyColor, animated);
        }
        if (emojiPicker instanceof EmojiCell) {
            ((EmojiCell) emojiPicker).setAdaptiveEmojiColor(currentAccount, selectedReplyColor, true);
            ((EmojiCell) emojiPicker).setEmoji(selectedReplyEmoji, false, animated);
        }
        if (wallpaperPicker instanceof ThemeChooser) {
            ((ThemeChooser) wallpaperPicker).setSelectedEmoticon(getThemeChooserEmoticon(), animated);
            ((ThemeChooser) wallpaperPicker).setGalleryWallpaper(galleryWallpaper);
        }
    }

    public void updateProfilePreview(boolean animated) {
        final View profilePreview = findChildAt(profilePreviewRow);
        final View colorPicker = findChildAt(profileColorGridRow);
        final View emojiPicker = findChildAt(profileEmojiRow);
        final View emojiStatusPicker = findChildAt(statusEmojiRow);
        final View packEmojiPicker = findChildAt(packEmojiRow);
        final View packStatusPicker = findChildAt(packStickerRow);

        if (profilePreview instanceof ProfilePreview) {
            if (selectedStatusEmoji instanceof TLRPC.TL_emojiStatusCollectible) {
                ((ProfilePreview) profilePreview).setColor(MessagesController.PeerColor.fromCollectible(selectedStatusEmoji), animated);
                ((ProfilePreview) profilePreview).setEmoji(((TLRPC.TL_emojiStatusCollectible) selectedStatusEmoji).pattern_document_id, true, animated);
            } else {
                ((ProfilePreview) profilePreview).setColor(selectedProfileColor, animated);
                ((ProfilePreview) profilePreview).setEmoji(selectedProfileEmoji, false, animated);
            }
            ((ProfilePreview) profilePreview).setEmojiStatus(selectedStatusEmoji, animated);
            ((ProfilePreview) profilePreview).profileView.overrideAvatarColor(selectedReplyColor);
        }
        if (colorPicker instanceof PeerColorActivity.PeerColorGrid) {
            ((PeerColorActivity.PeerColorGrid) colorPicker).setSelected(selectedProfileColor, animated);
        } else if (colorPicker instanceof PeerColorPicker) {
            ((PeerColorPicker) colorPicker).setSelected(selectedReplyColor, animated);
        }
        if (emojiPicker instanceof EmojiCell) {
            ((EmojiCell) emojiPicker).setAdaptiveEmojiColor(currentAccount, selectedProfileColor, false);
            ((EmojiCell) emojiPicker).setEmoji(selectedProfileEmoji, false, animated);
        }
        if (emojiStatusPicker instanceof EmojiCell) {
            if (selectedStatusEmoji instanceof TLRPC.TL_emojiStatusCollectible) {
                ((EmojiCell) emojiStatusPicker).setAdaptiveEmojiColor(MessagesController.PeerColor.fromCollectible(selectedStatusEmoji));
            } else {
                ((EmojiCell) emojiStatusPicker).setAdaptiveEmojiColor(currentAccount, selectedProfileColor, false);
            }
            ((EmojiCell) emojiStatusPicker).setEmoji(DialogObject.getEmojiStatusDocumentId(selectedStatusEmoji), DialogObject.isEmojiStatusCollectible(selectedStatusEmoji), animated);
        }
        if (packEmojiPicker instanceof EmojiCell) {
            ((EmojiCell) packEmojiPicker).setAdaptiveEmojiColor(currentAccount, selectedProfileColor, false);
            TLRPC.ChatFull chatFull = getMessagesController().getChatFull(-dialogId);
            if (chatFull != null && chatFull.emojiset != null) {
                ((EmojiCell) packEmojiPicker).setEmoji(getEmojiSetThumbId(chatFull.emojiset), false, false);
            } else {
                ((EmojiCell) packEmojiPicker).setEmoji(0, false, false);
            }
        }
        if (packStatusPicker instanceof EmojiCell) {
            TLRPC.ChatFull chatFull = getMessagesController().getChatFull(-dialogId);
            if (chatFull != null && chatFull.stickerset != null) {
                ((EmojiCell) packStatusPicker).setEmoji(getEmojiSetThumb(chatFull.stickerset), false, false);
            } else {
                ((EmojiCell) packStatusPicker).setEmoji(0, false, false);
            }
        }

        updateRows();
    }

    private long getEmojiSetThumbId(TLRPC.StickerSet emojiSet) {
        if (emojiSet == null) {
            return 0;
        }
        long thumbDocumentId = emojiSet.thumb_document_id;
        if (thumbDocumentId == 0) {
            TLRPC.TL_messages_stickerSet stickerSet = getMediaDataController().getGroupStickerSetById(emojiSet);
            if (!stickerSet.documents.isEmpty()) {
                thumbDocumentId = stickerSet.documents.get(0).id;
            }
        }
        return thumbDocumentId;
    }

    private TLRPC.Document getEmojiSetThumb(TLRPC.StickerSet emojiSet) {
        if (emojiSet == null) {
            return null;
        }
        long thumbDocumentId = emojiSet.thumb_document_id;
        if (thumbDocumentId == 0) {
            TLRPC.TL_messages_stickerSet stickerSet = getMediaDataController().getGroupStickerSetById(emojiSet);
            if (!stickerSet.documents.isEmpty()) {
                return stickerSet.documents.get(0);
            }
        }
        return null;
    }

    public View findChildAt(int position) {
        for (int i = 0; i < listView.getChildCount(); ++i) {
            View child = listView.getChildAt(i);
            if (listView.getChildAdapterPosition(child) == position) {
                return child;
            }
        }
        return null;
    }

    protected boolean needBoostInfoSection() {
        return false;
    }

    protected class ProfilePreview extends FrameLayout {
        public final PeerColorActivity.ColoredActionBar backgroundView;
        public final PeerColorActivity.ProfilePreview profileView;
        public SimpleTextView title;
        public TextView textInfo1;
        public TextView textInfo2;
        public LinearLayout infoLayout;

        public void setTitleSize() {
            boolean isLandScape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
            title.setTextSize(!AndroidUtilities.isTablet() && isLandScape ? 18 : 20);
            title.setTranslationY(dp(AndroidUtilities.isTablet() ? -2 : (isLandScape ? 4 : 0)));
        }

        public ProfilePreview(Context context) {
            super(context);
            backgroundView = new PeerColorActivity.ColoredActionBar(getContext(), resourceProvider);
            backgroundView.setProgressToGradient(1f);
            backgroundView.ignoreMeasure = true;
            addView(backgroundView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, isGroup ? 194 : 134, Gravity.FILL));
            profileView = new PeerColorActivity.ProfilePreview(getContext(), currentAccount, dialogId, resourceProvider){
                @Override
                public void setColor(int colorId, boolean animated) {
                    super.setColor(colorId, animated);
                    if (textInfo1 != null) {
                        textInfo1.setTextColor(profileView.subtitleView.getTextColor());
                    }
                }
            };
            addView(profileView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 104, Gravity.BOTTOM, 0, 0, 0, isGroup ? 24: 0));

            if (needBoostInfoSection()) {
                title = new SimpleTextView(getContext());
                title.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
                title.setTextColor(getThemedColor(Theme.key_actionBarDefaultTitle));
                title.setTypeface(AndroidUtilities.bold());
                title.setText(LocaleController.getString(R.string.ChangeChannelNameColor2));
                title.setAlpha(0f);
                setTitleSize();
                addView(title, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM,72,0,0,16));
                infoLayout = new LinearLayout(context);
                infoLayout.setOrientation(LinearLayout.HORIZONTAL);
                infoLayout.setBackground(Theme.createSelectorWithBackgroundDrawable(Theme.multAlpha(Color.BLACK, 0.065f), Color.BLACK));
                infoLayout.setGravity(Gravity.CENTER);
                infoLayout.setPadding(dp(4), dp(4), dp(4), dp(4));
                textInfo1 = new TextView(context);
                textInfo1.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
                textInfo1.setTextColor(profileView.subtitleView.getTextColor());
                textInfo2 = new TextView(context);
                textInfo2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
                textInfo2.setTextColor(Color.WHITE);
                textInfo1.setText(AndroidUtilities.replaceTags(LocaleController.formatPluralString("BoostingGroupBoostCount", boostsStatus != null ? boostsStatus.boosts : 0)));
                textInfo2.setText(LocaleController.getString(R.string.BoostingGroupBoostWhatAreBoosts));
                infoLayout.addView(textInfo1);
                infoLayout.addView(textInfo2, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 3,0,0,0));
                addView(infoLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));
            }
        }

        public void setColor(int colorId, boolean animated) {
            profileView.setColor(colorId, animated);
            backgroundView.setColor(currentAccount, colorId, animated);
        }

        public void setColor(MessagesController.PeerColor peerColor, boolean animated) {
            profileView.setColor(peerColor, animated);
            backgroundView.setColor(peerColor, animated);
        }

        public void setEmoji(long emojiId, boolean isCollectible, boolean animated) {
            profileView.setEmoji(emojiId, isCollectible, animated);
        }
        public void setEmojiStatus(TLRPC.EmojiStatus emojiStatus, boolean animated) {
            profileView.setStatusEmoji(DialogObject.getEmojiStatusDocumentId(emojiStatus), false, animated);
        }
    }

    private static class EmojiCell extends FrameLayout {

        private SimpleTextView textView;
        private Text offText;
        private AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable imageDrawable;
        private Theme.ResourcesProvider resourcesProvider;

        public EmojiCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);

            this.resourcesProvider = resourcesProvider;

            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));

            textView = new SimpleTextView(context);
            textView.setTextSize(16);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL, 23, 0, 48, 0));

            imageDrawable = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(this, false, dp(24), AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_STATIC);
        }

        private boolean needDivider = false;
        public void setDivider(boolean divider) {
            setWillNotDraw(!(this.needDivider = divider));
        }

        public void setLockLevel(int lvl) {
            if (lvl <= 0) {
                textView.setRightDrawable(null);
            } else {
                textView.setRightDrawable(new PeerColorActivity.LevelLock(getContext(), lvl, resourcesProvider));
                textView.setDrawablePadding(dp(6));
            }
        }

        private int color;
        public void setAdaptiveEmojiColor(int currentAccount, int colorId, boolean isReply) {
            if (colorId < 0) {
                setAdaptiveEmojiColor(null);
            } else if (colorId < 7) {
                color = Theme.getColor(Theme.keys_avatar_nameInMessage[colorId], resourcesProvider);
            } else {
                MessagesController.PeerColors peerColors = isReply ? MessagesController.getInstance(currentAccount).peerColors : MessagesController.getInstance(currentAccount).profilePeerColors;
                MessagesController.PeerColor peerColor = peerColors == null ? null : peerColors.getColor(colorId);
                setAdaptiveEmojiColor(peerColor);
            }
            invalidate();
        }

        public void setAdaptiveEmojiColor(MessagesController.PeerColor peerColor) {
            if (peerColor == null) {
                if (AndroidUtilities.computePerceivedBrightness(Theme.getColor(Theme.key_actionBarDefault, resourcesProvider)) > .8f) {
                    color = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourcesProvider);
                } else if (AndroidUtilities.computePerceivedBrightness(Theme.getColor(Theme.key_actionBarDefault, resourcesProvider)) < .2f) {
                    color = Theme.multAlpha(Theme.getColor(Theme.key_actionBarDefaultTitle, resourcesProvider), .5f);
                } else {
                    color = Theme.blendOver(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider), Theme.multAlpha(PeerColorActivity.adaptProfileEmojiColor(Theme.getColor(Theme.key_actionBarDefault, resourcesProvider)), .7f));
                }
            } else {
                if (peerColor != null) {
                    color = peerColor.getColor(0, resourcesProvider);
                } else {
                    color = Theme.getColor(Theme.keys_avatar_nameInMessage[0], resourcesProvider);
                }
            }
        }

        public void setText(CharSequence text) {
            textView.setText(text);
        }

        public void setEmoji(long documentId, boolean isCollectible, boolean animated) {
            if (documentId == 0) {
                imageDrawable.set((Drawable) null, animated);
                if (offText == null) {
                    offText = new Text(LocaleController.getString(R.string.ChannelReplyIconOff), 16);
                }
            } else {
                imageDrawable.set(documentId, animated);
                offText = null;
            }
            imageDrawable.setParticles(isCollectible, animated);
        }

        public void setEmoji(TLRPC.Document document, boolean isCollectible, boolean animated) {
            if (document == null) {
                imageDrawable.set((Drawable) null, animated);
                if (offText == null) {
                    offText = new Text(LocaleController.getString(R.string.ChannelReplyIconOff), 16);
                }
            } else {
                imageDrawable.set(document, animated);
                offText = null;
            }
            imageDrawable.setParticles(isCollectible, animated);
        }

        public void updateColors() {
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
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
            imageDrawable.setColor(color);
            if (offText != null) {
                offText.draw(canvas, getMeasuredWidth() - offText.getWidth() - dp(19), getMeasuredHeight() / 2f, Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourcesProvider), 1f);
            } else {
                imageDrawable.draw(canvas);
            }

            if (needDivider) {
                Paint dividerPaint = resourcesProvider != null ? resourcesProvider.getPaint(Theme.key_paint_divider) : Theme.dividerPaint;
                if (dividerPaint != null) {
                    canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(23), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(23) : 0), getMeasuredHeight() - 1, dividerPaint);
                }
            }
        }

        public int getColor() {
            return color;
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

    public static class ThemeChooser extends FrameLayout {

        private final int currentAccount;
        private final Theme.ResourcesProvider resourcesProvider;
        public final List<ChatThemeBottomSheet.ChatThemeItem> items = new ArrayList<>();
        private final RecyclerListView listView;
        private FlickerLoadingView progressView;
        private boolean withRemovedStub;

        private final RecyclerListView.SelectionAdapter adapter;

        private boolean dataLoaded;

        private Utilities.Callback<String> onEmoticonSelected;
        private String currentEmoticon;

        public void setWithRemovedStub(boolean withRemovedStub) {
            this.withRemovedStub = withRemovedStub;
        }

        public void setOnEmoticonSelected(Utilities.Callback<String> callback) {
            onEmoticonSelected = callback;
        }

        public void setSelectedEmoticon(String emoticon, boolean animated) {
            currentEmoticon = emoticon;

            int selectedPosition = -1;
            for (int i = 0; i < items.size(); ++i) {
                ChatThemeBottomSheet.ChatThemeItem item = items.get(i);
                item.isSelected = TextUtils.equals(currentEmoticon, item.getEmoticon()) || TextUtils.isEmpty(emoticon) && item.chatTheme.showAsDefaultStub;
                if (item.isSelected) {
                    selectedPosition = i;
                }
            }
            if (selectedPosition >= 0 && !animated && listView.getLayoutManager() instanceof LinearLayoutManager) {
                ((LinearLayoutManager) listView.getLayoutManager()).scrollToPositionWithOffset(selectedPosition, (AndroidUtilities.displaySize.x - dp(83)) / 2);
            }
            updateSelected();
        }

        private TLRPC.WallPaper fallbackWallpaper;
        public void setGalleryWallpaper(TLRPC.WallPaper wallPaper) {
            this.fallbackWallpaper = wallPaper;
            AndroidUtilities.forEachViews(listView, child -> {
                if (child instanceof ThemeSmallPreviewView) {
                    ((ThemeSmallPreviewView) child).setFallbackWallpaper(((ThemeSmallPreviewView) child).chatThemeItem.chatTheme.showAsRemovedStub ? null : fallbackWallpaper);
                }
            });
            if (fallbackWallpaper != null && (items.isEmpty() || items.get(0).chatTheme.showAsDefaultStub) && withRemovedStub) {
                items.add(0, new ChatThemeBottomSheet.ChatThemeItem(EmojiThemes.createChatThemesRemoved(currentAccount)));
                adapter.notifyDataSetChanged();
            }
        }

        private void updateSelected() {
            for (int i = 0; i < listView.getChildCount(); ++i) {
                View child = listView.getChildAt(i);
                if (child instanceof ThemeSmallPreviewView) {
                    int position = listView.getChildAdapterPosition(child);
                    if (position >= 0 && position < items.size()) {
                        ChatThemeBottomSheet.ChatThemeItem item = items.get(position);
                        ((ThemeSmallPreviewView) child).setSelected(item.isSelected, true);
                    }
                }
            }
        }

        public boolean isDark() {
            return resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
        }

        public ThemeChooser(Context context, boolean grid, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.currentAccount = currentAccount;
            this.resourcesProvider = resourcesProvider;

            if (!grid) {
                progressView = new FlickerLoadingView(getContext(), resourcesProvider);
                progressView.setViewType(FlickerLoadingView.CHAT_THEMES_TYPE);
                progressView.setVisibility(View.VISIBLE);
                addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 104, Gravity.START, 16, 13, 16, 6));
            }

            listView = new RecyclerListView(context, resourcesProvider) {
                @Override
                public Integer getSelectorColor(int position) {
                    return 0;
                }
            };
            listView.setClipToPadding(false);
            listView.setPadding(dp(16), dp(13), dp(16), dp(grid ? 13 : 6));
            if (grid) {
                listView.setHasFixedSize(false);
                GridLayoutManager gridLayoutManager = new GridLayoutManager(getContext(), 3);
                gridLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                    @Override
                    public int getSpanSize(int position) {
                        return 1;
                    }
                });
                listView.setLayoutManager(gridLayoutManager);
            } else {
                LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
                layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
                listView.setLayoutManager(layoutManager);
                listView.setAlpha(0f);
            }
            listView.setAdapter(adapter = new RecyclerListView.SelectionAdapter() {
                @Override
                public boolean isEnabled(RecyclerView.ViewHolder holder) {
                    return true;
                }

                @NonNull
                @Override
                public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                    return new RecyclerListView.Holder(new ThemeSmallPreviewView(parent.getContext(), currentAccount, resourcesProvider, grid ? ThemeSmallPreviewView.TYPE_GRID_CHANNEL : ThemeSmallPreviewView.TYPE_CHANNEL) {
                        @Override
                        protected String noThemeString() {
                            return LocaleController.getString(R.string.ChannelNoWallpaper);
                        }

                        @Override
                        protected int noThemeStringTextSize() {
                            if (!grid) {
                                return 13;
                            }
                            return super.noThemeStringTextSize();
                        }
                    });
                }

                @Override
                public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                    ThemeSmallPreviewView view = (ThemeSmallPreviewView) holder.itemView;
                    Theme.ThemeInfo themeInfo = items.get(position).chatTheme.getThemeInfo(items.get(position).themeIndex);
                    if (themeInfo != null && themeInfo.pathToFile != null && !themeInfo.previewParsed) {
                        File file = new File(themeInfo.pathToFile);
                        boolean fileExists = file.exists();
                        if (fileExists) {
                            parseTheme(themeInfo);
                        }
                    }
                    ChatThemeBottomSheet.ChatThemeItem newItem = items.get(position);
                    view.setEnabled(true);
                    view.setBackgroundColor(Theme.getColor(Theme.key_dialogBackgroundGray));
                    view.setItem(newItem, false);
                    view.setSelected(newItem.isSelected, false);
                    view.setFallbackWallpaper(newItem.chatTheme.showAsRemovedStub ? null : fallbackWallpaper);
                }

                @Override
                public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
                    final int position = holder.getAdapterPosition();
                    if (position < 0 || position >= items.size()) {
                        return;
                    }
                    ChatThemeBottomSheet.ChatThemeItem newItem = items.get(position);
                    ((ThemeSmallPreviewView) holder.itemView).setSelected(newItem.isSelected, false);
                    ((ThemeSmallPreviewView) holder.itemView).setFallbackWallpaper(newItem.chatTheme.showAsRemovedStub ? null : fallbackWallpaper);
                }

                @Override
                public int getItemCount() {
                    return items.size();
                }
            });
            addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, grid ? LayoutHelper.MATCH_PARENT : 13 + 111 + 6));
            listView.setOnItemClickListener((view, position) -> {
                if (position < 0 || position >= items.size()) {
                    return;
                }
                ChatThemeBottomSheet.ChatThemeItem thisItem = items.get(position);
                if (!grid) {
                    setSelectedEmoticon(thisItem.getEmoticon(), true);
                    if (view.getLeft() < listView.getPaddingLeft() + dp(24)) {
                        listView.smoothScrollBy((int) -(listView.getPaddingLeft() + dp(48) - view.getLeft()), 0);
                    } else if (view.getLeft() + view.getWidth() > listView.getMeasuredWidth() - listView.getPaddingRight() - dp(24)) {
                        listView.smoothScrollBy((int) (view.getLeft() + view.getWidth() - (listView.getMeasuredWidth() - listView.getPaddingRight() - dp(48))), 0);
                    }
                }
                if (onEmoticonSelected != null) {
                    onEmoticonSelected.run(thisItem.getEmoticon());
                }
            });

            ChatThemeController chatThemeController = ChatThemeController.getInstance(currentAccount);
            chatThemeController.preloadAllWallpaperThumbs(true);
            chatThemeController.preloadAllWallpaperThumbs(false);
            chatThemeController.preloadAllWallpaperImages(true);
            chatThemeController.preloadAllWallpaperImages(false);
            chatThemeController.requestAllChatThemes(new ResultCallback<List<EmojiThemes>>() {
                @Override
                public void onComplete(List<EmojiThemes> result) {
//                    if (result != null && !result.isEmpty()) {
//                        themeDelegate.setCachedThemes(result);
//                    }
                    NotificationCenter.getInstance(currentAccount).doOnIdle(() -> {
                        onDataLoaded(result);
                    });
                }

                @Override
                public void onError(TLRPC.TL_error error) {
                    Toast.makeText(getContext(), error.text, Toast.LENGTH_SHORT).show();
                }
            }, true);

            updateState(false);
        }

        public void updateColors() {
            final boolean isDark = isDark();
            for (int i = 0; i < items.size(); ++i) {
                ChatThemeBottomSheet.ChatThemeItem item = items.get(i);
                item.themeIndex = isDark ? 1 : 0;
            }
            AndroidUtilities.forEachViews(listView, view -> {
                ((ThemeSmallPreviewView) view).setBackgroundColor(Theme.getColor(Theme.key_dialogBackgroundGray, resourcesProvider));
            });
            adapter.notifyDataSetChanged();
        }

        private void onDataLoaded(List<EmojiThemes> result) {
            if (result == null || result.isEmpty()) {
                return;
            }

            dataLoaded = true;
            items.clear();

            ChatThemeBottomSheet.ChatThemeItem noThemeItem = new ChatThemeBottomSheet.ChatThemeItem(result.get(0));
            items.add(0, noThemeItem);

            if (fallbackWallpaper != null && withRemovedStub) {
                items.add(0, new ChatThemeBottomSheet.ChatThemeItem(EmojiThemes.createChatThemesRemoved(currentAccount)));
            }

            final boolean isDark = resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
            for (int i = 1; i < result.size(); ++i) {
                EmojiThemes chatTheme = result.get(i);
                ChatThemeBottomSheet.ChatThemeItem item = new ChatThemeBottomSheet.ChatThemeItem(chatTheme);

                chatTheme.loadPreviewColors(currentAccount);

                item.themeIndex = isDark ? 1 : 0;
                items.add(item);
            }

            int selectedPosition = -1;
            for (int i = 0; i < items.size(); ++i) {
                ChatThemeBottomSheet.ChatThemeItem item = items.get(i);
                item.isSelected = TextUtils.equals(currentEmoticon, item.getEmoticon()) || TextUtils.isEmpty(currentEmoticon) && item.chatTheme.showAsDefaultStub;
                if (item.isSelected) {
                    selectedPosition = i;
                }
            }
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }

//            resetToPrimaryState(false);
            listView.animate().alpha(1f).setDuration(150).start();
            updateState(true);

            if (selectedPosition >= 0 && listView.getLayoutManager() instanceof LinearLayoutManager) {
                ((LinearLayoutManager) listView.getLayoutManager()).scrollToPositionWithOffset(selectedPosition, (AndroidUtilities.displaySize.x - dp(83)) / 2);
            }
        }

        private final HashMap<String, Theme.ThemeInfo> loadingThemes = new HashMap<>();
        private final HashMap<Theme.ThemeInfo, String> loadingWallpapers = new HashMap<>();
        private boolean parseTheme(Theme.ThemeInfo themeInfo) {
            if (themeInfo == null || themeInfo.pathToFile == null) {
                return false;
            }
            boolean finished = false;
            File file = new File(themeInfo.pathToFile);
            try (FileInputStream stream = new FileInputStream(file)) {
                int currentPosition = 0;
                int idx;
                int read;
                int linesRead = 0;
                while ((read = stream.read(ThemesHorizontalListCell.bytes)) != -1) {
                    int previousPosition = currentPosition;
                    int start = 0;
                    for (int a = 0; a < read; a++) {
                        if (ThemesHorizontalListCell.bytes[a] == '\n') {
                            linesRead++;
                            int len = a - start + 1;
                            String line = new String(ThemesHorizontalListCell.bytes, start, len - 1, "UTF-8");
                            if (line.startsWith("WLS=")) {
                                String wallpaperLink = line.substring(4);
                                Uri uri = Uri.parse(wallpaperLink);
                                themeInfo.slug = uri.getQueryParameter("slug");
                                themeInfo.pathToWallpaper = new File(ApplicationLoader.getFilesDirFixed(), Utilities.MD5(wallpaperLink) + ".wp").getAbsolutePath();

                                String mode = uri.getQueryParameter("mode");
                                if (mode != null) {
                                    mode = mode.toLowerCase();
                                    String[] modes = mode.split(" ");
                                    if (modes != null && modes.length > 0) {
                                        for (int b = 0; b < modes.length; b++) {
                                            if ("blur".equals(modes[b])) {
                                                themeInfo.isBlured = true;
                                                break;
                                            }
                                        }
                                    }
                                }
                                String pattern = uri.getQueryParameter("pattern");
                                if (!TextUtils.isEmpty(pattern)) {
                                    try {
                                        String bgColor = uri.getQueryParameter("bg_color");
                                        if (!TextUtils.isEmpty(bgColor)) {
                                            themeInfo.patternBgColor = Integer.parseInt(bgColor.substring(0, 6), 16) | 0xff000000;
                                            if (bgColor.length() >= 13 && AndroidUtilities.isValidWallChar(bgColor.charAt(6))) {
                                                themeInfo.patternBgGradientColor1 = Integer.parseInt(bgColor.substring(7, 13), 16) | 0xff000000;
                                            }
                                            if (bgColor.length() >= 20 && AndroidUtilities.isValidWallChar(bgColor.charAt(13))) {
                                                themeInfo.patternBgGradientColor2 = Integer.parseInt(bgColor.substring(14, 20), 16) | 0xff000000;
                                            }
                                            if (bgColor.length() == 27 && AndroidUtilities.isValidWallChar(bgColor.charAt(20))) {
                                                themeInfo.patternBgGradientColor3 = Integer.parseInt(bgColor.substring(21), 16) | 0xff000000;
                                            }
                                        }
                                    } catch (Exception ignore) {

                                    }
                                    try {
                                        String rotation = uri.getQueryParameter("rotation");
                                        if (!TextUtils.isEmpty(rotation)) {
                                            themeInfo.patternBgGradientRotation = Utilities.parseInt(rotation);
                                        }
                                    } catch (Exception ignore) {

                                    }
                                    String intensity = uri.getQueryParameter("intensity");
                                    if (!TextUtils.isEmpty(intensity)) {
                                        themeInfo.patternIntensity = Utilities.parseInt(intensity);
                                    }
                                    if (themeInfo.patternIntensity == 0) {
                                        themeInfo.patternIntensity = 50;
                                    }
                                }
                            } else if (line.startsWith("WPS")) {
                                themeInfo.previewWallpaperOffset = currentPosition + len;
                                finished = true;
                                break;
                            } else {
                                if ((idx = line.indexOf('=')) != -1) {
                                    int key = ThemeColors.stringKeyToInt(line.substring(0, idx));
                                    if (key == Theme.key_chat_inBubble || key == Theme.key_chat_outBubble || key == Theme.key_chat_wallpaper || key == Theme.key_chat_wallpaper_gradient_to1 || key == Theme.key_chat_wallpaper_gradient_to2 || key == Theme.key_chat_wallpaper_gradient_to3) {
                                        String param = line.substring(idx + 1);
                                        int value;
                                        if (param.length() > 0 && param.charAt(0) == '#') {
                                            try {
                                                value = Color.parseColor(param);
                                            } catch (Exception ignore) {
                                                value = Utilities.parseInt(param);
                                            }
                                        } else {
                                            value = Utilities.parseInt(param);
                                        }
                                        if (key == Theme.key_chat_inBubble) {
                                            themeInfo.setPreviewInColor(value);
                                        } else if (key == Theme.key_chat_outBubble) {
                                            themeInfo.setPreviewOutColor(value);
                                        } else if (key == Theme.key_chat_wallpaper) {
                                            themeInfo.setPreviewBackgroundColor(value);
                                        } else if (key == Theme.key_chat_wallpaper_gradient_to1) {
                                            themeInfo.previewBackgroundGradientColor1 = value;
                                        } else if (key == Theme.key_chat_wallpaper_gradient_to2) {
                                            themeInfo.previewBackgroundGradientColor2 = value;
                                        } else if (key == Theme.key_chat_wallpaper_gradient_to3) {
                                            themeInfo.previewBackgroundGradientColor3 = value;
                                        }
                                    }
                                }
                            }
                            start += len;
                            currentPosition += len;
                        }
                    }
                    if (finished || previousPosition == currentPosition) {
                        break;
                    }
                    stream.getChannel().position(currentPosition);
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }

            if (themeInfo.pathToWallpaper != null && !themeInfo.badWallpaper) {
                file = new File(themeInfo.pathToWallpaper);
                if (!file.exists()) {
                    if (!loadingWallpapers.containsKey(themeInfo)) {
                        loadingWallpapers.put(themeInfo, themeInfo.slug);
                        TL_account.getWallPaper req = new TL_account.getWallPaper();
                        TLRPC.TL_inputWallPaperSlug inputWallPaperSlug = new TLRPC.TL_inputWallPaperSlug();
                        inputWallPaperSlug.slug = themeInfo.slug;
                        req.wallpaper = inputWallPaperSlug;
                        ConnectionsManager.getInstance(themeInfo.account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                            if (response instanceof TLRPC.TL_wallPaper) {
                                TLRPC.WallPaper wallPaper = (TLRPC.WallPaper) response;
                                String name = FileLoader.getAttachFileName(wallPaper.document);
                                if (!loadingThemes.containsKey(name)) {
                                    loadingThemes.put(name, themeInfo);
                                    FileLoader.getInstance(themeInfo.account).loadFile(wallPaper.document, wallPaper, FileLoader.PRIORITY_NORMAL, 1);
                                }
                            } else {
                                themeInfo.badWallpaper = true;
                            }
                        }));
                    }
                    return false;
                }
            }
            themeInfo.previewParsed = true;
            return true;
        }

        private void updateState(boolean animated) {
            if (!dataLoaded) {
                AndroidUtilities.updateViewVisibilityAnimated(progressView, true, 1f, true, animated);
            } else {
                AndroidUtilities.updateViewVisibilityAnimated(progressView, false, 1f, true, animated);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), heightMeasureSpec);
        }
    }

    private BaseFragment bulletinFragment;
    public ChannelColorActivity setOnApplied(BaseFragment bulletinFragment) {
        this.bulletinFragment = bulletinFragment;
        return this;
    }

    private void showBulletin() {
        if (bulletinFragment != null) {
            if (bulletinFragment instanceof ChatEditActivity) {
                ((ChatEditActivity) bulletinFragment).updateColorCell();
            }
            BulletinFactory.of(bulletinFragment).createSimpleBulletin(
                    R.raw.contact_check,
                    LocaleController.getString(isGroup ? R.string.GroupAppearanceUpdated : R.string.ChannelAppearanceUpdated)
            ).show();
            bulletinFragment = null;
        }
    }

    public void updateColors() {
        actionBar.setBackgroundColor(getThemedColor(Theme.key_actionBarDefault));
        actionBar.setTitleColor(getThemedColor(Theme.key_actionBarDefaultTitle));
        actionBar.setItemsColor(getThemedColor(Theme.key_actionBarDefaultIcon), false);
        actionBar.setItemsBackgroundColor(getThemedColor(Theme.key_actionBarDefaultSelector), false);
        listView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        adapter.notifyDataSetChanged();
        AndroidUtilities.forEachViews(listView, this::updateColors);
        buttonContainer.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        button.updateColors();
        setNavigationBarColor(getNavigationBarColor());
    }

    public boolean hasUnsavedChanged() {
        return (
            currentReplyColor != selectedReplyColor ||
            currentReplyEmoji != selectedReplyEmoji ||
            currentProfileColor != selectedProfileColor ||
            currentProfileEmoji != selectedProfileEmoji ||
            !DialogObject.emojiStatusesEqual(currentStatusEmoji, selectedStatusEmoji) ||
            !ChatThemeController.wallpaperEquals(currentWallpaper, selectedWallpaper)
        );
    }

    private void updateColors(View view) {
        if (view instanceof TextInfoPrivacyCell) {
            ((TextInfoPrivacyCell) view).setBackground(Theme.getThemedDrawableByKey(getContext(), listView.getChildAdapterPosition(view) == statusHintRow ? R.drawable.greydivider_bottom : R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow, resourceProvider));
        } else {
            view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
            if (view instanceof EmojiCell) {
                ((EmojiCell) view).updateColors();
            } else if (view instanceof TextCell) {
                ((TextCell) view).updateColors();
            } else if (view instanceof PeerColorPicker) {
                ((PeerColorPicker) view).updateColors();
            } else if (view instanceof ThemeChooser) {
                ((ThemeChooser) view).updateColors();
            }
        }
    }

    private static class PeerColorPicker extends FrameLayout {
        private final Theme.ResourcesProvider resourcesProvider;
        public final RecyclerListView listView;
        public final LinearLayoutManager layoutManager;
        public final RecyclerListView.SelectionAdapter adapter;
        private final int currentAccount;

        @Override
        public boolean onInterceptTouchEvent(MotionEvent e) {
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(canScrollHorizontally(-1) || canScrollHorizontally(1));
            }
            return super.onInterceptTouchEvent(e);
        }

        public PeerColorPicker(Context context, final int currentAccount, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.currentAccount = currentAccount;
            this.resourcesProvider = resourcesProvider;

            listView = new RecyclerListView(context, resourcesProvider) {
                @Override
                public Integer getSelectorColor(int position) {
                    return 0;
                }
            };
            listView.setPadding(dp(6), dp(5), dp(6), 0);
            listView.setClipToPadding(false);

            listView.setAdapter(adapter = new RecyclerListView.SelectionAdapter() {
                @Override
                public boolean isEnabled(RecyclerView.ViewHolder holder) {
                    return true;
                }

                @NonNull
                @Override
                public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                    return new RecyclerListView.Holder(new ColorCell(context));
                }

                @Override
                public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                    ColorCell cell = (ColorCell) holder.itemView;
                    cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
                    cell.setSelected(position == selectedPosition, false);
                    MessagesController.PeerColors peerColors = MessagesController.getInstance(currentAccount).peerColors;
                    if (peerColors != null && position >= 0 && position < peerColors.colors.size()) {
                        cell.set(peerColors.colors.get(position));
                    }
                }

                @Override
                public int getItemCount() {
                    MessagesController.PeerColors peerColors = MessagesController.getInstance(currentAccount).peerColors;
                    return (peerColors == null ? 0 : peerColors.colors.size());
                }
            });
            layoutManager = new LinearLayoutManager(context);
            layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
            listView.setLayoutManager(layoutManager);
            addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }

        private int selectedPosition;
        public void setSelected(int color, boolean animated) {
            setSelectedPosition(toPosition(color), animated);
        }

        public void setSelectedPosition(int position, boolean animated) {
            if (position != selectedPosition) {
                selectedPosition = position;
                if (!animated) {
                    layoutManager.scrollToPositionWithOffset(position, (AndroidUtilities.displaySize.x - dp(56)) / 2);
                }
                AndroidUtilities.forEachViews(listView, child -> ((ColorCell) child).setSelected(listView.getChildAdapterPosition(child) == selectedPosition, animated));
            }
        }

        public int getColorId() {
            return toColorId(selectedPosition);
        }

        public int toPosition(final int colorId) {
            MessagesController.PeerColors peerColors = MessagesController.getInstance(currentAccount).peerColors;
            if (peerColors == null) {
                return 0;
            }
            for (int i = 0; i < peerColors.colors.size(); ++i) {
                if (peerColors.colors.get(i).id == colorId) {
                    return i;
                }
            }
            return 0;
        }

        public void updateColors() {
            final MessagesController.PeerColors peerColors = MessagesController.getInstance(currentAccount).peerColors;
            AndroidUtilities.forEachViews(listView, view -> {
                if (view instanceof ColorCell) {
                    ((ColorCell) view).setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
                    int position = listView.getChildAdapterPosition(view);
                    if (peerColors != null && position >= 0 && position < peerColors.colors.size()) {
                        ((ColorCell) view).set(peerColors.colors.get(position));
                    }
                }
            });
        }

        public int toColorId(int position) {
            MessagesController.PeerColors peerColors = MessagesController.getInstance(currentAccount).peerColors;
            if (peerColors == null || position < 0 || position >= peerColors.colors.size()) {
                return 0;
            }
            return peerColors.colors.get(position).id;
        }

        private class ColorCell extends View {
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
                final boolean isDark = resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
                if (isDark && color.hasColor2() && !color.hasColor3()) {
                    paint1.setColor(color.getColor(1, resourcesProvider));
                    paint2.setColor(color.getColor(0, resourcesProvider));
                } else {
                    paint1.setColor(color.getColor(0, resourcesProvider));
                    paint2.setColor(color.getColor(1, resourcesProvider));
                }
                paint3.setColor(color.getColor(2, resourcesProvider));
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

    private View changeDayNightView;
    private float changeDayNightViewProgress;
    private ValueAnimator changeDayNightViewAnimator;

    @SuppressLint("NotifyDataSetChanged")
    public void toggleTheme() {
        FrameLayout decorView1 = (FrameLayout) getParentActivity().getWindow().getDecorView();
        Bitmap bitmap = Bitmap.createBitmap(decorView1.getWidth(), decorView1.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas bitmapCanvas = new Canvas(bitmap);
        dayNightItem.setAlpha(0f);
        decorView1.draw(bitmapCanvas);
        dayNightItem.setAlpha(1f);

        Paint xRefPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        xRefPaint.setColor(0xff000000);
        xRefPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bitmapPaint.setFilterBitmap(true);
        int[] position = new int[2];
        dayNightItem.getLocationInWindow(position);
        float x = position[0];
        float y = position[1];
        float cx = x + dayNightItem.getMeasuredWidth() / 2f;
        float cy = y + dayNightItem.getMeasuredHeight() / 2f;

        float r = Math.max(bitmap.getHeight(), bitmap.getWidth()) + AndroidUtilities.navigationBarHeight;

        Shader bitmapShader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        bitmapPaint.setShader(bitmapShader);
        changeDayNightView = new View(getContext()) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                if (isDark) {
                    if (changeDayNightViewProgress > 0f) {
                        bitmapCanvas.drawCircle(cx, cy, r * changeDayNightViewProgress, xRefPaint);
                    }
                    canvas.drawBitmap(bitmap, 0, 0, bitmapPaint);
                } else {
                    canvas.drawCircle(cx, cy, r * (1f - changeDayNightViewProgress), bitmapPaint);
                }
                canvas.save();
                canvas.translate(x, y);
                dayNightItem.draw(canvas);
                canvas.restore();
            }
        };
        changeDayNightView.setOnTouchListener((v, event) -> true);
        changeDayNightViewProgress = 0f;
        changeDayNightViewAnimator = ValueAnimator.ofFloat(0, 1f);
        changeDayNightViewAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            boolean changedNavigationBarColor = false;

            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                changeDayNightViewProgress = (float) valueAnimator.getAnimatedValue();
                changeDayNightView.invalidate();
                if (!changedNavigationBarColor && changeDayNightViewProgress > .5f) {
                    changedNavigationBarColor = true;
                }
            }
        });
        changeDayNightViewAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (changeDayNightView != null) {
                    if (changeDayNightView.getParent() != null) {
                        ((ViewGroup) changeDayNightView.getParent()).removeView(changeDayNightView);
                    }
                    changeDayNightView = null;
                }
                changeDayNightViewAnimator = null;
                super.onAnimationEnd(animation);
            }
        });
        changeDayNightViewAnimator.setDuration(400);
        changeDayNightViewAnimator.setInterpolator(Easings.easeInOutQuad);
        changeDayNightViewAnimator.start();

        decorView1.addView(changeDayNightView, new ViewGroup.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        AndroidUtilities.runOnUIThread(() -> {
            if (resourceProvider instanceof ThemeDelegate) {
                ((ThemeDelegate) resourceProvider).toggle();
            } else {
                isDark = !isDark;
                updateThemeColors();
            }
            setForceDark(isDark, true);
            updateColors();
        });
    }

    private boolean forceDark = isDark;
    public void setForceDark(boolean isDark, boolean playAnimation) {
        if (forceDark == isDark) {
            return;
        }
        forceDark = isDark;
        if (playAnimation) {
            sunDrawable.setCustomEndFrame(isDark ? sunDrawable.getFramesCount() : 0);
            if (sunDrawable != null) {
                sunDrawable.start();
            }
        } else {
            int frame = isDark ? sunDrawable.getFramesCount() - 1 : 0;
            sunDrawable.setCurrentFrame(frame, false, true);
            sunDrawable.setCustomEndFrame(frame);
            if (dayNightItem != null) {
                dayNightItem.invalidate();
            }
        }
    }

    private Theme.ResourcesProvider parentResourcesProvider;
    private final SparseIntArray currentColors = new SparseIntArray();
    private final Theme.MessageDrawable msgInDrawable, msgInDrawableSelected;
    private final Theme.MessageDrawable msgOutDrawable, msgOutDrawableSelected;
    private final Drawable msgOutCheckReadDrawable, msgOutHalfCheckDrawable;
    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    {
        dividerPaint.setStrokeWidth(1);
        dividerPaint.setColor(Theme.getColor(Theme.key_divider, resourceProvider));
        msgOutCheckReadDrawable = ContextCompat.getDrawable(ApplicationLoader.applicationContext, R.drawable.msg_check_s).mutate();
        msgOutHalfCheckDrawable = ContextCompat.getDrawable(ApplicationLoader.applicationContext, R.drawable.msg_halfcheck).mutate();
    }

    public void updateThemeColors() {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", Activity.MODE_PRIVATE);
        String dayThemeName = preferences.getString("lastDayTheme", "Blue");
        if (Theme.getTheme(dayThemeName) == null || Theme.getTheme(dayThemeName).isDark()) {
            dayThemeName = "Blue";
        }
        String nightThemeName = preferences.getString("lastDarkTheme", "Dark Blue");
        if (Theme.getTheme(nightThemeName) == null || !Theme.getTheme(nightThemeName).isDark()) {
            nightThemeName = "Dark Blue";
        }
        Theme.ThemeInfo themeInfo = Theme.getActiveTheme();
        if (dayThemeName.equals(nightThemeName)) {
            if (themeInfo.isDark() || dayThemeName.equals("Dark Blue") || dayThemeName.equals("Night")) {
                dayThemeName = "Blue";
            } else {
                nightThemeName = "Dark Blue";
            }
        }

        if (isDark) {
            themeInfo = Theme.getTheme(nightThemeName);
        } else {
            themeInfo = Theme.getTheme(dayThemeName);
        }

        currentColors.clear();
        final String[] wallpaperLink = new String[1];
        final SparseIntArray themeColors;
        if (themeInfo.assetName != null) {
            themeColors = Theme.getThemeFileValues(null, themeInfo.assetName, wallpaperLink);
        } else {
            themeColors = Theme.getThemeFileValues(new File(themeInfo.pathToFile), null, wallpaperLink);
        }
        int[] defaultColors = Theme.getDefaultColors();
        if (defaultColors != null) {
            for (int i = 0; i < defaultColors.length; ++i) {
                currentColors.put(i, defaultColors[i]);
            }
        }
        if (themeColors != null) {
            for (int i = 0; i < themeColors.size(); ++i) {
                currentColors.put(themeColors.keyAt(i), themeColors.valueAt(i));
            }
            Theme.ThemeAccent accent = themeInfo.getAccent(false);
            if (accent != null) {
                accent.fillAccentColors(themeColors, currentColors);
            }
        }
        dividerPaint.setColor(Theme.getColor(Theme.key_divider, resourceProvider));

        backgroundDrawable = PreviewView.getBackgroundDrawable(backgroundDrawable, currentAccount, selectedWallpaper, isDark);
        View messagesCellPreview = findChildAt(messagesPreviewRow);
        if (messagesCellPreview instanceof ThemePreviewMessagesCell) {
            ((ThemePreviewMessagesCell) messagesCellPreview).setOverrideBackground(backgroundDrawable);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.chatWasBoostedByUser) {
            if (dialogId == (long) args[2]) {
                updateBoostsAndLevels((TL_stories.TL_premium_boostsStatus) args[0]);
            }
        } else if (id == NotificationCenter.boostByChannelCreated) {
            boolean isGiveaway = (boolean) args[1];
            if (!isGiveaway) {
                getMessagesController().getBoostsController().getBoostsStats(dialogId, this::updateBoostsAndLevels);
            }
        } else if (id == NotificationCenter.dialogDeleted) {
            long dialogId = (long) args[0];
            if (this.dialogId == dialogId) {
                if (parentLayout != null && parentLayout.getLastFragment() == this) {
                    finishFragment();
                } else {
                    removeSelfFromStack();
                }
            }
        }
    }

    private void updateBoostsAndLevels(TL_stories.TL_premium_boostsStatus boostsStatus) {
        if (boostsStatus != null) {
            TLRPC.Chat chat = getMessagesController().getChat(-dialogId);
            this.boostsStatus = boostsStatus;
            this.currentLevel = boostsStatus.level;
            if (chat != null) {
                chat.level = currentLevel;
            }
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
            updateButton(true);
        }
    }

    protected boolean isForum() {
        return false;
    }

}
