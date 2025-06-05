package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.ContentUris;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Size;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.BlurringShader;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.Text;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

public class MultipleStoriesSelector extends FrameLayout {

    private final Theme.ResourcesProvider resourcesProvider;
    private final BlurringShader.BlurManager blurManager;

    private final BlurringShader.StoryBlurDrawer backgroundBlur;

    private final UniversalRecyclerView listView;

    public MultipleStoriesSelector(Context context, Theme.ResourcesProvider resourcesProvider, BlurringShader.BlurManager blurManager) {
        super(context);

        this.resourcesProvider = resourcesProvider;
        this.blurManager = blurManager;

        closePath.rewind();
        closePath.moveTo(-dp(4.33f), -dp(4.33f));
        closePath.lineTo(dp(4.33f), dp(4.33f));
        closePath.moveTo(-dp(4.33f), dp(4.33f));
        closePath.lineTo(dp(4.33f), -dp(4.33f));

        backgroundBlur = new BlurringShader.StoryBlurDrawer(blurManager, this, BlurringShader.StoryBlurDrawer.BLUR_TYPE_BACKGROUND, !customBlur());

        setPadding(dp(12), dp(12), dp(12), dp(10 + 30 + 4));

        listView = new UniversalRecyclerView(context, UserConfig.selectedAccount, 0, false, this::fillItems, this::onItemClick, null, resourcesProvider, UItem.MAX_SPAN_COUNT, LinearLayoutManager.HORIZONTAL) {
            @Override
            public Integer getSelectorColor(int position) {
                return 0;
            }
            @Override
            protected void swappedElements() {
                AndroidUtilities.forEachViews(listView, view -> {
                    if (view instanceof EntryView) {
                        ((EntryView) view).setPosition(getPositionOf(listView.getChildAdapterPosition(view)));
                        view.setPressed(false);
                    }
                });
            }
        };
        listView.adapter.setApplyBackground(false);
        listView.setClipToPadding(false);
        listView.setClipChildren(false);
        listView.setPadding(dp(2), 0, dp(2), 0);
        addView(listView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 120, Gravity.RIGHT | Gravity.BOTTOM));
        listView.allowReorder(true);
        listView.listenReorder(this::whenReordered, true);
        showList(false, false);

