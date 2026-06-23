package org.Tajgram.ui;

import static org.Tajgram.messenger.AndroidUtilities.accelerateInterpolator;
import static org.Tajgram.messenger.AndroidUtilities.dp;
import static org.Tajgram.messenger.AndroidUtilities.replaceSingleTag;
import static org.Tajgram.messenger.LocaleController.formatString;
import static org.Tajgram.messenger.LocaleController.formatYearMonthDay;
import static org.Tajgram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.Tajgram.messenger.AndroidUtilities;
import org.Tajgram.messenger.ApplicationLoader;
import org.Tajgram.messenger.FileLog;
import org.Tajgram.messenger.MessagesController;
import org.Tajgram.messenger.R;
import org.Tajgram.messenger.UserConfig;
import org.Tajgram.messenger.UserObject;
import org.Tajgram.messenger.browser.Browser;
import org.Tajgram.tgnet.TLRPC;
import org.Tajgram.ui.ActionBar.BaseFragment;
import org.Tajgram.ui.ActionBar.BottomSheet;
import org.Tajgram.ui.ActionBar.Theme;
import org.Tajgram.ui.Components.BackupImageView;
import org.Tajgram.ui.Components.LayoutHelper;
import org.Tajgram.ui.Components.RLottieImageView;
import org.Tajgram.ui.Stars.ExplainStarsSheet;
import org.Tajgram.ui.Stories.recorder.ButtonWithCounterView;
import org.Tajgram.ui.bots.AffiliateProgramFragment;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AccountFrozenAlert {

    public static boolean shown;

    public static boolean isSpamBot(int currentAccount, TLRPC.User user) {
        if (user == null) {
            return false;
        }
        final String username = UserObject.getPublicUsername(user);
        if (username == null) {
            return false;
        }
        try {
            final Matcher m = Pattern.compile("t\\.me/([a-zA-Z0-9]+)/?").matcher(MessagesController.getInstance(currentAccount).freezeAppealUrl);
            return m.find() && username.equalsIgnoreCase(m.group(1));
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }

    public static void show(int currentAccount) {
        if (shown) return;
        if (UserConfig.selectedAccount != currentAccount) return;
        Context context = LaunchActivity.instance;
        if (context == null) context = ApplicationLoader.applicationContext;
        if (context == null) return;
        BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
        show(context, currentAccount, lastFragment != null ? lastFragment.getResourceProvider() : null);
    }

    public static void show(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
        if (shown) return;

        BottomSheet.Builder b = new BottomSheet.Builder(context, false, resourcesProvider);
        BottomSheet[] sheet = new BottomSheet[1];

        final Runnable openAppeal = () -> {
            String url = MessagesController.getInstance(currentAccount).freezeAppealUrl;
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }
            Browser.openUrl(context, url);
            sheet[0].dismiss();
        };

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setPadding(dp(16), dp(20), dp(16), dp(8));
        linearLayout.setClipChildren(false);
        linearLayout.setClipToPadding(false);

        RLottieImageView imageView = new RLottieImageView(context);
        imageView.setAnimation(R.raw.media_forbidden, dp(115), dp(115));
        imageView.playAnimation();
        linearLayout.addView(imageView, LayoutHelper.createLinear(115, 115, Gravity.CENTER, 0, 0, 0, 9));

        TextView textView = new TextView(context);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        textView.setText(getString(R.string.AccountFrozenTitle));
        textView.setGravity(Gravity.CENTER);
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 0, 0, 23));

        ExplainStarsSheet.FeatureCell featureCell = new ExplainStarsSheet.FeatureCell(context, ExplainStarsSheet.FeatureCell.STYLE_SHEET, resourcesProvider);
        featureCell.set(R.drawable.msg_block2, getString(R.string.AccountFrozen1Title), getString(R.string.AccountFrozen1Text));
        linearLayout.addView(featureCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 0, 0, 0));

        featureCell = new ExplainStarsSheet.FeatureCell(context, ExplainStarsSheet.FeatureCell.STYLE_SHEET, resourcesProvider);
        featureCell.set(R.drawable.menu_privacy, getString(R.string.AccountFrozen2Title), getString(R.string.AccountFrozen2Text));
        linearLayout.addView(featureCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 0, 0, 0));

        featureCell = new ExplainStarsSheet.FeatureCell(context, ExplainStarsSheet.FeatureCell.STYLE_SHEET, resourcesProvider);
        featureCell.set(R.drawable.menu_feature_hourglass, getString(R.string.AccountFrozen3Title), replaceSingleTag(formatString(R.string.AccountFrozen3Text, formatYearMonthDay(MessagesController.getInstance(currentAccount).freezeUntilDate, true)), openAppeal::run));
        linearLayout.addView(featureCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 0, 0, 0));

        ButtonWithCounterView button = new ButtonWithCounterView(context, true, resourcesProvider);
        button.setText(getString(R.string.AccountFrozenButtonAppeal), false);
        button.setOnClickListener(v -> openAppeal.run());
        linearLayout.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.FILL_HORIZONTAL, 0, 13, 0, 4));

        button = new ButtonWithCounterView(context, false, resourcesProvider);
        button.setText(getString(R.string.AccountFrozenButtonUnderstood), false);
        button.setOnClickListener(v -> sheet[0].dismiss());
        linearLayout.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.FILL_HORIZONTAL, 0, 0, 0, 0));

        b.setCustomView(linearLayout);
        sheet[0] = b.create();
        sheet[0].useBackgroundTopPadding = false;

        sheet[0].fixNavigationBar();
        shown = true;
        sheet[0].show();
        sheet[0].setOnDismissListener(v -> {
            shown = false;
        });
    }

}
