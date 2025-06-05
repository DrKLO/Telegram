package org.telegram.ui.Cells;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.URLSpanNoUnderline;

public class SettingsSuggestionCell extends LinearLayout {

    public final static int TYPE_PHONE = 0;
    public final static int TYPE_PASSWORD = 1;
    public final static int TYPE_GRACE = 2;

    private TextView textView;
    private TextView detailTextView;
    private TextView yesButton;
    private TextView noButton;
    private Theme.ResourcesProvider resourcesProvider;

    private int currentType;

    private int currentAccount = UserConfig.selectedAccount;

    public SettingsSuggestionCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setOrientation(VERTICAL);

        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader, resourcesProvider));
        addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 21, 15, 21, 0));

        detailTextView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
        detailTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        detailTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        detailTextView.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText, resourcesProvider));
        detailTextView.setHighlightColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkSelection, resourcesProvider));
        detailTextView.setMovementMethod(new AndroidUtilities.LinkMovementMethodMy());
        detailTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        addView(detailTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 21, 14, 21, 0));

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(HORIZONTAL);
        addView(linearLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 21, 16, 21, 15));

        for (int a = 0; a < 2; a++) {
            TextView textView = new TextView(context);
            textView.setBackground(Theme.AdaptiveRipple.filledRectByKey(Theme.key_featuredStickers_addButton, 8));
            ScaleStateListAnimator.apply(textView, 0.02f, 1.5f);
            textView.setLines(1);
            textView.setSingleLine(true);
            textView.setGravity(Gravity.CENTER_HORIZONTAL);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setGravity(Gravity.CENTER);
            textView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, resourcesProvider));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setTypeface(AndroidUtilities.bold());
            linearLayout.addView(textView, LayoutHelper.createLinear(0, 44, 0.5f, a == 0 ? 0 : 4, 0, a == 0 ? 4 : 0, 0));
            if (a == 0) {
                yesButton = textView;
                yesButton.setOnClickListener(v -> onYesClick(currentType));
            } else {
                noButton = textView;
                noButton.setOnClickListener(v -> onNoClick(currentType));
            }
        }
    }

    public void setType(int type) {
        currentType = type;
        if (type == TYPE_PHONE) {
            final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(UserConfig.getInstance(currentAccount).clientUserId);
            textView.setText(LocaleController.formatString("CheckPhoneNumber", R.string.CheckPhoneNumber, PhoneFormat.getInstance().format("+" + user.phone)));
            String text = LocaleController.getString(R.string.CheckPhoneNumberInfo);
            SpannableStringBuilder builder = new SpannableStringBuilder(text);
            int index1 = text.indexOf("**");
            int index2 = text.lastIndexOf("**");
            if (index1 >= 0 && index2 >= 0 && index1 != index2) {
                builder.replace(index2, index2 + 2, "");
                builder.replace(index1, index1 + 2, "");
                try {
                    builder.setSpan(new URLSpanNoUnderline(LocaleController.getString(R.string.CheckPhoneNumberLearnMoreUrl)), index1, index2 - 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            detailTextView.setText(builder);
            yesButton.setText(LocaleController.getString(R.string.CheckPhoneNumberYes));
            noButton.setVisibility(View.VISIBLE);
            noButton.setText(LocaleController.getString(R.string.CheckPhoneNumberNo));
        } else if (type == TYPE_PASSWORD) {
            textView.setText(LocaleController.getString(R.string.YourPasswordHeader));
            detailTextView.setText(LocaleController.getString(R.string.YourPasswordRemember));
            yesButton.setText(LocaleController.getString(R.string.YourPasswordRememberYes));
            noButton.setVisibility(View.VISIBLE);
            noButton.setText(LocaleController.getString(R.string.YourPasswordRememberNo));
        } else if (type == TYPE_GRACE) {
            textView.setText(LocaleController.getString(R.string.GraceSuggestionTitle));
            detailTextView.setText(LocaleController.getString(R.string.GraceSuggestionMessage));
            yesButton.setText(LocaleController.getString(R.string.GraceSuggestionButton));
            noButton.setVisibility(View.GONE);
        }
    }

    protected void onYesClick(int type) {

    }

    protected void onNoClick(int type) {

    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), heightMeasureSpec);
    }
}
