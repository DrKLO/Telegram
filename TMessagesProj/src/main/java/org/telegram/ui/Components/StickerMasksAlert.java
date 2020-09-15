package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.LongSparseArray;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.math.MathUtils;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.LinearSmoothScrollerCustom;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.EmptyCell;
import org.telegram.ui.Cells.StickerEmojiCell;
import org.telegram.ui.Cells.StickerSetNameCell;
import org.telegram.ui.ContentPreviewViewer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

@SuppressWarnings("unchecked")
public class StickerMasksAlert extends BottomSheet implements NotificationCenter.NotificationCenterDelegate {

    private Drawable shadowDrawable;

    private int scrollOffsetY;

    private StickerMasksAlertDelegate delegate;

    private FrameLayout bottomTabContainer;
    private View shadowLine;
    private AnimatorSet bottomTabContainerAnimation;

    private StickersGridAdapter stickersGridAdapter;
    private StickersSearchGridAdapter stickersSearchGridAdapter;
    private RecyclerListView.OnItemClickListener stickersOnItemClickListener;
    private ScrollSlidingTabStrip stickersTab;
    private RecyclerListView gridView;
    private boolean emojiSmoothScrolling;
    private RecyclerAnimationScrollHelper scrollHelper;
    private boolean ignoreStickersScroll;
    private GridLayoutManager stickersLayoutManager;
    private SearchField stickersSearchField;

    private String[] lastSearchKeyboardLanguage;

    private Drawable[] stickerIcons;

    private int searchFieldHeight;

    private int currentAccount = UserConfig.selectedAccount;

    private ArrayList<TLRPC.TL_messages_stickerSet>[] stickerSets = new ArrayList[]{new ArrayList<>(), new ArrayList<>(), new ArrayList<>()};
    private ArrayList<TLRPC.Document>[] recentStickers = new ArrayList[]{new ArrayList<>(), new ArrayList<>(), new ArrayList<>()};
    
    private ArrayList<TLRPC.Document> favouriteStickers = new ArrayList<>();

    private int typeIndex(int type) {
        switch (type) {
            case MediaDataController.TYPE_IMAGE:
                return 0;
            case MediaDataController.TYPE_MASK:
                return 1;
            case MediaDataController.TYPE_EMOJIPACKS:
            default:
                return 2;
        }
    }

    private int stickersTabOffset;
    private int recentTabBum = -2;
    private int favTabBum = -2;

    private int lastNotifyWidth;
    private int lastNotifyHeight;
    private int lastNotifyHeight2;

    private ImageView emojiButton;
    private ImageView stickersButton;
    private ImageView masksButton;

    private int currentType;

    private static boolean IsFullRecent() {
        return org.telegram.messenger.MessagesController
            .getGlobalMainSettings().getBoolean("fullRecentStickers", false);
    }

    public interface StickerMasksAlertDelegate {
        void onStickerSelected(Object parentObject, TLRPC.Document sticker);
    }

    private ContentPreviewViewer.ContentPreviewViewerDelegate contentPreviewViewerDelegate = new ContentPreviewViewer.ContentPreviewViewerDelegate() {
        @Override
        public void sendSticker(TLRPC.Document sticker, String query, Object parent, boolean notify, int scheduleDate) {
            delegate.onStickerSelected(parent, sticker);
        }

        @Override
        public boolean needSend(int contentType) {
            return false;
        }

        @Override
        public boolean canSchedule() {
            return false;
        }

        @Override
        public boolean isInScheduleMode() {
            return false;
        }

        @Override
        public void openSet(TLRPC.InputStickerSet set, boolean clearsInputField) {

        }

        @Override
        public long getDialogId() {
            return 0;
        }

        @Override
        public boolean needMenu() {
            return false;
        }
    };

    private class SearchField extends FrameLayout {

        private ImageView clearSearchImageView;
        private CloseProgressDrawable2 progressDrawable;
        private EditTextBoldCursor searchEditText;
//        private View shadowView;
        private AnimatorSet shadowAnimator;

        public SearchField(Context context, int type) {
            super(context);
//
//            shadowView = new View(context);
//            shadowView.setAlpha(0.0f);
//            shadowView.setTag(1);
//            shadowView.setBackgroundColor(0x12000000);
//            addView(shadowView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.getShadowHeight(), Gravity.BOTTOM | Gravity.LEFT));

            View backgroundView = new View(context);
            backgroundView.setBackgroundColor(0xff252525);
            addView(backgroundView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, searchFieldHeight));

            View searchBackground = new View(context);
            searchBackground.setBackgroundDrawable(Theme.createRoundRectDrawable(AndroidUtilities.dp(18), 0xff363636));
            addView(searchBackground, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.LEFT | Gravity.TOP, 14, 14, 14, 0));

            ImageView searchIconImageView = new ImageView(context);
            searchIconImageView.setScaleType(ImageView.ScaleType.CENTER);
            searchIconImageView.setImageResource(R.drawable.smiles_inputsearch);
            searchIconImageView.setColorFilter(new PorterDuffColorFilter(0xff777777, PorterDuff.Mode.MULTIPLY));
            addView(searchIconImageView, LayoutHelper.createFrame(36, 36, Gravity.LEFT | Gravity.TOP, 16, 14, 0, 0));

            clearSearchImageView = new ImageView(context);
            clearSearchImageView.setScaleType(ImageView.ScaleType.CENTER);
            clearSearchImageView.setImageDrawable(progressDrawable = new CloseProgressDrawable2() {
                @Override
                public int getCurrentColor() {
                    return 0xff777777;
                }
            });
            progressDrawable.setSide(AndroidUtilities.dp(7));
            clearSearchImageView.setScaleX(0.1f);
            clearSearchImageView.setScaleY(0.1f);
            clearSearchImageView.setAlpha(0.0f);
            addView(clearSearchImageView, LayoutHelper.createFrame(36, 36, Gravity.RIGHT | Gravity.TOP, 14, 14, 14, 0));
            clearSearchImageView.setOnClickListener(v -> {
                searchEditText.setText("");
                AndroidUtilities.showKeyboard(searchEditText);
            });

