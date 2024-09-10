package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.LongSparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SMSJobController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.web.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.TL_smsjobs;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CheckBoxSquare;
import org.telegram.ui.Components.FireworksOverlay;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.Locale;

public class SMSSubscribeSheet {

    public static BottomSheet show(Context context, TL_smsjobs.TL_smsjobs_eligibleToJoin isEligible, Runnable onHide, Theme.ResourcesProvider resourcesProvider) {
        BottomSheet sheet = new BottomSheet(context, false, resourcesProvider);
        sheet.fixNavigationBar(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);

        ImageView imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setImageResource(R.drawable.large_sms_code);
        imageView.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        imageView.setBackground(Theme.createCircleDrawable(dp(80), Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider)));
        layout.addView(imageView, LayoutHelper.createLinear(80, 80, Gravity.CENTER_HORIZONTAL, 0, 24, 0, 12));

        TextView textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        textView.setGravity(Gravity.CENTER);
        textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        textView.setTypeface(AndroidUtilities.bold());
        textView.setText(getString(R.string.SmsSubscribeTitle));
        layout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 30, 0, 30, 6));

        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setGravity(Gravity.CENTER);
        textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4, resourcesProvider));
        textView.setText(AndroidUtilities.replaceTags(getString(R.string.SmsSubscribeMessage)));
        layout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 30, 0, 30, 14));

        final Runnable openPremium = () -> {
            BaseFragment fragment = LaunchActivity.getLastFragment();
            if (fragment != null) {
                BaseFragment.BottomSheetParams params = new BaseFragment.BottomSheetParams();
                params.transitionFromLeft = true;
                params.allowNestedScroll = false;
                fragment.showAsSheet(new PremiumPreviewFragment("sms"), params);
            }
        };
        layout.addView(new FeatureCell(context, R.drawable.menu_feature_sms,     getString(R.string.SmsSubscribeFeature1Title), formatPluralString("SmsSubscribeFeature1Message", isEligible == null ? 100 : isEligible.monthly_sent_sms), resourcesProvider), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 30, 16, 30, 0));
        layout.addView(new FeatureCell(context, R.drawable.menu_feature_premium, getString(R.string.SmsSubscribeFeature2Title), AndroidUtilities.replaceSingleTag(getString(R.string.SmsSubscribeFeature2Message), openPremium), resourcesProvider), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 30, 16, 30, 0));
        layout.addView(new FeatureCell(context, R.drawable.menu_feature_gift,    getString(R.string.SmsSubscribeFeature3Title), getString(R.string.SmsSubscribeFeature3Message), resourcesProvider), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 30, 16, 30, 0));

        final Runnable openTOS = () -> {
            if (isEligible == null) return;
            Browser.openUrl(context, isEligible.terms_of_use);
        };
        FrameLayout acceptLayout = new FrameLayout(context);
        final CheckBoxSquare checkBox = new CheckBoxSquare(context, false);
        checkBox.setDuplicateParentStateEnabled(false);
        checkBox.setFocusable(false);
        checkBox.setFocusableInTouchMode(false);
        checkBox.setClickable(false);
        acceptLayout.addView(checkBox, LayoutHelper.createFrame(18, 18, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, 21, 0, 21, 0));
        LinkSpanDrawable.LinksTextView linkTextView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
        linkTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        linkTextView.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText, resourcesProvider));
        linkTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        linkTextView.setMaxLines(2);
        linkTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        linkTextView.setEllipsize(TextUtils.TruncateAt.END);
        linkTextView.setText(AndroidUtilities.replaceSingleTag(getString(R.string.SmsSubscribeAccept), openTOS));
        acceptLayout.addView(linkTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 16 : 58, 21, LocaleController.isRTL ? 58 : 16, 21));
        layout.addView(acceptLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 9, 0, 9, 0));

        ButtonWithCounterView button = new ButtonWithCounterView(context, resourcesProvider);
        button.setText(getString(R.string.SmsSubscribeActivate), false);
        button.setEnabled(false);
        layout.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 14, 0, 14, 0));
        acceptLayout.setOnClickListener(v -> {
            checkBox.setChecked(!checkBox.isChecked(), true);
            button.setEnabled(checkBox.isChecked());
        });
        final float[] shiftDp = new float[] { 4 };
        button.setOnClickListener(v -> {
            final int currentAccount = UserConfig.selectedAccount;
            if (!button.isEnabled()) {
                AndroidUtilities.shakeViewSpring(acceptLayout, shiftDp[0] = -shiftDp[0]);
                BotWebViewVibrationEffect.APP_ERROR.vibrate();
                return;
            }
            SMSJobController.getInstance(currentAccount).setState(SMSJobController.STATE_ASKING_PERMISSION);
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.mainUserInfoChanged);
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.smsJobStatusUpdate);
            requestSMSPermissions(context, () -> {
                SMSJobController.getInstance(currentAccount).checkSelectedSIMCard();
                if (SMSJobController.getInstance(currentAccount).getSelectedSIM() == null) {
                    sheet.dismiss();
                    SMSJobController.getInstance(currentAccount).setState(SMSJobController.STATE_NO_SIM);
                    new AlertDialog.Builder(context, resourcesProvider)
                        .setTitle(LocaleController.getString(R.string.SmsNoSimTitle))
                        .setMessage(AndroidUtilities.replaceTags(LocaleController.getString(R.string.SmsNoSimMessage)))
                        .setPositiveButton(LocaleController.getString(R.string.OK), null)
                        .show();
                    return;
                }
                button.setLoading(true);
                ConnectionsManager.getInstance(currentAccount).sendRequest(new TL_smsjobs.TL_smsjobs_join(), (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                    if (err != null) {
                        button.setLoading(false);
                        BulletinFactory.showError(err);
                    } else if (res instanceof TLRPC.TL_boolFalse) {
                        button.setLoading(false);
                        BulletinFactory.global().createErrorBulletin(LocaleController.getString(R.string.UnknownError)).show();
                    } else {
                        SMSJobController.getInstance(currentAccount).setState(SMSJobController.STATE_JOINED);
                        sheet.dismiss();
                        SMSJobController.getInstance(currentAccount).loadStatus(true);
                        showSubscribed(context, resourcesProvider);
                    }
                }));
            }, false);
        });

        linkTextView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
        linkTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4, resourcesProvider));
        linkTextView.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText, resourcesProvider));
        linkTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        linkTextView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        linkTextView.setText(AndroidUtilities.replaceSingleTag(getString(R.string.SmsSubscribeActivateText), openTOS));
        linkTextView.setGravity(Gravity.CENTER);
        layout.addView(linkTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 30, 17, 30, 14));

        sheet.setCustomView(layout);

        sheet.show();
        if (onHide != null) {
            sheet.setOnHideListener(v -> onHide.run());
        }
        return sheet;
    }

    private static class FeatureCell extends LinearLayout {
        public FeatureCell(Context context, int icon, CharSequence title, CharSequence message, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            setOrientation(HORIZONTAL);

            ImageView iconView = new ImageView(context);
            iconView.setImageResource(icon);
            iconView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider), PorterDuff.Mode.SRC_IN));
            addView(iconView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 0, 4, 17, 0));

            LinearLayout textLayout = new LinearLayout(context);
            textLayout.setOrientation(VERTICAL);
            addView(textLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL));

            TextView titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            titleView.setTypeface(AndroidUtilities.bold());
            titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
            titleView.setText(title);
            textLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 1));

            LinkSpanDrawable.LinksTextView messageView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
            messageView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            messageView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4, resourcesProvider));
            messageView.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText, resourcesProvider));
            messageView.setText(message);
            textLayout.addView(messageView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(Math.min(dp(330), MeasureSpec.getSize(widthMeasureSpec)), MeasureSpec.EXACTLY), heightMeasureSpec);
        }
    }

    public static BottomSheet showSubscribed(Context context, Theme.ResourcesProvider resourcesProvider) {
        final int currentAccount = UserConfig.selectedAccount;
        BottomSheet sheet = new BottomSheet(context, false, resourcesProvider);
        sheet.fixNavigationBar(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);

        RLottieImageView imageView = new RLottieImageView(context);
        imageView.setAnimation(R.raw.giveaway_results, 120, 120);
        imageView.getAnimatedDrawable().multiplySpeed(1.8f);
        imageView.playAnimation();
        layout.addView(imageView, LayoutHelper.createLinear(120, 120, Gravity.CENTER_HORIZONTAL, 0, 24, 0, 12));

        TextView textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        textView.setGravity(Gravity.CENTER);
        textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        textView.setTypeface(AndroidUtilities.bold());
        textView.setText(getString(R.string.SmsPremiumActivated));
        layout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 30, 0, 30, 14));

        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setGravity(Gravity.CENTER);
        textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        final TextView finalTextView2 = textView;
        TLRPC.User user = UserConfig.getInstance(currentAccount).getCurrentUser();
        String countryCode = null;
        if (user != null) {
            countryCode = SMSJobController.getCountryFromPhoneNumber(context, user.phone);
        }
        String country = null;
        if (!TextUtils.isEmpty(countryCode)) {
            try {
                country = new Locale("", countryCode).getDisplayCountry();
            } catch (Exception e) {

            }
        }
        if (country != null) {
            finalTextView2.setText(AndroidUtilities.replaceTags(formatString(R.string.SmsPremiumActivatedText, country)));
        } else {
            finalTextView2.setText(AndroidUtilities.replaceTags(getString(R.string.SmsPremiumActivatedTextUnknown)));
        }
        layout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 30, 0, 30, 16));

        LinkSpanDrawable.LinksTextView linkTextView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
        linkTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        linkTextView.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText, resourcesProvider));
        linkTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        linkTextView.setGravity(Gravity.CENTER);
        linkTextView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        linkTextView.setText(AndroidUtilities.replaceSingleTag(getString(R.string.SmsPremiumActivatedText2), () -> {
            sheet.dismiss();
            BaseFragment lastFragment = LaunchActivity.getLastFragment();
            if (lastFragment != null) {
                lastFragment.presentFragment(new SMSStatsActivity());
            }
        }));
        layout.addView(linkTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 30, 0, 30, 24));

        ButtonWithCounterView button = new ButtonWithCounterView(context, resourcesProvider);
        button.setText(getString(R.string.OK), false);
        layout.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 14, 0, 14, 0));
        button.setOnClickListener(v -> {
            sheet.dismiss();
        });

        sheet.setCustomView(layout);

        sheet.show();

        final FireworksOverlay fireworksOverlay = new FireworksOverlay(context);
        sheet.getContainer().addView(fireworksOverlay, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        fireworksOverlay.postDelayed(() -> {
            fireworksOverlay.start(true);
        }, 720);
        sheet.setOnHideListener(v -> {
            fireworksOverlay.animate().alpha(0).start();
        });

        return sheet;
    }

    private static LongSparseArray<Utilities.Callback<Boolean>> permissionsCallbacks;
    public static void requestSMSPermissions(Context context, Runnable whenDone, boolean forceOpenSettings) {
        if (permissionsCallbacks == null) {
            permissionsCallbacks = new LongSparseArray<>();
        }
        Activity _activity = AndroidUtilities.findActivity(context);
        if (_activity == null) _activity = LaunchActivity.instance;
        if (_activity == null || whenDone == null) return;
        final Activity activity = _activity;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            final boolean hasSMSPermission = activity.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
            final boolean hasPhonePermission = activity.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED && activity.checkSelfPermission(Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED;
            if (hasSMSPermission && hasPhonePermission) {
                whenDone.run();
                return;
            }
            if (activity.shouldShowRequestPermissionRationale(Manifest.permission.SEND_SMS) || activity.shouldShowRequestPermissionRationale(Manifest.permission.READ_PHONE_STATE) || activity.shouldShowRequestPermissionRationale(Manifest.permission.READ_PHONE_NUMBERS) || forceOpenSettings) {
                int messageResId;
                if (!hasSMSPermission && !hasPhonePermission) {
                    messageResId = R.string.SmsPermissionTextSMSPhoneSettings;
                } else if (!hasSMSPermission) {
                    messageResId = R.string.SmsPermissionTextSMSSettings;
                } else {
                    messageResId = R.string.SmsPermissionTextPhoneSettings;
                }
                new AlertDialog.Builder(activity)
                    .setMessage(AndroidUtilities.replaceTags(getString(messageResId)))
                    .setPositiveButton(LocaleController.getString(R.string.Settings), (dialog, which) -> {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
                        intent.setData(uri);
                        activity.startActivity(intent);
                    })
                    .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                    .setOnDismissListener(dialog -> {

                    })
                    .setTopImage(R.drawable.permissions_sms, Theme.getColor(Theme.key_dialogTopBackground))
                    .show();
            } else {
                new AlertDialog.Builder(activity)
                    .setMessage(AndroidUtilities.replaceTags(getString(R.string.SmsPermissionText)))
                    .setPositiveButton(LocaleController.getString(R.string.Next), (dialog, which) -> {
                        final int requestCode = (int) (1000 + Math.abs(Math.random() * (Integer.MAX_VALUE - 1000)));
                        permissionsCallbacks.put(requestCode, success -> {
                            if (!success) {
                                requestSMSPermissions(context, whenDone, true);
                            } else {
                                whenDone.run();
                            }
                        });
                        activity.requestPermissions(new String[] { Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS }, requestCode);
                    })
                    .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                    .setOnDismissListener(dialog -> {

                    })
                    .setTopImage(R.drawable.permissions_sms, Theme.getColor(Theme.key_dialogTopBackground))
                    .show();
            }
            return;
        }
        whenDone.run();
    }

    public static boolean checkSMSPermissions(int requestCode, String[] permissions, int[] grantResults) {
        if (permissionsCallbacks != null) {
            Utilities.Callback<Boolean> callback = permissionsCallbacks.get(requestCode);
            if (callback != null) {
                permissionsCallbacks.remove(requestCode);
                boolean granted = true;
                for (int i = 0; i < grantResults.length; ++i) {
                    if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                        granted = false;
                    }
                }
                final boolean finalGranted = granted;
                AndroidUtilities.runOnUIThread(() -> callback.run(finalGranted));
                return true;
            }
        }
        return false;
    }

}
