package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.displayMetrics;
import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.getSystemProperty;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.util.Log;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.core.widget.NestedScrollView;

import com.google.android.gms.vision.Frame;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.Paint.Input;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class TranslateAlert extends Dialog {
    private FrameLayout bulletinContainer;
//    private FrameLayout statusBar;
    private FrameLayout contentView;
    private FrameLayout container;
    private TextView titleView;
    private LinearLayout subtitleView;
    private LoadingTextView subtitleFromView;
    private ImageView subtitleArrowView;
    private TextView subtitleToView;
    private ImageView backButton;
    private FrameLayout header;
    private FrameLayout headerShadowView;
    private boolean scrollViewScrollable = false;
    private NestedScrollView scrollView;
    private LinearLayout textsView;
//    private TextView translateMoreView;
    private TextView buttonTextView;
    private FrameLayout buttonView;
    private FrameLayout buttonShadowView;
    private TextView allTextsView;
    private FrameLayout textsContainerView;
    private FrameLayout allTextsContainer;

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
        if (containerOpenAnimationT == t)
            return;
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
//        titleView.forceLayout();
//
//        statusBar.setAlpha(Math.max(0, (t - .8f) / .2f));
//        statusBar.setTranslationY(Math.max(0, (1f - (t - .9f) / .1f) * dp(48)));
//        statusBar.setScaleY(Math.max(0, (t - .8f) / .2f));

        subtitleLayout.setMargins(
            dp(lerp(22, 72, t)) - subtitleFromView.padHorz,
            dp(lerp(47, 30, t)) - subtitleFromView.padVert,
            subtitleLayout.rightMargin,
            subtitleLayout.bottomMargin
        );
        subtitleView.setLayoutParams(subtitleLayout);
//        subtitleView.forceLayout();

        backButton.setAlpha(t);
        backButton.setScaleX(.75f + .25f * t);
        backButton.setScaleY(.75f + .25f * t);
        backButton.setClickable(t > .5f);
        headerShadowView.setAlpha(scrollView.getScrollY() > 0 ? 1f : t);

        headerLayout.height = (int) lerp(dp(70), dp(56), t);
        header.setLayoutParams(headerLayout);
//        header.forceLayout();

        scrollViewLayout.setMargins(
            scrollViewLayout.leftMargin,
            (int) lerp(dp(70), dp(56), t),
            scrollViewLayout.rightMargin,
            scrollViewLayout.bottomMargin
        );
        scrollView.setLayoutParams(scrollViewLayout);

//        container.invalidate();
        container.requestLayout();
//        contentView.forceLayout();
//        allTextsView.setTextIsSelectable(t >= 1f);
//        for (int i = 0; i < textsView.getChildCount(); ++i) {
//            View child = textsView.getChildAt(i);
//            if (child instanceof LoadingTextView)
//                ((LoadingTextView) child).setTextIsSelectable(t >= 1f);
//        }
    }


    private boolean openAnimationToAnimatorPriority = false;
    private ValueAnimator openAnimationToAnimator = null;
    private void openAnimationTo(float to, boolean priority) {
        if (openAnimationToAnimatorPriority && !priority)
            return;
        openAnimationToAnimatorPriority = priority;
        to = Math.min(Math.max(to, 0), 1);
        if (openAnimationToAnimator != null)
            openAnimationToAnimator.cancel();
        openAnimationToAnimator = ValueAnimator.ofFloat(containerOpenAnimationT, to);
        openAnimationToAnimator.addUpdateListener(a -> openAnimation((float) a.getAnimatedValue()));
        openAnimationToAnimator.addListener(new Animator.AnimatorListener() {
            @Override public void onAnimationStart(Animator animator) { }
            @Override public void onAnimationRepeat(Animator animator) { }

            @Override
            public void onAnimationEnd(Animator animator) {
                openAnimationToAnimatorPriority = false;
            }

            @Override
            public void onAnimationCancel(Animator animator) {
                openAnimationToAnimatorPriority = false;
            }
        });
        openAnimationToAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
        openAnimationToAnimator.setDuration(220);
        openAnimationToAnimator.start();
        if (to >= .5 && blockIndex <= 1)
            fetchNext();
    }

    private int textsViewMinHeight = 0;
    private int minHeight() {
        return (textsView == null ? 0 : textsView.getMeasuredHeight()) + dp(
            66 + // header
            1 +  // button separator
            16 + // button top padding
            48 + // button
            16   // button bottom padding
        );
    }
    private boolean canExpand() {
        return (
            textsView.getChildCount() < textBlocks.size() ||
            minHeight() >= (AndroidUtilities.displayMetrics.heightPixels * heightMaxPercent) //||
//            (scrollView.canScrollVertically(1) || scrollView.canScrollVertically(-1))
        ) && textsView.getChildCount() > 0 && ((LoadingTextView) textsView.getChildAt(0)).loaded;
    }
    private void updateCanExpand() {
        boolean canExpand = canExpand();
        if (containerOpenAnimationT > 0f && !canExpand)
            openAnimationTo(0f, false);

        buttonShadowView.animate().alpha(canExpand ? 1f : 0f).setDuration((long) (Math.abs(buttonShadowView.getAlpha() - (canExpand ? 1f : 0f)) * 220)).start();
    }

    public interface OnLinkPress {
        public void run(URLSpan urlSpan);
    }

    private int textPadHorz, textPadVert;

    private int scrollShouldBe = -1;
    private boolean allowScroll = true;
    private ValueAnimator scrollerToBottom = null;
    private String fromLanguage, toLanguage;
    private CharSequence text;
    private BaseFragment fragment;
    private boolean noforwards;
    private OnLinkPress onLinkPress = null;
    public TranslateAlert(BaseFragment fragment, Context context, String fromLanguage, String toLanguage, CharSequence text, boolean noforwards, OnLinkPress onLinkPress) {
        this(fragment, context, -1, null, -1, fromLanguage, toLanguage, text, noforwards, onLinkPress);
    }
    public TranslateAlert(BaseFragment fragment, Context context, int currentAccount, TLRPC.InputPeer peer, int msgId, String fromLanguage, String toLanguage, CharSequence text, boolean noforwards, OnLinkPress onLinkPress) {
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

        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        } else if (Build.VERSION.SDK_INT >= 21) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
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