            searchEditText = new EditTextBoldCursor(context) {
                @Override
                public boolean onTouchEvent(MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        searchEditText.requestFocus();
                        AndroidUtilities.showKeyboard(searchEditText);
                    }
                    return super.onTouchEvent(event);
                }
            };
            searchEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            searchEditText.setHintTextColor(0xff777777);
            searchEditText.setTextColor(0xffffffff);
            searchEditText.setBackgroundDrawable(null);
            searchEditText.setPadding(0, 0, 0, 0);
            searchEditText.setMaxLines(1);
            searchEditText.setLines(1);
            searchEditText.setSingleLine(true);
            searchEditText.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            if (type == 0) {
                searchEditText.setHint(LocaleController.getString("SearchStickersHint", R.string.SearchStickersHint));
            } else if (type == 1) {
                searchEditText.setHint(LocaleController.getString("SearchEmojiHint", R.string.SearchEmojiHint));
            } else if (type == 2) {
                searchEditText.setHint(LocaleController.getString("SearchGifsTitle", R.string.SearchGifsTitle));
            }
            searchEditText.setCursorColor(0xffffffff);
            searchEditText.setCursorSize(AndroidUtilities.dp(20));
            searchEditText.setCursorWidth(1.5f);
            addView(searchEditText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 40, Gravity.LEFT | Gravity.TOP, 16 + 38, 12, 16 + 30, 0));
            searchEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {

                }

                @Override
                public void afterTextChanged(Editable s) {
                    boolean show = searchEditText.length() > 0;
                    boolean showed = clearSearchImageView.getAlpha() != 0;
                    if (show != showed) {
                        clearSearchImageView.animate()
                                .alpha(show ? 1.0f : 0.0f)
                                .setDuration(150)
                                .scaleX(show ? 1.0f : 0.1f)
                                .scaleY(show ? 1.0f : 0.1f)
                                .start();
                    }
                    stickersSearchGridAdapter.search(searchEditText.getText().toString());
                }
            });
        }

        public void hideKeyboard() {
            AndroidUtilities.hideKeyboard(searchEditText);
        }

        private void showShadow(boolean show, boolean animated) {
//            if (show && shadowView.getTag() == null || !show && shadowView.getTag() != null) {
//                return;
//            }
//            if (shadowAnimator != null) {
//                shadowAnimator.cancel();
//                shadowAnimator = null;
//            }
//            shadowView.setTag(show ? null : 1);
//            if (animated) {
//                shadowAnimator = new AnimatorSet();
//                shadowAnimator.playTogether(ObjectAnimator.ofFloat(shadowView, View.ALPHA, show ? 1.0f : 0.0f));
//                shadowAnimator.setDuration(200);
//                shadowAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
//                shadowAnimator.addListener(new AnimatorListenerAdapter() {
//                    @Override
//                    public void onAnimationEnd(Animator animation) {
//                        shadowAnimator = null;
//                    }
//                });
//                shadowAnimator.start();
//            } else {
//                shadowView.setAlpha(show ? 1.0f : 0.0f);
//            }
        }
    }

    public StickerMasksAlert(Context context, boolean isVideo, Theme.ResourcesProvider resourcesProvider) {
        super(context, true, resourcesProvider);
        behindKeyboardColorKey = -1;
        behindKeyboardColor = 0xff252525;
        useLightStatusBar = false;
        fixNavigationBar(0xff252525);

        currentType = MediaDataController.TYPE_IMAGE;

        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.stickersDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.recentDocumentsDidLoad);
        MediaDataController.getInstance(currentAccount).loadRecents(MediaDataController.TYPE_IMAGE, false, true, false);
        MediaDataController.getInstance(currentAccount).loadRecents(MediaDataController.TYPE_MASK, false, true, false);
        MediaDataController.getInstance(currentAccount).loadRecents(MediaDataController.TYPE_FAVE, false, true, false);

        shadowDrawable = context.getResources().getDrawable(R.drawable.sheet_shadow_round).mutate();
        shadowDrawable.setColorFilter(new PorterDuffColorFilter(0xff252525, PorterDuff.Mode.MULTIPLY));

        containerView = new SizeNotifierFrameLayout(context) {

            private boolean ignoreLayout = false;
            private RectF rect = new RectF();
            private long lastUpdateTime;
            private float statusBarProgress;

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int totalHeight = MeasureSpec.getSize(heightMeasureSpec);
                if (Build.VERSION.SDK_INT >= 21 && !isFullscreen) {
                    ignoreLayout = true;
                    setPadding(backgroundPaddingLeft, AndroidUtilities.statusBarHeight, backgroundPaddingLeft, 0);
                    ignoreLayout = false;
                }
                int availableHeight = totalHeight - getPaddingTop();
                int padding;
                if (measureKeyboardHeight() > AndroidUtilities.dp(20)) {
                    padding = 0;
                    statusBarProgress = 1.0f;
                } else {
                    padding = availableHeight - (availableHeight / 5 * 3) + AndroidUtilities.dp(16);
                }
                if (gridView.getPaddingTop() != padding) {
                    ignoreLayout = true;
                    gridView.setPinnedSectionOffsetY(-padding);
                    gridView.setPadding(AndroidUtilities.dp(4), padding, AndroidUtilities.dp(4), AndroidUtilities.dp(48));
                    ignoreLayout = false;
                }
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(totalHeight, MeasureSpec.EXACTLY));
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                super.onLayout(changed, l, t, r, b);
                updateLayout(false);
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (ev.getAction() == MotionEvent.ACTION_DOWN && scrollOffsetY != 0 && ev.getY() < scrollOffsetY + AndroidUtilities.dp(12)) {
                    dismiss();
                    return true;
                }
                return super.onInterceptTouchEvent(ev);
            }

            @Override
            public boolean onTouchEvent(MotionEvent e) {
                return !isDismissed() && super.onTouchEvent(e);
            }

            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }

            @Override
            protected void onDraw(Canvas canvas) {
                int offset = AndroidUtilities.dp(13);
                int top = scrollOffsetY - backgroundPaddingTop - offset;
                if (currentSheetAnimationType == 1) {
                    top += gridView.getTranslationY();
                }
                int y = top + AndroidUtilities.dp(20);

                int height = getMeasuredHeight() + AndroidUtilities.dp(15) + backgroundPaddingTop;
                float rad = 1.0f;

                int h = AndroidUtilities.dp(12);
                if (top + backgroundPaddingTop < h) {
                    float toMove = offset + AndroidUtilities.dp(11 - 7);
                    float moveProgress = Math.min(1.0f, (h - top - backgroundPaddingTop) / toMove);
                    float availableToMove = h - toMove;

                    int diff = (int) (availableToMove * moveProgress);
                    top -= diff;
                    y -= diff;
                    height += diff;
                    rad = 1.0f - moveProgress;
                }

                if (Build.VERSION.SDK_INT >= 21) {
                    top += AndroidUtilities.statusBarHeight;
                    y += AndroidUtilities.statusBarHeight;
                }

                shadowDrawable.setBounds(0, top, getMeasuredWidth(), height);
                shadowDrawable.draw(canvas);

                if (rad != 1.0f) {
                    Theme.dialogs_onlineCirclePaint.setColor(0xff252525);
                    rect.set(backgroundPaddingLeft, backgroundPaddingTop + top, getMeasuredWidth() - backgroundPaddingLeft, backgroundPaddingTop + top + AndroidUtilities.dp(24));
                    canvas.drawRoundRect(rect, AndroidUtilities.dp(12) * rad, AndroidUtilities.dp(12) * rad, Theme.dialogs_onlineCirclePaint);
                }

                long newTime = SystemClock.elapsedRealtime();
                long dt = newTime - lastUpdateTime;
                if (dt > 18) {
                    dt = 18;
                }
                lastUpdateTime = newTime;
                if (rad > 0) {
                    float alphaProgress = 1.0f;
                    int w = AndroidUtilities.dp(36);
                    rect.set((getMeasuredWidth() - w) / 2, y, (getMeasuredWidth() + w) / 2, y + AndroidUtilities.dp(4));
                    int color = 0xff4b4b4b;
                    int alpha = Color.alpha(color);
                    Theme.dialogs_onlineCirclePaint.setColor(color);
                    Theme.dialogs_onlineCirclePaint.setAlpha((int) (alpha * alphaProgress * rad));
                    canvas.drawRoundRect(rect, AndroidUtilities.dp(2), AndroidUtilities.dp(2), Theme.dialogs_onlineCirclePaint);
                    if (statusBarProgress > 0.0f) {
                        statusBarProgress -= dt / 180.0f;
                        if (statusBarProgress < 0.0f) {
                            statusBarProgress = 0.0f;
                        } else {
                            invalidate();
                        }
                    }
                } else {
                    if (statusBarProgress < 1.0f) {
                        statusBarProgress += dt / 180.0f;
                        if (statusBarProgress > 1.0f) {
                            statusBarProgress = 1.0f;
                        } else {
                            invalidate();
                        }
                    }
                }

                int color1 = 0xff252525;
                int finalColor = Color.argb((int) (255 * statusBarProgress), (int) (Color.red(color1) * 0.8f), (int) (Color.green(color1) * 0.8f), (int) (Color.blue(color1) * 0.8f));
                Theme.dialogs_onlineCirclePaint.setColor(finalColor);
                canvas.drawRect(backgroundPaddingLeft, 0, getMeasuredWidth() - backgroundPaddingLeft, AndroidUtilities.statusBarHeight, Theme.dialogs_onlineCirclePaint);
            }
        };
        containerView.setWillNotDraw(false);
        containerView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);

        searchFieldHeight = AndroidUtilities.dp(64);

        stickerIcons = new Drawable[]{
                Theme.createEmojiIconSelectorDrawable(context, R.drawable.stickers_recent, 0xff4b4b4b, 0xff6ebaed),
                Theme.createEmojiIconSelectorDrawable(context, R.drawable.stickers_favorites, 0xff4b4b4b, 0xff6ebaed),
        };

        MediaDataController.getInstance(currentAccount).checkStickers(MediaDataController.TYPE_IMAGE);
        MediaDataController.getInstance(currentAccount).checkStickers(MediaDataController.TYPE_MASK);
        MediaDataController.getInstance(currentAccount).checkFeaturedStickers();
        gridView = new RecyclerListView(context) {

            @Override
            protected boolean allowSelectChildAtPosition(float x, float y) {
                return y >= scrollOffsetY + (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent event) {
                boolean result = ContentPreviewViewer.getInstance().onInterceptTouchEvent(event, gridView, containerView.getMeasuredHeight(), contentPreviewViewerDelegate, resourcesProvider);
                return super.onInterceptTouchEvent(event) || result;
            }

            SparseArray<ArrayList<ImageViewEmoji>> viewsGroupedByLines = new SparseArray<>();
            ArrayList<DrawingInBackgroundLine> lineDrawables = new ArrayList<>();
            ArrayList<DrawingInBackgroundLine> lineDrawablesTmp = new ArrayList<>();
            ArrayList<ArrayList<ImageViewEmoji>> unusedArrays = new ArrayList<>();
            ArrayList<DrawingInBackgroundLine> unusedLineDrawables = new ArrayList<>();

            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);

                for (int i = 0; i < viewsGroupedByLines.size(); i++) {
                    ArrayList<ImageViewEmoji> arrayList = viewsGroupedByLines.valueAt(i);
                    arrayList.clear();
                    unusedArrays.add(arrayList);
                }
                viewsGroupedByLines.clear();
                for (int i = 0; i < getChildCount(); ++i) {
                    View child = getChildAt(i);
                    if (child instanceof ImageViewEmoji) {
                        int top = child.getTop() + (int) child.getTranslationY();
                        ArrayList<ImageViewEmoji> arrayList = viewsGroupedByLines.get(top);
                        if (arrayList == null) {
                            if (!unusedArrays.isEmpty()) {
                                arrayList = unusedArrays.remove(unusedArrays.size() - 1);
                            } else {
                                arrayList = new ArrayList<>();
                            }
                            viewsGroupedByLines.put(top, arrayList);
                        }
                        arrayList.add((ImageViewEmoji) child);
                    }
                }

                lineDrawablesTmp.clear();
                lineDrawablesTmp.addAll(lineDrawables);
                lineDrawables.clear();

                long time = System.currentTimeMillis();
                for (int i = 0; i < viewsGroupedByLines.size(); i++) {
                    ArrayList<ImageViewEmoji> arrayList = viewsGroupedByLines.valueAt(i);
                    ImageViewEmoji firstView = arrayList.get(0);
                    int position = firstView.position;
                    DrawingInBackgroundLine drawable = null;
                    for (int k = 0; k < lineDrawablesTmp.size(); k++) {
                        if (lineDrawablesTmp.get(k).position == position) {
                            drawable = lineDrawablesTmp.get(k);
                            lineDrawablesTmp.remove(k);
                            break;
                        }
                    }
                    if (drawable == null) {
                        if (!unusedLineDrawables.isEmpty()) {
                            drawable = unusedLineDrawables.remove(unusedLineDrawables.size() - 1);
                        } else {
                            drawable = new DrawingInBackgroundLine();
                        }
                        drawable.position = position;
                        drawable.onAttachToWindow();
                    }
                    lineDrawables.add(drawable);
                    drawable.imageViewEmojis = arrayList;
                    canvas.save();
                    canvas.translate(firstView.getLeft(), firstView.getY() + firstView.getPaddingTop());
                    drawable.startOffset = firstView.getLeft();
                    int w = getMeasuredWidth() - firstView.getLeft() * 2;
                    int h = firstView.getMeasuredHeight() - firstView.getPaddingBottom();
                    if (w > 0 && h > 0) {
                        drawable.draw(canvas, time, w, h, 1f);
                    }
                    canvas.restore();
                }

                for (int i = 0; i < lineDrawablesTmp.size(); i++) {
                    if (unusedLineDrawables.size() < 3) {
                        unusedLineDrawables.add(lineDrawablesTmp.get(i));
                        lineDrawablesTmp.get(i).imageViewEmojis = null;
                        lineDrawablesTmp.get(i).reset();
                    } else {
                        lineDrawablesTmp.get(i).onDetachFromWindow();
                    }
                }
                lineDrawablesTmp.clear();
            }
        };
        gridView.setLayoutManager(stickersLayoutManager = new GridLayoutManager(context, 5) {
            @Override
            public void smoothScrollToPosition(RecyclerView recyclerView, RecyclerView.State state, int position) {
                LinearSmoothScroller linearSmoothScroller = new LinearSmoothScroller(recyclerView.getContext()) {
                    @Override
                    public int calculateDyToMakeVisible(View view, int snapPreference) {
                        int dy = super.calculateDyToMakeVisible(view, snapPreference);
                        dy -= (gridView.getPaddingTop() - AndroidUtilities.dp(7));
                        return dy;
                    }

                    @Override
                    protected int calculateTimeForDeceleration(int dx) {
                        return super.calculateTimeForDeceleration(dx) * 4;
                    }
                };
                linearSmoothScroller.setTargetPosition(position);
                startSmoothScroll(linearSmoothScroller);
            }
        });
        stickersLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (gridView.getAdapter() == stickersGridAdapter) {
                    if (position == 0) {
                        return stickersGridAdapter.stickersPerRow;
                    }
                    if (position != stickersGridAdapter.totalItems) {
                        Object object = stickersGridAdapter.cache.get(position);
                        if (object == null || stickersGridAdapter.cache.get(position) instanceof TLRPC.Document) {
                            return 1;
                        }
                    }
                    return stickersGridAdapter.stickersPerRow;
                } else {
                    if (position != stickersSearchGridAdapter.totalItems) {
                        Object object = stickersSearchGridAdapter.cache.get(position);
                        if (object == null || stickersSearchGridAdapter.cache.get(position) instanceof TLRPC.Document) {
                            return 1;
                        }
                    }
                    return stickersGridAdapter.stickersPerRow;
                }
            }
        });
        scrollHelper = new RecyclerAnimationScrollHelper(gridView, stickersLayoutManager);
        scrollHelper.setAnimationCallback(new RecyclerAnimationScrollHelper.AnimationCallback() {
            @Override
            public void onPreAnimation() {
                emojiSmoothScrolling = true;
            }

            @Override
            public void onEndAnimation() {
                emojiSmoothScrolling = false;
            }

            @Override
            public void ignoreView(View view, boolean ignore) {
                if (view instanceof ImageViewEmoji) {
                    ((ImageViewEmoji) view).ignoring = ignore;
                }
            }
        });
        gridView.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(8 + 48), AndroidUtilities.dp(4), AndroidUtilities.dp(48));
        gridView.setClipToPadding(false);
        gridView.setHorizontalScrollBarEnabled(false);
        gridView.setVerticalScrollBarEnabled(false);
        gridView.setGlowColor(0xff252525);
        gridView.setSelectorDrawableColor(0);
        stickersSearchGridAdapter = new StickersSearchGridAdapter(context);
        gridView.setAdapter(stickersGridAdapter = new StickersGridAdapter(context));
        gridView.setOnTouchListener((v, event) -> ContentPreviewViewer.getInstance().onTouch(event, gridView, containerView.getMeasuredHeight(), stickersOnItemClickListener, contentPreviewViewerDelegate, resourcesProvider));
        stickersOnItemClickListener = (view, position) -> {
            if (view instanceof ImageViewEmoji) {
                ContentPreviewViewer.getInstance().reset();
                ImageViewEmoji cell = (ImageViewEmoji) view;
                delegate.onStickerSelected(null, cell.document);
                dismiss();
            } else if (view instanceof StickerEmojiCell) {
                ContentPreviewViewer.getInstance().reset();
                StickerEmojiCell cell = (StickerEmojiCell) view;
                delegate.onStickerSelected(cell.getParentObject(), cell.getSticker());
                dismiss();
            }
        };
        gridView.setOnItemClickListener(stickersOnItemClickListener);
        containerView.addView(gridView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        stickersTab = new ScrollSlidingTabStrip(context, resourcesProvider) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                return super.onInterceptTouchEvent(ev);
            }
        };

        stickersSearchField = new SearchField(context, 0);
        containerView.addView(stickersSearchField, new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, searchFieldHeight + AndroidUtilities.getShadowHeight()));

        stickersTab.setType(ScrollSlidingTabStrip.Type.TAB);
        stickersTab.setUnderlineHeight(AndroidUtilities.getShadowHeight());
        stickersTab.setIndicatorColor(0xff6ebaed);
        stickersTab.setUnderlineColor(0);
        stickersTab.setBackgroundColor(0xff252525);
        containerView.addView(stickersTab, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36 + 6, Gravity.LEFT | Gravity.TOP));
        stickersTab.setDelegate(page -> {
            int scrollToPosition;
            if (page == recentTabBum) {
                scrollToPosition = stickersGridAdapter.getPositionForPack("recent");
                stickersTab.onPageScrolled(recentTabBum, recentTabBum > 0 ? recentTabBum : stickersTabOffset);
            } else if (page == favTabBum) {
                scrollToPosition = stickersGridAdapter.getPositionForPack("fav");
                stickersTab.onPageScrolled(favTabBum, favTabBum > 0 ? favTabBum : stickersTabOffset);
            } else {
                int index = page - stickersTabOffset;
                if (index >= stickerSets[typeIndex(currentType)].size()) {
                    return;
                }
                if (index >= stickerSets[typeIndex(currentType)].size()) {
                    index = stickerSets[typeIndex(currentType)].size() - 1;
                }
                scrollToPosition = stickersGridAdapter.getPositionForPack(stickerSets[typeIndex(currentType)].get(index));
            }
            int currentPosition = stickersLayoutManager.findFirstVisibleItemPosition();
            if (currentPosition == scrollToPosition) {
                return;
            }
//            stickersLayoutManager.scrollToPositionWithOffset(scrollToPosition, -gridView.getPaddingTop() + searchFieldHeight + AndroidUtilities.dp(48));
            scrollEmojisToPosition(scrollToPosition, -gridView.getPaddingTop() + searchFieldHeight + AndroidUtilities.dp(48));
        });

        gridView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    stickersSearchField.hideKeyboard();
                }
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                updateLayout(true);
            }
        });

