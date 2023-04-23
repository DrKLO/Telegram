/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.WebFile;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ContextLinkCell;
import org.telegram.ui.Cells.StickerCell;
import org.telegram.ui.Cells.StickerEmojiCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EmojiPacksAlert;
import org.telegram.ui.Components.EmojiView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SuggestEmojiView;

import java.util.ArrayList;

public class ContentPreviewViewer {

    private class FrameLayoutDrawer extends FrameLayout {
        public FrameLayoutDrawer(Context context) {
            super(context);
            setWillNotDraw(false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            ContentPreviewViewer.this.onDraw(canvas);
        }
    }

    public interface ContentPreviewViewerDelegate {
        default boolean can() {
            return true;
        }

        void openSet(TLRPC.InputStickerSet set, boolean clearInputField);

        boolean needSend(int contentType);
        default void sendSticker(TLRPC.Document sticker, String query, Object parent, boolean notify, int scheduleDate) {}
        default void sendGif(Object gif, Object parent, boolean notify, int scheduleDate) {}
        default void sendEmoji(TLRPC.Document emoji) {}

        boolean canSchedule();
        boolean isInScheduleMode();

        long getDialogId();

        default boolean needRemove() {
            return false;
        }
        default void remove(SendMessagesHelper.ImportingSticker sticker) {

        }

        default String getQuery(boolean isGif) {
            return null;
        }

        default boolean needOpen() {
            return true;
        }

        default void gifAddedOrDeleted() {}

        default boolean needMenu() {
            return true;
        }

        default Boolean canSetAsStatus(TLRPC.Document document) {
            return null;
        }
        default void setAsEmojiStatus(TLRPC.Document document, Integer until) {}

        default boolean needCopy() {
            return false;
        }
        default void copyEmoji(TLRPC.Document document) {}

        default void resetTouch() {}

        default boolean needRemoveFromRecent(TLRPC.Document document) {
            return false;
        }
        default void removeFromRecent(TLRPC.Document document) {}
    }

    public final static int CONTENT_TYPE_NONE = -1;
    public final static int CONTENT_TYPE_STICKER = 0;
    public final static int CONTENT_TYPE_GIF = 1;
    public final static int CONTENT_TYPE_EMOJI = 2;

    private static TextPaint textPaint;

    private int startX;
    private int startY;
    private float lastTouchY;
    private float currentMoveY;
    private float moveY = 0;
    private float finalMoveY;
    private float startMoveY;
    private float currentMoveYProgress;
    private View currentPreviewCell;
    private boolean clearsInputField;
    private Runnable openPreviewRunnable;
    ActionBarPopupWindow popupWindow;
    private ActionBarPopupWindow visibleMenu;
    private ContentPreviewViewerDelegate delegate;

    private boolean isRecentSticker;

    private WindowInsets lastInsets;

    private int currentAccount;

    private ColorDrawable backgroundDrawable = new ColorDrawable(0x71000000);
    private Bitmap blurrBitmap;
    private Activity parentActivity;
    private WindowManager.LayoutParams windowLayoutParams;
    private FrameLayout windowView;
    private FrameLayoutDrawer containerView;
    private ImageReceiver centerImage = new ImageReceiver();
    private ImageReceiver effectImage = new ImageReceiver();
    private boolean isVisible = false;
    private float showProgress;
    private StaticLayout stickerEmojiLayout;
    private long lastUpdateTime;
    private int keyboardHeight = AndroidUtilities.dp(200);
    private Drawable slideUpDrawable;
    private boolean menuVisible;
    private float blurProgress;
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private UnlockPremiumView unlockPremiumView;
    private boolean closeOnDismiss;
    private boolean drawEffect;

    private Runnable showSheetRunnable = new Runnable() {
        @Override
        public void run() {
            if (parentActivity == null) {
                return;
            }
            closeOnDismiss = true;
            if (currentContentType == CONTENT_TYPE_STICKER) {
                if (MessageObject.isPremiumSticker(currentDocument) && !AccountInstance.getInstance(currentAccount).getUserConfig().isPremium()) {
                    showUnlockPremiumView();
                    menuVisible = true;
                    containerView.invalidate();
                    containerView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    return;
                }
                final boolean inFavs = MediaDataController.getInstance(currentAccount).isStickerInFavorites(currentDocument);
                ArrayList<CharSequence> items = new ArrayList<>();
                final ArrayList<Integer> actions = new ArrayList<>();
                ArrayList<Integer> icons = new ArrayList<>();
                menuVisible = true;
                containerView.invalidate();
                if (delegate != null) {
                    if (delegate.needSend(currentContentType) && !delegate.isInScheduleMode()) {
                        items.add(LocaleController.getString("SendStickerPreview", R.string.SendStickerPreview));
                        icons.add(R.drawable.msg_send);
                        actions.add(0);
                    }
                    if (delegate.needSend(currentContentType) && !delegate.isInScheduleMode()) {
                        items.add(LocaleController.getString("SendWithoutSound", R.string.SendWithoutSound));
                        icons.add(R.drawable.input_notify_off);
                        actions.add(6);
                    }
                    if (delegate.canSchedule()) {
                        items.add(LocaleController.getString("Schedule", R.string.Schedule));
                        icons.add(R.drawable.msg_autodelete);
                        actions.add(3);
                    }
                    if (currentStickerSet != null && delegate.needOpen()) {
                        items.add(LocaleController.formatString("ViewPackPreview", R.string.ViewPackPreview));
                        icons.add(R.drawable.msg_media);
                        actions.add(1);
                    }
                    if (delegate.needRemove()) {
                        items.add(LocaleController.getString("ImportStickersRemoveMenu", R.string.ImportStickersRemoveMenu));
                        icons.add(R.drawable.msg_delete);
                        actions.add(5);
                    }
                }
                if (!MessageObject.isMaskDocument(currentDocument) && (inFavs || MediaDataController.getInstance(currentAccount).canAddStickerToFavorites() && MessageObject.isStickerHasSet(currentDocument))) {
                    items.add(inFavs ? LocaleController.getString("DeleteFromFavorites", R.string.DeleteFromFavorites) : LocaleController.getString("AddToFavorites", R.string.AddToFavorites));
                    icons.add(inFavs ? R.drawable.msg_unfave : R.drawable.msg_fave);
                    actions.add(2);
                }
                if (isRecentSticker) {
                    items.add(LocaleController.getString("DeleteFromRecent", R.string.DeleteFromRecent));
                    icons.add(R.drawable.msg_delete);
                    actions.add(4);
                }
                if (items.isEmpty()) {
                    return;
                }
                int[] ic = new int[icons.size()];
                for (int a = 0; a < icons.size(); a++) {
                    ic[a] = icons.get(a);
                }

                View.OnClickListener onItemClickListener = new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (parentActivity == null) {
                            return;
                        }
                        int which = (int) v.getTag();
                        if (actions.get(which) == 0 || actions.get(which) == 6) {
                            if (delegate != null) {
                                delegate.sendSticker(currentDocument, currentQuery, parentObject, actions.get(which) == 0, 0);
                            }
                        } else if (actions.get(which) == 1) {
                            if (delegate != null) {
                                delegate.openSet(currentStickerSet, clearsInputField);
                            }
                        } else if (actions.get(which) == 2) {
                            MediaDataController.getInstance(currentAccount).addRecentSticker(MediaDataController.TYPE_FAVE, parentObject, currentDocument, (int) (System.currentTimeMillis() / 1000), inFavs);
                        } else if (actions.get(which) == 3) {
                            TLRPC.Document sticker = currentDocument;
                            Object parent = parentObject;
                            String query = currentQuery;
                            ContentPreviewViewerDelegate stickerPreviewViewerDelegate = delegate;
                            if (stickerPreviewViewerDelegate == null) {
                                return;
                            }
                            AlertsCreator.createScheduleDatePickerDialog(parentActivity, stickerPreviewViewerDelegate.getDialogId(), (notify, scheduleDate) -> stickerPreviewViewerDelegate.sendSticker(sticker, query, parent, notify, scheduleDate));
                        } else if (actions.get(which) == 4) {
                            MediaDataController.getInstance(currentAccount).addRecentSticker(MediaDataController.TYPE_IMAGE, parentObject, currentDocument, (int) (System.currentTimeMillis() / 1000), true);
                        } else if (actions.get(which) == 5) {
                            delegate.remove(importingSticker);
                        }
                        if (popupWindow != null) {
                            popupWindow.dismiss();
                        }
                    }
                };
                ActionBarPopupWindow.ActionBarPopupWindowLayout previewMenu = new ActionBarPopupWindow.ActionBarPopupWindowLayout(containerView.getContext(), R.drawable.popup_fixed_alert3, resourcesProvider);

                for (int i = 0; i < items.size(); i++) {
                    View item = ActionBarMenuItem.addItem(previewMenu, icons.get(i), items.get(i), false, resourcesProvider);
                    item.setTag(i);
                    item.setOnClickListener(onItemClickListener);
                }
                popupWindow = new ActionBarPopupWindow(previewMenu, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
                    @Override
                    public void dismiss() {
                        super.dismiss();
                        popupWindow = null;
                        menuVisible = false;
                        if (closeOnDismiss) {
                            close();
                        }
                    }
                };
                popupWindow.setPauseNotifications(true);
                popupWindow.setDismissAnimationDuration(100);
                popupWindow.setScaleOut(true);
                popupWindow.setOutsideTouchable(true);
                popupWindow.setClippingEnabled(true);
                popupWindow.setAnimationStyle(R.style.PopupContextAnimation);
                popupWindow.setFocusable(true);
                previewMenu.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST));
                popupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
                popupWindow.getContentView().setFocusableInTouchMode(true);