//        statusBar = new FrameLayout(context) {
//            @Override
//            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
//                int fullWidth = MeasureSpec.getSize(widthMeasureSpec);
//                super.onMeasure(
//                    MeasureSpec.makeMeasureSpec(
//                        (int) Math.max(fullWidth * 0.8f, Math.min(dp(480), fullWidth)),
//                        MeasureSpec.getMode(widthMeasureSpec)
//                    ),
//                    heightMeasureSpec
//                );
//            }
//        };
//        statusBar.setBackgroundColor(Theme.getColor(Theme.key_chat_attachEmptyImage));
//        statusBar.setPivotY(AndroidUtilities.statusBarHeight);
//        contentView.addView(statusBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, AndroidUtilities.statusBarHeight / AndroidUtilities.density, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, -AndroidUtilities.statusBarHeight / AndroidUtilities.density, 0, 0));

        Paint containerPaint = new Paint();
        containerPaint.setColor(Theme.getColor(Theme.key_dialogBackground));
        containerPaint.setShadowLayer(dp(2), 0, dp(-0.66f), 0x1e000000);
        container = new FrameLayout(context) {
            private int contentHeight = Integer.MAX_VALUE;
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int fullWidth = MeasureSpec.getSize(widthMeasureSpec);
                int fullHeight = MeasureSpec.getSize(widthMeasureSpec);
                boolean isPortrait = fullHeight > fullWidth;
//                int minHeight = (int) Math.min(dp(550), AndroidUtilities.displayMetrics.heightPixels * heightMaxPercent);
                int minHeight = (int) (AndroidUtilities.displayMetrics.heightPixels * heightMaxPercent);
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

//                containerPath.reset();
//                containerPath.moveTo(0, h);
//                rectF.set(0, 0, r + r, r + r);
//                containerPath.arcTo(rectF, 180, 90);
//                rectF.set(w - r - r, 0, w, r + r);
//                containerPath.arcTo(rectF, 270, 90);
//                containerPath.lineTo(w, h);
//                containerPath.close();
//                canvas.drawPath(containerPath, containerPaint);

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

//        String from = languageName(fromLanguage);
//        String to = languageName(toLanguage);
//        String subtitleText = LocaleController.formatString("FromLanguageToLanguage", R.string.FromLanguageToLanguage, (from != null ? from : ""), (to != null ? to : ""));
        subtitleView = new LinearLayout(context);
        subtitleView.setOrientation(LinearLayout.HORIZONTAL);
        if (Build.VERSION.SDK_INT >= 17)
            subtitleView.setLayoutDirection(LocaleController.isRTL ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        subtitleView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);

        textPadHorz = dp(6);
        textPadVert = dp(1.5f);

        String fromLanguageName = languageName(fromLanguage);
        subtitleFromView = new LoadingTextView(context, dp(6), dp(1.5f), fromLanguageName == null ? languageName(toLanguage) : fromLanguageName, false, true) {
            @Override
            protected void onLoadAnimation(float t) {
                MarginLayoutParams lp = (MarginLayoutParams) subtitleFromView.getLayoutParams();
                if (LocaleController.isRTL) {
                    lp.leftMargin = dp(2f - t * 6f);
                } else {
                    lp.rightMargin = dp(2f - t * 6f);
                }
                subtitleFromView.setLayoutParams(lp);
            }
        };
        subtitleFromView.showLoadingText(false);
        subtitleFromView.setLines(1);
        subtitleFromView.setTextColor(Theme.getColor(Theme.key_player_actionBarSubtitle));
        subtitleFromView.setTextSize(dp(14));
        if (fromLanguageName != null)
            subtitleFromView.setText(fromLanguageName);

        subtitleArrowView = new ImageView(context);
        subtitleArrowView.setImageResource(R.drawable.search_arrow);
        subtitleArrowView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_player_actionBarSubtitle), PorterDuff.Mode.MULTIPLY));
        if (LocaleController.isRTL)
            subtitleArrowView.setScaleX(-1f);

        subtitleToView = new TextView(context);
        subtitleToView.setLines(1);
        subtitleToView.setTextColor(Theme.getColor(Theme.key_player_actionBarSubtitle));
        subtitleToView.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(14));
        subtitleToView.setText(languageName(toLanguage));

        if (LocaleController.isRTL) {
            subtitleView.setPadding(subtitleFromView.padHorz, 0, textPadHorz - subtitleFromView.padHorz, 0);
            subtitleView.addView(subtitleToView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
            subtitleView.addView(subtitleArrowView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 3, 1, 0, 0));
            subtitleView.addView(subtitleFromView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 2, 0, 0, 0));
        } else {
            subtitleView.setPadding(textPadHorz - subtitleFromView.padHorz, 0, subtitleFromView.padHorz, 0);
            subtitleView.addView(subtitleFromView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 0, 2, 0));
            subtitleView.addView(subtitleArrowView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 1, 3, 0));
            subtitleView.addView(subtitleToView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
        }
        subtitleFromView.updateHeight();

        header.addView(subtitleView, subtitleLayout = LayoutHelper.createFrame(
            LayoutHelper.MATCH_PARENT,
            LayoutHelper.WRAP_CONTENT,
            Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT),
            22 - textPadHorz / AndroidUtilities.density,
            47 - textPadVert / AndroidUtilities.density,
            22 - textPadHorz / AndroidUtilities.density,
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
                if (scrollAtBottom() && fetchNext()) {
                    openAnimationTo(1f, true);
                }
            }
        };