//        View topShadow = new View(context);
//        topShadow.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, 0xffe2e5e7));
//        containerView.addView(topShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 6));

        bottomTabContainer = new FrameLayout(context) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                return super.onInterceptTouchEvent(ev);
            }
        };

        shadowLine = new View(context);
        shadowLine.setBackgroundColor(0x12000000);
        bottomTabContainer.addView(shadowLine, new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.getShadowHeight()));

        View bottomTabContainerBackground = new View(context);
        bottomTabContainerBackground.setBackgroundColor(0xff252525);
        bottomTabContainer.addView(bottomTabContainerBackground, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(48), Gravity.LEFT | Gravity.BOTTOM));

        containerView.addView(bottomTabContainer, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(48) + AndroidUtilities.getShadowHeight(), Gravity.LEFT | Gravity.BOTTOM));

        LinearLayout itemsLayout = new LinearLayout(context);
        itemsLayout.setOrientation(LinearLayout.HORIZONTAL);

        emojiButton = new ImageView(context) {
            @Override
            public void setSelected(boolean selected) {
                super.setSelected(selected);
                Drawable background = getBackground();
                if (Build.VERSION.SDK_INT >= 21 && background != null) {
                    int color = selected ? 0xff6ebaed : 0x1effffff;
                    Theme.setSelectorDrawableColor(background, Color.argb(30, Color.red(color), Color.green(color), Color.blue(color)), true);
                }
            }
        };
        emojiButton.setScaleType(ImageView.ScaleType.CENTER);
        emojiButton.setImageDrawable(Theme.createEmojiIconSelectorDrawable(context, R.drawable.smiles_tab_smiles, 0xffffffff, 0xff6ebaed));
        if (Build.VERSION.SDK_INT >= 21) {
            RippleDrawable rippleDrawable = (RippleDrawable) Theme.createSelectorDrawable(0x1effffff);
            Theme.setRippleDrawableForceSoftware(rippleDrawable);
            emojiButton.setBackground(rippleDrawable);
        }
        itemsLayout.addView(emojiButton, LayoutHelper.createLinear(70, 48));
        emojiButton.setOnClickListener(v -> {
            if (currentType == MediaDataController.TYPE_EMOJIPACKS) {
                return;
            }
            currentType = MediaDataController.TYPE_EMOJIPACKS;
            updateType();
        });

        stickersButton = new ImageView(context) {
            @Override
            public void setSelected(boolean selected) {
                super.setSelected(selected);
                Drawable background = getBackground();
                if (Build.VERSION.SDK_INT >= 21 && background != null) {
                    int color = selected ? 0xff6ebaed : 0x1effffff;
                    Theme.setSelectorDrawableColor(background, Color.argb(30, Color.red(color), Color.green(color), Color.blue(color)), true);
                }
            }
        };
        stickersButton.setScaleType(ImageView.ScaleType.CENTER);
        stickersButton.setImageDrawable(Theme.createEmojiIconSelectorDrawable(context, R.drawable.smiles_tab_stickers, 0xffffffff, 0xff6ebaed));
        if (Build.VERSION.SDK_INT >= 21) {
            RippleDrawable rippleDrawable = (RippleDrawable) Theme.createSelectorDrawable(0x1effffff);
            Theme.setRippleDrawableForceSoftware(rippleDrawable);
            stickersButton.setBackground(rippleDrawable);
        }
        itemsLayout.addView(stickersButton, LayoutHelper.createLinear(70, 48));
        stickersButton.setOnClickListener(v -> {
            if (currentType == MediaDataController.TYPE_IMAGE) {
                return;
            }
            currentType = MediaDataController.TYPE_IMAGE;
            updateType();
        });

        if (!isVideo) {
            masksButton = new ImageView(context) {
                @Override
                public void setSelected(boolean selected) {
                    super.setSelected(selected);
                    Drawable background = getBackground();
                    if (Build.VERSION.SDK_INT >= 21 && background != null) {
                        int color = selected ? 0xff6ebaed : 0x1effffff;
                        Theme.setSelectorDrawableColor(background, Color.argb(30, Color.red(color), Color.green(color), Color.blue(color)), true);
                    }
                }
            };
            masksButton.setScaleType(ImageView.ScaleType.CENTER);
            masksButton.setImageDrawable(Theme.createEmojiIconSelectorDrawable(context, R.drawable.ic_masks_msk1, 0xffffffff, 0xff6ebaed));
            if (Build.VERSION.SDK_INT >= 21) {
                RippleDrawable rippleDrawable = (RippleDrawable) Theme.createSelectorDrawable(0x1effffff);
                Theme.setRippleDrawableForceSoftware(rippleDrawable);
                masksButton.setBackground(rippleDrawable);
            }
            itemsLayout.addView(masksButton, LayoutHelper.createLinear(70, 48));
            masksButton.setOnClickListener(v -> {
                if (currentType == MediaDataController.TYPE_MASK) {
                    return;
                }
                currentType = MediaDataController.TYPE_MASK;
                updateType();
            });
        }

        bottomTabContainer.addView(itemsLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 48, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM));

        checkDocuments(true);
        reloadStickersAdapter();
    }

    private int getCurrentTop() {
        if (gridView.getChildCount() != 0) {
            View child = gridView.getChildAt(0);
            RecyclerListView.Holder holder = (RecyclerListView.Holder) gridView.findContainingViewHolder(child);
            if (holder != null) {
                return gridView.getPaddingTop() - (holder.getAdapterPosition() == 0 && child.getTop() >= 0 ? child.getTop() : 0);
            }
        }
        return -1000;
    }

    private void updateType() {
        if (currentType == MediaDataController.TYPE_EMOJIPACKS) {
            stickersLayoutManager.setSpanCount(8);
        } else {
            stickersLayoutManager.setSpanCount(5);
        }
        stickersLayoutManager.requestLayout();
        if (gridView.getChildCount() > 0) {
            View firstView = gridView.getChildAt(0);
            RecyclerView.ViewHolder holder = gridView.findContainingViewHolder(firstView);
            if (holder != null) {
                int top;
                if (holder.getAdapterPosition() != 0) {
                    top = -gridView.getPaddingTop();
                } else {
                    top = -gridView.getPaddingTop() + firstView.getTop();
                }
                stickersLayoutManager.scrollToPositionWithOffset(0, top);
            }
        }
        checkDocuments(true);
    }

    private void scrollEmojisToPosition(int p, int offset) {
        View view = stickersLayoutManager.findViewByPosition(p);
        int firstPosition = stickersLayoutManager.findFirstVisibleItemPosition();
        if ((view == null && Math.abs(p - firstPosition) > stickersLayoutManager.getSpanCount() * 9f) || !SharedConfig.animationsEnabled()) {
            scrollHelper.setScrollDirection(stickersLayoutManager.findFirstVisibleItemPosition() < p ? RecyclerAnimationScrollHelper.SCROLL_DIRECTION_DOWN : RecyclerAnimationScrollHelper.SCROLL_DIRECTION_UP);
            scrollHelper.scrollToPosition(p, offset, false, true);
        } else {
            ignoreStickersScroll = true;
            LinearSmoothScrollerCustom linearSmoothScroller = new LinearSmoothScrollerCustom(getContext(), LinearSmoothScrollerCustom.POSITION_TOP) {
                @Override
                public void onEnd() {
                    emojiSmoothScrolling = false;
                }

                @Override
                protected void onStart() {
                    emojiSmoothScrolling = true;
                }
            };
            linearSmoothScroller.setTargetPosition(p);
            linearSmoothScroller.setOffset(offset);
            stickersLayoutManager.startSmoothScroll(linearSmoothScroller);
        }
    }

    @Override
    protected boolean canDismissWithSwipe() {
        return false;
    }

    public void setDelegate(StickerMasksAlertDelegate stickerMasksAlertDelegate) {
        delegate = stickerMasksAlertDelegate;
    }

    private void updateLayout(boolean animated) {
        if (gridView.getChildCount() <= 0) {
            gridView.setTopGlowOffset(scrollOffsetY = gridView.getPaddingTop());
            containerView.invalidate();
            return;
        }
        View child = gridView.getChildAt(0);
        RecyclerListView.Holder holder = (RecyclerListView.Holder) gridView.findContainingViewHolder(child);
        int top = child.getTop();
        int newOffset = AndroidUtilities.dp(7);
        if (top >= AndroidUtilities.dp(7) && holder != null && holder.getAdapterPosition() == 0) {
            newOffset = top;
        }
        newOffset += -AndroidUtilities.dp(11);
        if (scrollOffsetY != newOffset) {
            gridView.setTopGlowOffset(scrollOffsetY = newOffset);
            stickersTab.setTranslationY(newOffset);
            stickersSearchField.setTranslationY(newOffset + AndroidUtilities.dp(32));
            containerView.invalidate();
        }

        holder = (RecyclerListView.Holder) gridView.findViewHolderForAdapterPosition(0);
        if (holder == null) {
            stickersSearchField.showShadow(true, animated);
        } else {
            stickersSearchField.showShadow(holder.itemView.getTop() < gridView.getPaddingTop(), animated);
        }

        if (gridView.getAdapter() == stickersSearchGridAdapter) {
            holder = (RecyclerListView.Holder) gridView.findViewHolderForAdapterPosition(stickersSearchGridAdapter.getItemCount() - 1);
            if (holder != null && holder.getItemViewType() == 5) {
                FrameLayout layout = (FrameLayout) holder.itemView;
                int count = layout.getChildCount();
                float tr = -(layout.getTop() - searchFieldHeight - AndroidUtilities.dp(48)) / 2;
                for (int a = 0; a < count; a++) {
                    layout.getChildAt(a).setTranslationY(tr);
                }
            }
        }

        checkPanels();
    }

    private void showBottomTab(boolean show, boolean animated) {
        if (show && bottomTabContainer.getTag() == null || !show && bottomTabContainer.getTag() != null) {
            return;
        }
        if (bottomTabContainerAnimation != null) {
            bottomTabContainerAnimation.cancel();
            bottomTabContainerAnimation = null;
        }
        bottomTabContainer.setTag(show ? null : 1);
        if (animated) {
            bottomTabContainerAnimation = new AnimatorSet();
            bottomTabContainerAnimation.playTogether(
                    ObjectAnimator.ofFloat(bottomTabContainer, View.TRANSLATION_Y, show ? 0 : AndroidUtilities.dp(49)),
                    ObjectAnimator.ofFloat(shadowLine, View.TRANSLATION_Y, show ? 0 : AndroidUtilities.dp(49))
            );
            bottomTabContainerAnimation.setDuration(200);
            bottomTabContainerAnimation.setInterpolator(CubicBezierInterpolator.EASE_OUT);
            bottomTabContainerAnimation.start();
        } else {
            bottomTabContainer.setTranslationY(show ? 0 : AndroidUtilities.dp(49));
            shadowLine.setTranslationY(show ? 0 : AndroidUtilities.dp(49));
        }
    }

    private class DrawingInBackgroundLine extends DrawingInBackgroundThreadDrawable {
        public int position;
        public int startOffset;
        ArrayList<ImageViewEmoji> imageViewEmojis;
        ArrayList<ImageViewEmoji> drawInBackgroundViews = new ArrayList<>();

        @Override
        public void draw(Canvas canvas, long time, int w, int h, float alpha) {
            if (imageViewEmojis == null) {
                return;
            }
            boolean drawInUi = imageViewEmojis.size() <= 4 || SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_LOW || !LiteMode.isEnabled(LiteMode.FLAG_ANIMATED_EMOJI_KEYBOARD);
            if (!drawInUi) {
//                boolean animatedExpandIn = animateExpandStartTime > 0 && (SystemClock.elapsedRealtime() - animateExpandStartTime) < animateExpandDuration();
                for (int i = 0; i < imageViewEmojis.size(); i++) {
                    ImageViewEmoji img = imageViewEmojis.get(i);
                    if (img.pressedProgress != 0 || img.backAnimator != null) {
                        drawInUi = true;
                        break;
                    }
                }
            }
            if (drawInUi) {
                prepareDraw(System.currentTimeMillis());
                drawInUiThread(canvas, alpha);
                reset();
            } else {
                super.draw(canvas, time, w, h, alpha);
            }
        }

        @Override
        public void prepareDraw(long time) {
            drawInBackgroundViews.clear();
            for (int i = 0; i < imageViewEmojis.size(); i++) {
                ImageViewEmoji imageView = imageViewEmojis.get(i);
                AnimatedEmojiDrawable drawable = imageView.drawable;
                if (drawable == null || drawable.getImageReceiver() == null) {
                    continue;
                }

                drawable.update(time);
                imageView.backgroundThreadDrawHolder[threadIndex] = drawable.getImageReceiver().setDrawInBackgroundThread(imageView.backgroundThreadDrawHolder[threadIndex], threadIndex);
                imageView.backgroundThreadDrawHolder[threadIndex].time = time;
                imageView.backgroundThreadDrawHolder[threadIndex].overrideAlpha = 1f;
                drawable.setAlpha(255);
                int topOffset = (int) (imageView.getHeight() * .03f);
                AndroidUtilities.rectTmp2.set(imageView.getLeft() + imageView.getPaddingLeft() - startOffset, topOffset, imageView.getRight() - imageView.getPaddingRight() - startOffset, topOffset + imageView.getMeasuredHeight() - imageView.getPaddingTop() - imageView.getPaddingBottom());
                imageView.backgroundThreadDrawHolder[threadIndex].setBounds(AndroidUtilities.rectTmp2);
                imageView.drawable = drawable;
    //                imageView.drawable.setColorFilter(animatedEmojiTextColorFilter);
                imageView.imageReceiver = drawable.getImageReceiver();
                drawInBackgroundViews.add(imageView);
            }
        }

        @Override
        public void drawInBackground(Canvas canvas) {
            for (int i = 0; i < drawInBackgroundViews.size(); i++) {
                ImageViewEmoji imageView = drawInBackgroundViews.get(i);
                if (imageView.drawable != null) {
                    imageView.drawable.draw(canvas, imageView.backgroundThreadDrawHolder[threadIndex], false);
                }
            }
        }

        private OvershootInterpolator appearScaleInterpolator = new OvershootInterpolator(3f);

        @Override
        protected void drawInUiThread(Canvas canvas, float alpha) {
            if (imageViewEmojis != null) {
                canvas.save();
                canvas.translate(-startOffset, 0);
                for (int i = 0; i < imageViewEmojis.size(); i++) {
                    ImageViewEmoji imageView = imageViewEmojis.get(i);
                    AnimatedEmojiDrawable drawable = imageView.drawable;
                    if (drawable == null) {
                        continue;
                    }

                    int topOffset = (int) (imageView.getHeight() * .03f);
                    AndroidUtilities.rectTmp2.set(imageView.getLeft() + imageView.getPaddingLeft(), topOffset, imageView.getRight() - imageView.getPaddingRight(), topOffset + imageView.getMeasuredHeight() - imageView.getPaddingBottom() - imageView.getPaddingTop());
                    float scale = 1;
                    if (imageView.pressedProgress != 0) {
                        scale *= 0.8f + 0.2f * (1f - imageView.pressedProgress);
                    }
//                    boolean animatedExpandIn = animateExpandStartTime > 0 && (SystemClock.elapsedRealtime() - animateExpandStartTime) < animateExpandDuration();
//                    if (animatedExpandIn && animateExpandFromPosition >= 0 && animateExpandToPosition >= 0 && animateExpandStartTime > 0) {
//                        int position = getChildAdapterPosition(imageView);
//                        final int pos = position - animateExpandFromPosition;
//                        final int count = animateExpandToPosition - animateExpandFromPosition;
//                        if (pos >= 0 && pos < count) {
//                            final float appearDuration = animateExpandAppearDuration();
//                            final float crossfadeDuration = animateExpandCrossfadeDuration();
//                            final float CrossfadeT = MathUtils.clamp((SystemClock.elapsedRealtime() - animateExpandStartTime - (appearDuration * .45f)) / crossfadeDuration, 0, 1);
//                            final float AppearT = CubicBezierInterpolator.EASE_OUT.getInterpolation(MathUtils.clamp((SystemClock.elapsedRealtime() - animateExpandStartTime) / appearDuration, 0, 1));
//                            final float crossfadeT = AndroidUtilities.cascade(CrossfadeT, pos, count, count / 5f);
//                            final float alphaT = AndroidUtilities.cascade(AppearT, pos, count, count / 4f);
//                            final float scaleT = AndroidUtilities.cascade(AppearT, pos + (count / 4), count + (count / 4), count / 4f);
//                            scale *= .5f + appearScaleInterpolator.getInterpolation(scaleT) * .5f;
//                            alpha *= alphaT;
//                        }
//                    }
                    drawable.setAlpha((int) (255 * alpha));
                    drawable.setBounds(AndroidUtilities.rectTmp2);
                    if (scale != 1) {
                        canvas.save();
                        canvas.scale(scale, scale, AndroidUtilities.rectTmp2.centerX(), AndroidUtilities.rectTmp2.centerY());
                        drawable.draw(canvas);
                        canvas.restore();
                    } else {
                        drawable.draw(canvas);
                    }
                }
                canvas.restore();
            }
        }

        @Override
        public void onFrameReady() {
            super.onFrameReady();
            for (int i = 0; i < drawInBackgroundViews.size(); i++) {
                ImageViewEmoji imageView = drawInBackgroundViews.get(i);
                if (imageView.backgroundThreadDrawHolder != null) {
                    imageView.backgroundThreadDrawHolder[threadIndex].release();
                }
            }
            gridView.invalidate();
        }
    }

    private void updateStickerTabs() {
        if (stickersTab == null) {
            return;
        }

        if (stickersButton != null) {
            emojiButton.setSelected(currentType == MediaDataController.TYPE_EMOJIPACKS);
            stickersButton.setSelected(currentType == MediaDataController.TYPE_IMAGE);
            if (masksButton != null) {
                masksButton.setSelected(currentType == MediaDataController.TYPE_MASK);
            }
        }

        recentTabBum = -2;
        favTabBum = -2;

        stickersTabOffset = 0;
        int lastPosition = stickersTab.getCurrentPosition();
        stickersTab.beginUpdate(false);

        if (currentType == MediaDataController.TYPE_IMAGE && !favouriteStickers.isEmpty()) {
            favTabBum = stickersTabOffset;
            stickersTabOffset++;
            stickersTab.addIconTab(1, stickerIcons[1]).setContentDescription(LocaleController.getString("FavoriteStickers", R.string.FavoriteStickers));
        }

        if (!recentStickers[typeIndex(currentType)].isEmpty()) {
            recentTabBum = stickersTabOffset;
            stickersTabOffset++;
            stickersTab.addIconTab(0, stickerIcons[0]).setContentDescription(LocaleController.getString("RecentStickers", R.string.RecentStickers));
        }

        stickerSets[typeIndex(currentType)].clear();
        ArrayList<TLRPC.TL_messages_stickerSet> packs = MediaDataController.getInstance(currentAccount).getStickerSets(currentType);
        for (int a = 0; a < packs.size(); a++) {
            TLRPC.TL_messages_stickerSet pack = packs.get(a);
            if (pack.set.archived || pack.documents == null || pack.documents.isEmpty()) {
                continue;
            }
            stickerSets[typeIndex(currentType)].add(pack);
        }
        for (int a = 0; a < stickerSets[typeIndex(currentType)].size(); a++) {
            TLRPC.TL_messages_stickerSet stickerSet = stickerSets[typeIndex(currentType)].get(a);
            TLRPC.Document document = stickerSet.documents.get(0);
            TLObject thumb = FileLoader.getClosestPhotoSizeWithSize(stickerSet.set.thumbs, 90);
            if (thumb == null) {
                thumb = document;
            }
            stickersTab.addStickerTab(thumb, document, stickerSet).setContentDescription(stickerSet.set.title + ", " + LocaleController.getString("AccDescrStickerSet", R.string.AccDescrStickerSet));
        }
        stickersTab.commitUpdate();
        stickersTab.updateTabStyles();
        if (lastPosition != 0) {
            stickersTab.onPageScrolled(lastPosition, lastPosition);
        }
        checkPanels();
    }

    private void checkPanels() {
        if (stickersTab == null) {
            return;
        }
        int count = gridView.getChildCount();
        View child = null;
        for (int a = 0; a < count; a++) {
            child = gridView.getChildAt(a);
            if (child.getBottom() > searchFieldHeight + AndroidUtilities.dp(48)) {
                break;
            }
        }
        if (child == null) {
            return;
        }
        RecyclerListView.Holder holder = (RecyclerListView.Holder) gridView.findContainingViewHolder(child);
        int position = holder != null ? holder.getAdapterPosition() : RecyclerView.NO_POSITION;
        if (position != RecyclerView.NO_POSITION) {
            int firstTab;
            if (favTabBum > 0) {
                firstTab = favTabBum;
            } else if (recentTabBum > 0) {
                firstTab = recentTabBum;
            } else {
                firstTab = stickersTabOffset;
            }
            stickersTab.onPageScrolled(stickersGridAdapter.getTabForPosition(position), firstTab);
        }
    }

    public void addRecentSticker(TLRPC.Document document) {
        if (document == null) {
            return;
        }
        MediaDataController.getInstance(currentAccount).addRecentSticker(currentType, null, document, (int) (System.currentTimeMillis() / 1000), false);
        boolean wasEmpty = recentStickers[typeIndex(currentType)].isEmpty();
        recentStickers[typeIndex(currentType)] = MediaDataController.getInstance(currentAccount).getRecentStickers(currentType, IsFullRecent());
        if (stickersGridAdapter != null) {
            stickersGridAdapter.notifyDataSetChanged();
        }
        if (wasEmpty) {
            updateStickerTabs();
        }
    }

    private void reloadStickersAdapter() {
        if (stickersGridAdapter != null) {
            stickersGridAdapter.notifyDataSetChanged();
        }
        if (stickersSearchGridAdapter != null) {
            stickersSearchGridAdapter.notifyDataSetChanged();
        }
        if (ContentPreviewViewer.getInstance().isVisible()) {
            ContentPreviewViewer.getInstance().close();
        }
        ContentPreviewViewer.getInstance().reset();
    }

    @Override
    public void dismissInternal() {
        super.dismissInternal();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.stickersDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.recentDocumentsDidLoad);
    }

    private void checkDocuments(boolean force) {
        int previousCount = recentStickers[typeIndex(currentType)].size();
        int previousCount2 = favouriteStickers.size();
        recentStickers[typeIndex(currentType)] = MediaDataController.getInstance(currentAccount).getRecentStickers(currentType, IsFullRecent());
        favouriteStickers = MediaDataController.getInstance(currentAccount).getRecentStickers(MediaDataController.TYPE_FAVE, IsFullRecent());
        if (currentType == MediaDataController.TYPE_IMAGE) {
            for (int a = 0; a < favouriteStickers.size(); a++) {
                TLRPC.Document favSticker = favouriteStickers.get(a);
                for (int b = 0; b < recentStickers[typeIndex(currentType)].size(); b++) {
                    TLRPC.Document recSticker = recentStickers[typeIndex(currentType)].get(b);
                    if (recSticker.dc_id == favSticker.dc_id && recSticker.id == favSticker.id) {
                        recentStickers[typeIndex(currentType)].remove(b);
                        break;
                    }
                }
            }
        }
        if (force || previousCount != recentStickers[typeIndex(currentType)].size() || previousCount2 != favouriteStickers.size()) {
            updateStickerTabs();
        }
        if (stickersGridAdapter != null) {
            stickersGridAdapter.notifyDataSetChanged();
        }
        if (!force) {
            checkPanels();
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.stickersDidLoad) {
            if ((Integer) args[0] == currentType) {
                updateStickerTabs();
                reloadStickersAdapter();
                checkPanels();
            }
        } else if (id == NotificationCenter.recentDocumentsDidLoad) {
            boolean isGif = (Boolean) args[0];
            int type = (Integer) args[1];
            if (!isGif && (type == currentType || type == MediaDataController.TYPE_FAVE)) {
                checkDocuments(false);
            }
        } else if (id == NotificationCenter.emojiLoaded) {
            if (gridView != null) {
                int count = gridView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = gridView.getChildAt(a);
                    if (child instanceof StickerSetNameCell || child instanceof StickerEmojiCell) {
                        child.invalidate();
                    }
                }
            }
        }
    }

    private class StickersGridAdapter extends RecyclerListView.SelectionAdapter {

        private Context context;
        private int stickersPerRow, stickersPerRowType;
        private SparseArray<Object> rowStartPack = new SparseArray<>();
        private HashMap<Object, Integer> packStartPosition = new HashMap<>();
        private SparseArray<Object> cache = new SparseArray<>();
        private SparseArray<Object> cacheParents = new SparseArray<>();
        private SparseIntArray positionToRow = new SparseIntArray();
        private int totalItems;

        public StickersGridAdapter(Context context) {
            this.context = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            final int viewType = holder.getItemViewType();
            return viewType == 0 || viewType == -1;
        }

        @Override
        public int getItemCount() {
            return totalItems != 0 ? totalItems + 1 : 0;
        }

        public Object getItem(int i) {
            return cache.get(i);
        }

        public int getPositionForPack(Object pack) {
            Integer pos = packStartPosition.get(pack);
            if (pos == null) {
                return -1;
            }
            return pos;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0) {
                return 4;
            }
            Object object = cache.get(position);
            if (object != null) {
                if (object instanceof TLRPC.Document) {
                    if (currentType == MediaDataController.TYPE_EMOJIPACKS) {
                        return -1;
                    } else {
                        return 0;
                    }
                } else {
                    return 2;
                }
            }
            return 1;
        }

        public int getTabForPosition(int position) {
            if (position == 0) {
                position = 1;
            }
            if (stickersPerRow == 0 || stickersPerRowType != currentType) {
                int width = gridView.getMeasuredWidth();
                if (width == 0) {
                    width = AndroidUtilities.displaySize.x;
                }
                stickersPerRow = width / AndroidUtilities.dp(currentType == MediaDataController.TYPE_EMOJIPACKS ? 45 : 72);
                stickersPerRowType = currentType;
            }
            int row = positionToRow.get(position, Integer.MIN_VALUE);
            if (row == Integer.MIN_VALUE) {
                return stickerSets[typeIndex(currentType)].size() - 1 + stickersTabOffset;
            }
            Object pack = rowStartPack.get(row);
            if (pack instanceof String) {
                if ("recent".equals(pack)) {
                    return recentTabBum;
                } else {
                    return favTabBum;
                }
            } else {
                TLRPC.TL_messages_stickerSet set = (TLRPC.TL_messages_stickerSet) pack;
                int idx = stickerSets[typeIndex(currentType)].indexOf(set);
                return idx + stickersTabOffset;
            }
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = null;
            switch (viewType) {
                case -1:
                    ImageViewEmoji imageViewEmoji = new ImageViewEmoji(context);
                    imageViewEmoji.getImageReceiver().setLayerNum(playingImagesLayerNum);
                    view = imageViewEmoji;
                    break;
                case 0:
                    StickerEmojiCell stickerEmojiCell = new StickerEmojiCell(context, false, resourcesProvider) {
                        public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            if (currentType == MediaDataController.TYPE_EMOJIPACKS) {
                                super.onMeasure(widthMeasureSpec, widthMeasureSpec);
                            } else {
                                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(82), MeasureSpec.EXACTLY));
                            }
                        }
                    };
                    stickerEmojiCell.getImageView().setLayerNum(playingImagesLayerNum);
                    view = stickerEmojiCell;
                    break;
                case 1:
                    view = new EmptyCell(context);
                    break;
                case 2:
                    StickerSetNameCell cell = new StickerSetNameCell(context, false, resourcesProvider);
                    cell.setTitleColor(0xff888888);
                    view = cell;
                    break;
                case 4:
                    view = new View(context);
                    view.setLayoutParams(new RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, searchFieldHeight + AndroidUtilities.dp(48)));
                    break;
            }

            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case -1: {
                    TLRPC.Document sticker = (TLRPC.Document) cache.get(position);
                    ImageViewEmoji cell = (ImageViewEmoji) holder.itemView;
                    cell.position = position;
                    cell.setDocument(cell.document = sticker);
                    cell.isRecent = recentStickers[typeIndex(currentType)].contains(sticker) || favouriteStickers.contains(sticker);
                    break;
                }
                case 0: {
                    TLRPC.Document sticker = (TLRPC.Document) cache.get(position);
                    StickerEmojiCell cell = (StickerEmojiCell) holder.itemView;
                    cell.setSticker(sticker, cacheParents.get(position), false);
                    cell.setRecent(recentStickers[typeIndex(currentType)].contains(sticker));
                    break;
                }
                case 1: {
                    EmptyCell cell = (EmptyCell) holder.itemView;
                    if (position == totalItems) {
                        int row = positionToRow.get(position - 1, Integer.MIN_VALUE);
                        if (row == Integer.MIN_VALUE) {
                            cell.setHeight(1);
                        } else {
                            ArrayList<TLRPC.Document> documents;
                            Object pack = rowStartPack.get(row);
                            if (pack instanceof TLRPC.TL_messages_stickerSet) {
                                documents = ((TLRPC.TL_messages_stickerSet) pack).documents;
                            } else if (pack instanceof String) {
                                if ("recent".equals(pack)) {
                                    documents = recentStickers[typeIndex(currentType)];
                                } else {
                                    documents = favouriteStickers;
                                }
                            } else {
                                documents = null;
                            }
                            if (documents == null) {
                                cell.setHeight(1);
                            } else {
                                if (documents.isEmpty()) {
                                    cell.setHeight(AndroidUtilities.dp(8));
                                } else {
                                    int height = gridView.getHeight() - (int) Math.ceil(documents.size() / (float) stickersPerRow) * AndroidUtilities.dp(82);
                                    cell.setHeight(height > 0 ? height : 1);
                                }
                            }
                        }
                    } else {
                        cell.setHeight(AndroidUtilities.dp(82));
                    }
                    break;
                }
                case 2: {
                    StickerSetNameCell cell = (StickerSetNameCell) holder.itemView;
                    Object object = cache.get(position);
                    if (object instanceof TLRPC.TL_messages_stickerSet) {
                        TLRPC.TL_messages_stickerSet set = (TLRPC.TL_messages_stickerSet) object;
                        if (set.set != null) {
                            cell.setText(set.set.title, 0);
                        }
                    } else if (object == recentStickers[typeIndex(currentType)]) {
                        cell.setText(LocaleController.getString("RecentStickers", R.string.RecentStickers), 0);
                    } else if (object == favouriteStickers) {
                        cell.setText(LocaleController.getString("FavoriteStickers", R.string.FavoriteStickers), 0);
                    }
                    break;
                }
            }
        }

        @Override
        public void notifyDataSetChanged() {
            int width = gridView.getMeasuredWidth();
            if (width == 0) {
                width = AndroidUtilities.displaySize.x;
            }
            stickersPerRow = width / AndroidUtilities.dp(currentType == MediaDataController.TYPE_EMOJIPACKS ? 45 : 72);
            stickersLayoutManager.setSpanCount(stickersPerRow);
            rowStartPack.clear();
            packStartPosition.clear();
            positionToRow.clear();
            cache.clear();
            totalItems = 0;
            ArrayList<TLRPC.TL_messages_stickerSet> packs = stickerSets[typeIndex(currentType)];
            int startRow = 0;
            for (int a = -3; a < packs.size(); a++) {
                ArrayList<TLRPC.Document> documents;
                TLRPC.TL_messages_stickerSet pack = null;
                String key;
                if (a == -3) {
                    cache.put(totalItems++, "search");
                    startRow++;
                    continue;
                } else if (a == -2) {
                    if (currentType == MediaDataController.TYPE_IMAGE) {
                        documents = favouriteStickers;
                        packStartPosition.put(key = "fav", totalItems);
                    } else {
                        documents = null;
                        key = null;
                    }
                } else if (a == -1) {
                    documents = recentStickers[typeIndex(currentType)];
                    packStartPosition.put(key = "recent", totalItems);
                } else {
                    key = null;
                    pack = packs.get(a);
                    documents = pack.documents;
                    packStartPosition.put(pack, totalItems);
                }
                if (documents == null || documents.isEmpty()) {
                    continue;
                }
                int count = (int) Math.ceil(documents.size() / (float) stickersPerRow);
                if (pack != null) {
                    cache.put(totalItems, pack);
                } else {
                    cache.put(totalItems, documents);
                }
                positionToRow.put(totalItems, startRow);
                for (int b = 0; b < documents.size(); b++) {
                    int num = 1 + b + totalItems;
                    cache.put(num, documents.get(b));
                    if (pack != null) {
                        cacheParents.put(num, pack);
                    } else {
                        cacheParents.put(num, key);
                    }
                    positionToRow.put(1 + b + totalItems, startRow + 1 + b / stickersPerRow);
                }
                for (int b = 0; b < count + 1; b++) {
                    if (pack != null) {
                        rowStartPack.put(startRow + b, pack);
                    } else {
                        rowStartPack.put(startRow + b, a == -1 ? "recent" : "fav");
                    }
                }
                totalItems += count * stickersPerRow + 1;
                startRow += count + 1;
            }
            super.notifyDataSetChanged();
        }
    }

    private class ImageViewEmoji extends BackupImageView {

        private boolean ignoring;
        public int position;
        public TLRPC.Document document;
        public boolean isRecent;
        public AnimatedEmojiDrawable drawable;
        public ImageReceiver imageReceiver;
        private ImageReceiver.BackgroundThreadDrawHolder[] backgroundThreadDrawHolder = new ImageReceiver.BackgroundThreadDrawHolder[DrawingInBackgroundThreadDrawable.THREAD_COUNT];
        float pressedProgress;
        ValueAnimator backAnimator;

        public ImageViewEmoji(Context context) {
            super(context);
            setPadding(AndroidUtilities.dp(3), AndroidUtilities.dp(3), AndroidUtilities.dp(3), AndroidUtilities.dp(3));
            setBackground(Theme.createRadSelectorDrawable(getThemedColor(Theme.key_listSelector), AndroidUtilities.dp(2), AndroidUtilities.dp(2)));
        }

        private void setDrawable(AnimatedEmojiDrawable drawable) {
            if (this.drawable != null) {
                this.drawable.removeView(this);
            }
            this.drawable = drawable;
            if (this.drawable != null) {
                this.drawable.addView(this);
            }
        }

        private void setDocument(TLRPC.Document document) {
            setDrawable(AnimatedEmojiDrawable.make(currentAccount, AnimatedEmojiDrawable.CACHE_TYPE_KEYBOARD, document));
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (drawable != null) {
                drawable.addView(this);
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (drawable != null) {
                drawable.removeView(this);
            }
        }

        @Override
        public void setPressed(boolean pressed) {
            if (isPressed() != pressed) {
                super.setPressed(pressed);
                invalidate();
                if (pressed) {
                    if (backAnimator != null) {
                        backAnimator.removeAllListeners();
                        backAnimator.cancel();
                    }
                }
                if (!pressed && pressedProgress != 0) {
                    backAnimator = ValueAnimator.ofFloat(pressedProgress, 0);
                    backAnimator.addUpdateListener(animation -> {
                        pressedProgress = (float) animation.getAnimatedValue();
                        invalidate();
                    });
                    backAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            backAnimator = null;
                        }
                    });
                    backAnimator.setInterpolator(new OvershootInterpolator(5.0f));
                    backAnimator.setDuration(350);
                    backAnimator.start();
                }
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (isPressed() && pressedProgress != 1f) {
                pressedProgress += (float) Math.min(40, 1000f / AndroidUtilities.screenRefreshRate) / 100f;
                pressedProgress = Utilities.clamp(pressedProgress, 1f, 0);
                invalidate();
            }
            float s = 0.8f + 0.2f * (1f - pressedProgress);
            canvas.save();
            canvas.scale(s, s, getMeasuredWidth() / 2f, getMeasuredHeight() / 2f);
            super.onDraw(canvas);
            canvas.restore();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, widthMeasureSpec);
        }
    }

    private class StickersSearchGridAdapter extends RecyclerListView.SelectionAdapter {

        private Context context;
        private SparseArray<Object> rowStartPack = new SparseArray<>();
        private SparseArray<Object> cache = new SparseArray<>();
        private SparseArray<Object> cacheParent = new SparseArray<>();
        private SparseIntArray positionToRow = new SparseIntArray();
        private SparseArray<String> positionToEmoji = new SparseArray<>();
        private int totalItems;

        private ArrayList<TLRPC.TL_messages_stickerSet> localPacks = new ArrayList<>();
        private HashMap<TLRPC.TL_messages_stickerSet, Boolean> localPacksByShortName = new HashMap<>();
        private HashMap<TLRPC.TL_messages_stickerSet, Integer> localPacksByName = new HashMap<>();
        private HashMap<ArrayList<TLRPC.Document>, String> emojiStickers = new HashMap<>();
        private ArrayList<ArrayList<TLRPC.Document>> emojiArrays = new ArrayList<>();

        private int reqId2;

        private int emojiSearchId;
        boolean cleared;
        private String searchQuery;
        private Runnable searchRunnable = new Runnable() {

            private void clear() {
                if (cleared) {
                    return;
                }
                cleared = true;
                emojiStickers.clear();
                emojiArrays.clear();
                localPacks.clear();
                localPacksByShortName.clear();
                localPacksByName.clear();
            }

            @Override
            public void run() {
                if (TextUtils.isEmpty(searchQuery)) {
                    return;
                }
                cleared = false;
                int lastId = ++emojiSearchId;

                final ArrayList<TLRPC.Document> emojiStickersArray = new ArrayList<>(0);
                final LongSparseArray<TLRPC.Document> emojiStickersMap = new LongSparseArray<>(0);
                HashMap<String, ArrayList<TLRPC.Document>> allStickers = MediaDataController.getInstance(currentAccount).getAllStickers();
                if (searchQuery.length() <= 14) {
                    CharSequence emoji = searchQuery;
                    int length = emoji.length();
                    for (int a = 0; a < length; a++) {
                        if (a < length - 1 && (emoji.charAt(a) == 0xD83C && emoji.charAt(a + 1) >= 0xDFFB && emoji.charAt(a + 1) <= 0xDFFF || emoji.charAt(a) == 0x200D && (emoji.charAt(a + 1) == 0x2640 || emoji.charAt(a + 1) == 0x2642))) {
                            emoji = TextUtils.concat(emoji.subSequence(0, a), emoji.subSequence(a + 2, emoji.length()));
                            length -= 2;
                            a--;
                        } else if (emoji.charAt(a) == 0xfe0f) {
                            emoji = TextUtils.concat(emoji.subSequence(0, a), emoji.subSequence(a + 1, emoji.length()));
                            length--;
                            a--;
                        }
                    }
                    ArrayList<TLRPC.Document> newStickers = allStickers != null ? allStickers.get(emoji.toString()) : null;
                    if (newStickers != null && !newStickers.isEmpty()) {
                        clear();
                        emojiStickersArray.addAll(newStickers);
                        for (int a = 0, size = newStickers.size(); a < size; a++) {
                            TLRPC.Document document = newStickers.get(a);
                            emojiStickersMap.put(document.id, document);
                        }
                        emojiStickers.put(emojiStickersArray, searchQuery);
                        emojiArrays.add(emojiStickersArray);
                    }
                }
                if (allStickers != null && !allStickers.isEmpty() && searchQuery.length() > 1) {
                    String[] newLanguage = AndroidUtilities.getCurrentKeyboardLanguage();
                    if (!Arrays.equals(lastSearchKeyboardLanguage, newLanguage)) {
                        MediaDataController.getInstance(currentAccount).fetchNewEmojiKeywords(newLanguage);
                    }
                    lastSearchKeyboardLanguage = newLanguage;
                    MediaDataController.getInstance(currentAccount).getEmojiSuggestions(lastSearchKeyboardLanguage, searchQuery, false, (param, alias) -> {
                        if (lastId != emojiSearchId) {
                            return;
                        }
                        boolean added = false;
                        for (int a = 0, size = param.size(); a < size; a++) {
                            String emoji = param.get(a).emoji;
                            ArrayList<TLRPC.Document> newStickers = allStickers != null ? allStickers.get(emoji) : null;
                            if (newStickers != null && !newStickers.isEmpty()) {
                                clear();
                                if (!emojiStickers.containsKey(newStickers)) {
                                    emojiStickers.put(newStickers, emoji);
                                    emojiArrays.add(newStickers);
                                    added = true;
                                }
                            }
                        }
                        if (added) {
                            notifyDataSetChanged();
                        } else if (reqId2 == 0) {
                            clear();
                            notifyDataSetChanged();
                        }
                    }, false);
                }
                ArrayList<TLRPC.TL_messages_stickerSet> local = MediaDataController.getInstance(currentAccount).getStickerSets(currentType);
                int index;
                for (int a = 0, size = local.size(); a < size; a++) {
                    TLRPC.TL_messages_stickerSet set = local.get(a);
                    if ((index = AndroidUtilities.indexOfIgnoreCase(set.set.title, searchQuery)) >= 0) {
                        if (index == 0 || set.set.title.charAt(index - 1) == ' ') {
                            clear();
                            localPacks.add(set);
                            localPacksByName.put(set, index);
                        }
                    } else if (set.set.short_name != null && (index = AndroidUtilities.indexOfIgnoreCase(set.set.short_name, searchQuery)) >= 0) {
                        if (index == 0 || set.set.short_name.charAt(index - 1) == ' ') {
                            clear();
                            localPacks.add(set);
                            localPacksByShortName.put(set, true);
                        }
                    }
                }
                local = MediaDataController.getInstance(currentAccount).getStickerSets(MediaDataController.TYPE_FEATURED);
                for (int a = 0, size = local.size(); a < size; a++) {
                    TLRPC.TL_messages_stickerSet set = local.get(a);
                    if ((index = AndroidUtilities.indexOfIgnoreCase(set.set.title, searchQuery)) >= 0) {
                        if (index == 0 || set.set.title.charAt(index - 1) == ' ') {
                            clear();
                            localPacks.add(set);
                            localPacksByName.put(set, index);
                        }
                    } else if (set.set.short_name != null && (index = AndroidUtilities.indexOfIgnoreCase(set.set.short_name, searchQuery)) >= 0) {
                        if (index == 0 || set.set.short_name.charAt(index - 1) == ' ') {
                            clear();
                            localPacks.add(set);
                            localPacksByShortName.put(set, true);
                        }
                    }
                }
                boolean validEmoji;
                if (validEmoji = Emoji.isValidEmoji(searchQuery)) {
                    stickersSearchField.progressDrawable.startAnimation();
                    final TLRPC.TL_messages_getStickers req2 = new TLRPC.TL_messages_getStickers();
                    req2.emoticon = searchQuery;
                    req2.hash = 0;
                    reqId2 = ConnectionsManager.getInstance(currentAccount).sendRequest(req2, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        if (req2.emoticon.equals(searchQuery)) {
                            stickersSearchField.progressDrawable.stopAnimation();
                            reqId2 = 0;
                            if (!(response instanceof TLRPC.TL_messages_stickers)) {
                                return;
                            }
                            TLRPC.TL_messages_stickers res = (TLRPC.TL_messages_stickers) response;
                            int oldCount = emojiStickersArray.size();
                            for (int a = 0, size = res.stickers.size(); a < size; a++) {
                                TLRPC.Document document = res.stickers.get(a);
                                if (emojiStickersMap.indexOfKey(document.id) >= 0) {
                                    continue;
                                }
                                emojiStickersArray.add(document);
                            }
                            int newCount = emojiStickersArray.size();
                            if (oldCount != newCount) {
                                emojiStickers.put(emojiStickersArray, searchQuery);
                                if (oldCount == 0) {
                                    emojiArrays.add(emojiStickersArray);
                                }
                                notifyDataSetChanged();
                            }
                            if (gridView.getAdapter() != stickersSearchGridAdapter) {
                                gridView.setAdapter(stickersSearchGridAdapter);
                            }
                        }
                    }));
                }
                if ((!validEmoji || !localPacks.isEmpty() || !emojiStickers.isEmpty()) && gridView.getAdapter() != stickersSearchGridAdapter) {
                    gridView.setAdapter(stickersSearchGridAdapter);
                }
                notifyDataSetChanged();
            }
        };

        public StickersSearchGridAdapter(Context context) {
            this.context = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            final int viewType = holder.getItemViewType();
            return viewType == 0 || viewType == -1;
        }

        @Override
        public int getItemCount() {
            if (totalItems != 1) {
                return totalItems + 1;
            } else {
                return 2;
            }
        }

        public Object getItem(int i) {
            return cache.get(i);
        }

        public void search(String text) {
            if (reqId2 != 0) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId2, true);
                reqId2 = 0;
            }
            if (TextUtils.isEmpty(text)) {
                searchQuery = null;
                localPacks.clear();
                emojiStickers.clear();
                if (gridView.getAdapter() != stickersGridAdapter) {
                    gridView.setAdapter(stickersGridAdapter);
                }
                notifyDataSetChanged();
            } else {
                searchQuery = text.toLowerCase();
            }
            AndroidUtilities.cancelRunOnUIThread(searchRunnable);
            AndroidUtilities.runOnUIThread(searchRunnable, 300);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0) {
                return 4;
            } else if (position == 1 && totalItems == 1) {
                return 5;
            }
            Object object = cache.get(position);
            if (object != null) {
                if (object instanceof TLRPC.Document) {
                    if (currentType == MediaDataController.TYPE_EMOJIPACKS) {
                        return -1;
                    } else {
                        return 0;
                    }
                } else {
                    return 2;
                }
            }
            return 1;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = null;
            switch (viewType) {
                case -1:
                    ImageViewEmoji imageViewEmoji = new ImageViewEmoji(context);
                    imageViewEmoji.getImageReceiver().setLayerNum(playingImagesLayerNum);
                    view = imageViewEmoji;
                    break;
                case 0:
                    StickerEmojiCell cell = new StickerEmojiCell(context, false, resourcesProvider) {
                        public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            if (currentType == MediaDataController.TYPE_EMOJIPACKS) {
                                super.onMeasure(widthMeasureSpec, widthMeasureSpec);
                            } else {
                                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(82), MeasureSpec.EXACTLY));
                            }
                        }
                    };
                    cell.getImageView().setLayerNum(playingImagesLayerNum);
                    view = cell;
                    break;
                case 1:
                    view = new EmptyCell(context);
                    break;
                case 2:
                    view = new StickerSetNameCell(context, false, resourcesProvider);
                    break;
                case 4:
                    view = new View(context);
                    view.setLayoutParams(new RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, searchFieldHeight + AndroidUtilities.dp(48)));
                    break;
                case 5:
                    FrameLayout frameLayout = new FrameLayout(context) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            int height = gridView.getMeasuredHeight();
                            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height - searchFieldHeight - AndroidUtilities.dp(48) - AndroidUtilities.dp(48), MeasureSpec.EXACTLY));
                        }
                    };

                    ImageView imageView = new ImageView(context);
                    imageView.setScaleType(ImageView.ScaleType.CENTER);
                    imageView.setImageResource(R.drawable.stickers_empty);
                    imageView.setColorFilter(new PorterDuffColorFilter(0xff949ba1, PorterDuff.Mode.MULTIPLY));
                    frameLayout.addView(imageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 0, 0, 50));

                    TextView textView = new TextView(context);
                    textView.setText(LocaleController.getString("NoStickersFound", R.string.NoStickersFound));
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                    textView.setTextColor(0xff949ba1);
                    frameLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 0, 0, 0));

                    view = frameLayout;
                    view.setLayoutParams(new RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    break;
            }

            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case -1: {
                    TLRPC.Document sticker = (TLRPC.Document) cache.get(position);
                    ImageViewEmoji cell = (ImageViewEmoji) holder.itemView;
                    cell.position = position;
                    cell.setDocument(cell.document = sticker);
                    cell.isRecent = recentStickers[typeIndex(currentType)].contains(sticker) || favouriteStickers.contains(sticker);
                    break;
                }
                case 0: {
                    TLRPC.Document sticker = (TLRPC.Document) cache.get(position);
                    StickerEmojiCell cell = (StickerEmojiCell) holder.itemView;
                    cell.setSticker(sticker, null, cacheParent.get(position), positionToEmoji.get(position), false);
                    cell.setRecent(recentStickers[typeIndex(currentType)].contains(sticker) || favouriteStickers.contains(sticker));
                    break;
                }
                case 1: {
                    EmptyCell cell = (EmptyCell) holder.itemView;
                    if (position == totalItems) {
                        int row = positionToRow.get(position - 1, Integer.MIN_VALUE);
                        if (row == Integer.MIN_VALUE) {
                            cell.setHeight(1);
                        } else {
                            Object pack = rowStartPack.get(row);
                            Integer count;
                            if (pack instanceof TLRPC.TL_messages_stickerSet) {
                                count = ((TLRPC.TL_messages_stickerSet) pack).documents.size();
                            } else if (pack instanceof Integer) {
                                count = (Integer) pack;
                            } else {
                                count = null;
                            }
                            if (count == null) {
                                cell.setHeight(1);
                            } else {
                                if (count == 0) {
                                    cell.setHeight(AndroidUtilities.dp(8));
                                } else {
                                    int height = gridView.getHeight() - (int) Math.ceil(count / (float) stickersGridAdapter.stickersPerRow) * AndroidUtilities.dp(82);
                                    cell.setHeight(height > 0 ? height : 1);
                                }
                            }
                        }
                    } else {
                        cell.setHeight(AndroidUtilities.dp(82));
                    }
                    break;
                }
                case 2: {
                    StickerSetNameCell cell = (StickerSetNameCell) holder.itemView;
                    Object object = cache.get(position);
                    if (object instanceof TLRPC.TL_messages_stickerSet) {
                        TLRPC.TL_messages_stickerSet set = (TLRPC.TL_messages_stickerSet) object;
                        if (!TextUtils.isEmpty(searchQuery) && localPacksByShortName.containsKey(set)) {
                            if (set.set != null) {
                                cell.setText(set.set.title, 0);
                            }
                            cell.setUrl(set.set.short_name, searchQuery.length());
                        } else {
                            Integer start = localPacksByName.get(set);
                            if (set.set != null && start != null) {
                                cell.setText(set.set.title, 0, start, !TextUtils.isEmpty(searchQuery) ? searchQuery.length() : 0);
                            }
                            cell.setUrl(null, 0);
                        }
                    }
                    break;
                }
            }
        }

        @Override
        public void notifyDataSetChanged() {
            rowStartPack.clear();
            positionToRow.clear();
            cache.clear();
            positionToEmoji.clear();
            totalItems = 0;
            int startRow = 0;
            for (int a = -1, localCount = localPacks.size(), emojiCount = (emojiArrays.isEmpty() ? 0 : 1); a < localCount + emojiCount; a++) {
                ArrayList<TLRPC.Document> documents;
                Object pack;
                String key;
                if (a == -1) {
                    cache.put(totalItems++, "search");
                    startRow++;
                    continue;
                } else {
                    if (a < localCount) {
                        TLRPC.TL_messages_stickerSet set = localPacks.get(a);
                        documents = set.documents;
                        pack = set;
                    } else {
                        int documentsCount = 0;
                        String lastEmoji = "";
                        for (int i = 0, N = emojiArrays.size(); i < N; i++) {
                            documents = emojiArrays.get(i);
                            String emoji = emojiStickers.get(documents);
                            if (emoji != null && !lastEmoji.equals(emoji)) {
                                lastEmoji = emoji;
                                positionToEmoji.put(totalItems + documentsCount, lastEmoji);
                            }
                            for (int b = 0, size = documents.size(); b < size; b++) {
                                int num = documentsCount + totalItems;
                                int row = startRow + documentsCount / stickersGridAdapter.stickersPerRow;

                                TLRPC.Document document = documents.get(b);
                                cache.put(num, document);
                                Object parent = MediaDataController.getInstance(currentAccount).getStickerSetById(MediaDataController.getStickerSetId(document));
                                if (parent != null) {
                                    cacheParent.put(num, parent);
                                }
                                positionToRow.put(num, row);
                                documentsCount++;
                            }
                        }
                        int count = (int) Math.ceil(documentsCount / (float) stickersGridAdapter.stickersPerRow);
                        for (int b = 0; b < count; b++) {
                            rowStartPack.put(startRow + b, documentsCount);
                        }
                        totalItems += count * stickersGridAdapter.stickersPerRow;
                        startRow += count;
                        continue;
                    }
                }
                if (documents.isEmpty()) {
                    continue;
                }
                int count = (int) Math.ceil(documents.size() / (float) stickersGridAdapter.stickersPerRow);
                cache.put(totalItems, pack);
                positionToRow.put(totalItems, startRow);
                for (int b = 0, size = documents.size(); b < size; b++) {
                    int num = 1 + b + totalItems;
                    int row = startRow + 1 + b / stickersGridAdapter.stickersPerRow;
                    TLRPC.Document document = documents.get(b);
                    cache.put(num, document);
                    if (pack != null) {
                        cacheParent.put(num, pack);
                    }
                    positionToRow.put(num, row);
                }
                for (int b = 0, N = count + 1; b < N; b++) {
                    rowStartPack.put(startRow + b, pack);
                }
                totalItems += 1 + count * stickersGridAdapter.stickersPerRow;
                startRow += count + 1;
            }
            super.notifyDataSetChanged();
        }
    }

    @Override
    public void setImageReceiverNumLevel(int playingImages, int onShowing) {
        super.setImageReceiverNumLevel(playingImages, onShowing);
        stickersTab.setImageReceiversLayerNum(playingImages);
    }
}
