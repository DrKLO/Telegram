package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.displayMetrics;
import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.widget.NestedScrollView;

import org.json.JSONArray;
import org.json.JSONTokener;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;

public class TranslateAlert extends Dialog {
    private FrameLayout bulletinContainer;
    private FrameLayout contentView;
    private FrameLayout container;
    private TextView titleView;
    private LinearLayout subtitleView;
    private InlineLoadingTextView subtitleFromView;
    private ImageView subtitleArrowView;
    private TextView subtitleToView;
    private ImageView backButton;
    private FrameLayout header;
    private FrameLayout headerShadowView;
    private boolean scrollViewScrollable = false;
    private NestedScrollView scrollView;
    private TextBlocksLayout textsView;
    private TextView buttonTextView;
    private FrameLayout buttonView;
    private FrameLayout buttonShadowView;
    private TextView allTextsView;
    private FrameLayout textsContainerView;

    private FrameLayout.LayoutParams titleLayout;
    private FrameLayout.LayoutParams subtitleLayout;
    private FrameLayout.LayoutParams backLayout;
    private FrameLayout.LayoutParams headerLayout;
    private FrameLayout.LayoutParams scrollViewLayout;

    private int blockIndex = 0;
    private ArrayList<CharSequence> textBlocks;

    private float containerOpenAnimationT = 0f;
    private float openAnimationT = 0f;
    private float epsilon = 0.001f;
    private void openAnimation(float t) {
        t = Math.min(Math.max(t, 0f), 1f);
        if (containerOpenAnimationT == t) {
            return;
        }
        containerOpenAnimationT = t;

        titleView.setScaleX(lerp(1f, 0.9473f, t));
        titleView.setScaleY(lerp(1f, 0.9473f, t));
        titleLayout.setMargins(
            dp(lerp(22, 72, t)),
            dp(lerp(22, 8, t)),
            titleLayout.rightMargin,
            titleLayout.bottomMargin
        );
        titleView.setLayoutParams(titleLayout);
        subtitleLayout.setMargins(
            dp(lerp(22, 72, t)) - LoadingTextView2.paddingHorizontal,
            dp(lerp(47, 30, t)) - LoadingTextView2.paddingVertical,
            subtitleLayout.rightMargin,
            subtitleLayout.bottomMargin
        );
        subtitleView.setLayoutParams(subtitleLayout);

        backButton.setAlpha(t);
        backButton.setScaleX(.75f + .25f * t);
        backButton.setScaleY(.75f + .25f * t);
        backButton.setClickable(t > .5f);
        headerShadowView.setAlpha(scrollView.getScrollY() > 0 ? 1f : t);

        headerLayout.height = (int) lerp(dp(70), dp(56), t);
        header.setLayoutParams(headerLayout);

        scrollViewLayout.setMargins(
            scrollViewLayout.leftMargin,
            (int) lerp(dp(70), dp(56), t),
            scrollViewLayout.rightMargin,
            scrollViewLayout.bottomMargin
        );
        scrollView.setLayoutParams(scrollViewLayout);
    }