//        scrollView.setOnTouchListener(new View.OnTouchListener() {
//            @Override
//            public boolean onTouch(View view, MotionEvent motionEvent) {
//                return allowScroll && containerOpenAnimationT >= 1f;
//            }
//        });
        scrollView.setClipChildren(true);

        textsView = new LinearLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(9999999, MeasureSpec.AT_MOST));
            }
        };
        textsView.setOrientation(LinearLayout.VERTICAL);
        textsView.setPadding(dp(22) - textPadHorz, dp(12) - textPadVert, dp(22) - textPadHorz, dp(12) - textPadVert);

//        translateMoreView = new TextView(context);
//        translateMoreView.setTextColor(Theme.getColor(Theme.key_dialogTextBlue));
//        translateMoreView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
//        translateMoreView.setText(LocaleController.getString("TranslateMore", R.string.TranslateMore));
//        translateMoreView.setVisibility(textBlocks.size() > 1 ? View.INVISIBLE : View.GONE);
//        translateMoreView.getPaint().setAntiAlias(true);
//        translateMoreView.getPaint().setFlags(Paint.ANTI_ALIAS_FLAG);
//        translateMoreView.setBackgroundDrawable(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_dialogLinkSelection), dp(1), dp(1)));
//        translateMoreView.setOnClickListener(e -> {
//            openAnimationTo(1f, true);
//            fetchNext();
//
//            if (containerOpenAnimationT >= 1f && canExpand()/* && atBottom*/) {
//                if (scrollerToBottom != null) {
//                    scrollerToBottom.cancel();
//                    scrollerToBottom = null;
//                }
//                allowScroll = false;
//                scrollView.stopNestedScroll();
//                scrollerToBottom = ValueAnimator.ofFloat(0f, 1f);
//                int fromScroll = scrollView.getScrollY();
//                scrollerToBottom.addUpdateListener(a -> {
//                    scrollView.setScrollY((int) (fromScroll + dp(150) * (float) a.getAnimatedValue()));
//                });
//                scrollerToBottom.addListener(new Animator.AnimatorListener() {
//                    @Override public void onAnimationRepeat(Animator animator) {}
//                    @Override public void onAnimationStart(Animator animator) {}
//                    @Override public void onAnimationEnd(Animator animator) {
//                        allowScroll = true;
//                    }
//                    @Override public void onAnimationCancel(Animator animator) { allowScroll = true; }
//                });
//                scrollerToBottom.setDuration(220);
//                scrollerToBottom.start();
//            }
//        });
//        translateMoreView.setPadding(textPadHorz, textPadVert, textPadHorz, textPadVert);
//        textsView.addView(translateMoreView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT,0, 0, 0, 0));

        Paint selectionPaint = new Paint();
        selectionPaint.setColor(Theme.getColor(Theme.key_chat_inTextSelectionHighlight));
        allTextsContainer = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(textsContainerView.getMeasuredHeight(), MeasureSpec.AT_MOST));
            }
        };
        allTextsContainer.setClipChildren(false);
        allTextsContainer.setClipToPadding(false);
        allTextsContainer.setPadding(dp(22), dp(12), dp(22), dp(12));

        allTextsView = new TextView(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(99999999, MeasureSpec.AT_MOST));
            }
            private Paint pressedLinkPaint = null;
            private Path pressedLinkPath = new Path() {
                private RectF rectF = new RectF();
                @Override
                public void addRect(float left, float top, float right, float bottom, @NonNull Direction dir) {
//                    super.addRect(left, top, right, bottom, dir);
                    rectF.set(left - textPadHorz / 2, top - textPadVert, right + textPadHorz / 2, bottom + textPadVert);
                    addRoundRect(rectF, dp(4), dp(4), Direction.CW);
                }
            };
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
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
        allTextsContainer.addView(allTextsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        textsContainerView = new FrameLayout(context);
        textsContainerView.addView(allTextsContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        textsContainerView.addView(textsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        scrollView.addView(textsContainerView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f));

        container.addView(scrollView, scrollViewLayout = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL, 0, 70, 0, 81));

//        translateMoreView.bringToFront();
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
//        setUseLightStatusBar(true);
    }

    private boolean scrollAtBottom() {
        View view = (View) scrollView.getChildAt(scrollView.getChildCount() - 1);
        int bottom = view.getBottom();
        if (textsView.getChildCount() > 0) {
            view = textsView.getChildAt(textsView.getChildCount() - 1);
            if (view instanceof LoadingTextView && !((LoadingTextView) view).loaded)
                bottom = view.getTop();
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
        openAnimationTo(t, false);
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
//            container.invalidate();

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
                allTextsContainer.getGlobalVisibleRect(textRect);
                if (textRect.contains((int) x, (int) y) && !scrolling) {
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
                            //                    return super.dispatchTouchEvent(event) || true;
                            return true;
                        } else if (pressedLink != null) {
                            allTextsView.invalidate();
                            pressedLink = null;
                        }
                    } else if (pressedLink != null) {
                        allTextsView.invalidate();
                        pressedLink = null;
                    }
                } else if (pressedLink != null) {
                    allTextsView.invalidate();
                    pressedLink = null;
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }

            scrollView.getGlobalVisibleRect(scrollRect);
            backButton.getGlobalVisibleRect(backRect);
            buttonView.getGlobalVisibleRect(buttonRect);