        setWillNotDraw(false);

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setColor(0xFFFFFFFF);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(12 + 120 + 10 + 30 + 4), MeasureSpec.EXACTLY));
    }

    private void whenReordered(int id, ArrayList<UItem> items) {
        selectedOrder.clear();
        for (UItem item : items) {
            selectedOrder.add(item.id);
        }
        updateItemsAnimated();
    }

    private void updateItemsAnimated() {
        AndroidUtilities.forEachViews(listView, view -> {
            if (view instanceof EntryView) {
                final int position = listView.getChildAdapterPosition(view);
                final UItem item = listView.adapter.getItem(position);
                if (item == null) return;
                ((EntryView) view).setPosition(getPositionOf(position));
                ((EntryView) view).setSelected(selectedStory == item.id, true);
                ((EntryView) view).setChecked(selectedStories.contains(item.id), true);
                view.setPressed(false);
            }
        });
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        adapter.reorderSectionStart();
        int currentIndex = 0;
        for (int i = 0; i < selectedOrder.size(); ++i) {
            final int index = selectedOrder.get(i);
            items.add(
                EntryView.Factory.asStoryEntry(index, currentIndex, stories.get(index))
                    .setChecked(selectedStory == index)
                    .setCollapsed(selectedStories.contains(index))
                    .setClickCallback(v -> {
                        final boolean checked = selectedStories.contains(index);
                        if (checked) {
                            if (selectedStories.size() <= 1) {
                                return;
                            }
                            selectedStories.remove((Integer) index);
                        } else {
                            selectedStories.add((Integer) index);
                        }
                        updateItemsAnimated();
                    })
            );
            if (selectedStories.contains(index)) {
                currentIndex++;
            }
        }
        adapter.reorderSectionEnd();
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        onSwitchToStory(item.id, (StoryEntry) item.object);
    }

    private ArrayList<StoryEntry> stories = new ArrayList<>();
    private ArrayList<Integer> selectedOrder = new ArrayList<>();
    private ArrayList<Integer> selectedStories = new ArrayList<>();
    private int selectedStory;
    public void set(ArrayList<StoryEntry> stories, ArrayList<Integer> selectedOrder, ArrayList<Integer> selectedStories) {
        showList(false, false);
        this.stories = stories;
        this.selectedOrder = selectedOrder;
        this.selectedStories = selectedStories;

        counter = new Text(Integer.toString(stories.size()), 20, AndroidUtilities.getTypeface("fonts/num.otf"));
        hint = new Text(LocaleController.formatPluralStringComma("HintViewStoriesMultiple", stories.size()), 14);

        listView.adapter.update(false);
    }

    private final Runnable hideHint = () -> {
        if (this.hintShown) {
            this.hintShown = false;
            invalidate();
        }
    };
    public void showHint() {
        if (hintShown || listShown) return;
        final int hintValue = MessagesController.getGlobalMainSettings().getInt("multistorieshint", 0);
        if (hintValue >= 3) return;
        MessagesController.getGlobalMainSettings().edit().putInt("multistorieshint", hintValue + 1).apply();
        AndroidUtilities.cancelRunOnUIThread(hideHint);
        hintShown = true;
        invalidate();
        AndroidUtilities.runOnUIThread(hideHint, 5500);
    }

    public void setSelected(int selectedStory) {
        if (this.selectedStory == selectedStory) return;
        this.selectedStory = selectedStory;
        AndroidUtilities.forEachViews(listView, view -> {
            if (view instanceof EntryView) {
                final int position = listView.getChildAdapterPosition(view);
                final UItem item = listView.adapter.getItem(position);
                if (item == null) return;
                ((EntryView) view).setPosition(getPositionOf(position));
                ((EntryView) view).setSelected(selectedStory == item.id, true);
                view.setPressed(false);
            }
        });
    }

    private int getPositionOf(int index) {
        if (!selectedOrder.contains(index)) return -1;
        int a = 0;
        for (int i = 0; i < Math.min(index, stories.size()); ++i) {
            if (selectedOrder.contains(i)) {
                a++;
            }
        }
        return a;
    }

    public void update() {
        listView.adapter.update(false);
    }

    protected void onSwitchToStory(int index, StoryEntry entry) {

    }

    protected void drawBlur(BlurringShader.StoryBlurDrawer blur, Canvas canvas, RectF rect, float r, boolean text, float ox, float oy, boolean thisView, float alpha) {

    }

    protected boolean customBlur() {
        return false;
    }

    private ButtonBounce buttonBounce = new ButtonBounce(this);
    private final RectF buttonBounds = new RectF();
    private final RectF buttonTouchBounds = new RectF();
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Text counter;
    private final Path closePath = new Path();
    private final RectF listBounds = new RectF();

    private final Paint darkenBackground = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF hintBounds = new RectF();
    private final RectF hintArc = new RectF();
    private final Path hintClipPath = new Path();
    private boolean hintShown;
    private AnimatedFloat animatedHint = new AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
    private Text hint;

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        listView.setPivotX(listView.getWidth() - dp(15));
        listView.setPivotY(listView.getHeight());
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        float s = buttonBounce.getScale(0.1f);
        canvas.save();
        buttonBounds.set(getWidth() - dp(12 + 30), getHeight() - dp(30 + 4), getWidth() - dp(12), getHeight() - dp(4));
        buttonTouchBounds.set(buttonBounds);
        buttonTouchBounds.inset(-dp(8), -dp(8));

        canvas.scale(s, s, buttonBounds.centerX(), buttonBounds.centerY());
        drawBlur(canvas, buttonBounds, buttonBounds.width() / 2f, 1.0f);

        strokePaint.setStrokeWidth(dp(2));
        strokePaint.setAlpha(0xFF);
        canvas.drawCircle(buttonBounds.centerX(), buttonBounds.centerY(), buttonBounds.width() / 2 - dp(0.9f), strokePaint);

        if (counter != null) {
            counter.draw(canvas, buttonBounds.centerX() - counter.getCurrentWidth() / 2f, buttonBounds.centerY() - dp(0.6f), 0xFFFFFFFF, 1.0f - listView.getAlpha());
        }
        if (listView.getAlpha() > 0.0f) {
            canvas.save();
            canvas.translate(buttonBounds.centerX(), buttonBounds.centerY());
            strokePaint.setAlpha((int) (0xFF * listView.getAlpha()));
            canvas.drawPath(closePath, strokePaint);
            canvas.restore();
        }
        canvas.restore();

        if (hint != null) {
            final float showHint = animatedHint.set(hintShown);
            if (showHint > 0.0f) {
                final float scale = lerp(0.6f, 1.0f, showHint);
                final float w = dp(11) + hint.getWidth() + dp(11);
                final float h = dp(32);
                hintBounds.set(buttonBounds.right - w, buttonBounds.top - dp(9.66f) - h, buttonBounds.right, buttonBounds.top - dp(9.66f));
                hintBounds.set(hintBounds.right - hintBounds.width() * scale, hintBounds.bottom - hintBounds.height() * scale, hintBounds.right, hintBounds.bottom);
                hintBounds.offset(0, dp(4) * (1.0f - showHint));
                hintClipPath.rewind();
                final float r = dp(8);
                hintArc.set(hintBounds.left, hintBounds.top, hintBounds.left + r, hintBounds.top + r);
                hintClipPath.arcTo(hintArc, 180, 90, false);
                hintArc.set(hintBounds.right - r, hintBounds.top, hintBounds.right, hintBounds.top + r);
                hintClipPath.arcTo(hintArc, 270, 90, false);
                hintArc.set(hintBounds.right - r, hintBounds.bottom - r, hintBounds.right, hintBounds.bottom);
                hintClipPath.arcTo(hintArc, 0, 90, false);
                hintClipPath.lineTo(hintBounds.right - dp(8), hintBounds.bottom);
                hintClipPath.lineTo(hintBounds.right - dp(8 + 6.5f), hintBounds.bottom + dp(5.66f));
                hintClipPath.lineTo(hintBounds.right - dp(8 + 13), hintBounds.bottom);
                hintArc.set(hintBounds.left, hintBounds.bottom - r, hintBounds.left + r, hintBounds.bottom);
                hintClipPath.arcTo(hintArc, 90, 90, false);
                hintClipPath.close();
                hintBounds.bottom += dp(5.66f);
                canvas.save();
                canvas.clipPath(hintClipPath);
                drawBlur(canvas, hintBounds, r, showHint);
                canvas.restore();
                canvas.save();
                canvas.scale(scale, scale, hintBounds.right, hintBounds.bottom);
                hint.draw(canvas, buttonBounds.right - w + dp(11), buttonBounds.top - dp(9.66f) - h / 2.0f, 0xFFFFFFFF, showHint);
                canvas.restore();
            }
        }

        super.dispatchDraw(canvas);
    }

    private final Path clipPath = new Path();
    @Override
    protected boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime) {
        if (child == listView) {
            listBounds.set(
                listView.getX(), listView.getY(),
                listView.getX() + listView.getWidth(), listView.getY() + listView.getHeight()
            );
            AndroidUtilities.scaleRect(listBounds, listView.getScaleX(), listView.getX() + listView.getPivotX(), listView.getY() + listView.getPivotY());
            drawBlur(canvas, listBounds, dp(10), listView.getAlpha());
            clipPath.rewind();
            clipPath.addRoundRect(listBounds, dp(10), dp(10), Path.Direction.CW);
            canvas.save();
            canvas.clipPath(clipPath);
            boolean r = super.drawChild(canvas, child, drawingTime);
            canvas.restore();
            return r;
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final boolean buttonHit = buttonBounds.contains(event.getX(), event.getY());
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            buttonBounce.setPressed(buttonHit);
            if (listShown && !buttonHit && !listBounds.contains(event.getX(), event.getY())) {
                showList(false, true);
                return true;
            }
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            if (!buttonHit) {
                buttonBounce.setPressed(false);
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            if (buttonBounce.isPressed()) {
                showList(!listShown, true);
            }
            buttonBounce.setPressed(false);
        } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            buttonBounce.setPressed(false);
        }
        return buttonBounce.isPressed() || super.onTouchEvent(event);
    }

    private boolean listShown = true;
    public void showList(boolean show, boolean animated) {
        if (listShown == show) return;
        listShown = show;
        listView.animate().cancel();
        if (animated) {
            listView.setVisibility(VISIBLE);
            listView.animate()
                .alpha(show ? 1.0f : 0.0f)
                .scaleX(show ? 1.0f : 0.65f)
                .scaleY(show ? 1.0f : 0.65f)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (!show) {
                            listView.setVisibility(GONE);
                        }
                    }
                })
                .setUpdateListener(anm -> invalidate())
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .setDuration(360)
                .start();
        } else {
            listView.setVisibility(show ? VISIBLE : GONE);
            listView.setAlpha(show ? 1.0f : 0.0f);
            listView.setScaleX(show ? 1.0f : 0.65f);
            listView.setScaleY(show ? 1.0f : 0.65f);
            invalidate();
        }
        if (show && hintShown) {
            hintShown = false;
            invalidate();
        }
    }

    public boolean onBackPressed() {
        if (listShown) {
            showList(false, true);
            return true;
        }
        return false;
    }

    private void drawBlur(Canvas canvas, RectF bounds, float r, float alpha) {
        if (alpha < 1.0f) {
            canvas.saveLayerAlpha(bounds, (int) (0xFF * alpha), Canvas.ALL_SAVE_FLAG);
        }
        if (customBlur()) {
            drawBlur(backgroundBlur, canvas, bounds, r, false, 0, 0, true, 1.0f);
            darkenBackground.setAlpha(0x26);
            canvas.drawRoundRect(bounds, r, r, darkenBackground);
        } else {
            Paint[] blurPaints = backgroundBlur.getPaints(1.0f, 0, 0);
            if (blurPaints == null || blurPaints[1] == null) {
                darkenBackground.setAlpha(0x80);
                canvas.drawRoundRect(bounds, r, r, darkenBackground);
            } else {
                if (blurPaints[0] != null) {
                    canvas.drawRoundRect(bounds, r, r, blurPaints[0]);
                }
                if (blurPaints[1] != null) {
                    canvas.drawRoundRect(bounds, r, r, blurPaints[1]);
                }
                darkenBackground.setAlpha((int) (0x33 * alpha));
                canvas.drawRoundRect(bounds, r, r, darkenBackground);
            }
        }
        if (alpha < 1.0f) {
            canvas.restore();
        }
    }

    public static class EntryView extends View {

        private final ImageReceiver imageReceiver = new ImageReceiver(this);

        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final AnimatedTextView.AnimatedTextDrawable counter = new AnimatedTextView.AnimatedTextDrawable();

        public EntryView(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);

            counter.setCallback(this);
            counter.setTextColor(0xFFFFFFFF);
            counter.setGravity(Gravity.CENTER);
            counter.setTextSize(dp(16));
            counter.setTypeface(AndroidUtilities.getTypeface("fonts/num.otf"));
            counter.setOverrideFullWidth(AndroidUtilities.displaySize.x);
            counter.setAnimationProperties(0.65f, 0, 480, CubicBezierInterpolator.EASE_OUT_QUINT);
            counter.setScaleProperty(.35f);

            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setColor(0xFFFFFFFF);
            fillPaint.setColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider));

            imageReceiver.setRoundRadius(dp(6));
            ScaleStateListAnimator.apply(this);
        }

        @Override
        protected boolean verifyDrawable(@NonNull Drawable who) {
            return who == counter || super.verifyDrawable(who);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            imageReceiver.onAttachedToWindow();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            imageReceiver.onDetachedFromWindow();
        }

        public void setPosition(int position) {
            counter.setText(position < 0 ? "" : Integer.toString(1 + position), true);
        }

        private boolean selected;
        public void setSelected(boolean selected, boolean animated) {
            if (this.selected == selected) return;
            this.selected = selected;
            if (!animated) {
                animatedSelected.force(this.selected);
            }
            invalidate();
        }

        private boolean checked;
        public void setChecked(boolean checked, boolean animated) {
            if (this.checked == checked) return;
            this.checked = checked;
            if (!animated) {
                animatedChecked.force(this.checked);
            }
            invalidate();
        }

        private View.OnClickListener onCheckboxClick;
        public void setOnCheckboxClick(View.OnClickListener listener) {
            this.onCheckboxClick = listener;
        }

        private ButtonBounce checkboxBounce = new ButtonBounce(this);
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            final boolean checkboxHit = event.getX() >= cx - dp(14) && event.getX() <= cx + dp(14) && event.getY() >= cy - dp(14) && event.getY() <= cy + dp(14);
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                checkboxBounce.setPressed(checkboxHit);
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                if (checkboxBounce.isPressed() && checkboxHit && onCheckboxClick != null) {
                    onCheckboxClick.onClick(this);
                }
                checkboxBounce.setPressed(false);
            } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                checkboxBounce.setPressed(false);
            }
            return checkboxBounce.isPressed() || super.onTouchEvent(event);
        }

        private int lastId = -1;
        private String lastEntryPath;
        public void set(int id, int position, StoryEntry entry) {
            if (lastId != id) {
                lastEntryPath = null;
                imageReceiver.clearImage();
                lastId = id;
            }
            counter.setText(Integer.toString(1 + position), false);
            if (entry.draftThumbFile != null) {
                if (TextUtils.equals(lastEntryPath, entry.draftThumbFile.getPath()))
                    return;
                lastEntryPath = entry.draftThumbFile.getPath();
                Utilities.searchQueue.postRunnable(() -> {
                    final BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(entry.draftThumbFile.getPath(), opts);

                    StoryEntry.setupScale(opts, dp(94), dp(112));
                    opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    opts.inDither = true;
                    opts.inJustDecodeBounds = false;

                    final Bitmap bitmap = BitmapFactory.decodeFile(entry.draftThumbFile.getPath(), opts);
                    AndroidUtilities.runOnUIThread(() -> {
                        imageReceiver.setImageBitmap(bitmap);
                    });
                });
            } else if (entry.isVideo) {
                Bitmap bitmap = null;
                if (entry.blurredVideoThumb != null) {
                    bitmap = entry.blurredVideoThumb;
                }
                if (bitmap == null && entry.thumbPath != null && entry.thumbPath.startsWith("vthumb://")) {
                    if (TextUtils.equals(lastEntryPath, entry.thumbPath))
                        return;
                    lastEntryPath = entry.thumbPath;

                    long imageId = Long.parseLong(entry.thumbPath.substring(9));

                    if (bitmap == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        try {
                            Uri uri;
                            if (entry.isVideo) {
                                uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, imageId);
                            } else {
                                uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageId);
                            }
                            bitmap = getContext().getContentResolver().loadThumbnail(uri, new Size(dp(94), dp(112)), null);
                        } catch (Exception ignore) {}
                    }
                }
                imageReceiver.setImageBitmap(bitmap);
            } else if (entry.file != null) {
                if (TextUtils.equals(lastEntryPath, entry.file.getPath()))
                    return;
                lastEntryPath = entry.file.getPath();
                Utilities.searchQueue.postRunnable(() -> {
                    final BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(entry.file.getPath(), opts);

                    StoryEntry.setupScale(opts, dp(94), dp(112));
                    opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    opts.inDither = true;
                    opts.inJustDecodeBounds = false;

                    final Bitmap bitmap = BitmapFactory.decodeFile(entry.file.getPath(), opts);
                    AndroidUtilities.runOnUIThread(() -> {
                        imageReceiver.setImageBitmap(bitmap);
                    });
                });
            }
        }

        private float cx, cy, r;
        private AnimatedFloat animatedSelected = new AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
        private AnimatedFloat animatedChecked = new AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);

        @Override
        protected void dispatchDraw(@NonNull Canvas canvas) {
            super.dispatchDraw(canvas);

            imageReceiver.setImageCoords(dp(2), dp(4), dp(94), dp(112));
            imageReceiver.draw(canvas);

            strokePaint.setStrokeWidth(dp(1.5f));
            final float selected = animatedSelected.set(this.selected);
            if (selected > 0.0f) {
                AndroidUtilities.rectTmp.set(dp(2), dp(4), dp(2 + 94), dp(4 + 112));
                strokePaint.setAlpha((int) (0xFF * selected));
                canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(6), dp(6), strokePaint);
            }

            cx = getWidth() - dp(4.33f + 12.833f) - dp(3);// * selected;
            cy = dp(5 + 12.833f) + dp(3);// * selected;
            r = dp(12.833f);

            final float checked = animatedChecked.set(this.checked);
            final float scale = checkboxBounce.getScale(0.075f);
            canvas.save();
            canvas.scale(scale, scale, cx, cy);

            if (checked > 0.0f) {
                fillPaint.setAlpha((int) (0xFF * checked));
                canvas.drawCircle(cx, cy, r, fillPaint);
            }
            strokePaint.setAlpha(0xFF);
            canvas.drawCircle(cx, cy, r - dp(1), strokePaint);

            if (checked > 0.0f) {
                counter.setBounds(cx - r, cy, cx + r, cy);
                counter.setAlpha((int) (0xFF * checked));
                counter.draw(canvas);
            }

            canvas.restore();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(dp(2 + 94 + 2), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(4 + 112 + 4), MeasureSpec.EXACTLY)
            );
        }

        public static class Factory extends UItem.UItemFactory<EntryView> {
            static { setup(new Factory()); }

            @Override
            public EntryView createView(Context context, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new EntryView(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                ((EntryView) view).set(item.id, item.intValue, (StoryEntry) item.object);
                ((EntryView) view).setSelected(item.checked, false);
                ((EntryView) view).setChecked(item.collapsed, false);
                ((EntryView) view).setOnCheckboxClick(item.clickCallback);
            }

            public static UItem asStoryEntry(int id, int position, StoryEntry entry) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = id;
                item.object = entry;
                item.intValue = position;
                return item;
            }
        }
    }
}
