package org.telegram.ui.web;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.OKLCH;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ArticleViewer;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LineProgressView;
import org.telegram.ui.GradientClip;

public class WebActionBar extends FrameLayout {

    private final Theme.ResourcesProvider resourcesProvider;

    public final RectF rect = new RectF();

    public final Title[] titles = new Title[2];
    public float titleProgress = 0f;
    public final float[] progress = new float[2];

    public boolean[] colorSet = new boolean[3];
    public final Paint[] backgroundPaint = new Paint[2];
    public final Paint[] progressBackgroundPaint = new Paint[2];
    public final Paint[] shadowPaint = new Paint[2];
    public final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Paint addressBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Paint addressRoundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public int textColor, iconColor;
    public int addressBackgroundColor, addressTextColor;

    public final TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    public boolean isMenuShown = false;

    public int height = dp(56);
    public float scale = 1f;

    public final LinearLayout leftmenu;
    public final LinearLayout rightmenu;

    public final ImageView clearButton;
    public final Drawable clearButtonSelector;

    public final ImageView backButton;
    public final BackDrawable backButtonDrawable;
    public final Drawable backButtonSelector;

    public final ImageView forwardButton;
    public final ForwardDrawable forwardButtonDrawable;
    public final Drawable forwardButtonSelector;

    public final ImageView menuButton;
    public final Drawable menuButtonSelector;

    public boolean searching;
    public float searchingProgress = 0f;
    public final FrameLayout searchContainer;
    public final EditTextBoldCursor searchEditText;

    public boolean addressing;
    public float addressingProgress = 0f;
    public final FrameLayout addressContainer;
    public final EditTextBoldCursor addressEditText;
    private int searchEngineIndex;

    public final LineProgressView lineProgressView;

    public static final int search_item = 1;
    public static final int share_item = 2;
    public static final int open_item = 3;
    public static final int settings_item = 4;
    public static final int reload_item = 5;
    public static final int bookmark_item = 6;
    public static final int bookmarks_item = 7;
    public static final int history_item = 8;
    public static final int forward_item = 9;
    public static final int instant_item = 10;

    public WebActionBar(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        titlePaint.setTypeface(AndroidUtilities.bold());
        titlePaint.setTextSize(dp(18.33f));

        for (int i = 0; i < 2; ++i) {
            backgroundPaint[i] = new Paint(Paint.ANTI_ALIAS_FLAG);
            progressBackgroundPaint[i] = new Paint(Paint.ANTI_ALIAS_FLAG);
            shadowPaint[i] = new Paint(Paint.ANTI_ALIAS_FLAG);
        }

        searchContainer = new FrameLayout(context);
        addView(searchContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 56, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));