//            translateMoreView.getGlobalVisibleRect(translateMoreRect);
            fromTranslateMoreView = false; // translateMoreRect.contains((int) x, (int) y);
            if (pressedLink == null && /*!(scrollRect.contains((int) x, (int) y) && !canExpand() && containerOpenAnimationT < .5f && !scrolling) &&*/ !fromTranslateMoreView && !hasSelection()) {
                if (
                    !backRect.contains((int) x, (int) y) &&
                    !buttonRect.contains((int) x, (int) y) &&
                    event.getAction() == MotionEvent.ACTION_DOWN
                ) {
                    fromScrollRect = scrollRect.contains((int) x, (int) y) && (containerOpenAnimationT > 0 || !canExpand());
                    maybeScrolling = true;
                    scrolling = scrollRect.contains((int) x, (int) y) && textsView.getChildCount() > 0 && !((LoadingTextView) textsView.getChildAt(0)).loaded;
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
                            minHeight = Math.min(fullHeight, fullHeight * heightMaxPercent);
                    float scrollYPx = minHeight * (1f - -Math.min(Math.max(fromScrollY, -1), 0)) +
                            (fullHeight - minHeight) * Math.min(1, Math.max(fromScrollY, 0)) + dy;
                    float scrollY = scrollYPx > minHeight ? (scrollYPx - minHeight) / (fullHeight - minHeight) : -(1f - scrollYPx / minHeight);
                    if (!canExpand())
                        scrollY = Math.min(scrollY, 0);
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
                                    /*fromScrollRect && Math.ceil(fromScrollY) >= 1f ? -1f :*/ Math.round(fromScrollY) + (scrollY > fromScrollY ? 1f : -1f) * (float) Math.ceil(Math.abs(fromScrollY - scrollY)) :
                                    Math.round(fromScrollY)
                            );
                        }
                        //                    if (fromScrollRect)
                        //                        return super.dispatchTouchEvent(event) || true;
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

    private LoadingTextView addBlock(CharSequence startText, boolean scaleFromZero) {
        LoadingTextView textView = new LoadingTextView(getContext(), textPadHorz, textPadVert, startText, scaleFromZero, false) {
            boolean hadSelection = false;
            int selStart, selEnd;
            @Override
            protected void onLoadStart() {
                allTextsView.clearFocus();
            }
            @Override
            protected void onLoadEnd() {
//                hadSelection = hasSelection();
//                selStart = Selection.getSelectionStart(allTexts);
//                selEnd = Selection.getSelectionEnd(allTexts);
                scrollView.post(() -> {
                    allTextsView.setText(allTexts);
                    allTextsView.measure(MeasureSpec.makeMeasureSpec(allTextsContainer.getWidth() - allTextsContainer.getPaddingLeft() - allTextsContainer.getPaddingRight(), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(textsView.getHeight(), MeasureSpec.AT_MOST));
                    allTextsView.layout(
                        allTextsContainer.getLeft() + allTextsContainer.getPaddingLeft(),
                        allTextsContainer.getTop() + allTextsContainer.getPaddingTop(),
                        allTextsContainer.getLeft() + allTextsContainer.getPaddingLeft() + allTextsView.getMeasuredWidth(),
                        allTextsContainer.getTop() + allTextsContainer.getPaddingTop() + allTextsView.getMeasuredHeight()
                    );
//                    if (hadSelection)
//                        Selection.setSelection(allTexts, selStart, selEnd);
                });

                contentView.post(() -> {
                    if (scrollAtBottom())
                        fetchNext();
                });
            }
        };
//        textView.setLines(0);
//        textView.setMaxLines(0);
//        textView.setSingleLine(false);
//        textView.setEllipsizeNull();
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        textView.setTextSize(dp(16));
        textView.setTranslationY((textsView.getChildCount()/* - 1*/) * (textPadVert * -4f + dp(.48f)));
        textsView.addView(textView, textsView.getChildCount()/* - 1*/, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, 0, 0, 0, 0));
        return textView;
    }

    private float openingT = 0f;
    private ValueAnimator openingAnimator;

