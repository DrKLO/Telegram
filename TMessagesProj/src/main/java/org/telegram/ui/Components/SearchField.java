package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;

import java.util.List;

@SuppressWarnings("FieldCanBeLocal")
public class SearchField extends FrameLayout {

    private View searchBackground;
    private ImageView searchIconImageView;
    private ImageView clearSearchImageView;
    private CloseProgressDrawable2 progressDrawable;
    private EditTextBoldCursor searchEditText;
    private final Theme.ResourcesProvider resourcesProvider;

    public SearchField(Context context, boolean supportRtl, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        FrameLayout.LayoutParams lp;

        searchBackground = new View(context);
        searchBackground.setBackgroundDrawable(Theme.createRoundRectDrawable(AndroidUtilities.dp(18), getThemedColor(Theme.key_dialogSearchBackground)));
        if (supportRtl) {
            lp = LayoutHelper.createFrameRelatively(LayoutHelper.MATCH_PARENT, 36, Gravity.START | Gravity.TOP, 14, 11, 14, 0);
        } else {
            lp = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.LEFT | Gravity.TOP, 14, 11, 14, 0);
        }
        addView(searchBackground, lp);

        searchIconImageView = new ImageView(context);
        searchIconImageView.setScaleType(ImageView.ScaleType.CENTER);
        searchIconImageView.setImageResource(R.drawable.smiles_inputsearch);
        searchIconImageView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_dialogSearchIcon), PorterDuff.Mode.MULTIPLY));
        if (supportRtl) {
            lp = LayoutHelper.createFrameRelatively(36, 36, Gravity.START | Gravity.TOP, 16, 11, 0, 0);
        } else {
            lp = LayoutHelper.createFrame(36, 36, Gravity.LEFT | Gravity.TOP, 16, 11, 0, 0);
        }
        addView(searchIconImageView, lp);

        clearSearchImageView = new ImageView(context);
        clearSearchImageView.setScaleType(ImageView.ScaleType.CENTER);
        clearSearchImageView.setImageDrawable(progressDrawable = new CloseProgressDrawable2() {
            @Override
            protected int getCurrentColor() {
                return getThemedColor(Theme.key_dialogSearchIcon);
            }
        });
        progressDrawable.setSide(AndroidUtilities.dp(7));
        clearSearchImageView.setScaleX(0.1f);
        clearSearchImageView.setScaleY(0.1f);
        clearSearchImageView.setAlpha(0.0f);
        if (supportRtl) {
            lp = LayoutHelper.createFrameRelatively(36, 36, Gravity.END | Gravity.TOP, 14, 11, 14, 0);
        } else {
            lp = LayoutHelper.createFrame(36, 36, Gravity.RIGHT | Gravity.TOP, 14, 11, 14, 0);
        }
        addView(clearSearchImageView, lp);
        clearSearchImageView.setOnClickListener(v -> {
            searchEditText.setText("");
            AndroidUtilities.showKeyboard(searchEditText);
        });

        searchEditText = new EditTextBoldCursor(context) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent event) {
                processTouchEvent(event);
                return super.dispatchTouchEvent(event);
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (!isEnabled()) {
                    return false;
                }
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    onFieldTouchUp(this);
                }
                return super.onTouchEvent(event);
            }
        };
        searchEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        searchEditText.setHintTextColor(getThemedColor(Theme.key_dialogSearchHint));
        searchEditText.setTextColor(getThemedColor(Theme.key_dialogSearchText));
        searchEditText.setBackgroundDrawable(null);
        searchEditText.setPadding(0, 0, 0, 0);
        searchEditText.setMaxLines(1);
        searchEditText.setLines(1);
        searchEditText.setSingleLine(true);
        searchEditText.setGravity((supportRtl ? LayoutHelper.getAbsoluteGravityStart() : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        searchEditText.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        searchEditText.setCursorColor(getThemedColor(Theme.key_featuredStickers_addedIcon));
        searchEditText.setCursorSize(AndroidUtilities.dp(20));
        searchEditText.setCursorWidth(1.5f);
        if (supportRtl) {
            lp = LayoutHelper.createFrameRelatively(LayoutHelper.MATCH_PARENT, 40, Gravity.START | Gravity.TOP, 16 + 38, 9, 16 + 30, 0);
        } else {
            lp = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 40, Gravity.LEFT | Gravity.TOP, 16 + 38, 9, 16 + 30, 0);
        }
        addView(searchEditText, lp);
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
                onTextChange(searchEditText.getText().toString());
            }
        });
        searchEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (event != null && (event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == KeyEvent.KEYCODE_SEARCH || event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                searchEditText.hideActionMode();
                AndroidUtilities.hideKeyboard(searchEditText);
            }
            return false;
        });
    }

    public void hideKeyboard() {
        AndroidUtilities.hideKeyboard(searchEditText);
    }

    public void setHint(String text) {
        searchEditText.setHint(text);
    }

    protected void onFieldTouchUp(EditTextBoldCursor editText) {

    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    public void processTouchEvent(MotionEvent event) {

    }

    public void onTextChange(String text) {

    }

    public View getSearchBackground() {
        return searchBackground;
    }

    public EditTextBoldCursor getSearchEditText() {
        return searchEditText;
    }

    public CloseProgressDrawable2 getProgressDrawable() {
        return progressDrawable;
    }

    public void getThemeDescriptions(List<ThemeDescription> descriptions) {
        descriptions.add(new ThemeDescription(searchBackground, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_dialogSearchBackground));
        descriptions.add(new ThemeDescription(searchIconImageView, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_dialogSearchIcon));
        descriptions.add(new ThemeDescription(clearSearchImageView, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_dialogSearchIcon));
        descriptions.add(new ThemeDescription(searchEditText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_dialogSearchText));
        descriptions.add(new ThemeDescription(searchEditText, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_dialogSearchHint));
        descriptions.add(new ThemeDescription(searchEditText, ThemeDescription.FLAG_CURSORCOLOR, null, null, null, null, Theme.key_featuredStickers_addedIcon));
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }
}