        addressContainer = new FrameLayout(context);
        addView(addressContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 56, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));

        leftmenu = new LinearLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                setPivotY(ArticleViewer.BOTTOM_ACTION_BAR ? getMeasuredHeight() : 0);
                setPivotX(0);
            }
        };
        leftmenu.setOrientation(LinearLayout.HORIZONTAL);
        addView(leftmenu, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 56, Gravity.LEFT | Gravity.BOTTOM));

        backButton = new ImageView(context);
        backButton.setScaleType(ImageView.ScaleType.CENTER);
        backButtonDrawable = new BackDrawable(false);
        backButtonDrawable.setAnimationTime(200.0f);
        backButtonDrawable.setRotation(1.0f, false);
        backButton.setImageDrawable(backButtonDrawable);
        backButton.setBackground(backButtonSelector = Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        leftmenu.addView(backButton, LayoutHelper.createLinear(54, 56));

        rightmenu = new LinearLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                setPivotY(ArticleViewer.BOTTOM_ACTION_BAR ? getMeasuredHeight() : 0);
                setPivotX(getMeasuredWidth());
            }
        };
        rightmenu.setOrientation(LinearLayout.HORIZONTAL);
        addView(rightmenu, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 56, Gravity.RIGHT | Gravity.BOTTOM));

        forwardButton = new ImageView(context);
        forwardButton.setScaleType(ImageView.ScaleType.CENTER);
        forwardButton.setImageDrawable(forwardButtonDrawable = new ForwardDrawable());
        forwardButtonDrawable.setState(false);
        forwardButton.setBackground(forwardButtonSelector = Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        rightmenu.addView(forwardButton, LayoutHelper.createLinear(54, 56));

        menuButton = new ImageView(context);
        menuButton.setScaleType(ImageView.ScaleType.CENTER);
        menuButton.setImageResource(R.drawable.ic_ab_other);
        menuButton.setColorFilter(new PorterDuffColorFilter(0, PorterDuff.Mode.SRC_IN));
        menuButton.setOnClickListener(v -> {
            if (!(getParent() instanceof ViewGroup)) return;
            Utilities.CallbackReturn<Integer, Runnable> click = id -> () -> menuListener.run(id);
            ItemOptions o = ItemOptions.makeOptions((ViewGroup) getParent(), menuButton);
            o.setDimAlpha(0);
            o.setColors(menuTextColor, menuIconColor);
            o.translate(0, -dp(52));
            o.setMinWidth(200);
            o.setSelectorColor(Theme.blendOver(menuBackgroundColor, Theme.multAlpha(menuTextColor, .1f)));
            if (AndroidUtilities.computePerceivedBrightness(menuBackgroundColor) > .721f) {
                o.setBackgroundColor(0xFFFFFFFF);
                o.setGapBackgroundColor(0xFFF0F0F0);
            } else {
                o.setBackgroundColor(0xFF1F1F1F);
                o.setGapBackgroundColor(0xFF121212);
            }
            if (menuType == ArticleViewer.PageLayout.TYPE_ARTICLE) {
                o.add(R.drawable.msg_openin, getString(R.string.OpenInExternalApp), click.run(open_item));
                o.add(R.drawable.msg_search, getString(R.string.Search), click.run(search_item));
                o.add(R.drawable.msg_share, getString(R.string.ShareFile), click.run(share_item));
                o.add(R.drawable.msg_settings_old, getString(R.string.Settings), click.run(settings_item));
            } else if (menuType == ArticleViewer.PageLayout.TYPE_WEB) {
                if (!isTonsite) {
                    o.add(R.drawable.msg_openin, getString(R.string.OpenInExternalApp), click.run(open_item));
                    o.addGap();
                }
                if (hasForward) {
                    o.add(R.drawable.msg_arrow_forward, getString(R.string.WebForward), click.run(forward_item));
                }
                final WebInstantView.Loader instantView = getInstantViewLoader();
                if (instantView != null && !(instantView.isDone() && instantView.getWebPage() == null)) {
                    o.add(R.drawable.menu_instant_view, getString(R.string.OpenLocalInstantView), click.run(instant_item));
                    ActionBarMenuSubItem item = o.getLast();
                    item.setEnabled(instantView.getWebPage() != null);
                    item.setAlpha(item.isEnabled() ? 1f : 0.5f);
                    Runnable cancel = instantView.listen(() -> {
                        item.setEnabled(instantView.getWebPage() != null);
                        item.animate().alpha(item.isEnabled() ? 1f : 0.5f);
                    });
                    o.setOnDismiss(cancel);
                }
                o.add(R.drawable.msg_reset, getString(R.string.Refresh), click.run(reload_item));
                o.add(R.drawable.msg_search, getString(R.string.Search), click.run(search_item));
                o.add(R.drawable.msg_saved, getString(R.string.WebBookmark), click.run(bookmark_item));
                o.add(R.drawable.msg_share, getString(R.string.ShareFile), click.run(share_item));
                o.addGap();
                if (!BrowserHistory.getHistory().isEmpty()) {
                    o.add(R.drawable.menu_views_recent, getString(R.string.WebHistory), click.run(history_item));
                }
                o.add(R.drawable.menu_browser_bookmarks, getString(R.string.WebBookmarks), click.run(bookmarks_item));
                o.add(R.drawable.msg_settings_old, getString(R.string.Settings), click.run(settings_item));
            }
            o.setOnDismiss(() -> {
                isMenuShown = false;
            });
            o.show();
            isMenuShown = true;
        });
        menuButton.setBackground(menuButtonSelector = Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        menuButton.setContentDescription(getString("AccDescrMoreOptions", R.string.AccDescrMoreOptions));
        rightmenu.addView(menuButton, LayoutHelper.createLinear(54, 56));

        searchEditText = new EditTextBoldCursor(context) {
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (!AndroidUtilities.showKeyboard(this)) {
                        clearFocus();
                        requestFocus();
                    }
                }
                return super.onTouchEvent(event);
            }
        };
        searchEditText.setVisibility(GONE);
        searchEditText.setAlpha(0f);
        searchEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        searchEditText.setSingleLine(true);
        searchEditText.setHint(getString(R.string.Search));
        searchEditText.setBackgroundResource(0);
        searchEditText.setCursorWidth(1.5f);
        searchEditText.setGravity(Gravity.FILL_VERTICAL);
        searchEditText.setClipToPadding(true);
        searchEditText.setPadding(dp(54 + 4), 0, dp(54 + 54 + 4), 0);
        searchEditText.setTranslationY(-dp(.66f));
        searchEditText.setInputType(searchEditText.getInputType() | EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        searchEditText.setImeOptions(EditorInfo.IME_FLAG_NO_FULLSCREEN | EditorInfo.IME_ACTION_SEARCH);
        searchEditText.setTextIsSelectable(false);
        searchEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (event != null && (event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == KeyEvent.KEYCODE_SEARCH || event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                AndroidUtilities.hideKeyboard(searchEditText);
            }
            return false;
        });
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                AndroidUtilities.updateViewShow(clearButton, s.length() > 0 && searching, true, true);
                onSearchUpdated(s.toString());
            }
        });
        searchContainer.addView(searchEditText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        addressEditText = new EditTextBoldCursor(context) {
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (!AndroidUtilities.showKeyboard(this)) {
                        clearFocus();
                        requestFocus();
                    }
                }
                return super.onTouchEvent(event);
            }
        };
        addressEditText.setVisibility(GONE);
        addressEditText.setAlpha(0f);
        addressEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15.66f);
        addressEditText.setSingleLine(true);
        searchEngineIndex = SharedConfig.searchEngineType;
        addressEditText.setHint(LocaleController.formatString(R.string.AddressPlaceholder, SearchEngine.getCurrent().name));
        addressEditText.setBackgroundResource(0);
        addressEditText.setCursorWidth(1.5f);
        addressEditText.setGravity(Gravity.FILL_VERTICAL);
        addressEditText.setInputType(addressEditText.getInputType() | EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        addressEditText.setImeOptions(EditorInfo.IME_FLAG_NO_FULLSCREEN | EditorInfo.IME_ACTION_GO);
        addressEditText.setTextIsSelectable(false);
        addressEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                if (urlCallback != null) {
                    urlCallback.run(addressEditText.getText().toString());
                }
                showAddress(false, true);
            }
            return false;
        });
        addressContainer.addView(addressEditText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 48, 0, 12, 0));

        clearButton = new ImageView(context);
        clearButton.setScaleType(ImageView.ScaleType.CENTER);
        clearButton.setImageResource(R.drawable.ic_close_white);
        clearButton.setBackground(clearButtonSelector = Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        clearButton.setVisibility(GONE);
        clearButton.setAlpha(0f);
        clearButton.setOnClickListener(v -> {
            searchEditText.setText("");
        });
        addView(clearButton, LayoutHelper.createFrame(54, 56, Gravity.BOTTOM | Gravity.RIGHT));

        lineProgressView = new LineProgressView(context);
        lineProgressView.setPivotX(0.0f);
        lineProgressView.setPivotY(ArticleViewer.BOTTOM_ACTION_BAR ? 0 : dp(2));
        addView(lineProgressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 2, (ArticleViewer.BOTTOM_ACTION_BAR ? Gravity.TOP : Gravity.BOTTOM) | Gravity.FILL_HORIZONTAL));

        setWillNotDraw(false);

        titles[0] = new Title();
        titles[1] = new Title();

        setColors(Theme.getColor(Theme.key_iv_background, resourcesProvider), false);
        setMenuColors(Theme.getColor(Theme.key_iv_background, resourcesProvider));
    }

    protected WebInstantView.Loader getInstantViewLoader() {
        return null;
    }

    private boolean occupyStatusBar;
    public void occupyStatusBar(boolean occupyStatusBar) {
        this.occupyStatusBar = occupyStatusBar;
    }

    protected void onOpenedMenu() {}
    protected void onSearchUpdated(String s) {}
    protected void onColorsUpdated() {}
    protected void onScrolledProgress(float delta) {}

    public void setTitle(String title, boolean animated) {
        setTitle(0, title, animated);
    }

    public void setTitle(int i, String title, boolean animated) {
        CharSequence prevText = titles[i].title.getText();
        if (prevText == null || !TextUtils.equals(prevText.toString(), title)) {
            CharSequence cs = title;
            cs = Emoji.replaceEmoji(cs, titles[i].title.getPaint().getFontMetricsInt(), false);
            titles[i].title.setText(cs, animated);
        }
    }

    public void setSubtitle(String subtitle, boolean animated) {
        setSubtitle(0, subtitle, animated);
    }
    public void setSubtitle(int i, String subtitle, boolean animated) {
        CharSequence prevText = titles[i].subtitle.getText();
        if (prevText == null || !TextUtils.equals(prevText.toString(), subtitle)) {
            CharSequence cs = subtitle;
            cs = Emoji.replaceEmoji(cs, titles[i].subtitle.getPaint().getFontMetricsInt(), false);
            titles[i].subtitle.setText(cs, animated);
        }
    }

    public void setIsDangerous(int i, boolean isDangerous, boolean animated) {
        if (titles[i].isDangerous != isDangerous) {
            titles[i].isDangerous = isDangerous;
            if (!animated) {
                titles[i].animatedDangerous.set(isDangerous ? 1f : 0f, true);
            }
            invalidate();
        }
    }

    public String getTitle() {
        final CharSequence text = titles[0].title.getText();
        if (text == null) return "";
        return text.toString();
    }

    public void swap() {
        Title t = titles[0];
        titles[0] = titles[1];
        titles[1] = t;

        float p = progress[0];
        progress[0] = progress[1];
        progress[1] = p;

        int c = getBackgroundColor(0);
        setBackgroundColor(0, getBackgroundColor(1));
        setBackgroundColor(1, c);

        invalidate();
    }

    private Utilities.Callback<Integer> menuListener;
    public void setMenuListener(Utilities.Callback<Integer> menuListener) {
        this.menuListener = menuListener;
    }

    private int menuType = -1;
    public void setMenuType(int type) {
        if (menuType != type) {
            menuType = type;
        }
    }

    public void setTransitionProgress(float progress) {
        titleProgress = progress;
        invalidate();
    }

    public void setProgress(float progress) {
        setProgress(0, progress);
    }
    public void setProgress(int i, float progress) {
        this.progress[i] = progress;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(topPadding() + dp(56), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
    }

    private int fromBackgroundColor, fromIconColor;
    private int toBackgroundColor;
    private int backgroundColor;
    private int rippleColor;
    private ValueAnimator colorAnimator;

    public void setBackgroundColor(int i, int backgroundColor) {
        if (colorSet[i] && backgroundPaint[i].getColor() == backgroundColor)
            return;
        colorSet[i] = true;
        backgroundPaint[i].setColor(backgroundColor);
        final float dark = AndroidUtilities.computePerceivedBrightness(backgroundColor) <= .721f ? 1f : 0f;
        final int iconColor = ColorUtils.blendARGB(Color.BLACK, Color.WHITE, dark);
        progressBackgroundPaint[i].setColor(Theme.blendOver(backgroundColor, Theme.multAlpha(iconColor, lerp(.07f, .2f, dark))));
        shadowPaint[i].setColor(Theme.blendOver(backgroundColor, Theme.multAlpha(iconColor, lerp(.14f, .24f, dark))));
        titles[i].title.setTextColor(iconColor);
        titles[i].subtitleColor = Theme.blendOver(backgroundColor, Theme.multAlpha(iconColor, .6f));
        titles[i].subtitle.setTextColor(ColorUtils.blendARGB(titles[i].subtitleColor, Theme.getColor(Theme.key_text_RedBold), titles[i].animatedDangerous.get()));
        invalidate();
    }

    public int getBackgroundColor(int i) {
        return backgroundPaint[i].getColor();
    }

    public int menuBackgroundColor;
    public int menuTextColor;
    public int menuIconColor;
    public boolean hasForward;
    public boolean hasLoaded;
    public boolean isTonsite;

    public void setHasForward(boolean value) {
        this.hasForward = value;
    }

    public void setIsLoaded(boolean value) {
        this.hasLoaded = value;
    }

    public void setMenuColors(int backgroundColor) {
        double[] lch = OKLCH.rgb2oklch(OKLCH.rgb(backgroundColor));
        final boolean isDark = lch[0] < .5f;
        menuBackgroundColor = isDark ? Color.BLACK : Color.WHITE;
        menuTextColor = isDark ? Color.WHITE : Color.BLACK;
        menuIconColor = Theme.multAlpha(menuTextColor, .6f);
    }

    public void setIsTonsite(boolean value) {
        this.isTonsite = value;
    }

    public void setColors(int backgroundColor, boolean animated) {
        setColors(backgroundColor, -1f, animated);
    }

    public void setColors(int backgroundColor, float dark, boolean animated) {
        if (colorSet[2] && this.backgroundColor == backgroundColor) {
            return;
        }

        if (!animated) {
            colorSet[2] = true;
            if (dark < 0) {
                dark = AndroidUtilities.computePerceivedBrightness(backgroundColor) <= .721f ? 1f : 0f;
            }

            this.textColor = ColorUtils.blendARGB(Color.BLACK, Color.WHITE, dark);
            this.iconColor = Theme.multAlpha(textColor, .55f);

            this.backgroundColor = backgroundColor;

//            double[] lch = OKLCH.rgb2oklch(OKLCH.rgb(backgroundColor));
//            final boolean isDark = lch[0] < .5f;
//            if (isDark) {
//                lch[0] = Utilities.clamp(lch[0], 0.025, 0);
//            } else {
//                lch[0] = Utilities.clamp(lch[0], 1, 0.975);
//            }
//            lch[1] = Utilities.clamp(lch[1], 0.01, 0);
//            addressBackgroundColor = OKLCH.rgb(OKLCH.oklch2rgb(lch));
//            addressTextColor = isDark ? Color.WHITE : Color.BLACK;
            addressBackgroundColor = ColorUtils.blendARGB(Color.WHITE, Color.BLACK, dark);
            addressTextColor = ColorUtils.blendARGB(Color.WHITE, Color.BLACK, 1f - dark);
            onAddressColorsChanged(addressBackgroundColor, addressTextColor);
            addressBackgroundPaint.setColor(addressBackgroundColor);
            addressRoundPaint.setColor(Theme.blendOver(addressBackgroundColor, Theme.multAlpha(textColor, lerp(.07f, .2f, dark))));
            addressEditText.setHintTextColor(Theme.multAlpha(addressTextColor, .6f));
            addressEditText.setTextColor(addressTextColor);
            addressEditText.setCursorColor(addressTextColor);
            addressEditText.setHandlesColor(addressTextColor);

            lineProgressView.setProgressColor(Theme.getColor(Theme.key_iv_ab_progress, resourcesProvider));

            backButtonDrawable.setColor(ColorUtils.blendARGB(textColor, addressTextColor, addressingProgress));
            backButtonDrawable.setRotatedColor(ColorUtils.blendARGB(textColor, addressTextColor, addressingProgress));
            forwardButtonDrawable.setColor(textColor);
            menuButton.setColorFilter(new PorterDuffColorFilter(textColor, PorterDuff.Mode.SRC_IN));
            forwardButton.setColorFilter(new PorterDuffColorFilter(textColor, PorterDuff.Mode.SRC_IN));
            clearButton.setColorFilter(new PorterDuffColorFilter(textColor, PorterDuff.Mode.SRC_IN));

            rippleColor = Theme.blendOver(backgroundColor, Theme.multAlpha(textColor, .22f));
            Theme.setSelectorDrawableColor(backButtonSelector, rippleColor, true);
            Theme.setSelectorDrawableColor(forwardButtonSelector, rippleColor, true);
            Theme.setSelectorDrawableColor(menuButtonSelector, rippleColor, true);
            Theme.setSelectorDrawableColor(clearButtonSelector, rippleColor, true);

            searchEditText.setHintTextColor(Theme.multAlpha(textColor, .6f));
            searchEditText.setTextColor(textColor);
            searchEditText.setCursorColor(textColor);
            searchEditText.setHandlesColor(textColor);

            onColorsUpdated();

            invalidate();

            return;
        }

        if (colorAnimator != null) {
            colorAnimator.cancel();
        }
        fromBackgroundColor = this.backgroundColor;
        final float fromDark = AndroidUtilities.computePerceivedBrightness(fromBackgroundColor) <= .721f ? 1f : 0f;
        final float toDark = AndroidUtilities.computePerceivedBrightness(backgroundColor) <= .721f ? 1f : 0f;
        colorAnimator = ValueAnimator.ofFloat(0, 1);
        colorAnimator.addUpdateListener(anm -> {
            final float t = (float) anm.getAnimatedValue();
            setColors(ColorUtils.blendARGB(fromBackgroundColor, backgroundColor, t), lerp(fromDark, toDark, t), false);
        });
        colorAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                setColors(backgroundColor, toDark, false);
            }
        });
        colorAnimator.start();
    }

    protected void onAddressColorsChanged(int backgroundColor, int textColor) {

    }

    public int getBackgroundColor() {
        return this.backgroundColor;
    }

    public int getTextColor() {
        return textColor;
    }

    public void setHeight(int h) {
        if (this.height != h) {
            this.height = h;

            scale = (float) Math.pow(h / (float) dp(56), 0.5f);
            leftmenu.setScaleX(scale);
            leftmenu.setScaleY(scale);
            leftmenu.setTranslationX(dp(42) * (1f - scale));
            leftmenu.setTranslationY(dp(ArticleViewer.BOTTOM_ACTION_BAR ? 16 : -12) * (1f - scale));
            rightmenu.setScaleX(scale);
            rightmenu.setScaleY(scale);
            rightmenu.setTranslationX(-dp(42) * (1f - scale));
            rightmenu.setTranslationY(dp(ArticleViewer.BOTTOM_ACTION_BAR ? 16 : -12) * (1f - scale));

            lineProgressView.setTranslationY(height - dp(56));

            invalidate();
        }
    }

    public final GradientClip clip = new GradientClip();

    public class Title {

        public final AnimatedTextView.AnimatedTextDrawable title = new AnimatedTextView.AnimatedTextDrawable(true, true, true);
        public final AnimatedTextView.AnimatedTextDrawable subtitle = new AnimatedTextView.AnimatedTextDrawable(true, true, true);
        public final AnimatedFloat animatedDangerous = new AnimatedFloat(WebActionBar.this, 0, 300, CubicBezierInterpolator.EASE_OUT_QUINT);
        public int subtitleColor;

        public boolean isDangerous = false;
        public int warningDrawableColor;
        public final Drawable warningDrawable;

        public Title() {
            title.ignoreRTL = true;
            title.setTextSize(dp(18.33f));
            title.setScaleProperty(.6f);
            title.setTypeface(AndroidUtilities.bold());
            title.setEllipsizeByGradient(false);
            title.setCallback(WebActionBar.this);
            title.setOverrideFullWidth(9999999);

            subtitle.ignoreRTL = true;
            subtitle.setTextSize(dp(14));
            subtitle.setEllipsizeByGradient(false);
            subtitle.setCallback(WebActionBar.this);
            subtitle.setOverrideFullWidth(9999999);

            warningDrawable = getContext().getResources().getDrawable(R.drawable.warning_sign).mutate();
        }

        public void draw(Canvas canvas, float w, float h, float alpha) {
            rect.set(0, 0, w, h);
            canvas.saveLayerAlpha(rect, (int) (0xFF * alpha), Canvas.ALL_SAVE_FLAG);

            final float both = title.isNotEmpty() * subtitle.isNotEmpty();

            canvas.save();
            canvas.translate(0, -dp(1) + h * (ArticleViewer.BOTTOM_ACTION_BAR ? .41f : .82f) * (1f - scale));
            canvas.translate(0, -dp(4) * both);
            float s = scale * lerp(1f, .86f, both);
            canvas.scale(s, s, 0, 0);
            title.setBounds(0, 0, w, h);
            title.draw(canvas);
            canvas.restore();

            final float dangerous = animatedDangerous.set(isDangerous);

            canvas.save();
            canvas.translate(0, -dp(1) + h * (ArticleViewer.BOTTOM_ACTION_BAR ? .41f : .82f) * (1f - scale) * both + dp(14) * both - dp(4) * (1f - both));
            s = scale * lerp(1.15f, .9f, both);
            canvas.scale(s, s, 0, 0);
            subtitle.setTextColor(ColorUtils.blendARGB(subtitleColor, Theme.getColor(Theme.key_text_RedBold), dangerous));
            if (dangerous > 0) {
                if (warningDrawableColor != subtitle.getTextColor()) {
                    warningDrawable.setColorFilter(new PorterDuffColorFilter(warningDrawableColor = subtitle.getTextColor(), PorterDuff.Mode.SRC_IN));
                }
                warningDrawable.setAlpha((int) (0xFF * dangerous));
                warningDrawable.setBounds(0, (int) (h - dp(16)) / 2, dp(16), (int) (h + dp(16)) / 2);
                warningDrawable.draw(canvas);
            }
            subtitle.setBounds(dp(20) * dangerous, 0, w, h);
            subtitle.draw(canvas);
            canvas.restore();

            rect.set(w - dp(12), 0, w, h);
            clip.draw(canvas, rect, GradientClip.RIGHT, 1f);
            canvas.restore();
        }

    }

    public int topPadding() {
        return occupyStatusBar ? AndroidUtilities.statusBarHeight : 0;
    }

    public void drawBackground(Canvas canvas, float h, float alpha, float shadowalpha, boolean withShadow) {
        final float shadowh = Math.max(dp(0.66f), 1);
        float t = ArticleViewer.BOTTOM_ACTION_BAR ? getHeight() - h : 0;
        float b = ArticleViewer.BOTTOM_ACTION_BAR ? getHeight()     : h;
        float shadowt = ArticleViewer.BOTTOM_ACTION_BAR ? t : b - shadowh;

        float l = getWidth() * titleProgress;
        int wasAlpha;

        rect.set(0, t, getWidth(), b);
        wasAlpha = backgroundPaint[1].getAlpha();
        backgroundPaint[1].setAlpha((int) (wasAlpha * alpha));
        canvas.drawRect(rect, backgroundPaint[1]);
        backgroundPaint[1].setAlpha(wasAlpha);

        if (titleProgress > 0f) {
            rect.set(0, t, progress[1] * getWidth(), b);
            wasAlpha = progressBackgroundPaint[1].getAlpha();
            progressBackgroundPaint[1].setAlpha((int) (wasAlpha * alpha * (1f - searchingProgress) * (1f - addressingProgress)));
            canvas.drawRect(rect, progressBackgroundPaint[1]);
            progressBackgroundPaint[1].setAlpha(wasAlpha);

            if (withShadow) {
                rect.set(0, shadowt, l, shadowt + shadowh);
                wasAlpha = shadowPaint[1].getAlpha();
                shadowPaint[1].setAlpha((int) (wasAlpha * alpha * shadowalpha * (1f - addressingProgress)));
                canvas.drawRect(rect, shadowPaint[1]);
                shadowPaint[1].setAlpha(wasAlpha);
            }
        }

        if (titleProgress < 1f) {
            scrimPaint.setColor(Theme.multAlpha(0x60000000, (1f - titleProgress) * alpha));
            rect.set(0, t, l, b);
            canvas.drawRect(rect, scrimPaint);

            rect.set(l, t, getWidth(), b);
            wasAlpha = backgroundPaint[0].getAlpha();
            backgroundPaint[0].setAlpha((int) (wasAlpha * alpha));
            canvas.drawRect(rect, backgroundPaint[0]);
            backgroundPaint[0].setAlpha(wasAlpha);
        }

        rect.set(l, t, l + progress[0] * getWidth(), b);
        wasAlpha = progressBackgroundPaint[0].getAlpha();
        progressBackgroundPaint[0].setAlpha((int) ((1f - Utilities.clamp01(titleProgress * 4)) * wasAlpha * alpha * (1f - searchingProgress) * (1f - addressingProgress)));
        canvas.drawRect(rect, progressBackgroundPaint[0]);
        progressBackgroundPaint[0].setAlpha(wasAlpha);

        if (withShadow) {
            rect.set(l, shadowt, l + getWidth(), shadowt + shadowh);
            wasAlpha = shadowPaint[0].getAlpha();
            shadowPaint[0].setAlpha((int) (wasAlpha * alpha * shadowalpha * (1f - addressingProgress)));
            canvas.drawRect(rect, shadowPaint[0]);
            shadowPaint[0].setAlpha(wasAlpha);
        }
    }

    public boolean drawShadow;
    @Override
    protected void dispatchDraw(Canvas canvas) {
        drawBackground(canvas, topPadding() + height, 1f, 1f, drawShadow);

        float left = leftmenu.getRight();
        float right = rightmenu.getLeft();
        float top =    ArticleViewer.BOTTOM_ACTION_BAR ? getHeight() - height : topPadding();
        float bottom = ArticleViewer.BOTTOM_ACTION_BAR ? getHeight()          : topPadding() + height;

        if (titleProgress < 1) {
            canvas.save();
            final float leftpadding = getWidth() * titleProgress - dp(54 - 24) * Utilities.clamp01(2 * titleProgress);
            canvas.translate(left + leftpadding, top);
            final float s = lerp(1f, .5f, titleProgress);
            titles[0].draw(canvas, right - left - leftpadding, bottom - top, (1f - titleProgress) * (1f - searchingProgress));
            canvas.restore();
        }
        if (titleProgress > 0) {
            float l = getWidth() * titleProgress;
            canvas.save();
            canvas.clipRect(0, 0, l, getHeight());
            canvas.translate(left, top);
            canvas.translate(dp(-12) * (1f - titleProgress), 0);
            final float s = lerp(1f, .5f, 1f - titleProgress);
            canvas.scale(s, s, 0, (bottom - top) / 2f);
            titles[1].draw(canvas, right - left, bottom - top, titleProgress * (1f - searchingProgress) * (1f - addressingProgress));
            canvas.restore();
        }

        if (addressingProgress > 0) {
            int wasAlpha = addressBackgroundPaint.getAlpha();
            addressBackgroundPaint.setAlpha((int) (wasAlpha * addressingProgress));
            canvas.drawRect(0, 0, getWidth(), topPadding() + height, addressBackgroundPaint);
            addressBackgroundPaint.setAlpha(wasAlpha);

            final float h = dp(42), cx = (top + bottom) / 2f;
            rect.set(dp(6), cx - h / 2f, lerp(right, getWidth() - dp(6), addressingProgress), cx + h / 2f);
            wasAlpha = addressRoundPaint.getAlpha();
            addressRoundPaint.setAlpha((int) (wasAlpha * addressingProgress));
            canvas.drawRoundRect(rect, dp(50), dp(50), addressRoundPaint);
            addressRoundPaint.setAlpha(wasAlpha);
        }

        rect.set(0, top, getWidth(), bottom);
        canvas.save();
        canvas.clipRect(rect);

        super.dispatchDraw(canvas);
        canvas.restore();
    }

    private ValueAnimator searchAnimator;
    public void showSearch(boolean show, boolean animated) {
        if (searching == show) return;
        if (searchAnimator != null) {
            searchAnimator.cancel();
        }
        searching = show;
        if (animated) {
            searchEditText.setVisibility(VISIBLE);
            backButtonDrawable.setRotation(backButtonShown || show ? 0f : 1f, true);
            searchAnimator = ValueAnimator.ofFloat(searchingProgress, show ? 1 : 0);
            searchAnimator.addUpdateListener(anm -> {
                searchingProgress = (float) anm.getAnimatedValue();
                searchEditText.setAlpha(searchingProgress);
                invalidate();
            });
            searchAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!searching) {
                        searchEditText.setVisibility(GONE);
                        searchEditText.setText("");
                    }
                    searchEditText.setAlpha(searchingProgress = show ? 1 : 0f);
                    invalidate();

                    if (searching) {
                        searchEditText.requestFocus();
                        AndroidUtilities.showKeyboard(searchEditText);
                    } else {
                        searchEditText.clearFocus();
                        AndroidUtilities.hideKeyboard(searchEditText);
                    }
                }
            });
            searchAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            searchAnimator.setDuration(320);
            searchAnimator.start();
        } else {
            searchingProgress = show ? 1f : 0f;
            invalidate();
            searchEditText.setAlpha(show ? 1 : 0f);
            searchEditText.setVisibility(show ? VISIBLE : GONE);
            backButtonDrawable.setRotation(backButtonShown || show ? 0f : 1f, true);
            if (searching) {
                searchEditText.requestFocus();
                AndroidUtilities.showKeyboard(searchEditText);
            } else {
                searchEditText.clearFocus();
                AndroidUtilities.hideKeyboard(searchEditText);
            }
        }
        AndroidUtilities.updateViewShow(forwardButton, !show, true, animated);
        AndroidUtilities.updateViewShow(menuButton, !show, true, animated);
        AndroidUtilities.updateViewShow(clearButton, searchEditText.length() > 0 && searching, true, animated);
    }

    private boolean backButtonShown;

    public void setBackButton(boolean show) {
        backButtonShown = show;
        if (!isSearching() && !isAddressing()) {
            backButtonDrawable.setRotation(backButtonShown ? 0f : 1f, true);
        }
    }
    public void setBackButtonCached(boolean show) {
        backButtonShown = show;
    }

    public boolean isBackButton() {
        return backButtonShown;
    }

    public boolean isSearching() {
        return searching;
    }

    private Utilities.Callback<String> urlCallback;
    public void showAddress(String currentUrl, Utilities.Callback<String> urlCallback) {
        addressEditText.setText(currentUrl);
        addressEditText.setSelection(0, addressEditText.getText().length());
        addressEditText.setScrollX(0);
        this.urlCallback = urlCallback;
        showAddress(true, true);
    }

    private ValueAnimator addressAnimator;
    private void showAddressKeyboard() {
        if (addressing) {
            addressEditText.requestFocus();
            AndroidUtilities.showKeyboard(addressEditText);
        } else {
            addressEditText.clearFocus();
            AndroidUtilities.hideKeyboard(addressEditText);
        }
    };

    public void showAddress(boolean show, boolean animated) {
        if (addressing == show) return;
        if (addressAnimator != null) {
            addressAnimator.cancel();
        }
        addressing = show;
        if (show) {
            if (searchEngineIndex != SharedConfig.searchEngineType) {
                searchEngineIndex = SharedConfig.searchEngineType;
                addressEditText.setHint(LocaleController.formatString(R.string.AddressPlaceholder, SearchEngine.getCurrent().name));
            }
        }
        if (animated) {
            addressEditText.setVisibility(VISIBLE);
            backButtonDrawable.setRotation(backButtonShown || show ? 0f : 1f, true);
            addressAnimator = ValueAnimator.ofFloat(addressingProgress, show ? 1 : 0);
            addressAnimator.addUpdateListener(anm -> {
                onAddressingProgress(addressingProgress = (float) anm.getAnimatedValue());
                addressEditText.setAlpha(addressingProgress);
                menuButton.setTranslationX(dp(56) * addressingProgress);
                forwardButton.setTranslationX(dp(56 + 56) * addressingProgress);
                invalidate();
            });
            addressAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!addressing) {
                        addressEditText.setVisibility(GONE);
                    }
                    addressEditText.setAlpha(addressingProgress = show ? 1 : 0f);
                    onAddressingProgress(addressingProgress);
                    menuButton.setTranslationX(dp(56) * addressingProgress);
                    forwardButton.setTranslationX(dp(56 + 56) * addressingProgress);
                    invalidate();
                }
            });
            addressAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            addressAnimator.setDuration(360);
            addressAnimator.start();
        } else {
            onAddressingProgress(addressingProgress = show ? 1f : 0f);
            invalidate();
            addressEditText.setAlpha(show ? 1 : 0f);
            addressEditText.setVisibility(show ? VISIBLE : GONE);
            menuButton.setTranslationX(dp(56) * addressingProgress);
            forwardButton.setTranslationX(dp(56 + 56) * addressingProgress);
            backButtonDrawable.setRotation(backButtonShown || show ? 0f : 1f, true);
        }
        AndroidUtilities.cancelRunOnUIThread(this::showAddressKeyboard);
        AndroidUtilities.runOnUIThread(this::showAddressKeyboard, addressing ? 100 : 0);
    }

    protected void onAddressingProgress(float progress) {
        backButtonDrawable.setColor(ColorUtils.blendARGB(textColor, addressTextColor, addressingProgress));
        backButtonDrawable.setRotatedColor(ColorUtils.blendARGB(textColor, addressTextColor, addressingProgress));
        backButton.invalidate();
    }

    public void hideKeyboard() {
        if (searching) {
            searchEditText.clearFocus();
            AndroidUtilities.hideKeyboard(searchEditText);
        }
        if (addressing) {
            addressEditText.clearFocus();
            AndroidUtilities.hideKeyboard(addressEditText);
        }
    }

    public boolean isAddressing() {
        return addressing;
    }

    @SuppressLint("MissingSuperCall")
    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return true;
    }

    private float pressX, pressY;
    private long pressTime;
    private Runnable longPressRunnable = () -> {
        longClicked = true;
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        try {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
        } catch (Exception ignored) {}
    };

    public boolean longClicked = false;
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            longClicked = false;
            AndroidUtilities.cancelRunOnUIThread(longPressRunnable);
            if (ev.getX() > leftmenu.getRight() && ev.getX() < rightmenu.getLeft() && !isSearching() && !isAddressing()) {
                pressX = ev.getX();
                pressY = ev.getY();
                pressTime = System.currentTimeMillis();
                AndroidUtilities.runOnUIThread(longPressRunnable, (long) (ViewConfiguration.getLongPressTimeout() * .8f));
            }
        } else if (ev.getAction() == MotionEvent.ACTION_MOVE && System.currentTimeMillis() - pressTime > ViewConfiguration.getLongPressTimeout() * .8f) {
            AndroidUtilities.cancelRunOnUIThread(longPressRunnable);
            longClicked = true;
            onScrolledProgress((float) (ev.getX() - pressX) / (.8f * getWidth()));
            getParent().requestDisallowInterceptTouchEvent(true);
        } else if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) {
            AndroidUtilities.cancelRunOnUIThread(longPressRunnable);
            pressTime = 0;
        }
        pressX = ev.getX();
        return super.dispatchTouchEvent(ev);
    }

    public class ForwardDrawable extends Drawable {

        private final Path path = new Path();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeCap(Paint.Cap.ROUND);
        }
        private AnimatedFloat animatedState = new AnimatedFloat(this::invalidateSelf, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
        private boolean state;

        public void setState(boolean state) { // false = collapse, true = forward
            this.state = state;
            invalidateSelf();
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            final float state = this.animatedState.set(!this.state);

            final float cx = getBounds().centerX(), cy = getBounds().centerY();
            final float w = getBounds().width(), aw = w * 0.57f;

            path.rewind();
            path.moveTo(cx - lerp(aw / 2f, -aw / 2f, state), cy);
            path.lineTo(cx + aw / 2f, cy);
            path.moveTo(cx + aw / 2f - w * .27f, cy - w * .54f / 2f);
            path.lineTo(cx + aw / 2f, cy);
            path.lineTo(cx + aw / 2f - w * .27f, cy + w * .54f / 2f);

            canvas.save();
            paint.setStrokeWidth(dp(2));
            canvas.translate(0, -w * 0.10f * state);
            canvas.rotate(90 * state, cx, cy);
            canvas.drawPath(path, paint);
            canvas.restore();
        }

        public void setColor(int color) {
            paint.setColor(color);
            invalidateSelf();
        }

        @Override
        public void setAlpha(int alpha) {

        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {

        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }

        @Override
        public int getIntrinsicHeight() {
            return dp(24);
        }

        @Override
        public int getIntrinsicWidth() {
            return dp(24);
        }
    }

}
