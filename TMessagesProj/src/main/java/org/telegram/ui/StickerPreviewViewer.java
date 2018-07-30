/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DataQuery;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.Cells.ContextLinkCell;
import org.telegram.ui.Cells.StickerCell;
import org.telegram.ui.Cells.StickerEmojiCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

public class StickerPreviewViewer {

    private class FrameLayoutDrawer extends FrameLayout {
        public FrameLayoutDrawer(Context context) {
            super(context);
            setWillNotDraw(false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            StickerPreviewViewer.this.onDraw(canvas);
        }
    }

    public interface StickerPreviewViewerDelegate {
        void sendSticker(TLRPC.Document sticker);
        void openSet(TLRPC.InputStickerSet set);
        boolean needSend();
    }

    private static TextPaint textPaint;

    private int startX;
    private int startY;
    private View currentStickerPreviewCell;
    private Runnable openStickerPreviewRunnable;
    private Dialog visibleDialog;
    private StickerPreviewViewerDelegate delegate;

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
    private Runnable showSheetRunnable = new Runnable() {
        @Override
        public void run() {
            if (parentActivity == null || currentSet == null) {
                return;
            }
            final boolean inFavs = DataQuery.getInstance(currentAccount).isStickerInFavorites(currentSticker);
            BottomSheet.Builder builder = new BottomSheet.Builder(parentActivity);
            ArrayList<CharSequence> items = new ArrayList<>();
            final ArrayList<Integer> actions = new ArrayList<>();
            ArrayList<Integer> icons = new ArrayList<>();
            if (delegate != null) {
                if (delegate.needSend()) {
                    items.add(LocaleController.getString("SendStickerPreview", R.string.SendStickerPreview));
                    icons.add(R.drawable.stickers_send);
                    actions.add(0);
                }
                items.add(LocaleController.formatString("ViewPackPreview", R.string.ViewPackPreview));
                icons.add(R.drawable.stickers_pack);
                actions.add(1);
            }
            if (!MessageObject.isMaskDocument(currentSticker) && (inFavs || DataQuery.getInstance(currentAccount).canAddStickerToFavorites())) {
                items.add(inFavs ? LocaleController.getString("DeleteFromFavorites", R.string.DeleteFromFavorites) : LocaleController.getString("AddToFavorites", R.string.AddToFavorites));
                icons.add(inFavs ? R.drawable.stickers_unfavorite : R.drawable.stickers_favorite);
                actions.add(2);
            }
            if (items.isEmpty()) {
                return;
            }
            int[] ic = new int[icons.size()];
            for (int a = 0; a < icons.size(); a++) {
                ic[a] = icons.get(a);
            }
            builder.setItems(items.toArray(new CharSequence[items.size()]), ic, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, final int which) {
                    if (parentActivity == null) {
                        return;
                    }
                    if (actions.get(which) == 0) {
                        if (delegate != null) {
                            delegate.sendSticker(currentSticker);
                        }
                    } else if (actions.get(which) == 1) {
                        if (delegate != null) {
                            delegate.openSet(currentSet);
                        }
                    } else if (actions.get(which) == 2) {
                        DataQuery.getInstance(currentAccount).addRecentSticker(DataQuery.TYPE_FAVE, currentSticker, (int) (System.currentTimeMillis() / 1000), inFavs);
                    }
                }
            });
            visibleDialog = builder.create();
            visibleDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    visibleDialog = null;
                    close();
                }
            });
            visibleDialog.show();
            containerView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
    };

    private TLRPC.Document currentSticker;
    private TLRPC.InputStickerSet currentSet;

    @SuppressLint("StaticFieldLeak")
    private static volatile StickerPreviewViewer Instance = null;
    public static StickerPreviewViewer getInstance() {
        StickerPreviewViewer localInstance = Instance;
        if (localInstance == null) {
            synchronized (PhotoViewer.class) {
                localInstance = Instance;
                if (localInstance == null) {
                    Instance = localInstance = new StickerPreviewViewer();
                }
            }
        }
        return localInstance;
    }

    public static boolean hasInstance() {
        return Instance != null;
    }

    public void reset() {
        if (openStickerPreviewRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(openStickerPreviewRunnable);
            openStickerPreviewRunnable = null;
        }
        if (currentStickerPreviewCell != null) {
            if (currentStickerPreviewCell instanceof StickerEmojiCell) {
                ((StickerEmojiCell) currentStickerPreviewCell).setScaled(false);
            } else if (currentStickerPreviewCell instanceof StickerCell) {
                ((StickerCell) currentStickerPreviewCell).setScaled(false);
            } else if (currentStickerPreviewCell instanceof ContextLinkCell) {
                ((ContextLinkCell) currentStickerPreviewCell).setScaled(false);
            }
            currentStickerPreviewCell = null;
        }
    }

    public boolean onTouch(MotionEvent event, final View listView, final int height, final Object listener, StickerPreviewViewerDelegate stickerPreviewViewerDelegate) {
        delegate = stickerPreviewViewerDelegate;
        if (openStickerPreviewRunnable != null || isVisible()) {
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL || event.getAction() == MotionEvent.ACTION_POINTER_UP) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (listView instanceof AbsListView) {
                            ((AbsListView) listView).setOnItemClickListener((AdapterView.OnItemClickListener) listener);
                        } else if (listView instanceof RecyclerListView) {
                            ((RecyclerListView) listView).setOnItemClickListener((RecyclerListView.OnItemClickListener) listener);
                        }
                    }
                }, 150);
                if (openStickerPreviewRunnable != null) {
                    AndroidUtilities.cancelRunOnUIThread(openStickerPreviewRunnable);
                    openStickerPreviewRunnable = null;
                } else if (isVisible()) {
                    close();
                    if (currentStickerPreviewCell != null) {
                        if (currentStickerPreviewCell instanceof StickerEmojiCell) {
                            ((StickerEmojiCell) currentStickerPreviewCell).setScaled(false);
                        } else if (currentStickerPreviewCell instanceof StickerCell) {
                            ((StickerCell) currentStickerPreviewCell).setScaled(false);
                        } else if (currentStickerPreviewCell instanceof ContextLinkCell) {
                            ((ContextLinkCell) currentStickerPreviewCell).setScaled(false);
                        }
                        currentStickerPreviewCell = null;
                    }
                }
            } else if (event.getAction() != MotionEvent.ACTION_DOWN) {
                if (isVisible()) {
                    if (event.getAction() == MotionEvent.ACTION_MOVE) {
                        int x = (int) event.getX();
                        int y = (int) event.getY();
                        int count = 0;
                        if (listView instanceof AbsListView) {
                            count = ((AbsListView) listView).getChildCount();
                        } else if (listView instanceof RecyclerListView) {
                            count = ((RecyclerListView) listView).getChildCount();
                        }
                        for (int a = 0; a < count; a++) {
                            View view = null;
                            if (listView instanceof AbsListView) {
                                view = ((AbsListView) listView).getChildAt(a);
                            } else if (listView instanceof RecyclerListView) {
                                view = ((RecyclerListView) listView).getChildAt(a);
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
                            boolean ok = false;
                            if (view instanceof StickerEmojiCell) {
                                ok = true;
                            } else if (view instanceof StickerCell) {
                                ok = true;
                            } else if (view instanceof ContextLinkCell) {
                                ok = ((ContextLinkCell) view).isSticker();
                            }
                            if (!ok || view == currentStickerPreviewCell) {
                                break;
                            }
                            if (currentStickerPreviewCell instanceof StickerEmojiCell) {
                                ((StickerEmojiCell) currentStickerPreviewCell).setScaled(false);
                            } else if (currentStickerPreviewCell instanceof StickerCell) {
                                ((StickerCell) currentStickerPreviewCell).setScaled(false);
                            } else if (currentStickerPreviewCell instanceof ContextLinkCell) {
                                ((ContextLinkCell) currentStickerPreviewCell).setScaled(false);
                            }
                            currentStickerPreviewCell = view;
                            setKeyboardHeight(height);
                            if (currentStickerPreviewCell instanceof StickerEmojiCell) {
                                open(((StickerEmojiCell) currentStickerPreviewCell).getSticker(), ((StickerEmojiCell) currentStickerPreviewCell).isRecent());
                                ((StickerEmojiCell) currentStickerPreviewCell).setScaled(true);
                            } else if (currentStickerPreviewCell instanceof StickerCell) {
                                open(((StickerCell) currentStickerPreviewCell).getSticker(), false);
                                ((StickerCell) currentStickerPreviewCell).setScaled(true);
                            } else if (currentStickerPreviewCell instanceof ContextLinkCell) {
                                open(((ContextLinkCell) currentStickerPreviewCell).getDocument(), false);
                                ((ContextLinkCell) currentStickerPreviewCell).setScaled(true);
                            }
                            return true;
                        }
                    }
                    return true;
                } else if (openStickerPreviewRunnable != null) {
                    if (event.getAction() == MotionEvent.ACTION_MOVE) {
                        if (Math.hypot(startX - event.getX(), startY - event.getY()) > AndroidUtilities.dp(10)) {
                            AndroidUtilities.cancelRunOnUIThread(openStickerPreviewRunnable);
                            openStickerPreviewRunnable = null;
                        }
                    } else {
                        AndroidUtilities.cancelRunOnUIThread(openStickerPreviewRunnable);
                        openStickerPreviewRunnable = null;
                    }
                }
            }
        }
        return false;
    }

    public boolean onInterceptTouchEvent(MotionEvent event, final View listView, final int height, StickerPreviewViewerDelegate stickerPreviewViewerDelegate) {
        delegate = stickerPreviewViewerDelegate;
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            int x = (int) event.getX();
            int y = (int) event.getY();
            int count = 0;
            if (listView instanceof AbsListView) {
                count = ((AbsListView) listView).getChildCount();
            } else if (listView instanceof RecyclerListView) {
                count = ((RecyclerListView) listView).getChildCount();
            }
            for (int a = 0; a < count; a++) {
                View view = null;
                if (listView instanceof AbsListView) {
                    view = ((AbsListView) listView).getChildAt(a);
                } else if (listView instanceof RecyclerListView) {
                    view = ((RecyclerListView) listView).getChildAt(a);
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
                boolean ok = false;
                if (view instanceof StickerEmojiCell) {
                    ok = ((StickerEmojiCell) view).showingBitmap();
                } else if (view instanceof StickerCell) {
                    ok = ((StickerCell) view).showingBitmap();
                } else if (view instanceof ContextLinkCell) {
                    ContextLinkCell cell = (ContextLinkCell) view;
                    ok = cell.isSticker() && cell.showingBitmap();
                }
                if (!ok) {
                    return false;
                }
                startX = x;
                startY = y;
                currentStickerPreviewCell = view;
                openStickerPreviewRunnable = new Runnable() {
                    @Override
                    public void run() {
                        if (openStickerPreviewRunnable == null) {
                            return;
                        }
                        if (listView instanceof AbsListView) {
                            ((AbsListView) listView).setOnItemClickListener(null);
                            ((AbsListView) listView).requestDisallowInterceptTouchEvent(true);
                        } else if (listView instanceof RecyclerListView) {
                            ((RecyclerListView) listView).setOnItemClickListener((RecyclerListView.OnItemClickListener) null);
                            ((RecyclerListView) listView).requestDisallowInterceptTouchEvent(true);
                        }
                        openStickerPreviewRunnable = null;
                        setParentActivity((Activity) listView.getContext());
                        setKeyboardHeight(height);
                        if (currentStickerPreviewCell instanceof StickerEmojiCell) {
                            open(((StickerEmojiCell) currentStickerPreviewCell).getSticker(), ((StickerEmojiCell) currentStickerPreviewCell).isRecent());
                            ((StickerEmojiCell) currentStickerPreviewCell).setScaled(true);
                        } else if (currentStickerPreviewCell instanceof StickerCell) {
                            open(((StickerCell) currentStickerPreviewCell).getSticker(), false);
                            ((StickerCell) currentStickerPreviewCell).setScaled(true);
                        } else if (currentStickerPreviewCell instanceof ContextLinkCell) {
                            open(((ContextLinkCell) currentStickerPreviewCell).getDocument(), false);
                            ((ContextLinkCell) currentStickerPreviewCell).setScaled(true);
                        }
                    }
                };
                AndroidUtilities.runOnUIThread(openStickerPreviewRunnable, 200);
                return true;
            }
        }
        return false;
    }

    public void setDelegate(StickerPreviewViewerDelegate stickerPreviewViewerDelegate) {
        delegate = stickerPreviewViewerDelegate;
    }

    public void setParentActivity(Activity activity) {
        currentAccount = UserConfig.selectedAccount;
        centerImage.setCurrentAccount(currentAccount);
        if (parentActivity == activity) {
            return;
        }
        parentActivity = activity;

        windowView = new FrameLayout(activity);
        windowView.setFocusable(true);
        windowView.setFocusableInTouchMode(true);
        if (Build.VERSION.SDK_INT >= 23) {
            windowView.setFitsSystemWindows(true);
        }

        containerView = new FrameLayoutDrawer(activity);
        containerView.setFocusable(false);
        windowView.addView(containerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        containerView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_POINTER_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    close();
                }
                return true;
            }
        });

        windowLayoutParams = new WindowManager.LayoutParams();
        windowLayoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        windowLayoutParams.format = PixelFormat.TRANSLUCENT;
        windowLayoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        windowLayoutParams.gravity = Gravity.TOP;
        windowLayoutParams.type = WindowManager.LayoutParams.LAST_APPLICATION_WINDOW;
        if (Build.VERSION.SDK_INT >= 21) {
            windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
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

    public void open(TLRPC.Document sticker, boolean isRecent) {
        if (parentActivity == null || sticker == null) {
            return;
        }
        if (textPaint == null) {
            textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setTextSize(AndroidUtilities.dp(24));
        }

        TLRPC.InputStickerSet newSet = null;
        for (int a = 0; a < sticker.attributes.size(); a++) {
            TLRPC.DocumentAttribute attribute = sticker.attributes.get(a);
            if (attribute instanceof TLRPC.TL_documentAttributeSticker && attribute.stickerset != null) {
                newSet = attribute.stickerset;
                break;
            }
        }
        //&& (currentSet == null || currentSet.id != newSet.id)
        if (newSet != null) {
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
        currentSet = newSet;
        centerImage.setImage(sticker, null, sticker != null && sticker.thumb != null ? sticker.thumb.location : null, null, "webp", 1);
        stickerEmojiLayout = null;
        for (int a = 0; a < sticker.attributes.size(); a++) {
            TLRPC.DocumentAttribute attribute = sticker.attributes.get(a);
            if (attribute instanceof TLRPC.TL_documentAttributeSticker) {
                if (!TextUtils.isEmpty(attribute.alt)) {
                    CharSequence emoji = Emoji.replaceEmoji(attribute.alt, textPaint.getFontMetricsInt(), AndroidUtilities.dp(24), false);
                    stickerEmojiLayout = new StaticLayout(emoji, textPaint, AndroidUtilities.dp(100), Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
                    break;
                }
            }
        }

        currentSticker = sticker;
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
            lastUpdateTime = System.currentTimeMillis();
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
        currentSticker = null;
        currentSet = null;
        delegate = null;
        isVisible = false;
    }

    public void destroy() {
        isVisible = false;
        delegate = null;
        currentSticker = null;
        currentSet = null;
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
        int size = (int) (Math.min(containerView.getWidth(), containerView.getHeight()) / 1.8f);
        canvas.translate(containerView.getWidth() / 2, Math.max(size / 2 + AndroidUtilities.statusBarHeight + (stickerEmojiLayout!=null ? AndroidUtilities.dp(40) : 0), (containerView.getHeight() - keyboardHeight) / 2));
        Bitmap bitmap = centerImage.getBitmap();
        if (bitmap != null) {
            float scale = 0.8f * showProgress / 0.8f;
            size = (int) (size * scale);
            centerImage.setAlpha(showProgress);
            centerImage.setImageCoords(-size / 2, -size / 2, size, size);
            centerImage.draw(canvas);
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
                AndroidUtilities.unlockOrientation(parentActivity);
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        centerImage.setImageBitmap((Bitmap) null);
                    }
                });
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
}
