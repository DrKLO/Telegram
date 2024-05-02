package org.telegram.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChannelBoostsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;

import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ThemePreviewMessagesCell;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.Premium.LimitReachedBottomSheet;
import org.telegram.ui.Components.RecyclerListView;

public class GroupColorActivity extends ChannelColorActivity {

    private ChannelColorActivity.ProfilePreview profilePreview;
    private float profilePreviewPercent;
    private boolean isLoading;

    public GroupColorActivity(long dialogId) {
        super(dialogId);
        isGroup = true;
    }

    @Override
    protected int getProfileIconLevelMin() {
        return getMessagesController().groupProfileBgIconLevelMin;
    }

    @Override
    protected int getCustomWallpaperLevelMin() {
        return getMessagesController().groupCustomWallpaperLevelMin;
    }

    @Override
    protected int getWallpaperLevelMin() {
        return getMessagesController().groupWallpaperLevelMin;
    }

    @Override
    protected int getEmojiStatusLevelMin() {
        return getMessagesController().groupEmojiStatusLevelMin;
    }

    @Override
    protected int getEmojiStickersLevelMin() {
        return getMessagesController().groupEmojiStickersLevelMin;
    }

    @Override
    protected void updateRows() {
        rowsCount = 0;
        profilePreviewRow = rowsCount++;
        profileColorGridRow = rowsCount++;
        profileEmojiRow = rowsCount++;
        if (selectedProfileEmoji != 0 || selectedProfileColor >= 0) {
            boolean wasButton = removeProfileColorRow >= 0;
            removeProfileColorRow = rowsCount++;
            if (!wasButton && adapter != null) {
                adapter.notifyItemInserted(removeProfileColorRow);
                adapter.notifyItemChanged(profileEmojiRow);
                listView.scrollToPosition(0);
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
        packEmojiRow = rowsCount++;
        packEmojiHintRow = rowsCount++;
        statusEmojiRow = rowsCount++;
        statusHintRow = rowsCount++;
        TLRPC.ChatFull chatFull = getMessagesController().getChatFull(-dialogId);
        if (chatFull != null && chatFull.can_set_stickers) {
            packStickerRow = rowsCount++;
            packStickerHintRow = rowsCount++;
        } else {
            packStickerRow = -1;
            packStickerHintRow = -1;
        }
        messagesPreviewRow = rowsCount++;
        wallpaperThemesRow = rowsCount++;
        wallpaperRow = rowsCount++;
        wallpaperHintRow = rowsCount++;
    }

    @Override
    public void updateButton(boolean animated) {
        super.updateButton(animated);
        if (profilePreview != null) {
            profilePreview.textInfo1.setText(AndroidUtilities.replaceTags(LocaleController.formatPluralString("BoostingGroupBoostCount", boostsStatus != null ? boostsStatus.boosts : 0)));
        }
    }

    @Override
    protected int getEmojiPackStrRes() {
        return R.string.GroupEmojiPack;
    }

    @Override
    protected int getEmojiPackInfoStrRes() {
        return R.string.GroupEmojiPackInfo;
    }

    @Override
    protected int getStickerPackStrRes() {
        return R.string.GroupStickerPack;
    }

    @Override
    protected int getStickerPackInfoStrRes() {
        return R.string.GroupStickerPackInfo;
    }

    @Override
    protected int getProfileInfoStrRes() {
        return R.string.GroupProfileInfo;
    }

    @Override
    protected int getEmojiStatusStrRes() {
        return R.string.GroupEmojiStatus;
    }

    @Override
    protected int getEmojiStatusInfoStrRes() {
        return R.string.GroupEmojiStatusInfo;
    }

    @Override
    protected int getWallpaperStrRes() {
        return R.string.GroupWallpaper;
    }

    @Override
    protected int getWallpaper2InfoStrRes() {
        return R.string.GroupWallpaper2Info;
    }

    @Override
    protected int getMessagePreviewType() {
        return ThemePreviewMessagesCell.TYPE_GROUP_PEER_COLOR;
    }

    @Override
    public View createView(Context context) {
        View view = super.createView(context);
        updateColors();

        actionBar.setAddToContainer(false);
        actionBar.setTitle("");
        ((ViewGroup) view).addView(actionBar);

        view.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                initProfilePreview();
                profilePreview.infoLayout.setOnClickListener(v -> openBoostDialog(LimitReachedBottomSheet.TYPE_BOOSTS_FOR_USERS));
            }
        });
        return view;
    }

    @Override
    protected boolean needBoostInfoSection() {
        return true;
    }

    private void initProfilePreview() {
        if (profilePreview == null) {
            profilePreview = (ChannelColorActivity.ProfilePreview) findChildAt(profilePreviewRow);
        }
    }

    @Override
    protected void createListView() {
        listView = new RecyclerListView(getContext(), resourceProvider) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                if (profilePreview != null && profilePreviewPercent >= 1f) {
                    canvas.save();
                    canvas.translate(0, -(profilePreview.getMeasuredHeight() - actionBar.getMeasuredHeight()));
                    profilePreview.draw(canvas);
                    canvas.restore();
                }
            }
        };

        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                initProfilePreview();
                int profileHeight = profilePreview.getMeasuredHeight();
                int actionBarHeight = actionBar.getMeasuredHeight();
                int max = profileHeight - actionBarHeight;
                float y = profilePreview.getTop() * -1;
                profilePreviewPercent = Math.max(Math.min(1f, y / max), 0f);
                float profileAlphaPercent = Math.min(profilePreviewPercent * 2f, 1f);
                float titleAlphaPercent = Math.min(Math.max(profilePreviewPercent - 0.45f, 0f) * 2f, 1f);
                profilePreview.profileView.setAlpha(AndroidUtilities.lerp(1f, 0f, profileAlphaPercent));
                profilePreview.infoLayout.setAlpha(AndroidUtilities.lerp(1f, 0f, profileAlphaPercent));
                profilePreview.title.setAlpha(AndroidUtilities.lerp(0f, 1f, titleAlphaPercent));
                if (profilePreviewPercent >= 1f) {
                    profilePreview.setTranslationY(y - max);
                } else {
                    profilePreview.setTranslationY(0);
                }
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    if (profilePreviewPercent >= 0.5f && profilePreviewPercent < 1f) {
                        int maxTop = actionBar.getBottom();
                        RecyclerView.LayoutManager layoutManager = listView.getLayoutManager();
                        if (layoutManager != null) {
                            View view = layoutManager.findViewByPosition(0);
                            if (view != null) {
                                listView.smoothScrollBy(0, view.getBottom() - maxTop);
                            }
                        }
                    } else if (profilePreviewPercent < 0.5f) {
                        View firstView = null;
                        if (listView.getLayoutManager() != null) {
                            firstView = listView.getLayoutManager().findViewByPosition(0);
                        }
                        if (firstView != null && firstView.getTop() < 0) {
                            listView.smoothScrollBy(0, firstView.getTop());
                        }
                    }
                }
            }
        });
    }

    protected void openBoostDialog(int type) {
        if (boostsStatus == null || isLoading) {
            return;
        }
        isLoading = true;
        ChannelBoostsController boostsController = MessagesController.getInstance(currentAccount).getBoostsController();
        boostsController.userCanBoostChannel(dialogId, boostsStatus, canApplyBoost -> {
            if (canApplyBoost == null || getContext() == null) {
                isLoading = false;
                return;
            }
            LimitReachedBottomSheet limitReachedBottomSheet = new LimitReachedBottomSheet(this, getContext(), type, currentAccount, resourceProvider) {
                @Override
                public void onOpenAnimationEnd() {
                    isLoading = false;
                }

                @Override
                public void dismiss() {
                    super.dismiss();
                    isLoading = false;
                }
            };
            limitReachedBottomSheet.setCanApplyBoost(canApplyBoost);
            limitReachedBottomSheet.setBoostsStats(boostsStatus, true);
            limitReachedBottomSheet.setDialogId(dialogId);
            limitReachedBottomSheet.show();
        });
    }

    @Override
    public void updateColors() {
        super.updateColors();
        actionBar.setBackgroundColor(Color.TRANSPARENT);
        Drawable shadowDrawable = Theme.getThemedDrawableByKey(getContext(), R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow);
        Drawable background = new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider));
        CombinedDrawable combinedDrawable = new CombinedDrawable(background, shadowDrawable, 0, 0);
        combinedDrawable.setFullsize(true);
        buttonContainer.setBackground(combinedDrawable);
        if (profilePreview != null) {
            profilePreview.backgroundView.setColor(currentAccount, selectedProfileColor, false);
            profilePreview.profileView.setColor(selectedProfileColor, false);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (profilePreview != null) {
            profilePreview.setTitleSize();
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        super.didReceivedNotification(id, account, args);
        if (id == NotificationCenter.chatInfoDidLoad) {
            TLRPC.ChatFull chatFull = (TLRPC.ChatFull) args[0];
            if (chatFull.id == -dialogId) {
                updateProfilePreview(true);
            }
        }
    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.chatInfoDidLoad);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.chatInfoDidLoad);
    }
}