//    protected boolean useLightStatusBar = true;
//    protected boolean useLightNavBar;
//    public void setUseLightStatusBar(boolean value) {
//        useLightStatusBar = value;
//        if (Build.VERSION.SDK_INT >= 23) {
//            int color = Theme.getColor(Theme.key_actionBarDefault, null, true);
//            int flags = contentView.getSystemUiVisibility();
//            if (useLightStatusBar && color == 0xffffffff) {
//                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
//            } else {
//                flags &=~ View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
//            }
//            contentView.setSystemUiVisibility(flags);
//        }
//    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//
//        if (useLightStatusBar && Build.VERSION.SDK_INT >= 23) {
//            int color = Theme.getColor(Theme.key_actionBarDefault, null, true);
//            if (color == 0xffffffff) {
//                int flags = contentView.getSystemUiVisibility();
//                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
//                contentView.setSystemUiVisibility(flags);
//            }
//        }
//        if (useLightNavBar && Build.VERSION.SDK_INT >= 26) {
//            AndroidUtilities.setLightNavigationBar(getWindow(), false);
//        }

        contentView.setPadding(0, 0, 0, 0);
        setContentView(contentView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
//        int flags = contentView.getSystemUiVisibility();
//        flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
//        contentView.setSystemUiVisibility(flags);

        Window window = getWindow();

        window.setWindowAnimations(R.style.DialogNoAnimation);
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.dimAmount = 0;
        params.flags &= ~WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        params.flags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        if (Build.VERSION.SDK_INT >= 21) {
            params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR |
                WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
        }
        params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
//        if (Build.VERSION.SDK_INT >= 28) {
//            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
//        }
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
        if (openingAnimatorPriority && !priority)
            return;
        openingAnimatorPriority = priority;
        if (openingAnimator != null)
            openingAnimator.cancel();
        openingAnimator = ValueAnimator.ofFloat(openingT, T);
        backDrawable.setAlpha((int) (openingT * 51));
        openingAnimator.addUpdateListener(a -> {
            openingT = (float) a.getAnimatedValue();
            container.invalidate();
            backDrawable.setAlpha((int) (openingT * 51));
            bulletinContainer.setTranslationY((1f - openingT) * Math.min(minHeight(), displayMetrics.heightPixels * heightMaxPercent));
        });
        openingAnimator.addListener(new Animator.AnimatorListener() {
            @Override public void onAnimationCancel(Animator animator) {
                if (T <= 0f)
                    dismissInternal();
                else if (setAfter) {
                    allTextsView.setTextIsSelectable(!noforwards);
                    allTextsView.invalidate();
                    scrollView.stopNestedScroll();
                    openAnimation(T - 1f);
                }
                openingAnimatorPriority = false;
            }
            @Override public void onAnimationEnd(Animator animator) {
                if (T <= 0f)
                    dismissInternal();
                else if (setAfter) {
                    allTextsView.setTextIsSelectable(!noforwards);
                    allTextsView.invalidate();
                    scrollView.stopNestedScroll();
                    openAnimation(T - 1f);
                }
                openingAnimatorPriority = false;
            }
            @Override public void onAnimationRepeat(Animator animator) { }
            @Override public void onAnimationStart(Animator animator) { }
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
        if (locale == null || locale.equals("und") || locale.equals("auto"))
            return null;
//        if (locale != null && !locale.equals("und") && !locale.equals("auto")) {
//            String passportLang = LocaleController.getString("PassportLanguage_" + locale.toUpperCase());
//            if (passportLang != null && passportLang.length() > 0)
//                return passportLang;
//        }
        LocaleController.LocaleInfo localeInfo = LocaleController.getInstance().getBuiltinLanguageByPlural(locale);
        boolean isCurrentLanguageEnglish = false;
        try {
            isCurrentLanguageEnglish = LocaleController.getInstance().getCurrentLocaleInfo().pluralLangCode.equals("en");
        } catch (Exception e) {}
        if (localeInfo != null && ((isCurrentLanguageEnglish && localeInfo.nameEnglish != null) || (!isCurrentLanguageEnglish && localeInfo.name != null)))
            return isCurrentLanguageEnglish ? localeInfo.nameEnglish : localeInfo.name;
        return null;
    }

    public void updateSourceLanguage() {
        if (languageName(fromLanguage) != null) {
            subtitleView.setAlpha(1);
            if (!subtitleFromView.loaded)
                subtitleFromView.setText(languageName(fromLanguage));
        } else if (loaded) {
            subtitleView.animate().alpha(0).setDuration(150).start();
        }
    }

    private ArrayList<CharSequence> cutInBlocks(CharSequence full, int maxBlockSize) {
        ArrayList<CharSequence> blocks = new ArrayList<>();
        if (full == null)
            return blocks;
        while (full.length() > maxBlockSize) {
            String maxBlockStr = full.subSequence(0, maxBlockSize).toString();
            int n = -1;
            if (n == -1) n = maxBlockStr.lastIndexOf("\n\n");
            if (n == -1) n = maxBlockStr.lastIndexOf("\n");
            if (n == -1) n = maxBlockStr.lastIndexOf(". ");
            blocks.add(full.subSequence(0, n + 1));
            full = full.subSequence(n + 1, full.length());
        }
        if (full.length() > 0)
            blocks.add(full);
        return blocks;
    }


    public void showTranslateMoreView(boolean show) {
//        translateMoreView.setClickable(show);
//        translateMoreView.setVisibility(textBlocks.size() > 1 ? View.VISIBLE : View.GONE);
//        translateMoreView
//            .animate()
////            .translationX(show ? 0f : dp(4))
//            .alpha(show ? 1f : 0f)
//            .withEndAction(() -> {
//                if (!show)
//                    translateMoreView.setVisibility(textBlocks.size() > 1 ? View.INVISIBLE : View.GONE);
//            })
//            .setInterpolator(CubicBezierInterpolator.EASE_OUT)
//            .setDuration((long) (Math.abs(translateMoreView.getAlpha() - (show ? 1f : 0f)) * 85))
//            .start();
    }

    private boolean loading = false;
    private boolean loaded = false;
    private LoadingTextView lastLoadingBlock = null;
    private boolean fetchNext() {
        if (loading)
            return false;
        loading = true;

        showTranslateMoreView(false);
        if (blockIndex >= textBlocks.size())
            return false;

        CharSequence blockText = textBlocks.get(blockIndex);
        lastLoadingBlock = lastLoadingBlock == null ? addBlock(blockText, blockIndex != 0) : lastLoadingBlock;
        lastLoadingBlock.loading = true;

        fetchTranslation(
            blockText,
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
                        if (start == -1 || end == -1)
                            continue;
                        spannable.removeSpan(urlSpan);
                        spannable.setSpan(
                            new ClickableSpan() {
                                @Override
                                public void onClick(@NonNull View view) {
                                    if (onLinkPress != null) {
                                        onLinkPress.run(urlSpan);
                                        fastHide = true;
                                        dismiss();
                                    } else
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

                    AndroidUtilities.addLinks(spannable, Linkify.WEB_URLS);
                    urlSpans = spannable.getSpans(0, spannable.length(), URLSpan.class);
                    for (int i = 0; i < urlSpans.length; ++i) {
                        URLSpan urlSpan = urlSpans[i];
                        int start = spannable.getSpanStart(urlSpan),
                                end = spannable.getSpanEnd(urlSpan);
                        if (start == -1 || end == -1)
                            continue;
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

//                boolean hadSelection = hasSelection();
//                int selectionStart = Selection.getSelectionStart(allTexts),
//                    selectionEnd   = Selection.getSelectionEnd(allTexts);
                allTexts = new SpannableStringBuilder(allTextsView.getText()).append(blockIndex == 0 ? "" : "\n").append(spannable);
//                if (hadSelection)
//                    Selection.setSelection(allTexts, selectionStart, selectionEnd);

                if (lastLoadingBlock != null) {
                    lastLoadingBlock.setText(spannable);
                    lastLoadingBlock = null;
                }


                fromLanguage = sourceLanguage;
                updateSourceLanguage();

                blockIndex++;
                showTranslateMoreView(blockIndex < textBlocks.size());
                loading = false;

                if (blockIndex < textBlocks.size()) {
                    CharSequence nextTextBlock = textBlocks.get(blockIndex);
                    lastLoadingBlock = addBlock(nextTextBlock, true);
                    lastLoadingBlock.loading = false;
                }
            },
            (boolean rateLimit) -> {
                if (rateLimit)
                    Toast.makeText(getContext(), LocaleController.getString("TranslationFailedAlert1", R.string.TranslationFailedAlert1), Toast.LENGTH_SHORT).show();
                else
                    Toast.makeText(getContext(), LocaleController.getString("TranslationFailedAlert2", R.string.TranslationFailedAlert2), Toast.LENGTH_SHORT).show();
                if (blockIndex == 0)
                    dismiss();
            }
        );
        return true;
    }

    private String[] userAgents = new String[] {
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.45 Safari/537.36", // 13.5%
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.110 Safari/537.36", // 6.6%
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:94.0) Gecko/20100101 Firefox/94.0", // 6.4%
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:95.0) Gecko/20100101 Firefox/95.0", // 6.2%
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.93 Safari/537.36", // 5.2%
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.55 Safari/537.36" // 4.8%
    };
    public interface OnTranslationSuccess {
        public void run(String translated, String sourceLanguage);
    }
    public interface OnTranslationFail {
        public void run(boolean rateLimit);
    }
    private long minFetchingDuration = 1000;
    private void fetchTranslation(CharSequence text, OnTranslationSuccess onSuccess, OnTranslationFail onFail) {
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
                    connection.setRequestProperty("User-Agent", userAgents[(int) Math.round(Math.random() * (userAgents.length - 1))]);
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
                        if (blockText != null && !blockText.equals("null"))
                            result += /*(i > 0 ? "\n" : "") +*/ blockText;
                    }
                    if (text.length() > 0 && text.charAt(0) == '\n')
                        result = "\n" + result;
                    final String finalResult = result;
                    final String finalSourceLanguage = sourceLanguage;
                    long elapsed = SystemClock.elapsedRealtime() - start;
                    if (elapsed < minFetchingDuration)
                        sleep(minFetchingDuration - elapsed);
                    AndroidUtilities.runOnUIThread(() -> {
                        if (onSuccess != null)
                            onSuccess.run(finalResult, finalSourceLanguage);
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

    public static void showAlert(Context context, BaseFragment fragment, int currentAccount, TLRPC.InputPeer peer, int msgId, String fromLanguage, String toLanguage, CharSequence text, boolean noforwards, OnLinkPress onLinkPress) {
        TranslateAlert alert = new TranslateAlert(fragment, context, currentAccount, peer, msgId, fromLanguage, toLanguage, text, noforwards, onLinkPress);
        if (fragment != null) {
            if (fragment.getParentActivity() != null) {
                fragment.showDialog(alert);
            }
        } else {
            alert.show();
        }
    }
    public static void showAlert(Context context, BaseFragment fragment, String fromLanguage, String toLanguage, CharSequence text, boolean noforwards, OnLinkPress onLinkPress) {
        TranslateAlert alert = new TranslateAlert(fragment, context, fromLanguage, toLanguage, text, noforwards, onLinkPress);
        if (fragment != null) {
            if (fragment.getParentActivity() != null) {
                fragment.showDialog(alert);
            }
        } else {
            alert.show();
        }
    }

    private static class LoadingTextView extends FrameLayout {
        private TextView loadingTextView;
        public TextView textView = null;

        private CharSequence loadingString;
//        private StaticLayout loadingLayout;
//        private StaticLayout textLayout;
        private Paint loadingPaint = new Paint();
        private Paint loadingIdlePaint = new Paint();
        private Path loadingPath = new Path();
        private RectF fetchedPathRect = new RectF();
        public int padHorz = dp(6), padVert = dp(1.5f);
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
                    fetchedPathRect.set(
                        left - padHorz,
                        top - padVert,
                        right + padHorz,
                        bottom + padVert
                    );
                    got = true;
                }
            }
        };

        public void resize() {
            post(() -> {
                loadingTextView.forceLayout();
                textView.forceLayout();
                updateLoadingLayout();
                updateTextLayout();
                updateHeight();
            });
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
//            if (changed)
//                resize();
        }

        private boolean animateWidth = false;
        private boolean scaleFromZero = false;
        private long scaleFromZeroStart = 0;
        private final long scaleFromZeroDuration = 220l;
        public LoadingTextView(Context context, int padHorz, int padVert, CharSequence loadingString, boolean scaleFromZero, boolean animateWidth) {
            super(context);

            this.animateWidth = animateWidth;
            this.scaleFromZero = scaleFromZero;
            this.scaleFromZeroStart = SystemClock.elapsedRealtime();

            this.padHorz = padHorz;
            this.padVert = padVert;
            setPadding(padHorz, padVert, padHorz, padVert);

            loadingT = 0f;
            loadingTextView = new TextView(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(
                        animateWidth ?
                            MeasureSpec.makeMeasureSpec(
                                999999,
                                MeasureSpec.AT_MOST
                            ) : widthMeasureSpec,
                        MeasureSpec.makeMeasureSpec(
                            MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.AT_MOST ? 999999 : MeasureSpec.getSize(heightMeasureSpec),
                            MeasureSpec.getMode(heightMeasureSpec)
                        )
                    );
                }
            };
            loadingTextView.setText(this.loadingString = loadingString);
            loadingTextView.setVisibility(INVISIBLE);
            loadingTextView.measure(MeasureSpec.makeMeasureSpec(animateWidth ? 999999 : getWidth(), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(9999999, MeasureSpec.AT_MOST));
            addView(loadingTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

            textView = new TextView(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(
                        animateWidth ?
                            MeasureSpec.makeMeasureSpec(
                                999999,
                                MeasureSpec.AT_MOST
                            ) : widthMeasureSpec,
                        MeasureSpec.makeMeasureSpec(
                            MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.AT_MOST ? 999999 : MeasureSpec.getSize(heightMeasureSpec),
                            MeasureSpec.getMode(heightMeasureSpec)
                        )
                    );
                }
            };
            textView.setText("");
            textView.setVisibility(INVISIBLE);
            textView.measure(MeasureSpec.makeMeasureSpec(animateWidth ? 999999 : getWidth(), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(9999999, MeasureSpec.AT_MOST));
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

            int c1 = Theme.getColor(Theme.key_dialogBackground),
                c2 = Theme.getColor(Theme.key_dialogBackgroundGray);
            LinearGradient gradient = new LinearGradient(0, 0, gradientWidth, 0, new int[]{ c1, c2, c1 }, new float[] { 0, 0.67f, 1f }, Shader.TileMode.REPEAT);
            loadingPaint.setShader(gradient);
            loadingIdlePaint.setColor(c2);

            setWillNotDraw(false);
            setClipChildren(false);

            updateLoadingLayout();
        }

        protected void scrollToBottom() {}
        protected void onLoadEnd() {}
        protected void onLoadStart() {}
        protected void onLoadAnimation(float t) {}

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();

//            updateLoadingLayout();
//            updateTextLayout();
            updateHeight();
        }

        private void updateHeight() {
//            int loadingHeight = loadingLayout != null ? loadingLayout.getHeight() : loadingTextView.getMeasuredHeight();
            int loadingHeight = loadingTextView.getMeasuredHeight(),
                textHeight = textView == null ? loadingHeight : textView.getMeasuredHeight();
            float scaleFromZeroT = scaleFromZero ? Math.max(Math.min((float) (SystemClock.elapsedRealtime() - scaleFromZeroStart) / (float) scaleFromZeroDuration, 1f), 0f) : 1f;
            int height = (
                (int) (
                    (
                        padVert * 2 +
                        loadingHeight + (
                            textHeight -
                            loadingHeight
                        ) * loadingT
                    ) * scaleFromZeroT
                )
            );
            ViewGroup.LayoutParams params = (ViewGroup.LayoutParams) getLayoutParams();
            boolean newHeight = false;
            if (params == null) {
                newHeight = true;
                params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
            } else
                newHeight = params.height != height;
//            if (height > 0 || scaleFromZero)
                params.height = height;

            if (animateWidth) {
                int loadingWidth = loadingTextView.getMeasuredWidth() + padHorz * 2;
                int textWidth = (textView == null || textView.getMeasuredWidth() <= 0 ? loadingTextView.getMeasuredWidth() : textView.getMeasuredWidth()) + padHorz * 2;
                params.width = (int) ((loadingWidth + (textWidth - loadingWidth) * loadingT) * scaleFromZeroT);
            }

            this.setLayoutParams(params);
        }

//        private TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
//        private TextPaint loadingTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private float gradientWidth = dp(350f);
        private void updateLoadingLayout() {
            float textWidth = loadingTextView.getMeasuredWidth();
            if (textWidth > 0) {
//                loadingTextPaint.setAntiAlias(true);
//                loadingLayout = new StaticLayout(
//                    loadingString,
//                    loadingTextPaint,
//                    (int) textWidth,
//                    Layout.Alignment.ALIGN_NORMAL,
//                    1f, 0f, false
//                );
//                loadingPath.reset();
                if (loadingTextView != null) {
                    Layout loadingLayout = loadingTextView.getLayout();
                    if (loadingLayout != null) {
                        for (int i = 0; i < loadingLayout.getLineCount(); ++i) {
                            int start = loadingLayout.getLineStart(i), end = loadingLayout.getLineEnd(i);
                            if (start + 1 == end)
                                continue;
                            loadingLayout.getSelectionPath(start, end, fetchPath);
                            loadingPath.addRoundRect(fetchedPathRect, dp(4), dp(4), Path.Direction.CW);
                        }
                    }
                }

                updateHeight();
            }

            if (!loaded && loadingAnimator == null) {
                loadingAnimator = ValueAnimator.ofFloat(0f, 1f);
                loadingAnimator.addUpdateListener(a -> {
                    loadingT = 0f;
                    if (scaleFromZero && SystemClock.elapsedRealtime() < scaleFromZeroStart + scaleFromZeroDuration + 25)
                        updateHeight();
                    invalidate();
                });
                loadingAnimator.setDuration(Long.MAX_VALUE);
                loadingAnimator.start();
            }
        }

        private TextPaint textPaint = new TextPaint();
        private void updateTextLayout() {
            textView.setWidth(getWidth() - padHorz * 2);
//            float textWidth = getWidth() - padHorz * 2;
//            textPaint.setAntiAlias(true);
//            if (textWidth > 0) {
//                textLayout = new StaticLayout(
//                    text,
//                    textPaint,
//                    (int) textWidth,
//                    Layout.Alignment.ALIGN_NORMAL,
//                    1f, 0f, false
//                );
//            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
//            float measureHeight = MeasureSpec.getSize(heightMeasureSpec);
//            float loadingHeight = loadingLayout == null ? measureHeight : loadingLayout.getHeight();
//            float height = measureHeight + (loadingHeight - measureHeight) * (1f - loadingT);
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
//            updateLoadingLayout();
//            updateTextLayout();

            updateLoadingLayout();
//            this.resize();
        }

        public boolean loading = true;
        public boolean loaded = false;
        private float loadingT = 0f;
        private ValueAnimator loadingAnimator = null;

        public void setEllipsizeNull() {
            loadingTextView.setEllipsize(null);
            if (textView != null)
                textView.setEllipsize(null);
        }
        private boolean singleLine = true;
        public void setSingleLine(boolean singleLine) {
            loadingTextView.setSingleLine(this.singleLine = singleLine);
            if (textView != null)
                textView.setSingleLine(singleLine);
        }
        private int lines = -1;
        public void setLines(int lines) {
            loadingTextView.setLines(this.lines = lines);
            if (textView != null)
                textView.setLines(lines);
        }
        public void setGravity(int gravity) {
            loadingTextView.setGravity(gravity);
            if (textView != null)
                textView.setGravity(gravity);
        }
        public void setMaxLines(int maxLines) {
            loadingTextView.setMaxLines(maxLines);
            if (textView != null)
                textView.setMaxLines(maxLines);
        }
        private boolean showLoadingTextValue = true;
        public void showLoadingText(boolean show) {
            showLoadingTextValue = show;
        }
        private int textColor = 0x00000000;
        public void setTextColor(int textColor) {
//            loadingTextPaint.setColor(multAlpha(textColor, showLoadingTextValue ? 0.08f : 0f));
//            loadingTextView.setTextColor(multAlpha(textColor, showLoadingTextValue ? 0.08f : 0f));
//            textPaint.setColor(textColor);
            loadingTextView.setTextColor(this.textColor = textColor);
            if (textView != null)
                textView.setTextColor(textColor);
        }
        private float sz(int unit, float size) {
            Context c = getContext();
            return TypedValue.applyDimension(
                unit, size, (c == null ? Resources.getSystem() : c.getResources()).getDisplayMetrics()
            );
        }
        private int textSize;
        public void setTextSize(int size) {
            loadingTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, this.textSize = size);
            if (textView != null)
                textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
//            loadingTextPaint.setTextSize(size);
//            textPaint.setTextSize(size);
            loadingTextView.setText(loadingString = Emoji.replaceEmoji(loadingString, loadingTextView.getPaint().getFontMetricsInt(), dp(14), false));
            loadingTextView.measure(MeasureSpec.makeMeasureSpec(animateWidth ? 999999 : getWidth(), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(9999999, MeasureSpec.AT_MOST));

            if (textView != null) {
                textView.setText(Emoji.replaceEmoji(textView.getText(), textView.getPaint().getFontMetricsInt(), dp(14), false));
                textView.measure(MeasureSpec.makeMeasureSpec(animateWidth ? 999999 : getWidth(), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(9999999, MeasureSpec.AT_MOST));
            }
            updateLoadingLayout();
        }
        public int multAlpha(int color, float mult) {
            return (color & 0x00ffffff) | ((int) ((color >> 24 & 0xff) * mult) << 24);
        }
        private ValueAnimator animator = null;
        public void setText(CharSequence text) {
            textView.setText(text);
            textView.measure(MeasureSpec.makeMeasureSpec(animateWidth ? 999999 : getWidth(), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(9999999, MeasureSpec.AT_MOST));
            textView.layout(getLeft() + padHorz, getTop() + padVert, getLeft() + padHorz + textView.getMeasuredWidth(), getTop() + padVert + textView.getMeasuredHeight());

            if (!loaded) {
                loaded = true;
                loadingT = 0f;
                if (loadingAnimator != null) {
                    loadingAnimator.cancel();
                    loadingAnimator = null;
                }
                if (animator != null)
                    animator.cancel();
                animator = ValueAnimator.ofFloat(0f, 1f);
                animator.addUpdateListener(a -> {
                    loadingT = (float) a.getAnimatedValue();
                    onLoadAnimation(loadingT);
                    updateHeight();
                    invalidate();
                });
                onLoadStart();
                animator.addListener(new Animator.AnimatorListener() {
                    @Override public void onAnimationEnd(Animator animator) {
                        onLoadEnd();
                    }
                    @Override public void onAnimationCancel(Animator animator) {
                        onLoadEnd();
                    }
                    @Override public void onAnimationRepeat(Animator animator) {}
                    @Override public void onAnimationStart(Animator animator) {}
                });
//                animator.setInterpolator(CubicBezierInterpolator.EASE_BOTH);
                animator.setDuration(300);
                animator.start();
            } else
                updateHeight();
        }

        private long start = SystemClock.elapsedRealtime();
        private Path shadePath = new Path();
        private Path tempPath = new Path();
        private Path inPath = new Path();
        private RectF rect = new RectF();
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

            canvas.translate(padHorz, padVert);
            canvas.clipPath(loadingPath);
            canvas.translate(-padHorz, -padVert);
            canvas.translate(-dx, 0);
            shadePath.offset(dx, 0f, tempPath);
            canvas.drawPath(tempPath, loading ? loadingPaint : loadingIdlePaint);
            canvas.translate(dx, 0);
            canvas.restore();

            canvas.save();
            rect.set(0, 0, w, h);
            canvas.clipPath(inPath, Region.Op.DIFFERENCE);
            canvas.translate(padHorz, padVert);
            canvas.clipPath(loadingPath);
//            if (loadingLayout != null)
//                loadingLayout.draw(canvas);
            canvas.saveLayerAlpha(rect, (int) (255 * (showLoadingTextValue ? 0.08f : 0f)), Canvas.ALL_SAVE_FLAG);
//            loadingTextView.setAlpha(showLoadingTextValue ? 0.08f : 0f);
            loadingTextView.draw(canvas);
            canvas.restore();
            canvas.restore();

            if (textView != null) {
                canvas.save();
                canvas.clipPath(inPath);
                canvas.translate(padHorz, padVert);
                canvas.saveLayerAlpha(rect, (int) (255 * loadingT), Canvas.ALL_SAVE_FLAG);
                textView.draw(canvas);
                if (loadingT < 1f)
                    canvas.restore();
                canvas.restore();
            }
        }

        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            return false;
        }
    }
}