                int insets = 0;
                int top;
                if (Build.VERSION.SDK_INT >= 21 && lastInsets != null) {
                    insets = lastInsets.getStableInsetBottom() + lastInsets.getStableInsetTop();
                    top = lastInsets.getStableInsetTop();
                } else {
                    top = AndroidUtilities.statusBarHeight;
                }
                int size;
                if (currentContentType == CONTENT_TYPE_GIF) {
                    size = Math.min(containerView.getWidth(), containerView.getHeight() - insets) - AndroidUtilities.dp(40f);
                } else {
                    if (drawEffect) {
                        size = (int) (Math.min(containerView.getWidth(), containerView.getHeight() - insets) - AndroidUtilities.dpf2(40f));
                    } else {
                        size = (int) (Math.min(containerView.getWidth(), containerView.getHeight() - insets) / 1.8f);
                    }
                }

                int y = (int) (moveY + Math.max(size / 2 + top + (stickerEmojiLayout != null ? AndroidUtilities.dp(40) : 0), (containerView.getHeight() - insets - keyboardHeight) / 2) + size / 2);
                y += AndroidUtilities.dp(24);
                if (drawEffect) {
                    y += AndroidUtilities.dp(24);
                }
                popupWindow.showAtLocation(containerView, 0, (int) ((containerView.getMeasuredWidth() - previewMenu.getMeasuredWidth()) / 2f), y);

