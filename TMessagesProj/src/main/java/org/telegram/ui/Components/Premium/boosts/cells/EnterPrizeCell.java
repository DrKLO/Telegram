package org.telegram.ui.Components.Premium.boosts.cells;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.R;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextCaption;
import org.telegram.ui.Components.LayoutHelper;

@SuppressLint("ViewConstructor")
public class EnterPrizeCell extends LinearLayout {
    private static final int MAX_INPUT_LENGTH = 128;

    public interface AfterTextChangedListener {
        void afterTextChanged(String text);
    }

    private final Theme.ResourcesProvider resourcesProvider;
    private final EditTextCaption editText;
    private final TextView textView;
    private AfterTextChangedListener afterTextChangedListener;

    public EnterPrizeCell(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setOrientation(LinearLayout.HORIZONTAL);
        editText = new EditTextCaption(context, resourcesProvider);
        editText.setLines(1);
        editText.setSingleLine(true);
        InputFilter[] inputFilters = new InputFilter[1];
        inputFilters[0] = new InputFilter.LengthFilter(MAX_INPUT_LENGTH) {
            @Override
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                CharSequence result = super.filter(source, start, end, dest, dstart, dend);
                if (result != null && result.length() == 0) {
                    AndroidUtilities.shakeView(editText);
                    BotWebViewVibrationEffect.APP_ERROR.vibrate();
                }
                return result;
            }
        };
        editText.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editText.setFilters(inputFilters);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setTextColor(Theme.getColor(Theme.key_chat_messagePanelText, resourcesProvider));
        editText.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkOut, resourcesProvider));
        editText.setHighlightColor(Theme.getColor(Theme.key_chat_inTextSelectionHighlight, resourcesProvider));
        editText.setHintColor(Theme.getColor(Theme.key_chat_messagePanelHint, resourcesProvider));
        editText.setHintTextColor(Theme.getColor(Theme.key_chat_messagePanelHint, resourcesProvider));
        editText.setCursorColor(Theme.getColor(Theme.key_chat_messagePanelCursor, resourcesProvider));
        editText.setHandlesColor(Theme.getColor(Theme.key_chat_TextSelectionCursor, resourcesProvider));
        editText.setBackground(null);
        editText.setHint(LocaleController.getString("BoostingGiveawayEnterYourPrize", R.string.BoostingGiveawayEnterYourPrize));
        editText.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if (afterTextChangedListener != null) {
                    afterTextChangedListener.afterTextChanged(s.toString().trim());
                }
            }
        });
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);

        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));

        if (LocaleController.isRTL) {
            LinearLayout.LayoutParams lp = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 20, 0, 36, 0);
            lp.weight = 1;
            addView(editText, lp);
            addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 0, 20, 0));
        } else {
            addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 20, 0, 0, 0));
            addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 36, 0, 20, 0));
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(50), MeasureSpec.EXACTLY)
        );
    }

    public void setAfterTextChangedListener(AfterTextChangedListener afterTextChangedListener) {
        this.afterTextChangedListener = afterTextChangedListener;
    }

    public void setCount(int count) {
        textView.setText(String.valueOf(count));
    }
}