    private boolean openAnimationToAnimatorPriority = false;
    private ValueAnimator openAnimationToAnimator = null;
    private void openAnimationTo(float to, boolean priority) {
        openAnimationTo(to, priority, null);
    }
    private void openAnimationTo(float to, boolean priority, Runnable onAnimationEnd) {
        if (openAnimationToAnimatorPriority && !priority) {
            return;
        }
        openAnimationToAnimatorPriority = priority;
        to = Math.min(Math.max(to, 0), 1);
        if (openAnimationToAnimator != null) {
            openAnimationToAnimator.cancel();
        }
        openAnimationToAnimator = ValueAnimator.ofFloat(containerOpenAnimationT, to);
        openAnimationToAnimator.addUpdateListener(a -> openAnimation((float) a.getAnimatedValue()));
        openAnimationToAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                openAnimationToAnimatorPriority = false;
                if (onAnimationEnd != null)
                    onAnimationEnd.run();
            }
            @Override
            public void onAnimationCancel(Animator animator) {
                openAnimationToAnimatorPriority = false;
            }
        });
        openAnimationToAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
        openAnimationToAnimator.setDuration(220);
        openAnimationToAnimator.start();
        if (to >= .5 && blockIndex <= 1) {
            fetchNext();
        }
    }

    private int firstMinHeight = -1;
    private int minHeight() {
        return minHeight(false);
    }
    private int minHeight(boolean full) {
        int textsViewHeight = textsView == null ? 0 : textsView.getMeasuredHeight();
        int height =
            textsViewHeight +
            dp(
                66 + // header
                1 +  // button separator
                16 + // button top padding
                48 + // button
                16   // button bottom padding
            );
        if (firstMinHeight < 0 && textsViewHeight > 0)
            firstMinHeight = height;
        if (firstMinHeight > 0 && textBlocks.size() > 1 && !full)
            return firstMinHeight;
        return height;
    }
    private boolean canExpand() {
        return (
            textsView.getBlocksCount() < textBlocks.size() ||
            minHeight(true) >= (AndroidUtilities.displayMetrics.heightPixels * heightMaxPercent)
        );
    }
    private void updateCanExpand() {
        boolean canExpand = canExpand();
        if (containerOpenAnimationT > 0f && !canExpand) {
            openAnimationTo(0f, false);
        }

        buttonShadowView.animate().alpha(canExpand ? 1f : 0f).setDuration((long) (Math.abs(buttonShadowView.getAlpha() - (canExpand ? 1f : 0f)) * 220)).start();
    }

    public interface OnLinkPress {
        public void run(URLSpan urlSpan);
    }

    private boolean allowScroll = true;
    private ValueAnimator scrollerToBottom = null;
    private String fromLanguage, toLanguage;
    private CharSequence text;
    private BaseFragment fragment;
    private boolean noforwards;
    private OnLinkPress onLinkPress = null;
    private Runnable onDismiss = null;
    public TranslateAlert(BaseFragment fragment, Context context, String fromLanguage, String toLanguage, CharSequence text, boolean noforwards, OnLinkPress onLinkPress, Runnable onDismiss) {
        this(fragment, context, -1, null, -1, fromLanguage, toLanguage, text, noforwards, onLinkPress, onDismiss);
    }
    public TranslateAlert(BaseFragment fragment, Context context, int currentAccount, TLRPC.InputPeer peer, int msgId, String fromLanguage, String toLanguage, CharSequence text, boolean noforwards, OnLinkPress onLinkPress, Runnable onDismiss) {
        super(context, R.style.TransparentDialog);

        if (peer != null) {
            translateText(currentAccount, peer, msgId, fromLanguage != null && fromLanguage.equals("und") ? null : fromLanguage, toLanguage);
        }

        this.onLinkPress = onLinkPress;
        this.noforwards = noforwards;
        this.fragment = fragment;
        this.fromLanguage = fromLanguage != null && fromLanguage.equals("und") ? "auto" : fromLanguage;
        this.toLanguage = toLanguage;
        this.text = text;
        this.textBlocks = cutInBlocks(text, 1024);
        this.onDismiss = onDismiss;

        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        } else if (Build.VERSION.SDK_INT >= 21) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        }

        if (noforwards) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }

        contentView = new FrameLayout(context);
        contentView.setBackground(backDrawable);
        contentView.setClipChildren(false);
        contentView.setClipToPadding(false);
        if (Build.VERSION.SDK_INT >= 21) {
            contentView.setFitsSystemWindows(true);
            if (Build.VERSION.SDK_INT >= 30) {
                contentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |  View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            } else {
                contentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
            }
        }

        Paint containerPaint = new Paint();
        containerPaint.setColor(Theme.getColor(Theme.key_dialogBackground));
        containerPaint.setShadowLayer(dp(2), 0, dp(-0.66f), 0x1e000000);
        container = new FrameLayout(context) {
            private int contentHeight = Integer.MAX_VALUE;
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int fullWidth = MeasureSpec.getSize(widthMeasureSpec);
                int fullHeight = MeasureSpec.getSize(widthMeasureSpec);
                int minHeight = (int) (AndroidUtilities.displayMetrics.heightPixels * heightMaxPercent);
                if (textsView != null && textsView.getMeasuredHeight() <= 0) {
                    textsView.measure(
                        MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec) - textsView.getPaddingLeft() - textsView.getPaddingRight() - textsContainerView.getPaddingLeft() - textsContainerView.getPaddingRight(), MeasureSpec.EXACTLY),
                        0
                    );
                }
                int fromHeight = Math.min(minHeight, minHeight());
                int height = (int) (fromHeight + (AndroidUtilities.displayMetrics.heightPixels - fromHeight) * containerOpenAnimationT);
                updateCanExpand();
                super.onMeasure(
                    MeasureSpec.makeMeasureSpec(
                        (int) Math.max(fullWidth * 0.8f, Math.min(dp(480), fullWidth)),
                        MeasureSpec.getMode(widthMeasureSpec)
                    ),
                    MeasureSpec.makeMeasureSpec(
                        height,
                        MeasureSpec.EXACTLY
                    )
                );
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                contentHeight = Math.min(contentHeight, bottom - top);
            }

            private Path containerPath = new Path();
            private RectF containerRect = new RectF();
            private RectF rectF = new RectF();
            @Override
            protected void onDraw(Canvas canvas) {
                int w = getWidth(), h = getHeight(), r = dp(12 * (1f - containerOpenAnimationT));
                canvas.clipRect(0, 0, w, h);

                containerRect.set(0, 0, w, h + r);
                canvas.translate(0, (1f - openingT) * h);

                canvas.drawRoundRect(containerRect, r, r, containerPaint);
                super.onDraw(canvas);
            }
        };
        container.setWillNotDraw(false);

        header = new FrameLayout(context);

        titleView = new TextView(context);
        titleView.setPivotX(LocaleController.isRTL ? titleView.getWidth() : 0);
        titleView.setPivotY(0);
        titleView.setLines(1);
        titleView.setText(LocaleController.getString("AutomaticTranslation", R.string.AutomaticTranslation));
        titleView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(19));
        header.addView(titleView, titleLayout = LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.FILL_HORIZONTAL | Gravity.TOP,
                22, 22,22, 0
        ));
        titleView.post(() -> {
            titleView.setPivotX(LocaleController.isRTL ? titleView.getWidth() : 0);
        });

        subtitleView = new LinearLayout(context);
        subtitleView.setOrientation(LinearLayout.HORIZONTAL);
        if (Build.VERSION.SDK_INT >= 17) {
            subtitleView.setLayoutDirection(LocaleController.isRTL ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        }
        subtitleView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);

        String fromLanguageName = languageName(fromLanguage);
        subtitleFromView = new InlineLoadingTextView(context, fromLanguageName == null ? languageName(toLanguage) : fromLanguageName, dp(14), Theme.getColor(Theme.key_player_actionBarSubtitle)) {
            @Override
            protected void onLoadAnimation(float t) {
                MarginLayoutParams lp = (MarginLayoutParams) subtitleFromView.getLayoutParams();
                if (lp != null) {
                    if (LocaleController.isRTL) {
                        lp.leftMargin = dp(2f - t * 6f);
                    } else {
                        lp.rightMargin = dp(2f - t * 6f);
                    }
                    subtitleFromView.setLayoutParams(lp);
                }
            }
        };
        subtitleFromView.showLoadingText = false;
        subtitleArrowView = new ImageView(context);
        subtitleArrowView.setImageResource(R.drawable.search_arrow);
        subtitleArrowView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_player_actionBarSubtitle), PorterDuff.Mode.MULTIPLY));
        if (LocaleController.isRTL) {
            subtitleArrowView.setScaleX(-1f);
        }

        subtitleToView = new TextView(context);
        subtitleToView.setLines(1);
        subtitleToView.setTextColor(Theme.getColor(Theme.key_player_actionBarSubtitle));
        subtitleToView.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(14));
        subtitleToView.setText(languageName(toLanguage));

        if (LocaleController.isRTL) {
            subtitleView.setPadding(InlineLoadingTextView.paddingHorizontal, 0, 0, 0);
            subtitleView.addView(subtitleToView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
            subtitleView.addView(subtitleArrowView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 3, 1, 0, 0));
            subtitleView.addView(subtitleFromView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 2, 0, 0, 0));
        } else {
            subtitleView.setPadding(0, 0, InlineLoadingTextView.paddingHorizontal, 0);
            subtitleView.addView(subtitleFromView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 0, 2, 0));
            subtitleView.addView(subtitleArrowView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 1, 3, 0));
            subtitleView.addView(subtitleToView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
        }
        if (fromLanguageName != null) {
            subtitleFromView.set(fromLanguageName);
        }

        header.addView(subtitleView, subtitleLayout = LayoutHelper.createFrame(
            LayoutHelper.MATCH_PARENT,
            LayoutHelper.WRAP_CONTENT,
            Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT),
            22 - LoadingTextView2.paddingHorizontal / AndroidUtilities.density,
            47 - LoadingTextView2.paddingVertical / AndroidUtilities.density,
            22 - LoadingTextView2.paddingHorizontal / AndroidUtilities.density,
            0
        ));

        backButton = new ImageView(context);
        backButton.setImageResource(R.drawable.ic_ab_back);
        backButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogTextBlack), PorterDuff.Mode.MULTIPLY));
        backButton.setScaleType(ImageView.ScaleType.FIT_CENTER);
        backButton.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
        backButton.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector)));
        backButton.setClickable(false);
        backButton.setAlpha(0f);
        backButton.setOnClickListener(e -> dismiss());
        header.addView(backButton, backLayout = LayoutHelper.createFrame(56, 56, Gravity.LEFT | Gravity.CENTER_HORIZONTAL));

        headerShadowView = new FrameLayout(context);
        headerShadowView.setBackgroundColor(Theme.getColor(Theme.key_dialogShadowLine));
        headerShadowView.setAlpha(0);
        header.addView(headerShadowView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));

        header.setClipChildren(false);
        container.addView(header, headerLayout = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 70, Gravity.FILL_HORIZONTAL | Gravity.TOP));

        scrollView = new NestedScrollView(context) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                return allowScroll && containerOpenAnimationT >= 1f && canExpand() && super.onInterceptTouchEvent(ev);
            }

            @Override
            public void onNestedScroll(@NonNull View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {
                super.onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed);
            }

            @Override
            protected void onScrollChanged(int l, int t, int oldl, int oldt) {
                super.onScrollChanged(l, t, oldl, oldt);
                if (checkForNextLoading()) {
                    openAnimationTo(1f, true);
                }
            }
        };
        scrollView.setClipChildren(true);

        allTextsView = new TextView(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MOST_SPEC);
            }
            private Paint pressedLinkPaint = null;
            private Path pressedLinkPath = new Path() {
                private RectF rectF = new RectF();
                @Override
                public void addRect(float left, float top, float right, float bottom, @NonNull Direction dir) {
                    rectF.set(left - LoadingTextView2.paddingHorizontal / 2, top - LoadingTextView2.paddingVertical, right + LoadingTextView2.paddingHorizontal / 2, bottom + LoadingTextView2.paddingVertical);
                    addRoundRect(rectF, dp(4), dp(4), Direction.CW);
                }
            };
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                canvas.translate(getPaddingLeft(), getPaddingTop());
                if (pressedLink != null) {
                    try {
                        Layout layout = getLayout();
                        int start = allTexts.getSpanStart(pressedLink);
                        int end = allTexts.getSpanEnd(pressedLink);
                        layout.getSelectionPath(start, end, pressedLinkPath);

                        if (pressedLinkPaint == null) {
                            pressedLinkPaint = new Paint();
                            pressedLinkPaint.setColor(Theme.getColor(Theme.key_chat_linkSelectBackground));
                        }
                        canvas.drawPath(pressedLinkPath, pressedLinkPaint);
                    } catch (Exception e) { }
                }
            }
            @Override
            public boolean onTextContextMenuItem(int id) {
                if (id == android.R.id.copy && isFocused()) {
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                    android.content.ClipData clip = android.content.ClipData.newPlainText(
                    "label",
                        getText().subSequence(
                            Math.max(0, Math.min(getSelectionStart(), getSelectionEnd())),
                            Math.max(0, Math.max(getSelectionStart(), getSelectionEnd()))
                        )
                    );
                    clipboard.setPrimaryClip(clip);
                    BulletinFactory.of(bulletinContainer, null).createCopyBulletin(LocaleController.getString("TextCopied", R.string.TextCopied)).show();
                    clearFocus();
                    return true;
                } else
                    return super.onTextContextMenuItem(id);
            }
        };
        allTextsView.setTextColor(0x00000000);
        allTextsView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        allTextsView.setTextIsSelectable(!noforwards);
        allTextsView.setHighlightColor(Theme.getColor(Theme.key_chat_inTextSelectionHighlight));
        int handleColor = Theme.getColor(Theme.key_chat_TextSelectionCursor);
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                Drawable left = allTextsView.getTextSelectHandleLeft();
                left.setColorFilter(handleColor, PorterDuff.Mode.SRC_IN);
                allTextsView.setTextSelectHandleLeft(left);

                Drawable right = allTextsView.getTextSelectHandleRight();
                right.setColorFilter(handleColor, PorterDuff.Mode.SRC_IN);
                allTextsView.setTextSelectHandleRight(right);
            }
        } catch (Exception e) {}
        allTextsView.setMovementMethod(new LinkMovementMethod());

        textsView = new TextBlocksLayout(context, dp(16), Theme.getColor(Theme.key_dialogTextBlack), allTextsView);
        textsView.setPadding(
            dp(22) - LoadingTextView2.paddingHorizontal,
            dp(12) - LoadingTextView2.paddingVertical,
            dp(22) - LoadingTextView2.paddingHorizontal,
            dp(12) - LoadingTextView2.paddingVertical
        );
        for (CharSequence blockText : textBlocks)
            textsView.addBlock(blockText);

        textsContainerView = new FrameLayout(context);
        textsContainerView.addView(textsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        scrollView.addView(textsContainerView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f));

        container.addView(scrollView, scrollViewLayout = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL, 0, 70, 0, 81));

        fetchNext();

        buttonShadowView = new FrameLayout(context);
        buttonShadowView.setBackgroundColor(Theme.getColor(Theme.key_dialogShadowLine));
        container.addView(buttonShadowView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, 0, 0, 0, 80));

        buttonTextView = new TextView(context);
        buttonTextView.setLines(1);
        buttonTextView.setSingleLine(true);
        buttonTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        buttonTextView.setEllipsize(TextUtils.TruncateAt.END);
        buttonTextView.setGravity(Gravity.CENTER);
        buttonTextView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        buttonTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        buttonTextView.setText(LocaleController.getString("CloseTranslation", R.string.CloseTranslation));

        buttonView = new FrameLayout(context);
        buttonView.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(4), Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        buttonView.addView(buttonTextView);
        buttonView.setOnClickListener(e -> dismiss());

        container.addView(buttonView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM, 16, 16, 16, 16));
        contentView.addView(container, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));

        bulletinContainer = new FrameLayout(context);
        contentView.addView(bulletinContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 0, 0, 0, 81));
    }
    public void showDim(boolean enable) {
        contentView.setBackground(enable ? backDrawable : null);
    }

    private boolean scrollAtBottom() {
        View view = (View) scrollView.getChildAt(scrollView.getChildCount() - 1);
        int bottom = view.getBottom();
        LoadingTextView2 lastUnloadedBlock = textsView.getFirstUnloadedBlock();
        if (lastUnloadedBlock != null) {
            bottom = lastUnloadedBlock.getTop();
        }
        int diff = (bottom - (scrollView.getHeight() + scrollView.getScrollY()));
        return diff <= textsContainerView.getPaddingBottom();
    }

    private void setScrollY(float t) {
        openAnimation(t);
        openingT = Math.max(Math.min(1f + t, 1), 0);
        backDrawable.setAlpha((int) (openingT * 51));
        container.invalidate();
        bulletinContainer.setTranslationY((1f - openingT) * Math.min(minHeight(), displayMetrics.heightPixels * heightMaxPercent));
    }
    private void scrollYTo(float t) {
        scrollYTo(t, null);
    }
    private void scrollYTo(float t, Runnable onAnimationEnd) {
        openAnimationTo(t, false, onAnimationEnd);
        openTo(1f + t, false);
    }
    private float fromScrollY = 0;
    private float getScrollY() {
        return Math.max(Math.min(containerOpenAnimationT - (1 - openingT), 1), 0);
    }

    private boolean hasSelection() {
        return allTextsView.hasSelection();
    }

    private Rect containerRect = new Rect();
    private Rect textRect = new Rect();
    private Rect translateMoreRect = new Rect();
    private Rect buttonRect = new Rect();
    private Rect backRect = new Rect();
    private Rect scrollRect = new Rect();
    private float fromY = 0;
    private boolean pressedOutside = false;
    private boolean maybeScrolling = false;
    private boolean scrolling = false;
    private boolean fromScrollRect = false;
    private boolean fromTranslateMoreView = false;
    private float fromScrollViewY = 0;
    private Spannable allTexts = null;
    private ClickableSpan pressedLink;
    @Override
    public boolean dispatchTouchEvent(@NonNull MotionEvent event) {
        try {
            float x = event.getX();
            float y = event.getY();

            container.getGlobalVisibleRect(containerRect);
            if (!containerRect.contains((int) x, (int) y)) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    pressedOutside = true;
                    return true;
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (pressedOutside) {
                        pressedOutside = false;
                        dismiss();
                        return true;
                    }
                }
            }

            try {
                allTextsView.getGlobalVisibleRect(textRect);
                if (textRect.contains((int) x, (int) y) && !maybeScrolling) {
                    Layout allTextsLayout = allTextsView.getLayout();
                    int tx = (int) (x - allTextsView.getLeft() - container.getLeft()),
                            ty = (int) (y - allTextsView.getTop() - container.getTop() - scrollView.getTop() + scrollView.getScrollY());
                    final int line = allTextsLayout.getLineForVertical(ty);
                    final int off = allTextsLayout.getOffsetForHorizontal(line, tx);

                    final float left = allTextsLayout.getLineLeft(line);
                    if (allTexts != null && allTexts instanceof Spannable && left <= tx && left + allTextsLayout.getLineWidth(line) >= tx) {
                        ClickableSpan[] links = allTexts.getSpans(off, off, ClickableSpan.class);
                        if (links != null && links.length >= 1) {
                            if (event.getAction() == MotionEvent.ACTION_UP && pressedLink == links[0]) {
                                pressedLink.onClick(allTextsView);
                                pressedLink = null;
                                allTextsView.setTextIsSelectable(!noforwards);
                            } else if (event.getAction() == MotionEvent.ACTION_DOWN) {
                                pressedLink = links[0];
                            }
                            allTextsView.invalidate();
                            return true;
                        }
                    }
                }
                if (pressedLink != null) {
                    allTextsView.invalidate();
                    pressedLink = null;
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }

            scrollView.getGlobalVisibleRect(scrollRect);
            backButton.getGlobalVisibleRect(backRect);
            buttonView.getGlobalVisibleRect(buttonRect);
            if (pressedLink == null && /*!(scrollRect.contains((int) x, (int) y) && !canExpand() && containerOpenAnimationT < .5f && !scrolling) &&*/ !hasSelection()) {
                if (
                    !backRect.contains((int) x, (int) y) &&
                    !buttonRect.contains((int) x, (int) y) &&
                    event.getAction() == MotionEvent.ACTION_DOWN
                ) {
                    fromScrollRect = scrollRect.contains((int) x, (int) y) && (containerOpenAnimationT > 0 || !canExpand());
                    maybeScrolling = true;
                    scrolling = scrollRect.contains((int) x, (int) y) && textsView.getBlocksCount() > 0 && !((LoadingTextView2) textsView.getBlockAt(0)).loaded;
                    fromY = y;
                    fromScrollY = getScrollY();
                    fromScrollViewY = scrollView.getScrollY();
                    return super.dispatchTouchEvent(event) || true;
                } else if (maybeScrolling && (event.getAction() == MotionEvent.ACTION_MOVE || event.getAction() == MotionEvent.ACTION_UP)) {
                    float dy = fromY - y;
                    if (fromScrollRect) {
                        dy = -Math.max(0, -(fromScrollViewY + dp(48)) - dy);
                        if (dy < 0) {
                            scrolling = true;
                            allTextsView.setTextIsSelectable(false);
                        }
                    } else if (Math.abs(dy) > dp(4) && !fromScrollRect) {
                        scrolling = true;
                        allTextsView.setTextIsSelectable(false);
                        scrollView.stopNestedScroll();
                        allowScroll = false;
                    }
                    float fullHeight = AndroidUtilities.displayMetrics.heightPixels,
                          minHeight = Math.min(minHeight(), fullHeight * heightMaxPercent);
                    float scrollYPx = minHeight * (1f - -Math.min(Math.max(fromScrollY, -1), 0)) + (fullHeight - minHeight) * Math.min(1, Math.max(fromScrollY, 0)) + dy;
                    float scrollY = scrollYPx > minHeight ? (scrollYPx - minHeight) / (fullHeight - minHeight) : -(1f - scrollYPx / minHeight);
                    if (!canExpand()) {
                        scrollY = Math.min(scrollY, 0);
                    }
                    updateCanExpand();

                    if (scrolling) {
                        setScrollY(scrollY);
                        if (event.getAction() == MotionEvent.ACTION_UP) {
                            scrolling = false;
                            allTextsView.setTextIsSelectable(!noforwards);
                            maybeScrolling = false;
                            allowScroll = true;
                            scrollYTo(
                                Math.abs(dy) > dp(16) ?
                                    Math.round(fromScrollY) + (scrollY > fromScrollY ? 1f : -1f) * (float) Math.ceil(Math.abs(fromScrollY - scrollY)) :
                                    Math.round(fromScrollY),
                                () -> {
                                    contentView.post(this::checkForNextLoading);
                                }
                            );
                        }
                        return true;
                    }
                }
            }
            if (hasSelection() && maybeScrolling) {
                scrolling = false;
                allTextsView.setTextIsSelectable(!noforwards);
                maybeScrolling = false;
                allowScroll = true;
                scrollYTo(Math.round(fromScrollY));
            }
            return super.dispatchTouchEvent(event);
        } catch (Exception e) {
            e.printStackTrace();
            return super.dispatchTouchEvent(event);
        }
    }

    private float openingT = 0f;
    private ValueAnimator openingAnimator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        contentView.setPadding(0, 0, 0, 0);
        setContentView(contentView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        Window window = getWindow();

        window.setWindowAnimations(R.style.DialogNoAnimation);
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.dimAmount = 0;
        params.flags &= ~WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        params.flags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        if (Build.VERSION.SDK_INT >= 21) {
            params.flags |=
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR |
                WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
        }
        params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        window.setAttributes(params);

        container.forceLayout();
    }

    protected ColorDrawable backDrawable = new ColorDrawable(0xff000000) {
        @Override
        public void setAlpha(int alpha) {
            super.setAlpha(alpha);
            container.invalidate();
        }
    };
    @Override
    public void show() {
        super.show();

        openAnimation(0);
        openTo(1, true, true);
    }

    private boolean dismissed = false;
    @Override
    public void dismiss() {
        if (dismissed)
            return;
        dismissed = true;

        openTo(0, true);
    }
    private void openTo(float t, boolean priority) {
        openTo(t, priority, false);
    }
    private void openTo(float t) {
        openTo(t, false);
    }
    private float heightMaxPercent = .85f;

    private boolean fastHide = false;
    private boolean openingAnimatorPriority = false;
    private void openTo(float t, boolean priority, boolean setAfter) {
        final float T = Math.min(Math.max(t, 0), 1);
        if (openingAnimatorPriority && !priority) {
            return;
        }
        openingAnimatorPriority = priority;
        if (openingAnimator != null) {
            openingAnimator.cancel();
        }
        openingAnimator = ValueAnimator.ofFloat(openingT, T);
        backDrawable.setAlpha((int) (openingT * 51));
        openingAnimator.addUpdateListener(a -> {
            openingT = (float) a.getAnimatedValue();
            container.invalidate();
            backDrawable.setAlpha((int) (openingT * 51));
            bulletinContainer.setTranslationY((1f - openingT) * Math.min(minHeight(), displayMetrics.heightPixels * heightMaxPercent));
        });
        if (T <= 0f) {
            if (onDismiss != null) {
                onDismiss.run();
            }
        }
        openingAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                if (T <= 0f) {
                    dismissInternal();
                } else if (setAfter) {
                    allTextsView.setTextIsSelectable(!noforwards);
                    allTextsView.invalidate();
                    scrollView.stopNestedScroll();
                    openAnimation(T - 1f);
                }
                openingAnimatorPriority = false;
            }
        });
        openingAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        openingAnimator.setDuration((long) (Math.abs(openingT - T) * (fastHide ? 200 : 380)));
        openingAnimator.setStartDelay(setAfter ? 60 : 0);
        openingAnimator.start();
    }
    public void dismissInternal() {
        try {
            super.dismiss();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public String languageName(String locale) {
        if (locale == null || locale.equals("und") || locale.equals("auto")) {
            return null;
        }
        LocaleController.LocaleInfo localeInfo = LocaleController.getInstance().getBuiltinLanguageByPlural(locale);
        if (localeInfo == null) {
            return null;
        }
        boolean isCurrentLanguageEnglish = false;
        try {
            isCurrentLanguageEnglish = LocaleController.getInstance().getCurrentLocaleInfo().pluralLangCode.equals("en");
        } catch (Exception e) {}
        return isCurrentLanguageEnglish ? localeInfo.nameEnglish : localeInfo.name;
    }

    public void updateSourceLanguage() {
        if (languageName(fromLanguage) != null) {
            subtitleView.setAlpha(1);
            if (!subtitleFromView.loaded) {
                subtitleFromView.loaded(languageName(fromLanguage));
            }
        } else if (loaded) {
            subtitleView.animate().alpha(0).setDuration(150).start();
        }
    }

    private ArrayList<CharSequence> cutInBlocks(CharSequence full, int maxBlockSize) {
        ArrayList<CharSequence> blocks = new ArrayList<>();
        if (full == null) {
            return blocks;
        }
        while (full.length() > maxBlockSize) {
            String maxBlockStr = full.subSequence(0, maxBlockSize).toString();
            int n = -1;
            if (n == -1) n = maxBlockStr.lastIndexOf("\n\n");
            if (n == -1) n = maxBlockStr.lastIndexOf("\n");
            if (n == -1) n = maxBlockStr.lastIndexOf(". ");
            blocks.add(full.subSequence(0, n + 1));
            full = full.subSequence(n + 1, full.length());
        }
        if (full.length() > 0) {
            blocks.add(full);
        }
        return blocks;
    }

    private boolean loading = false;
    private boolean loaded = false;
    private boolean fetchNext() {
        if (loading) {
            return false;
        }
        loading = true;

        if (blockIndex >= textBlocks.size()) {
            return false;
        }

        CharSequence blockText = textBlocks.get(blockIndex);

        fetchTranslation(
            blockText,
            Math.min((blockIndex + 1) * 1000, 3500),
            (String translatedText, String sourceLanguage) -> {
                loaded = true;
                Spannable spannable = new SpannableStringBuilder(translatedText);
                try {
                    MessageObject.addUrlsByPattern(false, spannable, false, 0, 0, true);
                    URLSpan[] urlSpans = spannable.getSpans(0, spannable.length(), URLSpan.class);
                    for (int i = 0; i < urlSpans.length; ++i) {
                        URLSpan urlSpan = urlSpans[i];
                        int start = spannable.getSpanStart(urlSpan),
                            end = spannable.getSpanEnd(urlSpan);
                        if (start == -1 || end == -1) {
                            continue;
                        }
                        spannable.removeSpan(urlSpan);
                        spannable.setSpan(
                            new ClickableSpan() {
                                @Override
                                public void onClick(@NonNull View view) {
                                    if (onLinkPress != null) {
                                        onLinkPress.run(urlSpan);
                                        fastHide = true;
                                        dismiss();
                                    } else {
                                        AlertsCreator.showOpenUrlAlert(fragment, urlSpan.getURL(), false, false);
                                    }
                                }

                                @Override
                                public void updateDrawState(@NonNull TextPaint ds) {
                                    int alpha = Math.min(ds.getAlpha(), ds.getColor() >> 24 & 0xff);
                                    if (!(urlSpan instanceof URLSpanNoUnderline)) {
                                        ds.setUnderlineText(true);
                                    }
                                    ds.setColor(Theme.getColor(Theme.key_dialogTextLink));
                                    ds.setAlpha(alpha);
                                }
                            },
                            start, end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        );
                    }

                    AndroidUtilities.addLinks(spannable, Linkify.WEB_URLS);
                    urlSpans = spannable.getSpans(0, spannable.length(), URLSpan.class);
                    for (int i = 0; i < urlSpans.length; ++i) {
                        URLSpan urlSpan = urlSpans[i];
                        int start = spannable.getSpanStart(urlSpan),
                            end = spannable.getSpanEnd(urlSpan);
                        if (start == -1 || end == -1) {
                            continue;
                        }
                        spannable.removeSpan(urlSpan);
                        spannable.setSpan(
                            new ClickableSpan() {
                                @Override
                                public void onClick(@NonNull View view) {
                                    AlertsCreator.showOpenUrlAlert(fragment, urlSpan.getURL(), false, false);
                                }

                                @Override
                                public void updateDrawState(@NonNull TextPaint ds) {
                                    int alpha = Math.min(ds.getAlpha(), ds.getColor() >> 24 & 0xff);
                                    if (!(urlSpan instanceof URLSpanNoUnderline))
                                        ds.setUnderlineText(true);
                                    ds.setColor(Theme.getColor(Theme.key_dialogTextLink));
                                    ds.setAlpha(alpha);
                                }
                            },
                            start, end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        );
                    }

                    spannable = (Spannable) Emoji.replaceEmoji(spannable, allTextsView.getPaint().getFontMetricsInt(), dp(14), false);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                allTexts = new SpannableStringBuilder(allTexts == null ? "" : allTexts).append(blockIndex == 0 ? "" : "\n").append(spannable);
                textsView.setWholeText(allTexts);

                LoadingTextView2 block = textsView.getBlockAt(blockIndex);
                if (block != null) {
                    block.loaded(spannable, () -> contentView.post(this::checkForNextLoading));
                }

                if (sourceLanguage != null) {
                    fromLanguage = sourceLanguage;
                    updateSourceLanguage();
                }

                blockIndex++;
                loading = false;
            },
            (boolean rateLimit) -> {
                if (rateLimit) {
                    Toast.makeText(getContext(), LocaleController.getString("TranslationFailedAlert1", R.string.TranslationFailedAlert1), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(), LocaleController.getString("TranslationFailedAlert2", R.string.TranslationFailedAlert2), Toast.LENGTH_SHORT).show();
                }

                if (blockIndex == 0) {
                    dismiss();
                }
            }
        );
        return true;
    }

    private boolean checkForNextLoading() {
        if (scrollAtBottom()) {
            fetchNext();
            return true;
        }
        return false;
    }

    public interface OnTranslationSuccess {
        public void run(String translated, String sourceLanguage);
    }
    public interface OnTranslationFail {
        public void run(boolean rateLimit);
    }
    private void fetchTranslation(CharSequence text, long minDuration, OnTranslationSuccess onSuccess, OnTranslationFail onFail) {
        new Thread() {
            @Override
            public void run() {
                String uri = "";
                HttpURLConnection connection = null;
                long start = SystemClock.elapsedRealtime();
                try {
                    uri = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=";
                    uri += Uri.encode(fromLanguage);
                    uri += "&tl=";
                    uri += Uri.encode(toLanguage);
                    uri += "&dt=t&ie=UTF-8&oe=UTF-8&otf=1&ssel=0&tsel=0&kc=7&dt=at&dt=bd&dt=ex&dt=ld&dt=md&dt=qca&dt=rw&dt=rm&dt=ss&q=";
                    uri += Uri.encode(text.toString());
                    connection = (HttpURLConnection) new URI(uri).toURL().openConnection();
                    connection.setRequestMethod("GET");
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.45 Safari/537.36");
                    connection.setRequestProperty("Content-Type", "application/json");

                    StringBuilder textBuilder = new StringBuilder();
                    try (Reader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), Charset.forName("UTF-8")))) {
                        int c = 0;
                        while ((c = reader.read()) != -1) {
                            textBuilder.append((char) c);
                        }
                    }
                    String jsonString = textBuilder.toString();

                    JSONTokener tokener = new JSONTokener(jsonString);
                    JSONArray array = new JSONArray(tokener);
                    JSONArray array1 = array.getJSONArray(0);
                    String sourceLanguage = null;
                    try {
                        sourceLanguage = array.getString(2);
                    } catch (Exception e2) {}
                    if (sourceLanguage != null && sourceLanguage.contains("-")) {
                        sourceLanguage = sourceLanguage.substring(0, sourceLanguage.indexOf("-"));
                    }
                    String result = "";
                    for (int i = 0; i < array1.length(); ++i) {
                        String blockText = array1.getJSONArray(i).getString(0);
                        if (blockText != null && !blockText.equals("null")) {
                            result += /*(i > 0 ? "\n" : "") +*/ blockText;
                        }
                    }
                    if (text.length() > 0 && text.charAt(0) == '\n') {
                        result = "\n" + result;
                    }
                    final String finalResult = result;
                    final String finalSourceLanguage = sourceLanguage;

                    long elapsed = SystemClock.elapsedRealtime() - start;
                    if (elapsed < minDuration) {
                        sleep(minDuration - elapsed);
                    }
                    AndroidUtilities.runOnUIThread(() -> {
                        if (onSuccess != null) {
                            onSuccess.run(finalResult, finalSourceLanguage);
                        }
                    });
                } catch (Exception e) {
                    try {
                        Log.e("translate", "failed to translate a text " + (connection != null ? connection.getResponseCode() : null) + " " + (connection != null ? connection.getResponseMessage() : null));
                    } catch (IOException ioException) {
                        ioException.printStackTrace();
                    }
                    e.printStackTrace();

                    if (onFail != null && !dismissed) {
                        try {
                            final boolean rateLimit = connection != null && connection.getResponseCode() == 429;
                            AndroidUtilities.runOnUIThread(() -> {
                                onFail.run(rateLimit);
                            });
                        } catch (Exception e2) {
                            AndroidUtilities.runOnUIThread(() -> {
                                onFail.run(false);
                            });
                        }
                    }
                }
            }
        }.start();
    }
    private static void translateText(int currentAccount, TLRPC.InputPeer peer, int msg_id, String from_lang, String to_lang) {
        TLRPC.TL_messages_translateText req = new TLRPC.TL_messages_translateText();

        req.peer = peer;
        req.msg_id = msg_id;
        req.flags |= 1;

        if (from_lang != null) {
            req.from_lang = from_lang;
            req.flags |= 4;
        }

        req.to_lang = to_lang;

        try {
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (error, res) -> {
                // TODO
            });
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static TranslateAlert showAlert(Context context, BaseFragment fragment, int currentAccount, TLRPC.InputPeer peer, int msgId, String fromLanguage, String toLanguage, CharSequence text, boolean noforwards, OnLinkPress onLinkPress, Runnable onDismiss) {
        TranslateAlert alert = new TranslateAlert(fragment, context, currentAccount, peer, msgId, fromLanguage, toLanguage, text, noforwards, onLinkPress, onDismiss);
        if (fragment != null) {
            if (fragment.getParentActivity() != null) {
                fragment.showDialog(alert);
            }
        } else {
            alert.show();
        }
        return alert;
    }
    public static TranslateAlert showAlert(Context context, BaseFragment fragment, String fromLanguage, String toLanguage, CharSequence text, boolean noforwards, OnLinkPress onLinkPress, Runnable onDismiss) {
        TranslateAlert alert = new TranslateAlert(fragment, context, fromLanguage, toLanguage, text, noforwards, onLinkPress, onDismiss);
        if (fragment != null) {
            if (fragment.getParentActivity() != null) {
                fragment.showDialog(alert);
            }
        } else {
            alert.show();
        }
        return alert;
    }

    private static final int MOST_SPEC = View.MeasureSpec.makeMeasureSpec(999999, View.MeasureSpec.AT_MOST);
    public static class TextBlocksLayout extends ViewGroup {

        private TextView wholeTextView;
        private final int fontSize;
        private final int textColor;

        public TextBlocksLayout(Context context, int fontSize, int textColor, TextView wholeTextView) {
            super(context);

            this.fontSize = fontSize;
            this.textColor = textColor;

            if (wholeTextView != null) {
                wholeTextView.setPadding(LoadingTextView2.paddingHorizontal, LoadingTextView2.paddingVertical, LoadingTextView2.paddingHorizontal, LoadingTextView2.paddingVertical);
                addView(this.wholeTextView = wholeTextView);
            }
        }

        public void setWholeText(CharSequence wholeText) {
            // having focus on that text view can cause jumping scroll to the top after loading a new block
            // TODO(dkaraush): preserve selection after setting a new text
            wholeTextView.clearFocus();
            wholeTextView.setText(wholeText);
        }

        public LoadingTextView2 addBlock(CharSequence fromText) {
            LoadingTextView2 textView = new LoadingTextView2(getContext(), fromText, getBlocksCount() > 0, fontSize, textColor);
            addView(textView);
            if (wholeTextView != null) {
                wholeTextView.bringToFront();
            }
            return textView;
        }

        public int getBlocksCount() {
            return getChildCount() - (wholeTextView != null ? 1 : 0);
        }
        public LoadingTextView2 getBlockAt(int i) {
            View child = getChildAt(i);
            if (child instanceof LoadingTextView2) {
                return (LoadingTextView2) child;
            }
            return null;
        }

        public LoadingTextView2 getFirstUnloadedBlock() {
            final int count = getBlocksCount();
            for (int i = 0; i < count; ++i) {
                LoadingTextView2 block = getBlockAt(i);
                if (block != null && !block.loaded)
                    return block;
            }
            return null;
        }

        private static final int gap = -LoadingTextView2.paddingVertical * 4 + dp(.48f);
        public int height() {
            int height = 0;
            final int count = getBlocksCount();
            for (int i = 0; i < count; ++i) {
                height += getBlockAt(i).height();
            }
            return getPaddingTop() + height + getPaddingBottom();
        }

        protected void onHeightUpdated(int height) {}

        public void updateHeight() {
            boolean updated;
            int newHeight = height();
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
            if (lp == null) {
                lp = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, newHeight);
                updated = true;
            } else {
                updated = lp.height != newHeight;
                lp.height = newHeight;
            }

            if (updated) {
                this.setLayoutParams(lp);
                onHeightUpdated(newHeight);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            final int count = getBlocksCount();
            final int innerWidthMeasureSpec = MeasureSpec.makeMeasureSpec(
                MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight(),
                MeasureSpec.getMode(widthMeasureSpec)
            );
            for (int i = 0; i < count; ++i) {
                LoadingTextView2 block = getBlockAt(i);
                block.measure(innerWidthMeasureSpec, MOST_SPEC);
            }
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height(), MeasureSpec.EXACTLY));
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int y = 0, height = 0;
            final int count = getBlocksCount();
            for (int i = 0; i < count; ++i) {
                LoadingTextView2 block = getBlockAt(i);
                final int blockHeight = block.height();
                final int translationY = i > 0 ? gap : 0;
                block.layout(getPaddingLeft(), getPaddingTop() + y + translationY, r - l - getPaddingRight(), getPaddingTop() + y + blockHeight + translationY);
                y += blockHeight;
                if (i > 0 && i < count - 1) {
                    y += gap;
                }
                height += blockHeight;
            }

            wholeTextView.measure(
                MeasureSpec.makeMeasureSpec(r - l - getPaddingLeft() - getPaddingRight(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(b - t - getPaddingTop() - getPaddingBottom(), MeasureSpec.EXACTLY)
            );
            wholeTextView.layout(
                getPaddingLeft(),
                getPaddingTop(),
                (r - l) - getPaddingRight(),
                getPaddingTop() + wholeTextView.getMeasuredHeight()
            );
        }
    }


    public static class InlineLoadingTextView extends ViewGroup {

        public static final int paddingHorizontal = dp(6),
                                paddingVertical = 0;


        public boolean showLoadingText = true;

        private final TextView fromTextView;
        private final TextView toTextView;

        private final ValueAnimator loadingAnimator;

        private final long start = SystemClock.elapsedRealtime();
        public InlineLoadingTextView(Context context, CharSequence fromText, int fontSize, int textColor) {
            super(context);

            setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical);
            setClipChildren(false);
            setWillNotDraw(false);

            fromTextView = new TextView(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(MOST_SPEC, MOST_SPEC);
                }
            };
            fromTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize);
            fromTextView.setTextColor(textColor);
            fromTextView.setText(fromText);
            fromTextView.setLines(1);
            fromTextView.setMaxLines(1);
            fromTextView.setSingleLine(true);
            fromTextView.setEllipsize(null);
            addView(fromTextView);

            toTextView = new TextView(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(MOST_SPEC, MOST_SPEC);
                }
            };
            toTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize);
            toTextView.setTextColor(textColor);
            toTextView.setLines(1);
            toTextView.setMaxLines(1);
            toTextView.setSingleLine(true);
            toTextView.setEllipsize(null);
            addView(toTextView);

            int c1 = Theme.getColor(Theme.key_dialogBackground),
                c2 = Theme.getColor(Theme.key_dialogBackgroundGray);
            LinearGradient gradient = new LinearGradient(0, 0, gradientWidth, 0, new int[]{ c1, c2, c1 }, new float[] { 0, 0.67f, 1f }, Shader.TileMode.REPEAT);
            loadingPaint.setShader(gradient);

            loadingAnimator = ValueAnimator.ofFloat(0f, 1f);
            loadingAnimator.addUpdateListener(a -> invalidate());
            loadingAnimator.setDuration(Long.MAX_VALUE);
            loadingAnimator.start();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            fromTextView.measure(0, 0);
            toTextView.measure(0, 0);
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(
                    (int) AndroidUtilities.lerp(fromTextView.getMeasuredWidth(), toTextView.getMeasuredWidth(), loadingT) + getPaddingLeft() + getPaddingRight(),
                    MeasureSpec.EXACTLY
                ),
                MeasureSpec.makeMeasureSpec(
                    Math.max(fromTextView.getMeasuredHeight(), toTextView.getMeasuredHeight()),
                    MeasureSpec.EXACTLY
                )
            );
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            fromTextView.layout(getPaddingLeft(), getPaddingTop(), getPaddingLeft() + fromTextView.getMeasuredWidth(), getPaddingTop() + fromTextView.getMeasuredHeight());
            toTextView.layout(getPaddingLeft(), getPaddingTop(), getPaddingLeft() + toTextView.getMeasuredWidth(), getPaddingTop() + toTextView.getMeasuredHeight());
            updateWidth();
        }

        private void updateWidth() {
            boolean updated;

            int newWidth = (int) AndroidUtilities.lerp(fromTextView.getMeasuredWidth(), toTextView.getMeasuredWidth(), loadingT) + getPaddingLeft() + getPaddingRight();
            int newHeight = Math.max(fromTextView.getMeasuredHeight(), toTextView.getMeasuredHeight());
            LayoutParams lp = getLayoutParams();
            if (lp == null) {
                lp = new LinearLayout.LayoutParams(newWidth, newHeight);
                updated = true;
            } else {
                updated = lp.width != newWidth || lp.height != newHeight;
                lp.width = newWidth;
                lp.height = newHeight;
            }

            if (updated)
                setLayoutParams(lp);
        }

        protected void onLoadAnimation(float t) {}

        public boolean loaded = false;
        public float loadingT = 0f;
        private ValueAnimator loadedAnimator = null;
        public void loaded(CharSequence loadedText) {
            loaded(loadedText, 350,null);
        }
        public void loaded(CharSequence loadedText, Runnable onLoadEnd) {
            loaded(loadedText, 350, onLoadEnd);
        }
        public void loaded(CharSequence loadedText, long duration, Runnable onLoadEnd) {
            loaded = true;
            toTextView.setText(loadedText);

            if (loadingAnimator.isRunning()) {
                loadingAnimator.cancel();
            }
            if (loadedAnimator == null) {
                loadedAnimator = ValueAnimator.ofFloat(0f, 1f);
                loadedAnimator.addUpdateListener(a -> {
                    loadingT = (float) a.getAnimatedValue();
                    updateWidth();
                    invalidate();
                    onLoadAnimation(loadingT);
                });
                loadedAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (onLoadEnd != null)
                            onLoadEnd.run();
                    }
                });
                loadedAnimator.setDuration(duration);
                loadedAnimator.setInterpolator(CubicBezierInterpolator.EASE_BOTH);
                loadedAnimator.start();
            }
        }
        public void set(CharSequence loadedText) {
            loaded = true;
            toTextView.setText(loadedText);

            if (loadingAnimator.isRunning()) {
                loadingAnimator.cancel();
            }
            if (loadedAnimator != null) {
                loadedAnimator.cancel();
                loadedAnimator = null;
            }
            loadingT = 1f;
            requestLayout();
            updateWidth();
            invalidate();
            onLoadAnimation(1f);
        }

        private final RectF rect = new RectF();
        private final Path inPath = new Path(),
                tempPath = new Path(),
                loadingPath = new Path(),
                shadePath = new Path();
        private final Paint loadingPaint = new Paint();
        private final float gradientWidth = dp(350f);
        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();

            float cx = LocaleController.isRTL ? Math.max(w / 2f, w - 8f) : Math.min(w / 2f, 8f),
                    cy = Math.min(h / 2f, 8f),
                    R = (float) Math.sqrt(Math.max(
                        Math.max(cx*cx + cy*cy, (w-cx)*(w-cx) + cy*cy),
                        Math.max(cx*cx + (h-cy)*(h-cy), (w-cx)*(w-cx) + (h-cy)*(h-cy))
                    )),
                    r = loadingT * R;
            inPath.reset();
            inPath.addCircle(cx, cy, r, Path.Direction.CW);

            canvas.save();
            canvas.clipPath(inPath, Region.Op.DIFFERENCE);

            loadingPaint.setAlpha((int) ((1f - loadingT) * 255));
            float dx = gradientWidth - (((SystemClock.elapsedRealtime() - start) / 1000f * gradientWidth) % gradientWidth);
            shadePath.reset();
            shadePath.addRect(0, 0, w, h, Path.Direction.CW);

            loadingPath.reset();
            rect.set(0, 0, w, h);
            loadingPath.addRoundRect(rect, dp(4), dp(4), Path.Direction.CW);
            canvas.clipPath(loadingPath);
            canvas.translate(-dx, 0);
            shadePath.offset(dx, 0f, tempPath);
            canvas.drawPath(tempPath, loadingPaint);
            canvas.translate(dx, 0);
            canvas.restore();

            if (showLoadingText && fromTextView != null) {
                canvas.save();
                rect.set(0, 0, w, h);
                canvas.clipPath(inPath, Region.Op.DIFFERENCE);
                canvas.translate(paddingHorizontal, paddingVertical);
                canvas.saveLayerAlpha(rect, (int) (255 * .08f), Canvas.ALL_SAVE_FLAG);
                fromTextView.draw(canvas);
                canvas.restore();
                canvas.restore();
            }

            if (toTextView != null) {
                canvas.save();
                canvas.clipPath(inPath);
                canvas.translate(paddingHorizontal, paddingVertical);
                canvas.saveLayerAlpha(rect, (int) (255 * loadingT), Canvas.ALL_SAVE_FLAG);
                toTextView.draw(canvas);
                if (loadingT < 1f) {
                    canvas.restore();
                }
                canvas.restore();
            }
        }

        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            return false;
        }
    }

    public static class LoadingTextView2 extends ViewGroup {

        public static final int paddingHorizontal = dp(6),
                                paddingVertical = dp(1.5f);

        public boolean showLoadingText = true;

        private final TextView fromTextView;
        private final TextView toTextView;

        private final boolean scaleFromZero;
        private final ValueAnimator loadingAnimator;

        private final long start = SystemClock.elapsedRealtime();
        private float scaleT = 1f;
        public LoadingTextView2(Context context, CharSequence fromText, boolean scaleFromZero, int fontSize, int textColor) {
            super(context);

            setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical);
            setClipChildren(false);
            setWillNotDraw(false);

            fromTextView = new TextView(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(widthMeasureSpec, MOST_SPEC);
                }
            };
            fromTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize);
            fromTextView.setTextColor(textColor);
            fromTextView.setText(fromText);
            fromTextView.setLines(0);
            fromTextView.setMaxLines(0);
            fromTextView.setSingleLine(false);
            fromTextView.setEllipsize(null);
            addView(fromTextView);

            toTextView = new TextView(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(widthMeasureSpec, MOST_SPEC);
                }
            };
            toTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize);
            toTextView.setTextColor(textColor);
            toTextView.setLines(0);
            toTextView.setMaxLines(0);
            toTextView.setSingleLine(false);
            toTextView.setEllipsize(null);
            addView(toTextView);

            int c1 = Theme.getColor(Theme.key_dialogBackground),
                c2 = Theme.getColor(Theme.key_dialogBackgroundGray);
            LinearGradient gradient = new LinearGradient(0, 0, gradientWidth, 0, new int[]{ c1, c2, c1 }, new float[] { 0, 0.67f, 1f }, Shader.TileMode.REPEAT);
            loadingPaint.setShader(gradient);

            this.scaleFromZero = scaleFromZero;
            loadingAnimator = ValueAnimator.ofFloat(0f, 1f);
            if (scaleFromZero)
                scaleT = 0;
            loadingAnimator.addUpdateListener(a -> {
                invalidate();
                if (scaleFromZero) {
                    boolean scaleTWasNoFull = scaleT < 1f;
                    scaleT = Math.min(1, (SystemClock.elapsedRealtime() - start) / 400f);
                    if (scaleTWasNoFull) {
                        updateHeight();
                    }
                }
            });
            loadingAnimator.setDuration(Long.MAX_VALUE);
            loadingAnimator.start();
        }

        public int innerHeight() {
            return (int) (AndroidUtilities.lerp(fromTextView.getMeasuredHeight(), toTextView.getMeasuredHeight(), loadingT) * scaleT);
        }
        public int height() {
            return getPaddingTop() + innerHeight() + getPaddingBottom();
        }

        private void updateHeight() {
            ViewParent parent = getParent();
            if (parent instanceof TextBlocksLayout) {
                ((TextBlocksLayout) parent).updateHeight();
            }
        }

        public boolean loaded = false;
        private float loadingT = 0f;
        private ValueAnimator loadedAnimator = null;
        public void loaded(CharSequence loadedText, Runnable onLoadEnd) {
            loaded = true;
            toTextView.setText(loadedText);
            layout();

            if (loadingAnimator.isRunning()) {
                loadingAnimator.cancel();
            }
            if (loadedAnimator == null) {
                loadedAnimator = ValueAnimator.ofFloat(0f, 1f);
                loadedAnimator.addUpdateListener(a -> {
                    loadingT = (float) a.getAnimatedValue();
                    updateHeight();
                    invalidate();
                });
                loadedAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (onLoadEnd != null)
                            onLoadEnd.run();
                    }
                });
                loadedAnimator.setDuration(350);
                loadedAnimator.setInterpolator(CubicBezierInterpolator.EASE_BOTH);
                loadedAnimator.start();
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec),
                innerWidth = width - getPaddingLeft() - getPaddingRight();
            if (fromTextView.getMeasuredWidth() <= 0 || lastWidth != innerWidth) {
                measureChild(fromTextView, innerWidth);
                updateLoadingPath();
            }
            if (toTextView.getMeasuredWidth() <= 0 || lastWidth != innerWidth) {
                measureChild(toTextView, innerWidth);
            }
            lastWidth = innerWidth;
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height(), MeasureSpec.EXACTLY)
            );
        }

        int lastWidth = 0;
        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            layout(r - l - getPaddingLeft() - getPaddingRight(), true);
        }
        private void layout(int width, boolean force) {
            if (lastWidth != width || force) {
                layout(lastWidth = width);
            }
        }
        private void layout(int width) {
            measureChild(fromTextView, width);
            layoutChild(fromTextView, width);
            updateLoadingPath();
            measureChild(toTextView, width);
            layoutChild(toTextView, width);
            updateHeight();
        }
        private void layout() {
            layout(lastWidth);
        }
        private void measureChild(View view, int width) {
            view.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MOST_SPEC);
        }
        private void layoutChild(View view, int width) {
            view.layout(getPaddingLeft(), getPaddingTop(), getPaddingLeft() + width, getPaddingTop() + view.getMeasuredHeight());
        }

        private RectF fetchedPathRect = new RectF();
        private Path fetchPath = new Path() {
            private boolean got = false;

            @Override
            public void reset() {
                super.reset();
                got = false;
            }

            @Override
            public void addRect(float left, float top, float right, float bottom, @NonNull Direction dir) {
                if (!got) {
                    fetchedPathRect.set(left - paddingHorizontal, top - paddingVertical, right + paddingHorizontal, bottom + paddingVertical);
                    got = true;
                }
            }
        };
        private void updateLoadingPath() {
            if (fromTextView != null && fromTextView.getMeasuredWidth() > 0) {
                loadingPath.reset();
                Layout loadingLayout = fromTextView.getLayout();
                if (loadingLayout != null) {
                    CharSequence text = loadingLayout.getText();
                    final int lineCount = loadingLayout.getLineCount();
                    for (int i = 0; i < lineCount; ++i) {
                        float s = loadingLayout.getLineLeft(i),
                                e = loadingLayout.getLineRight(i),
                                l = Math.min(s, e),
                                r = Math.max(s, e);
                        int start = loadingLayout.getLineStart(i),
                              end = loadingLayout.getLineEnd(i);
                        boolean hasNonEmptyChar = false;
                        for (int j = start; j < end; ++j) {
                            char c = text.charAt(j);
                            if (c != '\n' && c != '\t' && c != ' ') {
                                hasNonEmptyChar = true;
                                break;
                            }
                        }
                        if (!hasNonEmptyChar)
                            continue;
                        fetchedPathRect.set(
                            l - paddingHorizontal,
                            loadingLayout.getLineTop(i) - paddingVertical,
                            r + paddingHorizontal,
                            loadingLayout.getLineBottom(i) + paddingVertical
                        );
                        loadingPath.addRoundRect(fetchedPathRect, dp(4), dp(4), Path.Direction.CW);
                    }
                }
            }
        }

        private final RectF rect = new RectF();
        private final Path inPath = new Path(),
                           tempPath = new Path(),
                           loadingPath = new Path(),
                           shadePath = new Path();
        private final Paint loadingPaint = new Paint();
        private final float gradientWidth = dp(350f);
        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();

            float cx = LocaleController.isRTL ? Math.max(w / 2f, w - 8f) : Math.min(w / 2f, 8f),
                  cy = Math.min(h / 2f, 8f),
                  R = (float) Math.sqrt(Math.max(
                    Math.max(cx*cx + cy*cy, (w-cx)*(w-cx) + cy*cy),
                    Math.max(cx*cx + (h-cy)*(h-cy), (w-cx)*(w-cx) + (h-cy)*(h-cy))
                  )),
                  r = loadingT * R;
            inPath.reset();
            inPath.addCircle(cx, cy, r, Path.Direction.CW);

            canvas.save();
            canvas.clipPath(inPath, Region.Op.DIFFERENCE);

            loadingPaint.setAlpha((int) ((1f - loadingT) * 255));
            float dx = gradientWidth - (((SystemClock.elapsedRealtime() - start) / 1000f * gradientWidth) % gradientWidth);
            shadePath.reset();
            shadePath.addRect(0, 0, w, h, Path.Direction.CW);

            canvas.translate(paddingHorizontal, paddingVertical);
            canvas.clipPath(loadingPath);
            canvas.translate(-paddingHorizontal, -paddingVertical);
            canvas.translate(-dx, 0);
            shadePath.offset(dx, 0f, tempPath);
            canvas.drawPath(tempPath, loadingPaint);
            canvas.translate(dx, 0);
            canvas.restore();

            if (showLoadingText && fromTextView != null) {
                canvas.save();
                rect.set(0, 0, w, h);
                canvas.clipPath(inPath, Region.Op.DIFFERENCE);
                canvas.translate(paddingHorizontal, paddingVertical);
                canvas.saveLayerAlpha(rect, (int) (255 * .08f), Canvas.ALL_SAVE_FLAG);
                fromTextView.draw(canvas);
                canvas.restore();
                canvas.restore();
            }

            if (toTextView != null) {
                canvas.save();
                canvas.clipPath(inPath);
                canvas.translate(paddingHorizontal, paddingVertical);
                canvas.saveLayerAlpha(rect, (int) (255 * loadingT), Canvas.ALL_SAVE_FLAG);
                toTextView.draw(canvas);
                if (loadingT < 1f) {
                    canvas.restore();
                }
                canvas.restore();
            }
        }

        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            return false;
        }
    }
}