                containerView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            } else if (currentContentType == CONTENT_TYPE_EMOJI && delegate != null) {
                menuVisible = true;
                containerView.invalidate();
                ArrayList<CharSequence> items = new ArrayList<>();
                final ArrayList<Integer> actions = new ArrayList<>();
                ArrayList<Integer> icons = new ArrayList<>();

                if (delegate.needSend(currentContentType)) {
                    items.add(LocaleController.getString("SendEmojiPreview", R.string.SendEmojiPreview));
                    icons.add(R.drawable.msg_send);
                    actions.add(0);
                }
                Boolean canSetAsStatus = delegate.canSetAsStatus(currentDocument);
                if (canSetAsStatus != null) {
                    if (canSetAsStatus) {
                        items.add(LocaleController.getString("SetAsEmojiStatus", R.string.SetAsEmojiStatus));
                        icons.add(R.drawable.msg_smile_status);
                        actions.add(1);
                    } else {
                        items.add(LocaleController.getString("RemoveStatus", R.string.RemoveStatus));
                        icons.add(R.drawable.msg_smile_status);
                        actions.add(2);
                    }
                }
                if (delegate.needCopy()) {
                    items.add(LocaleController.getString("CopyEmojiPreview", R.string.CopyEmojiPreview));
                    icons.add(R.drawable.msg_copy);
                    actions.add(3);
                }
                if (delegate.needRemoveFromRecent(currentDocument)) {
                    items.add(LocaleController.getString("RemoveFromRecent", R.string.RemoveFromRecent));
                    icons.add(R.drawable.msg_delete);
                    actions.add(4);
                }
                if (items.isEmpty()) {
                    return;
                }

                int[] ic = new int[icons.size()];
                for (int a = 0; a < icons.size(); a++) {
                    ic[a] = icons.get(a);
                }

                ActionBarPopupWindow.ActionBarPopupWindowLayout previewMenu = new ActionBarPopupWindow.ActionBarPopupWindowLayout(containerView.getContext(), R.drawable.popup_fixed_alert2, resourcesProvider);

                View.OnClickListener onItemClickListener = v -> {
                    if (parentActivity == null || delegate == null) {
                        return;
                    }
                    int which = (int) v.getTag();
                    int action = actions.get(which);
                    if (action == 0) {
                        delegate.sendEmoji(currentDocument);
                    } else if (action == 1) {
                        delegate.setAsEmojiStatus(currentDocument, null);
                    } else if (action == 2) {
                        delegate.setAsEmojiStatus(null, null);
                    } else if (action == 3) {
                        delegate.copyEmoji(currentDocument);
                    } else if (action == 4) {
                        delegate.removeFromRecent(currentDocument);
                    }
                    if (popupWindow != null) {
                        popupWindow.dismiss();
                    }
                };

                for (int i = 0; i < items.size(); i++) {
                    ActionBarMenuSubItem item = ActionBarMenuItem.addItem(i == 0, i == items.size() - 1, previewMenu, icons.get(i), items.get(i), false, resourcesProvider);
                    if (actions.get(i) == 4) {
                        item.setIconColor(getThemedColor(Theme.key_text_RedRegular));
                        item.setTextColor(getThemedColor(Theme.key_text_RedBold));
                    }
                    item.setTag(i);
                    item.setOnClickListener(onItemClickListener);
                }
                popupWindow = new ActionBarPopupWindow(previewMenu, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
                    @Override
                    public void dismiss() {
                        super.dismiss();
                        popupWindow = null;
                        menuVisible = false;
                        if (closeOnDismiss) {
                            close();
                        }
                    }
                };
                popupWindow.setPauseNotifications(true);
                popupWindow.setDismissAnimationDuration(150);
                popupWindow.setScaleOut(true);
                popupWindow.setOutsideTouchable(true);
                popupWindow.setClippingEnabled(true);
                popupWindow.setAnimationStyle(R.style.PopupContextAnimation);
                popupWindow.setFocusable(true);
                previewMenu.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST));
                popupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
                popupWindow.getContentView().setFocusableInTouchMode(true);

                int insets = 0;
                int top;
                if (Build.VERSION.SDK_INT >= 21 && lastInsets != null) {
                    insets = lastInsets.getStableInsetBottom() + lastInsets.getStableInsetTop();
                    top = lastInsets.getStableInsetTop();
                } else {
                    top = AndroidUtilities.statusBarHeight;
                }
                int size = Math.min(containerView.getWidth(), containerView.getHeight() - insets) - AndroidUtilities.dp(40f);

                int y = (int) (moveY + Math.max(size / 2 + top + (stickerEmojiLayout != null ? AndroidUtilities.dp(40) : 0), (containerView.getHeight() - insets - keyboardHeight) / 2) + size / 2);
                y += AndroidUtilities.dp(24) - moveY;
                popupWindow.showAtLocation(containerView, 0, (int) ((containerView.getMeasuredWidth() - previewMenu.getMeasuredWidth()) / 2f), y);
                ActionBarPopupWindow.startAnimation(previewMenu);

                containerView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);

                if (moveY != 0) {
                    if (finalMoveY == 0) {
                        finalMoveY = 0;
                        startMoveY = moveY;
                    }
                    ValueAnimator valueAnimator = ValueAnimator.ofFloat(0f, 1f);
                    valueAnimator.addUpdateListener(animation -> {
                        currentMoveYProgress = (float) animation.getAnimatedValue();
                        moveY = startMoveY + (finalMoveY - startMoveY) * currentMoveYProgress;
                        ContentPreviewViewer.this.containerView.invalidate();
                    });
                    valueAnimator.setDuration(350);
                    valueAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                    valueAnimator.start();
                }
            } else if (delegate != null) {
                menuVisible = true;
                containerView.invalidate();
                ArrayList<CharSequence> items = new ArrayList<>();
                final ArrayList<Integer> actions = new ArrayList<>();
                ArrayList<Integer> icons = new ArrayList<>();

                if (delegate.needSend(currentContentType) && !delegate.isInScheduleMode()) {
                    items.add(LocaleController.getString("SendGifPreview", R.string.SendGifPreview));
                    icons.add(R.drawable.msg_send);
                    actions.add(0);
                }
                if (delegate.needSend(currentContentType) && !delegate.isInScheduleMode()) {
                    items.add(LocaleController.getString("SendWithoutSound", R.string.SendWithoutSound));
                    icons.add(R.drawable.input_notify_off);
                    actions.add(4);
                }
                if (delegate.canSchedule()) {
                    items.add(LocaleController.getString("Schedule", R.string.Schedule));
                    icons.add(R.drawable.msg_autodelete);
                    actions.add(3);
                }

                boolean canDelete;
                if (currentDocument != null) {
                    if (canDelete = MediaDataController.getInstance(currentAccount).hasRecentGif(currentDocument)) {
                        items.add(LocaleController.formatString("Delete", R.string.Delete));
                        icons.add(R.drawable.msg_delete);
                        actions.add(1);
                    } else {
                        items.add(LocaleController.formatString("SaveToGIFs", R.string.SaveToGIFs));
                        icons.add(R.drawable.msg_gif_add);
                        actions.add(2);
                    }
                } else {
                    canDelete = false;
                }
                if (items.isEmpty()) {
                    return;
                }

                int[] ic = new int[icons.size()];
                for (int a = 0; a < icons.size(); a++) {
                    ic[a] = icons.get(a);
                }

                ActionBarPopupWindow.ActionBarPopupWindowLayout previewMenu = new ActionBarPopupWindow.ActionBarPopupWindowLayout(containerView.getContext(), R.drawable.popup_fixed_alert2, resourcesProvider);

                View.OnClickListener onItemClickListener = v -> {
                    if (parentActivity == null) {
                        return;
                    }
                    int which = (int) v.getTag();
                    if (actions.get(which) == 0) {
                        delegate.sendGif(currentDocument != null ? currentDocument : inlineResult, parentObject, true, 0);
                    } else if (actions.get(which) == 4) {
                        delegate.sendGif(currentDocument != null ? currentDocument : inlineResult, parentObject, false, 0);
                    } else if (actions.get(which) == 1) {
                        MediaDataController.getInstance(currentAccount).removeRecentGif(currentDocument);
                        delegate.gifAddedOrDeleted();
                    } else if (actions.get(which) == 2) {
                        MediaDataController.getInstance(currentAccount).addRecentGif(currentDocument, (int) (System.currentTimeMillis() / 1000), true);
                        MessagesController.getInstance(currentAccount).saveGif("gif", currentDocument);
                        delegate.gifAddedOrDeleted();
                    } else if (actions.get(which) == 3) {
                        TLRPC.Document document = currentDocument;
                        TLRPC.BotInlineResult result = inlineResult;
                        Object parent = parentObject;
                        ContentPreviewViewerDelegate stickerPreviewViewerDelegate = delegate;
                        AlertsCreator.createScheduleDatePickerDialog(parentActivity, stickerPreviewViewerDelegate.getDialogId(), (notify, scheduleDate) -> stickerPreviewViewerDelegate.sendGif(document != null ? document : result, parent, notify, scheduleDate), resourcesProvider);
                    }
                    if (popupWindow != null) {
                        popupWindow.dismiss();
                    }
                };

                for (int i = 0; i < items.size(); i++) {
                    ActionBarMenuSubItem item = ActionBarMenuItem.addItem(previewMenu, icons.get(i), items.get(i), false, resourcesProvider);
                    item.setTag(i);
                    item.setOnClickListener(onItemClickListener);

                    if (canDelete && i == items.size() - 1) {
                        item.setColors(getThemedColor(Theme.key_text_RedBold), getThemedColor(Theme.key_text_RedRegular));
                    }
                }
                popupWindow = new ActionBarPopupWindow(previewMenu, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
                    @Override
                    public void dismiss() {
                        super.dismiss();
                        popupWindow = null;
                        menuVisible = false;
                        if (closeOnDismiss) {
                            close();
                        }
                    }
                };
                popupWindow.setPauseNotifications(true);
                popupWindow.setDismissAnimationDuration(150);
                popupWindow.setScaleOut(true);
                popupWindow.setOutsideTouchable(true);
                popupWindow.setClippingEnabled(true);
                popupWindow.setAnimationStyle(R.style.PopupContextAnimation);
                popupWindow.setFocusable(true);
                previewMenu.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST));
                popupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
                popupWindow.getContentView().setFocusableInTouchMode(true);

                int insets = 0;
                int top;
                if (Build.VERSION.SDK_INT >= 21 && lastInsets != null) {
                    insets = lastInsets.getStableInsetBottom() + lastInsets.getStableInsetTop();
                    top = lastInsets.getStableInsetTop();
                } else {
                    top = AndroidUtilities.statusBarHeight;
                }
                int size = Math.min(containerView.getWidth(), containerView.getHeight() - insets) - AndroidUtilities.dp(40f);


                int y = (int) (moveY + Math.max(size / 2 + top + (stickerEmojiLayout != null ? AndroidUtilities.dp(40) : 0), (containerView.getHeight() - insets - keyboardHeight) / 2) + size / 2);
                y += AndroidUtilities.dp(24) - moveY;
                popupWindow.showAtLocation(containerView, 0, (int) ((containerView.getMeasuredWidth() - previewMenu.getMeasuredWidth()) / 2f), y);

                containerView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);

                if (moveY != 0) {
                    if (finalMoveY == 0) {
                        finalMoveY = 0;
                        startMoveY = moveY;
                    }
                    ValueAnimator valueAnimator = ValueAnimator.ofFloat(0f, 1f);
                    valueAnimator.addUpdateListener(animation -> {
                        currentMoveYProgress = (float) animation.getAnimatedValue();
                        moveY = startMoveY + (finalMoveY - startMoveY) * currentMoveYProgress;
                        ContentPreviewViewer.this.containerView.invalidate();
                    });
                    valueAnimator.setDuration(350);
                    valueAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                    valueAnimator.start();
                }
            }
        }
    };

    private void showUnlockPremiumView() {
        if (unlockPremiumView == null) {
            unlockPremiumView = new UnlockPremiumView(containerView.getContext(), UnlockPremiumView.TYPE_STICKERS, resourcesProvider);
            containerView.addView(unlockPremiumView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            unlockPremiumView.setOnClickListener(v -> {
                menuVisible = false;
                containerView.invalidate();
                close();
            });
            unlockPremiumView.premiumButtonView.buttonLayout.setOnClickListener(v -> {
                if (parentActivity instanceof LaunchActivity) {
                    LaunchActivity activity = (LaunchActivity) parentActivity;
                    if (activity.getActionBarLayout() != null && activity.getActionBarLayout().getLastFragment() != null) {
                        activity.getActionBarLayout().getLastFragment().dismissCurrentDialog();
                    }
                    activity.presentFragment(new PremiumPreviewFragment(PremiumPreviewFragment.featureTypeToServerString(PremiumPreviewFragment.PREMIUM_FEATURE_STICKERS)));
                }
                menuVisible = false;
                containerView.invalidate();
                close();
            });
        }
        AndroidUtilities.updateViewVisibilityAnimated(unlockPremiumView, false, 1f, false);
        AndroidUtilities.updateViewVisibilityAnimated(unlockPremiumView, true);
        unlockPremiumView.setTranslationY(0);
    }

    private int currentContentType;
    private TLRPC.Document currentDocument;
    private SendMessagesHelper.ImportingSticker importingSticker;
    private String currentQuery;
    private TLRPC.BotInlineResult inlineResult;
    private TLRPC.InputStickerSet currentStickerSet;
    private Object parentObject;
    private Theme.ResourcesProvider resourcesProvider;

    @SuppressLint("StaticFieldLeak")
    private static volatile ContentPreviewViewer Instance = null;

    public static ContentPreviewViewer getInstance() {
        ContentPreviewViewer localInstance = Instance;
        if (localInstance == null) {
            synchronized (PhotoViewer.class) {
                localInstance = Instance;
                if (localInstance == null) {
                    Instance = localInstance = new ContentPreviewViewer();
                }
            }
        }
        return localInstance;
    }

    public static boolean hasInstance() {
        return Instance != null;
    }

    public void reset() {
        if (openPreviewRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(openPreviewRunnable);
            openPreviewRunnable = null;
        }
        if (currentPreviewCell != null) {
            if (currentPreviewCell instanceof StickerEmojiCell) {
                ((StickerEmojiCell) currentPreviewCell).setScaled(false);
            } else if (currentPreviewCell instanceof StickerCell) {
                ((StickerCell) currentPreviewCell).setScaled(false);
            } else if (currentPreviewCell instanceof ContextLinkCell) {
                ((ContextLinkCell) currentPreviewCell).setScaled(false);
            }
            currentPreviewCell = null;
        }
    }

    public boolean onTouch(MotionEvent event, final RecyclerListView listView, final int height, final Object listener, ContentPreviewViewerDelegate contentPreviewViewerDelegate, Theme.ResourcesProvider resourcesProvider) {
        delegate = contentPreviewViewerDelegate;
        this.resourcesProvider = resourcesProvider;
        if (delegate != null && !delegate.can()) {
            return false;
        }
        if (openPreviewRunnable != null || isVisible()) {
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL || event.getAction() == MotionEvent.ACTION_POINTER_UP) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (listView instanceof RecyclerListView) {
                        listView.setOnItemClickListener((RecyclerListView.OnItemClickListener) listener);
                    }
                }, 150);
                if (openPreviewRunnable != null) {
                    AndroidUtilities.cancelRunOnUIThread(openPreviewRunnable);
                    openPreviewRunnable = null;
                } else if (isVisible()) {
                    close();
                    if (currentPreviewCell != null) {
                        if (currentPreviewCell instanceof StickerEmojiCell) {
                            ((StickerEmojiCell) currentPreviewCell).setScaled(false);
                        } else if (currentPreviewCell instanceof StickerCell) {
                            ((StickerCell) currentPreviewCell).setScaled(false);
                        } else if (currentPreviewCell instanceof ContextLinkCell) {
                            ((ContextLinkCell) currentPreviewCell).setScaled(false);
                        }
                        currentPreviewCell = null;
                    }
                }
            } else if (event.getAction() != MotionEvent.ACTION_DOWN) {
                if (isVisible) {
                    if (event.getAction() == MotionEvent.ACTION_MOVE) {
                        if (currentContentType == CONTENT_TYPE_GIF) {
                            if (!menuVisible && showProgress == 1.0f) {
                                if (lastTouchY == -10000) {
                                    lastTouchY = event.getY();
                                    currentMoveY = 0;
                                    moveY = 0;
                                } else {
                                    float newY = event.getY();
                                    currentMoveY += newY - lastTouchY;
                                    lastTouchY = newY;
                                    if (currentMoveY > 0) {
                                        currentMoveY = 0;
                                    } else if (currentMoveY < -AndroidUtilities.dp(60)) {
                                        currentMoveY = -AndroidUtilities.dp(60);
                                    }
                                    moveY = rubberYPoisition(currentMoveY, AndroidUtilities.dp(200));
                                    containerView.invalidate();
                                    if (currentMoveY <= -AndroidUtilities.dp(55)) {
                                        AndroidUtilities.cancelRunOnUIThread(showSheetRunnable);
                                        showSheetRunnable.run();
                                        return true;
                                    }
                                }
                            }
                            return true;
                        }
                        int x = (int) event.getX();
                        int y = (int) event.getY();
                        int count = listView.getChildCount();
                        for (int a = 0; a < count; a++) {
                            View view = null;
                            if (listView instanceof RecyclerListView) {
                                view = listView.getChildAt(a);
                            }
                            if (view == null) {
                                return false;
                            }
                            int top = view.getTop();
                            int bottom = view.getBottom();
                            int left = view.getLeft();
                            int right = view.getRight();
                            if (top > y || bottom < y || left > x || right < x) {
                                continue;
                            }
                            int contentType = CONTENT_TYPE_NONE;
                            if (view instanceof StickerEmojiCell) {
                                contentType = CONTENT_TYPE_STICKER;
                                centerImage.setRoundRadius(0);
                            } else if (view instanceof StickerCell) {
                                contentType = CONTENT_TYPE_STICKER;
                                centerImage.setRoundRadius(0);
                            } else if (view instanceof ContextLinkCell) {
                                ContextLinkCell cell = (ContextLinkCell) view;
                                if (cell.isSticker()) {
                                    contentType = CONTENT_TYPE_STICKER;
                                    centerImage.setRoundRadius(0);
                                } else if (cell.isGif()) {
                                    contentType = CONTENT_TYPE_GIF;
                                    centerImage.setRoundRadius(AndroidUtilities.dp(6));
                                }
                            } else if (view instanceof EmojiPacksAlert.EmojiImageView) {
                                contentType = CONTENT_TYPE_EMOJI;
                                centerImage.setRoundRadius(0);
                            } else if (view instanceof EmojiView.ImageViewEmoji && ((EmojiView.ImageViewEmoji) view).getSpan() != null) {
                                contentType = CONTENT_TYPE_EMOJI;
                                centerImage.setRoundRadius(0);
                            }
                            if (contentType == CONTENT_TYPE_NONE || view == currentPreviewCell) {
                                break;
                            }
                            if (delegate != null) {
                                delegate.resetTouch();
                            }
                            if (currentPreviewCell instanceof StickerEmojiCell) {
                                ((StickerEmojiCell) currentPreviewCell).setScaled(false);
                            } else if (currentPreviewCell instanceof StickerCell) {
                                ((StickerCell) currentPreviewCell).setScaled(false);
                            } else if (currentPreviewCell instanceof ContextLinkCell) {
                                ((ContextLinkCell) currentPreviewCell).setScaled(false);
                            }
                            currentPreviewCell = view;
                            clearsInputField = false;
                            menuVisible = false;
                            closeOnDismiss = false;
                            if (popupWindow != null) {
                                popupWindow.dismiss();
                            }
                            AndroidUtilities.updateViewVisibilityAnimated(unlockPremiumView, false);
                            if (currentPreviewCell instanceof StickerEmojiCell) {
                                StickerEmojiCell stickerEmojiCell = (StickerEmojiCell) currentPreviewCell;
                                open(stickerEmojiCell.getSticker(), stickerEmojiCell.getStickerPath(), MessageObject.findAnimatedEmojiEmoticon(stickerEmojiCell.getSticker(), null, currentAccount), delegate != null ? delegate.getQuery(false) : null, null, contentType, stickerEmojiCell.isRecent(), stickerEmojiCell.getParentObject(), resourcesProvider);
                                stickerEmojiCell.setScaled(true);
                            } else if (currentPreviewCell instanceof StickerCell) {
                                StickerCell stickerCell = (StickerCell) currentPreviewCell;
                                open(stickerCell.getSticker(), null, null, delegate != null ? delegate.getQuery(false) : null, null, contentType, false, stickerCell.getParentObject(), resourcesProvider);
                                stickerCell.setScaled(true);
                                clearsInputField = stickerCell.isClearsInputField();
                            } else if (currentPreviewCell instanceof ContextLinkCell) {
                                ContextLinkCell contextLinkCell = (ContextLinkCell) currentPreviewCell;
                                open(contextLinkCell.getDocument(), null, null, delegate != null ? delegate.getQuery(true) : null, contextLinkCell.getBotInlineResult(), contentType, false, contextLinkCell.getBotInlineResult() != null ? contextLinkCell.getInlineBot() : contextLinkCell.getParentObject(), resourcesProvider);
                                if (contentType != CONTENT_TYPE_GIF) {
                                    contextLinkCell.setScaled(true);
                                }
                            } else if (currentPreviewCell instanceof EmojiPacksAlert.EmojiImageView) {
                                EmojiPacksAlert.EmojiImageView imageView = (EmojiPacksAlert.EmojiImageView) currentPreviewCell;
                                TLRPC.Document document = imageView.getDocument();
                                if (document != null) {
                                    open(document, null, MessageObject.findAnimatedEmojiEmoticon(document, null, currentAccount), null, null, contentType, false, null, resourcesProvider);
                                }
                            } else if (currentPreviewCell instanceof EmojiView.ImageViewEmoji) {
                                EmojiView.ImageViewEmoji imageView = (EmojiView.ImageViewEmoji) currentPreviewCell;
                                AnimatedEmojiSpan span = imageView.getSpan();
                                TLRPC.Document document = null;
                                if (span != null) {
                                    document = span.document;
                                    if (document == null) {
                                        document = AnimatedEmojiDrawable.findDocument(currentAccount, span.getDocumentId());
                                    }
                                }
                                if (document != null) {
                                    open(document, null, MessageObject.findAnimatedEmojiEmoticon(document, null, currentAccount), null, null, contentType, false, null, resourcesProvider);
                                } else {
                                    return false;
                                }
                            } else if (currentPreviewCell instanceof SuggestEmojiView.EmojiImageView) {
                                SuggestEmojiView.EmojiImageView emojiImageView = (SuggestEmojiView.EmojiImageView) currentPreviewCell;
                                Drawable drawable = emojiImageView.drawable;
                                TLRPC.Document document = null;
                                if (drawable instanceof AnimatedEmojiDrawable) {
                                    document = ((AnimatedEmojiDrawable) drawable).getDocument();
                                }
                                if (document == null) {
                                    return false;
                                }
                                open(document, null, MessageObject.findAnimatedEmojiEmoticon(document, null, currentAccount), null, null, contentType, false, null, resourcesProvider);
                            }
                            runSmoothHaptic();

                            return true;
                        }
                    }
                    return true;
                } else if (openPreviewRunnable != null) {
                    if (event.getAction() == MotionEvent.ACTION_MOVE) {
                        if (Math.hypot(startX - event.getX(), startY - event.getY()) > AndroidUtilities.dp(10)) {
                            AndroidUtilities.cancelRunOnUIThread(openPreviewRunnable);
                            openPreviewRunnable = null;
                        }
                    } else {
                        AndroidUtilities.cancelRunOnUIThread(openPreviewRunnable);
                        openPreviewRunnable = null;
                    }
                }
            }
        }
        return false;
    }

    VibrationEffect vibrationEffect;

    protected void runSmoothHaptic() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            final Vibrator vibrator = (Vibrator) containerView.getContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrationEffect == null) {
                long[] vibrationWaveFormDurationPattern = {0, 2};
                vibrationEffect = VibrationEffect.createWaveform(vibrationWaveFormDurationPattern, -1);
            }
            vibrator.cancel();
            vibrator.vibrate(vibrationEffect);
        }
    }

    public boolean onInterceptTouchEvent(MotionEvent event, final RecyclerListView listView, final int height, ContentPreviewViewerDelegate contentPreviewViewerDelegate, Theme.ResourcesProvider resourcesProvider) {
        delegate = contentPreviewViewerDelegate;
        this.resourcesProvider = resourcesProvider;
        if (delegate != null && !delegate.can()) {
            return false;
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            int x = (int) event.getX();
            int y = (int) event.getY();
            int count = listView.getChildCount();
            for (int a = 0; a < count; a++) {
                View view = null;
                if (listView instanceof RecyclerListView) {
                    view = listView.getChildAt(a);
                }
                if (view == null) {
                    return false;
                }
                int top = view.getTop();
                int bottom = view.getBottom();
                int left = view.getLeft();
                int right = view.getRight();
                if (top > y || bottom < y || left > x || right < x) {
                    continue;
                }
                int contentType = CONTENT_TYPE_NONE;
                if (view instanceof StickerEmojiCell) {
                    if (((StickerEmojiCell) view).showingBitmap()) {
                        contentType = CONTENT_TYPE_STICKER;
                        centerImage.setRoundRadius(0);
                    }
                } else if (view instanceof StickerCell) {
                    if (((StickerCell) view).showingBitmap()) {
                        contentType = CONTENT_TYPE_STICKER;
                        centerImage.setRoundRadius(0);
                    }
                } else if (view instanceof ContextLinkCell) {
                    ContextLinkCell cell = (ContextLinkCell) view;
                    if (cell.showingBitmap()) {
                        if (cell.isSticker()) {
                            contentType = CONTENT_TYPE_STICKER;
                            centerImage.setRoundRadius(0);
                        } else if (cell.isGif()) {
                            contentType = CONTENT_TYPE_GIF;
                            centerImage.setRoundRadius(AndroidUtilities.dp(6));
                        }
                    }
                } else if (view instanceof EmojiPacksAlert.EmojiImageView) {
                    contentType = CONTENT_TYPE_EMOJI;
                    centerImage.setRoundRadius(0);
                } else if (view instanceof EmojiView.ImageViewEmoji && ((EmojiView.ImageViewEmoji) view).getSpan() != null) {
                    contentType = CONTENT_TYPE_EMOJI;
                    centerImage.setRoundRadius(0);
                } else if (view instanceof SuggestEmojiView.EmojiImageView) {
                    SuggestEmojiView.EmojiImageView emojiImageView = (SuggestEmojiView.EmojiImageView) view;
                    Drawable drawable = emojiImageView.drawable;
                    if (drawable instanceof AnimatedEmojiDrawable) {
                        contentType = CONTENT_TYPE_EMOJI;
                        centerImage.setRoundRadius(0);
                    }
                }
                if (contentType == CONTENT_TYPE_NONE) {
                    return false;
                }
                startX = x;
                startY = y;
                currentPreviewCell = view;
                int contentTypeFinal = contentType;
                openPreviewRunnable = () -> {
                    if (openPreviewRunnable == null) {
                        return;
                    }
                    boolean opened = false;
                    listView.setOnItemClickListener((RecyclerListView.OnItemClickListener) null);
                    listView.requestDisallowInterceptTouchEvent(true);
                    openPreviewRunnable = null;
                    setParentActivity(AndroidUtilities.findActivity(listView.getContext()));
                    //setKeyboardHeight(height);
                    clearsInputField = false;
                    if (currentPreviewCell instanceof StickerEmojiCell) {
                        StickerEmojiCell stickerEmojiCell = (StickerEmojiCell) currentPreviewCell;
                        open(stickerEmojiCell.getSticker(), stickerEmojiCell.getStickerPath(), MessageObject.findAnimatedEmojiEmoticon(stickerEmojiCell.getSticker(), null, currentAccount), delegate != null ? delegate.getQuery(false) : null, null, contentTypeFinal, stickerEmojiCell.isRecent(), stickerEmojiCell.getParentObject(), resourcesProvider);
                        opened = true;
                        stickerEmojiCell.setScaled(true);
                    } else if (currentPreviewCell instanceof StickerCell) {
                        StickerCell stickerCell = (StickerCell) currentPreviewCell;
                        open(stickerCell.getSticker(), null, null, delegate != null ? delegate.getQuery(false) : null, null, contentTypeFinal, false, stickerCell.getParentObject(), resourcesProvider);
                        opened = true;
                        stickerCell.setScaled(true);
                        clearsInputField = stickerCell.isClearsInputField();
                    } else if (currentPreviewCell instanceof ContextLinkCell) {
                        ContextLinkCell contextLinkCell = (ContextLinkCell) currentPreviewCell;
                        open(contextLinkCell.getDocument(), null, null, delegate != null ? delegate.getQuery(true) : null, contextLinkCell.getBotInlineResult(), contentTypeFinal, false, contextLinkCell.getBotInlineResult() != null ? contextLinkCell.getInlineBot() : contextLinkCell.getParentObject(), resourcesProvider);
                        opened = true;
                        if (contentTypeFinal != CONTENT_TYPE_GIF) {
                            contextLinkCell.setScaled(true);
                        }
                    } else if (currentPreviewCell instanceof EmojiPacksAlert.EmojiImageView) {
                        EmojiPacksAlert.EmojiImageView imageView = (EmojiPacksAlert.EmojiImageView) currentPreviewCell;
                        TLRPC.Document document = imageView.getDocument();
                        if (document != null) {
                            open(document, null, MessageObject.findAnimatedEmojiEmoticon(document, null, currentAccount), null, null, contentTypeFinal, false, null, resourcesProvider);
                            opened = true;
                        }
                    } else if (currentPreviewCell instanceof EmojiView.ImageViewEmoji) {
                        EmojiView.ImageViewEmoji imageView = (EmojiView.ImageViewEmoji) currentPreviewCell;
                        AnimatedEmojiSpan span = imageView.getSpan();
                        TLRPC.Document document = null;
                        if (span != null) {
                            document = span.document;
                            if (document == null) {
                                document = AnimatedEmojiDrawable.findDocument(currentAccount, span.getDocumentId());
                            }
                        }
                        if (document != null) {
                            open(document, null, MessageObject.findAnimatedEmojiEmoticon(document, null, currentAccount), null, null, contentTypeFinal, false, null, resourcesProvider);
                            opened = true;
                        }
                    } else if (currentPreviewCell instanceof SuggestEmojiView.EmojiImageView) {
                        SuggestEmojiView.EmojiImageView emojiImageView = (SuggestEmojiView.EmojiImageView) currentPreviewCell;
                        Drawable drawable = emojiImageView.drawable;
                        TLRPC.Document document = null;
                        if (drawable instanceof AnimatedEmojiDrawable) {
                            document = ((AnimatedEmojiDrawable) drawable).getDocument();
                        }
                        if (document != null) {
                            open(document, null, MessageObject.findAnimatedEmojiEmoticon(document, null, currentAccount), null, null, contentTypeFinal, false, null, resourcesProvider);
                            opened = true;
                        }
                    }
                    if (opened) {
                        currentPreviewCell.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                        if (delegate != null) {
                            delegate.resetTouch();
                        }
                    }
                };
                AndroidUtilities.runOnUIThread(openPreviewRunnable, 200);
                return true;
            }
        }
        return false;
    }

    public void setDelegate(ContentPreviewViewerDelegate contentPreviewViewerDelegate) {
        delegate = contentPreviewViewerDelegate;
    }

    public void setParentActivity(Activity activity) {
        currentAccount = UserConfig.selectedAccount;
        centerImage.setCurrentAccount(currentAccount);
        centerImage.setLayerNum(Integer.MAX_VALUE);
        effectImage.setCurrentAccount(currentAccount);
        effectImage.setLayerNum(Integer.MAX_VALUE);
        if (parentActivity == activity) {
            return;
        }
        parentActivity = activity;

        slideUpDrawable = parentActivity.getResources().getDrawable(R.drawable.preview_arrow);

        windowView = new FrameLayout(activity);
        windowView.setFocusable(true);
        windowView.setFocusableInTouchMode(true);
        if (Build.VERSION.SDK_INT >= 21) {
            windowView.setFitsSystemWindows(true);
            windowView.setOnApplyWindowInsetsListener((v, insets) -> {
                lastInsets = insets;
                return insets;
            });
        }

        containerView = new FrameLayoutDrawer(activity) {
            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                centerImage.onAttachedToWindow();
                effectImage.onAttachedToWindow();
            }

            @Override
            protected void onDetachedFromWindow() {
                super.onDetachedFromWindow();
                centerImage.onDetachedFromWindow();
                effectImage.onDetachedFromWindow();
            }
        };
        containerView.setFocusable(false);
        windowView.addView(containerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        containerView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_POINTER_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                close();
            }
            return true;
        });


        SharedPreferences sharedPreferences = MessagesController.getInstance(currentAccount).getGlobalEmojiSettings();
        keyboardHeight = sharedPreferences.getInt("kbd_height", AndroidUtilities.dp(200));

        windowLayoutParams = new WindowManager.LayoutParams();
        windowLayoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        windowLayoutParams.format = PixelFormat.TRANSLUCENT;
        windowLayoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        windowLayoutParams.gravity = Gravity.TOP;
        windowLayoutParams.type = WindowManager.LayoutParams.LAST_APPLICATION_WINDOW;
        if (Build.VERSION.SDK_INT >= 21) {
            windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        } else {
            windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        }
        centerImage.setAspectFit(true);
        centerImage.setInvalidateAll(true);
        centerImage.setParentView(containerView);

        effectImage.setAspectFit(true);
        effectImage.setInvalidateAll(true);
        effectImage.setParentView(containerView);
    }

    public void setKeyboardHeight(int height) {
        keyboardHeight = height;
    }

    public void open(TLRPC.Document document, SendMessagesHelper.ImportingSticker sticker, String emojiPath, String query, TLRPC.BotInlineResult botInlineResult, int contentType, boolean isRecent, Object parent, Theme.ResourcesProvider resourcesProvider) {
        if (parentActivity == null || windowView == null) {
            return;
        }
        this.resourcesProvider = resourcesProvider;
        isRecentSticker = isRecent;
        stickerEmojiLayout = null;
        backgroundDrawable.setColor(Theme.getActiveTheme().isDark() ? 0x71000000 : 0x64E6E6E6);
        drawEffect = false;
        centerImage.setColorFilter(null);
        if (contentType == CONTENT_TYPE_STICKER || contentType == CONTENT_TYPE_EMOJI) {
            if (document == null && sticker == null) {
                return;
            }
            if (textPaint == null) {
                textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                textPaint.setTextSize(AndroidUtilities.dp(24));
            }

            effectImage.clearImage();
            drawEffect = false;
            if (document != null) {
                TLRPC.InputStickerSet newSet = null;
                for (int a = 0; a < document.attributes.size(); a++) {
                    TLRPC.DocumentAttribute attribute = document.attributes.get(a);
                    if (attribute instanceof TLRPC.TL_documentAttributeSticker && attribute.stickerset != null) {
                        newSet = attribute.stickerset;
                        break;
                    }
                }
                if (contentType == CONTENT_TYPE_EMOJI && emojiPath != null) {
                    CharSequence emoji = Emoji.replaceEmoji(emojiPath, textPaint.getFontMetricsInt(), AndroidUtilities.dp(24), false);
                    stickerEmojiLayout = new StaticLayout(emoji, textPaint, AndroidUtilities.dp(500), Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
                }
                if ((newSet != null || contentType == CONTENT_TYPE_EMOJI) && (delegate == null || delegate.needMenu())) {
                    AndroidUtilities.cancelRunOnUIThread(showSheetRunnable);
                    AndroidUtilities.runOnUIThread(showSheetRunnable, 1300);
                }
                currentStickerSet = newSet;
                TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90);
                if (MessageObject.isVideoStickerDocument(document)) {
                    centerImage.setImage(ImageLocation.getForDocument(document), null, ImageLocation.getForDocument(thumb, document), null, null, 0, "webp", currentStickerSet, 1);
                } else {
                    centerImage.setImage(ImageLocation.getForDocument(document), null, ImageLocation.getForDocument(thumb, document), null, "webp", currentStickerSet, 1);
                    if (MessageObject.isPremiumSticker(document)) {
                        drawEffect = true;
                        effectImage.setImage(ImageLocation.getForDocument(MessageObject.getPremiumStickerAnimation(document), document), null, null, null, "tgs", currentStickerSet, 1);
                    }
                }
                if (MessageObject.isTextColorEmoji(document)) {
                    centerImage.setColorFilter(Theme.chat_animatedEmojiTextColorFilter);
                }
                if (stickerEmojiLayout == null) {
                    for (int a = 0; a < document.attributes.size(); a++) {
                        TLRPC.DocumentAttribute attribute = document.attributes.get(a);
                        if (attribute instanceof TLRPC.TL_documentAttributeSticker) {
                            if (!TextUtils.isEmpty(attribute.alt)) {
                                CharSequence emoji = Emoji.replaceEmoji(attribute.alt, textPaint.getFontMetricsInt(), AndroidUtilities.dp(24), false);
                                stickerEmojiLayout = new StaticLayout(emoji, textPaint, AndroidUtilities.dp(500), Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
                                break;
                            }
                        }
                    }
                }
            } else if (sticker != null) {
                centerImage.setImage(sticker.path, null, null, sticker.animated ? "tgs" : null, 0);
                if (emojiPath != null) {
                    CharSequence emoji = Emoji.replaceEmoji(emojiPath, textPaint.getFontMetricsInt(), AndroidUtilities.dp(24), false);
                    stickerEmojiLayout = new StaticLayout(emoji, textPaint, AndroidUtilities.dp(500), Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
                }
                if (delegate.needMenu()) {
                    AndroidUtilities.cancelRunOnUIThread(showSheetRunnable);
                    AndroidUtilities.runOnUIThread(showSheetRunnable, 1300);
                }
            }
        } else {
            if (document != null) {
                TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90);
                TLRPC.VideoSize videoSize = MessageObject.getDocumentVideoThumb(document);
                ImageLocation location = ImageLocation.getForDocument(document);
                location.imageType = FileLoader.IMAGE_TYPE_ANIMATION;
                if (videoSize != null) {
                    centerImage.setImage(location, null, ImageLocation.getForDocument(videoSize, document), null, ImageLocation.getForDocument(thumb, document), "90_90_b", null, document.size, null, "gif" + document, 0);
                } else {
                    centerImage.setImage(location, null, ImageLocation.getForDocument(thumb, document), "90_90_b", document.size, null, "gif" + document, 0);
                }
            } else if (botInlineResult != null) {
                if (botInlineResult.content == null) {
                    return;
                }
                if (botInlineResult.thumb instanceof TLRPC.TL_webDocument && "video/mp4".equals(botInlineResult.thumb.mime_type)) {
                    centerImage.setImage(ImageLocation.getForWebFile(WebFile.createWithWebDocument(botInlineResult.content)), null, ImageLocation.getForWebFile(WebFile.createWithWebDocument(botInlineResult.thumb)), null, ImageLocation.getForWebFile(WebFile.createWithWebDocument(botInlineResult.thumb)), "90_90_b", null, botInlineResult.content.size, null, "gif" + botInlineResult, 1);
                } else {
                    centerImage.setImage(ImageLocation.getForWebFile(WebFile.createWithWebDocument(botInlineResult.content)), null, ImageLocation.getForWebFile(WebFile.createWithWebDocument(botInlineResult.thumb)), "90_90_b", botInlineResult.content.size, null, "gif" + botInlineResult, 1);
                }
            } else {
                return;
            }
            AndroidUtilities.cancelRunOnUIThread(showSheetRunnable);
            AndroidUtilities.runOnUIThread(showSheetRunnable, 2000);
        }

        if (centerImage.getLottieAnimation() != null) {
            centerImage.getLottieAnimation().setCurrentFrame(0);
        }
        if (drawEffect && effectImage.getLottieAnimation() != null) {
            effectImage.getLottieAnimation().setCurrentFrame(0);
        }

        currentContentType = contentType;
        currentDocument = document;
        importingSticker = sticker;
        currentQuery = query;
        inlineResult = botInlineResult;
        parentObject = parent;
        this.resourcesProvider = resourcesProvider;
        containerView.invalidate();

        if (!isVisible) {
            AndroidUtilities.lockOrientation(parentActivity);
            try {
                if (windowView.getParent() != null) {
                    WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
                    wm.removeView(windowView);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
            wm.addView(windowView, windowLayoutParams);
            isVisible = true;
            showProgress = 0.0f;
            lastTouchY = -10000;
            currentMoveYProgress = 0.0f;
            finalMoveY = 0;
            currentMoveY = 0;
            moveY = 0;
            lastUpdateTime = System.currentTimeMillis();
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, 8);
        }
    }

    public boolean isVisible() {
        return isVisible;
    }

    public void closeWithMenu() {
        menuVisible = false;
        if (popupWindow != null) {
            popupWindow.dismiss();
            popupWindow = null;
        }
        close();
    }

    public void close() {
        if (parentActivity == null || menuVisible) {
            return;
        }
        AndroidUtilities.cancelRunOnUIThread(showSheetRunnable);
        showProgress = 1.0f;
        lastUpdateTime = System.currentTimeMillis();
        containerView.invalidate();
        currentDocument = null;
        currentStickerSet = null;
        currentQuery = null;
        delegate = null;
        isVisible = false;
        if (unlockPremiumView != null) {
            unlockPremiumView.animate().alpha(0).translationY(AndroidUtilities.dp(56)).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        }
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, 8);
    }

    public void destroy() {
        isVisible = false;
        delegate = null;
        currentDocument = null;
        currentQuery = null;
        currentStickerSet = null;
        if (parentActivity == null || windowView == null) {
            return;
        }
        if (blurrBitmap != null) {
            blurrBitmap.recycle();
            blurrBitmap = null;
        }
        blurProgress = 0f;
        menuVisible = false;
        try {
            if (windowView.getParent() != null) {
                WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
                wm.removeViewImmediate(windowView);
            }
            windowView = null;
        } catch (Exception e) {
            FileLog.e(e);
        }
        Instance = null;
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, 8);
    }

    private float rubberYPoisition(float offset, float factor) {
        float delta = Math.abs(offset);
        return (-((1.0f - (1.0f / ((delta * 0.55f / factor) + 1.0f))) * factor)) * (offset < 0.0f ? 1.0f : -1.0f);
    }

    @SuppressLint("DrawAllocation")
    private void onDraw(Canvas canvas) {
        if (containerView == null || backgroundDrawable == null) {
            return;
        }

        if (menuVisible && blurrBitmap == null) {
            prepareBlurBitmap();
        }

        if (blurrBitmap != null) {
            if (menuVisible && blurProgress != 1f) {
                blurProgress += 16 / 120f;
                if (blurProgress > 1f) {
                    blurProgress = 1f;
                }
                containerView.invalidate();
            } else if (!menuVisible && blurProgress != 0f) {
                blurProgress -= 16 / 120f;
                if (blurProgress < 0f) {
                    blurProgress = 0f;
                }
                containerView.invalidate();
            }

            if (blurProgress != 0 && blurrBitmap != null) {
                paint.setAlpha((int) (blurProgress * 255));
                canvas.save();
                canvas.scale(12f, 12f);
                canvas.drawBitmap(blurrBitmap, 0, 0, paint);
                canvas.restore();
            }
        }

        backgroundDrawable.setAlpha((int) (180 * showProgress));
        backgroundDrawable.setBounds(0, 0, containerView.getWidth(), containerView.getHeight());
        backgroundDrawable.draw(canvas);

        canvas.save();
        int size;
        int insets = 0;
        int top;
        if (Build.VERSION.SDK_INT >= 21 && lastInsets != null) {
            insets = lastInsets.getStableInsetBottom() + lastInsets.getStableInsetTop();
            top = lastInsets.getStableInsetTop();
        } else {
            top = AndroidUtilities.statusBarHeight;
        }

        if (currentContentType == CONTENT_TYPE_GIF) {
            size = Math.min(containerView.getWidth(), containerView.getHeight() - insets) - AndroidUtilities.dp(40f);
        } else {
            if (drawEffect) {
                size = (int) (Math.min(containerView.getWidth(), containerView.getHeight() - insets) - AndroidUtilities.dpf2(40f));
            } else {
                size = (int) (Math.min(containerView.getWidth(), containerView.getHeight() - insets) / 1.8f);
            }
        }
        float topOffset = Math.max(size / 2 + top + (stickerEmojiLayout != null ? AndroidUtilities.dp(40) : 0), (containerView.getHeight() - insets - keyboardHeight) / 2);
        if (drawEffect) {
            topOffset += AndroidUtilities.dp(40);
        }
        canvas.translate(containerView.getWidth() / 2, moveY + topOffset);
        float scale = 0.8f * showProgress / 0.8f;
        size = (int) (size * scale);

        if (drawEffect) {
            float smallImageSize = size * 0.6669f;
            float padding = size * 0.0546875f;
            centerImage.setAlpha(showProgress);
            centerImage.setImageCoords(size - smallImageSize - size / 2f - padding, (size - smallImageSize) / 2f - size / 2f, smallImageSize, smallImageSize);
            centerImage.draw(canvas);

            effectImage.setAlpha(showProgress);
            effectImage.setImageCoords(-size / 2f, -size / 2f, size, size);
            effectImage.draw(canvas);
        } else {
            centerImage.setAlpha(showProgress);
            centerImage.setImageCoords(-size / 2f, -size / 2f, size, size);
            centerImage.draw(canvas);
        }

        if (currentContentType == CONTENT_TYPE_GIF && slideUpDrawable != null) {
            int w = slideUpDrawable.getIntrinsicWidth();
            int h = slideUpDrawable.getIntrinsicHeight();
            int y = (int) (centerImage.getDrawRegion().top - AndroidUtilities.dp(17 + 6 * (currentMoveY / (float) AndroidUtilities.dp(60))));
            slideUpDrawable.setAlpha((int) (255 * (1.0f - currentMoveYProgress)));
            slideUpDrawable.setBounds(-w / 2, -h + y, w / 2, y);
            slideUpDrawable.draw(canvas);
        }
        if (stickerEmojiLayout != null) {
            if (drawEffect) {
                canvas.translate(-AndroidUtilities.dp(250), -effectImage.getImageHeight() / 2 - AndroidUtilities.dp(30));
            } else {
                canvas.translate(-AndroidUtilities.dp(250), -centerImage.getImageHeight() / 2 - AndroidUtilities.dp(30));
            }
            textPaint.setAlpha((int) (0xFF * showProgress));
            stickerEmojiLayout.draw(canvas);
        }
        canvas.restore();
        if (isVisible) {
            if (showProgress != 1) {
                long newTime = System.currentTimeMillis();
                long dt = newTime - lastUpdateTime;
                lastUpdateTime = newTime;
                showProgress += dt / 120.0f;
                containerView.invalidate();
                if (showProgress > 1.0f) {
                    showProgress = 1.0f;
                }
            }
        } else if (showProgress != 0) {
            long newTime = System.currentTimeMillis();
            long dt = newTime - lastUpdateTime;
            lastUpdateTime = newTime;
            showProgress -= dt / 120.0f;
            containerView.invalidate();
            if (showProgress < 0.0f) {
                showProgress = 0.0f;
            }
            if (showProgress == 0) {
                centerImage.setImageBitmap((Drawable) null);
                AndroidUtilities.unlockOrientation(parentActivity);
                AndroidUtilities.runOnUIThread(() -> centerImage.setImageBitmap((Bitmap) null));
                if (blurrBitmap != null) {
                    blurrBitmap.recycle();
                    blurrBitmap = null;
                }
                AndroidUtilities.updateViewVisibilityAnimated(unlockPremiumView, false, 1f, false);
                blurProgress = 0f;
                try {
                    if (windowView.getParent() != null) {
                        WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
                        wm.removeView(windowView);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }
    }

    private int getThemedColor(String key) {
        Integer color = resourcesProvider != null ? resourcesProvider.getColor(key) : null;
        return color != null ? color : Theme.getColor(key);
    }

    private void prepareBlurBitmap() {
        if (parentActivity == null) {
            return;
        }
        View parentView = parentActivity.getWindow().getDecorView();
        int w = (int) (parentView.getMeasuredWidth() / 12.0f);
        int h = (int) (parentView.getMeasuredHeight() / 12.0f);
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.scale(1.0f / 12.0f, 1.0f / 12.0f);
        canvas.drawColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        parentView.draw(canvas);
        if (parentActivity instanceof LaunchActivity && ((LaunchActivity) parentActivity).getActionBarLayout().getLastFragment().getVisibleDialog() != null) {
            ((LaunchActivity) parentActivity).getActionBarLayout().getLastFragment().getVisibleDialog().getWindow().getDecorView().draw(canvas);
        }
        Utilities.stackBlurBitmap(bitmap, Math.max(10, Math.max(w, h) / 180));
        blurrBitmap = bitmap;
    }

    public boolean showMenuFor(View view) {
        if (view instanceof StickerEmojiCell) {
            Activity activity = AndroidUtilities.findActivity(view.getContext());
            if (activity == null) {
                return true;
            }
            setParentActivity(activity);
            StickerEmojiCell stickerEmojiCell = (StickerEmojiCell) view;
            open(stickerEmojiCell.getSticker(), stickerEmojiCell.getStickerPath(), MessageObject.findAnimatedEmojiEmoticon(stickerEmojiCell.getSticker(), null, currentAccount), delegate != null ? delegate.getQuery(false) : null, null, CONTENT_TYPE_STICKER, stickerEmojiCell.isRecent(), stickerEmojiCell.getParentObject(), resourcesProvider);
            AndroidUtilities.cancelRunOnUIThread(showSheetRunnable);
            AndroidUtilities.runOnUIThread(showSheetRunnable, 16);
            stickerEmojiCell.setScaled(true);
            return true;
        }
        return false;
    }
}
