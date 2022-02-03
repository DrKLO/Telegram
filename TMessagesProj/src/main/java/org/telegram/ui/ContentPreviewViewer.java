/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
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
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.WebFile;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ContextLinkCell;
import org.telegram.ui.Cells.StickerCell;
import org.telegram.ui.Cells.StickerEmojiCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.EmojiView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

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
        void sendSticker(TLRPC.Document sticker, String query, Object parent, boolean notify, int scheduleDate);
        void openSet(TLRPC.InputStickerSet set, boolean clearInputField);
        boolean needSend();
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

        default void sendGif(Object gif, Object parent, boolean notify, int scheduleDate) {

        }

        default void gifAddedOrDeleted() {

        }

        default boolean needMenu() {
            return true;
        }
    }

    private final static int CONTENT_TYPE_NONE = -1;
    private final static int CONTENT_TYPE_STICKER = 0;
    private final static int CONTENT_TYPE_GIF = 1;

    private static TextPaint textPaint;

    private int startX;
    private int startY;
    private float lastTouchY;
    private float currentMoveY;
    private float moveY = 0;
    private float finalMoveY;
    private float startMoveY;
    private boolean animateY;
    private float currentMoveYProgress;
    private View currentPreviewCell;
    private boolean clearsInputField;
    private Runnable openPreviewRunnable;
    private BottomSheet visibleDialog;
    private ContentPreviewViewerDelegate delegate;

    private boolean isRecentSticker;

    private WindowInsets lastInsets;

    private int currentAccount;

    private ColorDrawable backgroundDrawable = new ColorDrawable(0x71000000);
    private Activity parentActivity;
    private WindowManager.LayoutParams windowLayoutParams;
    private FrameLayout windowView;
    private FrameLayoutDrawer containerView;
    private ImageReceiver centerImage = new ImageReceiver();
    private boolean isVisible = false;
    private float showProgress;
    private StaticLayout stickerEmojiLayout;
    private long lastUpdateTime;
    private int keyboardHeight = AndroidUtilities.dp(200);
    private Drawable slideUpDrawable;

    private Runnable showSheetRunnable = new Runnable() {
        @Override
        public void run() {
            if (parentActivity == null) {
                return;
            }
            if (currentContentType == CONTENT_TYPE_STICKER) {
                final boolean inFavs = MediaDataController.getInstance(currentAccount).isStickerInFavorites(currentDocument);
                BottomSheet.Builder builder = new BottomSheet.Builder(parentActivity, true, resourcesProvider);
                ArrayList<CharSequence> items = new ArrayList<>();
                final ArrayList<Integer> actions = new ArrayList<>();
                ArrayList<Integer> icons = new ArrayList<>();
                if (delegate != null) {
                    if (delegate.needSend() && !delegate.isInScheduleMode()) {
                        items.add(LocaleController.getString("SendStickerPreview", R.string.SendStickerPreview));
                        icons.add(R.drawable.outline_send);
                        actions.add(0);
                    }
                    if (!delegate.isInScheduleMode()) {
                        items.add(LocaleController.getString("SendWithoutSound", R.string.SendWithoutSound));
                        icons.add(R.drawable.input_notify_off);
                        actions.add(6);
                    }
                    if (delegate.canSchedule()) {
                        items.add(LocaleController.getString("Schedule", R.string.Schedule));
                        icons.add(R.drawable.msg_timer);
                        actions.add(3);
                    }
                    if (currentStickerSet != null && delegate.needOpen()) {
                        items.add(LocaleController.formatString("ViewPackPreview", R.string.ViewPackPreview));
                        icons.add(R.drawable.outline_pack);
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
                    icons.add(inFavs ? R.drawable.outline_unfave : R.drawable.outline_fave);
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
                builder.setItems(items.toArray(new CharSequence[0]), ic, (dialog, which) -> {
                    if (parentActivity == null) {
                        return;
                    }
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
                        AlertsCreator.createScheduleDatePickerDialog(parentActivity, stickerPreviewViewerDelegate.getDialogId(), (notify, scheduleDate) -> stickerPreviewViewerDelegate.sendSticker(sticker, query, parent, notify, scheduleDate));
                    } else if (actions.get(which) == 4) {
                        MediaDataController.getInstance(currentAccount).addRecentSticker(MediaDataController.TYPE_IMAGE, parentObject, currentDocument, (int) (System.currentTimeMillis() / 1000), true);
                    } else if (actions.get(which) == 5) {
                        delegate.remove(importingSticker);
                    }
                });
                builder.setDimBehind(false);
                visibleDialog = builder.create();
                visibleDialog.setOnDismissListener(dialog -> {
                    visibleDialog = null;
                    close();
                });
                visibleDialog.show();
                containerView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                if (delegate != null && delegate.needRemove()) {
                    BottomSheet.BottomSheetCell cell = visibleDialog.getItemViews().get(0);
                    cell.setTextColor(getThemedColor(Theme.key_dialogTextRed));
                    cell.setIconColor(getThemedColor(Theme.key_dialogRedIcon));
                }
            } else if (delegate != null) {
                animateY = true;
                visibleDialog = new BottomSheet(parentActivity, false) {
                    @Override
                    protected void onContainerTranslationYChanged(float translationY) {
                        if (animateY) {
                            ViewGroup container = getSheetContainer();
                            if (finalMoveY == 0) {
                                finalMoveY = 0;//-container.getMeasuredHeight() / 2;
                                startMoveY = moveY;
                            }
                            currentMoveYProgress = 1.0f - Math.min(1.0f, translationY / containerView.getMeasuredHeight());
                            moveY = startMoveY + (finalMoveY - startMoveY) * currentMoveYProgress;
                            ContentPreviewViewer.this.containerView.invalidate();
                            if (currentMoveYProgress == 1.0f) {
                                animateY = false;
                            }
                        }
                    }
                };
                ArrayList<CharSequence> items = new ArrayList<>();
                final ArrayList<Integer> actions = new ArrayList<>();
                ArrayList<Integer> icons = new ArrayList<>();

                if (delegate.needSend() && !delegate.isInScheduleMode()) {
                    items.add(LocaleController.getString("SendGifPreview", R.string.SendGifPreview));
                    icons.add(R.drawable.outline_send);
                    actions.add(0);
                }
                if (delegate.canSchedule()) {
                    items.add(LocaleController.getString("Schedule", R.string.Schedule));
                    icons.add(R.drawable.msg_timer);
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
                        icons.add(R.drawable.outline_add_gif);
                        actions.add(2);
                    }
                } else {
                    canDelete = false;
                }

                int[] ic = new int[icons.size()];
                for (int a = 0; a < icons.size(); a++) {
                    ic[a] = icons.get(a);
                }
                visibleDialog.setItems(items.toArray(new CharSequence[0]), ic, (dialog, which) -> {
                    if (parentActivity == null) {
                        return;
                    }
                    if (actions.get(which) == 0) {
                        delegate.sendGif(currentDocument != null ? currentDocument : inlineResult, parentObject, true, 0);
                    } else if (actions.get(which) == 1) {
                        MediaDataController.getInstance(currentAccount).removeRecentGif(currentDocument);
                        delegate.gifAddedOrDeleted();
                    } else if (actions.get(which) == 2) {
                        MediaDataController.getInstance(currentAccount).addRecentGif(currentDocument, (int) (System.currentTimeMillis() / 1000));
                        MessagesController.getInstance(currentAccount).saveGif("gif", currentDocument);
                        delegate.gifAddedOrDeleted();
                    } else if (actions.get(which) == 3) {
                        TLRPC.Document document = currentDocument;
                        TLRPC.BotInlineResult result = inlineResult;
                        Object parent = parentObject;
                        ContentPreviewViewerDelegate stickerPreviewViewerDelegate = delegate;
                        AlertsCreator.createScheduleDatePickerDialog(parentActivity, stickerPreviewViewerDelegate.getDialogId(), (notify, scheduleDate) -> stickerPreviewViewerDelegate.sendGif(document != null ? document : result, parent, notify, scheduleDate), resourcesProvider);
                    }
                });
                visibleDialog.setDimBehind(false);
                visibleDialog.setOnDismissListener(dialog -> {
                    visibleDialog = null;
                    close();
                });
                visibleDialog.show();
                containerView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                if (canDelete) {
                    visibleDialog.setItemColor(items.size() - 1, getThemedColor(Theme.key_dialogTextRed2), getThemedColor(Theme.key_dialogRedIcon));
                }
            }
        }
    };

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
                            if (visibleDialog == null && showProgress == 1.0f) {
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
                            }
                            if (contentType == CONTENT_TYPE_NONE || view == currentPreviewCell) {
                                break;
                            }
                            if (currentPreviewCell instanceof StickerEmojiCell) {
                                ((StickerEmojiCell) currentPreviewCell).setScaled(false);
                            } else if (currentPreviewCell instanceof StickerCell) {
                                ((StickerCell) currentPreviewCell).setScaled(false);
                            } else if (currentPreviewCell instanceof ContextLinkCell) {
                                ((ContextLinkCell) currentPreviewCell).setScaled(false);
                            }
                            currentPreviewCell = view;
                            setKeyboardHeight(height);
                            clearsInputField = false;
                            if (currentPreviewCell instanceof StickerEmojiCell) {
                                StickerEmojiCell stickerEmojiCell = (StickerEmojiCell) currentPreviewCell;
                                open(stickerEmojiCell.getSticker(), stickerEmojiCell.getStickerPath(), stickerEmojiCell.getEmoji(), delegate != null ? delegate.getQuery(false) : null, null, contentType, stickerEmojiCell.isRecent(), stickerEmojiCell.getParentObject(), resourcesProvider);
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
                    listView.setOnItemClickListener((RecyclerListView.OnItemClickListener) null);
                    listView.requestDisallowInterceptTouchEvent(true);
                    openPreviewRunnable = null;
                    setParentActivity((Activity) listView.getContext());
                    setKeyboardHeight(height);
                    clearsInputField = false;
                    if (currentPreviewCell instanceof StickerEmojiCell) {
                        StickerEmojiCell stickerEmojiCell = (StickerEmojiCell) currentPreviewCell;
                        open(stickerEmojiCell.getSticker(), stickerEmojiCell.getStickerPath(), stickerEmojiCell.getEmoji(), delegate != null ? delegate.getQuery(false) : null, null, contentTypeFinal, stickerEmojiCell.isRecent(), stickerEmojiCell.getParentObject(), resourcesProvider);
                        stickerEmojiCell.setScaled(true);
                    } else if (currentPreviewCell instanceof StickerCell) {
                        StickerCell stickerCell = (StickerCell) currentPreviewCell;
                        open(stickerCell.getSticker(), null, null, delegate != null ? delegate.getQuery(false) : null, null, contentTypeFinal, false, stickerCell.getParentObject(), resourcesProvider);
                        stickerCell.setScaled(true);
                        clearsInputField = stickerCell.isClearsInputField();
                    } else if (currentPreviewCell instanceof ContextLinkCell) {
                        ContextLinkCell contextLinkCell = (ContextLinkCell) currentPreviewCell;
                        open(contextLinkCell.getDocument(), null, null, delegate != null ? delegate.getQuery(true) : null, contextLinkCell.getBotInlineResult(), contentTypeFinal, false, contextLinkCell.getBotInlineResult() != null ? contextLinkCell.getInlineBot() : contextLinkCell.getParentObject(), resourcesProvider);
                        if (contentTypeFinal != CONTENT_TYPE_GIF) {
                            contextLinkCell.setScaled(true);
                        }
                    }
                    currentPreviewCell.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
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
            }

            @Override
            protected void onDetachedFromWindow() {
                super.onDetachedFromWindow();
                centerImage.onDetachedFromWindow();
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
        if (contentType == CONTENT_TYPE_STICKER) {
            if (document == null && sticker == null) {
                return;
            }
            if (textPaint == null) {
                textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                textPaint.setTextSize(AndroidUtilities.dp(24));
            }

            if (document != null) {
                TLRPC.InputStickerSet newSet = null;
                for (int a = 0; a < document.attributes.size(); a++) {
                    TLRPC.DocumentAttribute attribute = document.attributes.get(a);
                    if (attribute instanceof TLRPC.TL_documentAttributeSticker && attribute.stickerset != null) {
                        newSet = attribute.stickerset;
                        break;
                    }
                }
                if (newSet != null && (delegate == null || delegate.needMenu())) {
                    try {
                        if (visibleDialog != null) {
                            visibleDialog.setOnDismissListener(null);
                            visibleDialog.dismiss();
                            visibleDialog = null;
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    AndroidUtilities.cancelRunOnUIThread(showSheetRunnable);
                    AndroidUtilities.runOnUIThread(showSheetRunnable, 1300);
                }
                currentStickerSet = newSet;
                TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90);
                centerImage.setImage(ImageLocation.getForDocument(document), null, ImageLocation.getForDocument(thumb, document), null, "webp", currentStickerSet, 1);
                for (int a = 0; a < document.attributes.size(); a++) {
                    TLRPC.DocumentAttribute attribute = document.attributes.get(a);
                    if (attribute instanceof TLRPC.TL_documentAttributeSticker) {
                        if (!TextUtils.isEmpty(attribute.alt)) {
                            CharSequence emoji = Emoji.replaceEmoji(attribute.alt, textPaint.getFontMetricsInt(), AndroidUtilities.dp(24), false);
                            stickerEmojiLayout = new StaticLayout(emoji, textPaint, AndroidUtilities.dp(100), Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
                            break;
                        }
                    }
                }
            } else if (sticker != null) {
                centerImage.setImage(sticker.path, null, null, sticker.animated ? "tgs" : null, 0);
                if (emojiPath != null) {
                    CharSequence emoji = Emoji.replaceEmoji(emojiPath, textPaint.getFontMetricsInt(), AndroidUtilities.dp(24), false);
                    stickerEmojiLayout = new StaticLayout(emoji, textPaint, AndroidUtilities.dp(100), Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
                }
                if (delegate.needMenu()) {
                    try {
                        if (visibleDialog != null) {
                            visibleDialog.setOnDismissListener(null);
                            visibleDialog.dismiss();
                            visibleDialog = null;
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
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

    public void close() {
        if (parentActivity == null || visibleDialog != null) {
            return;
        }
        AndroidUtilities.cancelRunOnUIThread(showSheetRunnable);
        showProgress = 1.0f;
        lastUpdateTime = System.currentTimeMillis();
        containerView.invalidate();
        try {
            if (visibleDialog != null) {
                visibleDialog.dismiss();
                visibleDialog = null;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        currentDocument = null;
        currentStickerSet = null;
        currentQuery = null;
        delegate = null;
        isVisible = false;
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, 8);
    }

    public void destroy() {
        isVisible = false;
        delegate = null;
        currentDocument = null;
        currentQuery = null;
        currentStickerSet = null;
        try {
            if (visibleDialog != null) {
                visibleDialog.dismiss();
                visibleDialog = null;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (parentActivity == null || windowView == null) {
            return;
        }
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
            size = (int) (Math.min(containerView.getWidth(), containerView.getHeight() - insets) / 1.8f);
        }

        canvas.translate(containerView.getWidth() / 2, moveY + Math.max(size / 2 + top + (stickerEmojiLayout != null ? AndroidUtilities.dp(40) : 0), (containerView.getHeight() - insets - keyboardHeight) / 2));
        float scale = 0.8f * showProgress / 0.8f;
        size = (int) (size * scale);
        centerImage.setAlpha(showProgress);
        centerImage.setImageCoords(-size / 2, -size / 2, size, size);
        centerImage.draw(canvas);

        if (currentContentType == CONTENT_TYPE_GIF && slideUpDrawable != null) {
            int w = slideUpDrawable.getIntrinsicWidth();
            int h = slideUpDrawable.getIntrinsicHeight();
            int y = (int) (centerImage.getDrawRegion().top - AndroidUtilities.dp(17 + 6 * (currentMoveY / (float) AndroidUtilities.dp(60))));
            slideUpDrawable.setAlpha((int) (255 * (1.0f - currentMoveYProgress)));
            slideUpDrawable.setBounds(-w / 2, -h + y, w / 2, y);
            slideUpDrawable.draw(canvas);
        }
        if (stickerEmojiLayout != null) {
            canvas.translate(-AndroidUtilities.dp(50), -centerImage.getImageHeight() / 2 - AndroidUtilities.dp(30));
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

}
