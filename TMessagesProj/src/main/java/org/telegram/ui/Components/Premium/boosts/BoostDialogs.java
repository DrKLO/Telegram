package org.telegram.ui.Components.Premium.boosts;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.replaceTags;
import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.formatPluralStringComma;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.Components.Premium.boosts.SelectorBottomSheet.TYPE_CHANNEL;
import static org.telegram.ui.Components.Premium.boosts.SelectorBottomSheet.TYPE_COUNTRY;
import static org.telegram.ui.Components.Premium.boosts.SelectorBottomSheet.TYPE_USER;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.BoostsActivity;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EffectsTextView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.NumberPicker;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.Stars.StarsIntroActivity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class BoostDialogs {
    private final static long ONE_DAY = 1000 * 60 * 60 * 24;

    public static long getThreeDaysAfterToday() {
        return BoostDialogs.roundByFiveMinutes(new Date().getTime() + (ONE_DAY * 3));
    }

    public static void showToastError(Context context, TLRPC.TL_error error) {
        if (error != null && error.text != null && !TextUtils.isEmpty(error.text)) {
            Toast.makeText(context, error.text, Toast.LENGTH_LONG).show();
        }
    }

    public static void processApplyGiftCodeError(TLRPC.TL_error error, FrameLayout containerLayout, Theme.ResourcesProvider resourcesProvider, Runnable share) {
        if (error == null || error.text == null) {
            return;
        }
        if (error.text.contains("PREMIUM_SUB_ACTIVE_UNTIL_")) {
            String strDate = error.text.replace("PREMIUM_SUB_ACTIVE_UNTIL_", "");
            long date = Long.parseLong(strDate);
            String formattedDate = LocaleController.getInstance().getFormatterBoostExpired().format(new Date(date * 1000L));
            String subTitleText = getString("GiftPremiumActivateErrorText", R.string.GiftPremiumActivateErrorText);
            SpannableStringBuilder subTitleWithLink = AndroidUtilities.replaceSingleTag(
                    subTitleText,
                    Theme.key_undo_cancelColor, 0,
                    share);
            BulletinFactory.of(containerLayout, resourcesProvider).createSimpleBulletin(R.raw.chats_infotip,
                    LocaleController.getString(R.string.GiftPremiumActivateErrorTitle),
                    AndroidUtilities.replaceCharSequence("%1$s", subTitleWithLink, replaceTags("**" + formattedDate + "**"))
            ).show();
            try {
                containerLayout.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            } catch (Exception ignore) {}
        } else {
            BoostDialogs.showToastError(containerLayout.getContext(), error);
        }
    }

    private static void showBulletin(final BulletinFactory bulletinFactory, Theme.ResourcesProvider resourcesProvider, final TLRPC.Chat chat, final boolean isGiveaway) {
        AndroidUtilities.runOnUIThread(() -> bulletinFactory.createSimpleBulletin(R.raw.star_premium_2,
                isGiveaway ? getString("BoostingGiveawayCreated", R.string.BoostingGiveawayCreated)
                        : getString("BoostingAwardsCreated", R.string.BoostingAwardsCreated),
                AndroidUtilities.replaceSingleTag(
                        isGiveaway ? getString(ChatObject.isChannelAndNotMegaGroup(chat) ? R.string.BoostingCheckStatistic : R.string.BoostingCheckStatisticGroup) :
                                getString(ChatObject.isChannelAndNotMegaGroup(chat) ? R.string.BoostingCheckGiftsStatistic : R.string.BoostingCheckGiftsStatisticGroup),
                        Theme.key_undo_cancelColor, 0, () -> {
                            if (chat != null) {
                                BaseFragment.BottomSheetParams params = new BaseFragment.BottomSheetParams();
                                params.transitionFromLeft = true;
                                LaunchActivity.getLastFragment().showAsSheet(new BoostsActivity(-chat.id), params);
                            }
                        }, resourcesProvider)
        ).setDuration(Bulletin.DURATION_PROLONG).show(), 300);
    }

    public static void showGiftLinkForwardedBulletin(long did) {
        CharSequence text;
        if (did == UserConfig.getInstance(UserConfig.selectedAccount).clientUserId) {
            text = AndroidUtilities.replaceTags(LocaleController.getString(R.string.BoostingGiftLinkForwardedToSavedMsg));
        } else {
            if (DialogObject.isChatDialog(did)) {
                TLRPC.Chat chat = MessagesController.getInstance(UserConfig.selectedAccount).getChat(-did);
                text = AndroidUtilities.replaceTags(LocaleController.formatString("BoostingGiftLinkForwardedTo", R.string.BoostingGiftLinkForwardedTo, chat.title));
            } else {
                TLRPC.User user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(did);
                text = AndroidUtilities.replaceTags(LocaleController.formatString("BoostingGiftLinkForwardedTo", R.string.BoostingGiftLinkForwardedTo, UserObject.getFirstName(user)));
            }
        }
        AndroidUtilities.runOnUIThread(() -> {
            BulletinFactory bulletinFactory = BulletinFactory.global();
            if (bulletinFactory != null) {
                bulletinFactory.createSimpleBulletinWithIconSize(R.raw.forward, text, 30).show();
            }
        }, 450);
    }

    public static void showBulletinError(TLRPC.TL_error error) {
        BulletinFactory bulletinFactory = BulletinFactory.global();
        if (bulletinFactory == null || error == null || error.text == null) {
            return;
        }
        bulletinFactory.createErrorBulletin(error.text).show();
    }

    public static void showBulletin(FrameLayout container, Theme.ResourcesProvider resourcesProvider, final TLRPC.Chat chat, final boolean isGiveaway) {
        BulletinFactory bulletinFactory = BulletinFactory.of(container, resourcesProvider);
        showBulletin(bulletinFactory, resourcesProvider, chat, isGiveaway);
    }

    public static void showBulletin(final BaseFragment baseFragment, final TLRPC.Chat chat, final boolean isGiveaway) {
        if (baseFragment == null) {
            return;
        }
        BulletinFactory bulletinFactory = BulletinFactory.of(baseFragment);
        showBulletin(bulletinFactory, baseFragment.getResourceProvider(), chat, isGiveaway);
    }

    private static long roundByFiveMinutes(long dateMs) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(dateMs);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.set(Calendar.SECOND, 0);

        int minute = calendar.get(Calendar.MINUTE);
        while (minute % 5 != 0) {
            minute++;
        }
        calendar.set(Calendar.MINUTE, minute);
        return calendar.getTimeInMillis();
    }

    public static void showDatePicker(Context context, long currentDate, final AlertsCreator.ScheduleDatePickerDelegate datePickerDelegate, Theme.ResourcesProvider resourcesProvider) {
        final AlertsCreator.ScheduleDatePickerColors datePickerColors = new AlertsCreator.ScheduleDatePickerColors(resourcesProvider);
        BottomSheet.Builder builder = new BottomSheet.Builder(context, false, resourcesProvider);
        builder.setApplyBottomPadding(false);

        final NumberPicker dayPicker = new NumberPicker(context, resourcesProvider);
        dayPicker.setTextColor(datePickerColors.textColor);
        dayPicker.setTextOffset(dp(10));
        dayPicker.setItemCount(5);
        final NumberPicker hourPicker = new NumberPicker(context, resourcesProvider) {
            @Override
            protected CharSequence getContentDescription(int value) {
                return formatPluralString("Hours", value);
            }
        };
        hourPicker.setWrapSelectorWheel(true);
        hourPicker.setAllItemsCount(24);
        hourPicker.setItemCount(5);
        hourPicker.setTextColor(datePickerColors.textColor);
        hourPicker.setTextOffset(-dp(10));
        hourPicker.setTag("HOUR");
        final NumberPicker minutePicker = new NumberPicker(context, resourcesProvider) {
            @Override
            protected CharSequence getContentDescription(int value) {
                return formatPluralString("Minutes", value);
            }
        };
        minutePicker.setWrapSelectorWheel(true);
        minutePicker.setAllItemsCount(60);
        minutePicker.setItemCount(5);
        minutePicker.setTextColor(datePickerColors.textColor);
        minutePicker.setTextOffset(-dp(34));

        LinearLayout container = new LinearLayout(context) {

            boolean ignoreLayout = false;
            final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

            {
                setWillNotDraw(false);
                paint.setTextSize(dp(20));
                paint.setTypeface(AndroidUtilities.bold());
                paint.setColor(datePickerColors.textColor);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                ignoreLayout = true;
                int count;
                if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
                    count = 3;
                } else {
                    count = 5;
                }
                dayPicker.setItemCount(count);
                hourPicker.setItemCount(count);
                minutePicker.setItemCount(count);
                dayPicker.getLayoutParams().height = dp(NumberPicker.DEFAULT_SIZE_PER_COUNT) * count;
                hourPicker.getLayoutParams().height = dp(NumberPicker.DEFAULT_SIZE_PER_COUNT) * count;
                minutePicker.getLayoutParams().height = dp(NumberPicker.DEFAULT_SIZE_PER_COUNT) * count;
                ignoreLayout = false;
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                canvas.drawText(":", hourPicker.getRight() - dp(12), (getHeight() / 2f) - dp(11), paint);
            }

            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }
        };
        container.setOrientation(LinearLayout.VERTICAL);

        FrameLayout titleLayout = new FrameLayout(context);
        container.addView(titleLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 22, 0, 0, 4));

        TextView titleView = new TextView(context);
        titleView.setText(getString("BoostingSelectDateTime", R.string.BoostingSelectDateTime));
        titleView.setTextColor(datePickerColors.textColor);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.bold());
        titleLayout.addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 12, 0, 0));
        titleView.setOnTouchListener((v, event) -> true);

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        linearLayout.setWeightSum(1.0f);
        container.addView(linearLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 12, 0, 12));

        long currentTime = System.currentTimeMillis();
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(currentTime);
        int currentYear = calendar.get(Calendar.YEAR);

        TextView buttonTextView = new TextView(context) {
            @Override
            public CharSequence getAccessibilityClassName() {
                return Button.class.getName();
            }
        };

        long maxPeriodMs = BoostRepository.giveawayPeriodMax() * 1000L;
        Calendar calendarMaxPeriod = Calendar.getInstance();
        calendarMaxPeriod.setTimeInMillis(maxPeriodMs);
        int maxDay = calendarMaxPeriod.get(Calendar.DAY_OF_YEAR);
        calendarMaxPeriod.setTimeInMillis(System.currentTimeMillis());
        calendarMaxPeriod.add(Calendar.MILLISECOND, (int) maxPeriodMs);

        int maxHour = calendarMaxPeriod.get(Calendar.HOUR_OF_DAY);
        int maxMinute = calendar.get(Calendar.MINUTE);

        linearLayout.addView(dayPicker, LayoutHelper.createLinear(0, 54 * 5, 0.5f));
        dayPicker.setMinValue(0); //0 for today
        dayPicker.setMaxValue(maxDay - 1);
        dayPicker.setWrapSelectorWheel(false);
        dayPicker.setTag("DAY");
        dayPicker.setFormatter(value -> {
            if (value == 0) {
                return getString("MessageScheduleToday", R.string.MessageScheduleToday);
            } else {
                long date = currentTime + (long) value * 86400000L;
                calendar.setTimeInMillis(date);
                int year = calendar.get(Calendar.YEAR);
                if (year == currentYear) {
                    return LocaleController.getInstance().getFormatterScheduleDay().format(date);
                } else {
                    return LocaleController.getInstance().getFormatterScheduleYear().format(date);
                }
            }
        });
        final NumberPicker.OnValueChangeListener onValueChangeListener = (picker, oldVal, newVal) -> {
            try {
                container.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            } catch (Exception ignore) {}
            if (picker.getTag() != null && (picker.getTag().equals("DAY"))) {
                if (picker.getValue() == picker.getMinValue()) {
                    Calendar calendarCurrent = Calendar.getInstance();
                    calendarCurrent.setTimeInMillis(System.currentTimeMillis());
                    int minHour = calendarCurrent.get(Calendar.HOUR_OF_DAY);
                    int minMinute = calendarCurrent.get(Calendar.MINUTE);
                    int minValueMinute = (minMinute / 5) + 1;
                    if (minValueMinute > 11) {
                        if (minHour == 23) {
                            picker.setMinValue(picker.getMinValue() + 1);
                            hourPicker.setMinValue(0);
                        } else {
                            hourPicker.setMinValue(minHour + 1);
                        }
                        minutePicker.setMinValue(0);
                    } else {
                        hourPicker.setMinValue(minHour);
                        minutePicker.setMinValue(minValueMinute);
                    }
                } else if (picker.getValue() == picker.getMaxValue()) {
                    hourPicker.setMaxValue(maxHour);
                    minutePicker.setMaxValue(Math.min((maxMinute / 5), 11));
                } else {
                    hourPicker.setMinValue(0);
                    minutePicker.setMinValue(0);
                    hourPicker.setMaxValue(23);
                    minutePicker.setMaxValue(11);
                }
            }

            if (picker.getTag() != null && picker.getTag().equals("HOUR") && dayPicker.getValue() == dayPicker.getMinValue()) {
                if (picker.getValue() == picker.getMinValue()) {
                    Calendar calendarCurrent = Calendar.getInstance();
                    calendarCurrent.setTimeInMillis(System.currentTimeMillis());
                    int minMinute = calendarCurrent.get(Calendar.MINUTE);
                    int minValueMinute = (minMinute / 5) + 1;
                    if (minValueMinute > 11) {
                        minutePicker.setMinValue(0);
                    } else {
                        minutePicker.setMinValue(minValueMinute);
                    }
                } else {
                    minutePicker.setMinValue(0);
                    minutePicker.setMaxValue(11);
                }
            }
        };
        dayPicker.setOnValueChangedListener(onValueChangeListener);

        hourPicker.setMinValue(0);
        hourPicker.setMaxValue(23);
        linearLayout.addView(hourPicker, LayoutHelper.createLinear(0, 54 * 5, 0.2f));
        hourPicker.setFormatter(value -> String.valueOf(value));
        hourPicker.setOnValueChangedListener(onValueChangeListener);

        minutePicker.setMinValue(0);
        minutePicker.setMaxValue(11);
        minutePicker.setValue(0);
        minutePicker.setFormatter(value -> String.format("%02d", value * 5));
        linearLayout.addView(minutePicker, LayoutHelper.createLinear(0, 54 * 5, 0.3f));
        minutePicker.setOnValueChangedListener(onValueChangeListener);

        if (currentDate > 0) {
            calendar.setTimeInMillis(System.currentTimeMillis());
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            int days = (int) ((currentDate - calendar.getTimeInMillis()) / (24 * 60 * 60 * 1000));
            calendar.setTimeInMillis(currentDate);
            minutePicker.setValue(calendar.get(Calendar.MINUTE) / 5);
            hourPicker.setValue(calendar.get(Calendar.HOUR_OF_DAY));
            dayPicker.setValue(days);
            onValueChangeListener.onValueChange(dayPicker, dayPicker.getValue(), dayPicker.getValue());
            onValueChangeListener.onValueChange(hourPicker, hourPicker.getValue(), hourPicker.getValue());
        }

        buttonTextView.setPadding(dp(34), 0, dp(34), 0);
        buttonTextView.setGravity(Gravity.CENTER);
        buttonTextView.setTextColor(datePickerColors.buttonTextColor);
        buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        buttonTextView.setTypeface(AndroidUtilities.bold());
        buttonTextView.setBackground(Theme.AdaptiveRipple.filledRect(datePickerColors.buttonBackgroundColor, 8));
        buttonTextView.setText(getString("BoostingConfirm", R.string.BoostingConfirm));
        container.addView(buttonTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.BOTTOM, 16, 15, 16, 16));
        buttonTextView.setOnClickListener(v -> {
            calendar.setTimeInMillis(System.currentTimeMillis() + (long) dayPicker.getValue() * 24 * 3600 * 1000);
            calendar.set(Calendar.HOUR_OF_DAY, hourPicker.getValue());
            calendar.set(Calendar.MINUTE, minutePicker.getValue() * 5);
            datePickerDelegate.didSelectDate(true, (int) (calendar.getTimeInMillis() / 1000));
            builder.getDismissRunnable().run();
        });

        builder.setCustomView(container);
        BottomSheet bottomSheet = builder.show();
        bottomSheet.setBackgroundColor(datePickerColors.backgroundColor);
        bottomSheet.fixNavigationBar(datePickerColors.backgroundColor);
        AndroidUtilities.setLightStatusBar(bottomSheet.getWindow(), ColorUtils.calculateLuminance(datePickerColors.backgroundColor) > 0.7f);
    }

    public static void showUnsavedChanges(int type, Context context, Theme.ResourcesProvider resourcesProvider, Runnable onApply, Runnable onDiscard) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(getString("UnsavedChanges", R.string.UnsavedChanges));
        String text;
        switch (type) {
            case TYPE_USER:
                text = getString("BoostingApplyChangesUsers", R.string.BoostingApplyChangesUsers);
                break;
            case TYPE_CHANNEL:
                text = getString("BoostingApplyChangesChannels", R.string.BoostingApplyChangesChannels);
                break;
            case TYPE_COUNTRY:
                text = getString("BoostingApplyChangesCountries", R.string.BoostingApplyChangesCountries);
                break;
            default:
                text = "";
        }
        builder.setMessage(text);
        builder.setPositiveButton(getString("ApplyTheme", R.string.ApplyTheme), (dialogInterface, i) -> {
            onApply.run();
        });
        builder.setNegativeButton(getString("Discard", R.string.Discard), (dialogInterface, i) -> {
            onDiscard.run();
        });
        builder.show();
    }

    public static boolean checkReduceUsers(Context context, Theme.ResourcesProvider resourcesProvider, List<TLRPC.TL_premiumGiftCodeOption> list, TLRPC.TL_premiumGiftCodeOption selected) {
        if (selected.store_product == null) {
            List<Integer> result = new ArrayList<>();
            for (TLRPC.TL_premiumGiftCodeOption item : list) {
                if (item.months == selected.months && item.store_product != null) {
                    result.add(item.users);
                }
            }

            String downTo = TextUtils.join(", ", result);
            int current = selected.users;

            AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
            builder.setTitle(getString("BoostingReduceQuantity", R.string.BoostingReduceQuantity));
            builder.setMessage(replaceTags(formatPluralString("BoostingReduceUsersTextPlural", current, downTo)));
            builder.setPositiveButton(getString("OK", R.string.OK), (dialogInterface, i) -> {

            });
            builder.show();
            return true;
        }
        return false;
    }

    public static boolean checkReduceQuantity(List<Integer> sliderValues, Context context, Theme.ResourcesProvider resourcesProvider, List<TLRPC.TL_premiumGiftCodeOption> list, TLRPC.TL_premiumGiftCodeOption selected, Utilities.Callback<TLRPC.TL_premiumGiftCodeOption> onSuccess) {
        if (selected.store_product == null) {
            List<TLRPC.TL_premiumGiftCodeOption> result = new ArrayList<>();
            for (TLRPC.TL_premiumGiftCodeOption item : list) {
                if (item.months == selected.months && item.store_product != null && sliderValues.contains(item.users)) {
                    result.add(item);
                }
            }
            TLRPC.TL_premiumGiftCodeOption suggestion = result.get(0);

            for (TLRPC.TL_premiumGiftCodeOption option : result) {
                if (selected.users > option.users && option.users > suggestion.users) {
                    suggestion = option;
                }
            }

            final TLRPC.TL_premiumGiftCodeOption finalSuggestion = suggestion;

            String months = LocaleController.formatPluralString("GiftMonths", suggestion.months);
            int current = selected.users;
            int downTo = suggestion.users;
            AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
            builder.setTitle(getString("BoostingReduceQuantity", R.string.BoostingReduceQuantity));
            builder.setMessage(replaceTags(formatPluralString("BoostingReduceQuantityTextPlural", current, months, downTo)));
            builder.setPositiveButton(getString("Reduce", R.string.Reduce), (dialogInterface, i) -> onSuccess.run(finalSuggestion));
            builder.setNegativeButton(getString("Cancel", R.string.Cancel), (dialogInterface, i) -> {

            });
            builder.show();
            return true;
        }
        return false;
    }

    public static void showAbout(boolean isChannel, String from, long msgDate, TLRPC.TL_payments_giveawayInfo giveawayInfo, TLRPC.TL_messageMediaGiveaway giveaway, Context context, Theme.ResourcesProvider resourcesProvider) {
        int quantity = giveaway.quantity;
        String months = formatPluralString("BoldMonths", giveaway.months);
        String endDate = LocaleController.getInstance().getFormatterGiveawayMonthDay().format(new Date(giveaway.until_date * 1000L));

        String fromTime = LocaleController.getInstance().getFormatterDay().format(new Date(giveawayInfo.start_date * 1000L));
        String fromDate = LocaleController.getInstance().getFormatterGiveawayMonthDayYear().format(new Date(giveawayInfo.start_date * 1000L));
        final boolean isSeveralChats = giveaway.channels.size() > 1;
        final boolean isStars = (giveaway.flags & 32) != 0;
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(getString("BoostingGiveAwayAbout", R.string.BoostingGiveAwayAbout));
        SpannableStringBuilder stringBuilder = new SpannableStringBuilder();

        if (isStars) {
            stringBuilder.append(replaceTags(formatPluralStringComma(isChannel ? "BoostingStarsGiveawayHowItWorksText" : "BoostingStarsGiveawayHowItWorksTextGroup", (int) giveaway.stars, from)));
        } else {
            stringBuilder.append(replaceTags(formatPluralString(isChannel ? "BoostingGiveawayHowItWorksText" : "BoostingGiveawayHowItWorksTextGroup", quantity, from, quantity, months)));
        }
        stringBuilder.append("\n\n");

        if (giveaway.prize_description != null && !giveaway.prize_description.isEmpty()) {
            stringBuilder.append(replaceTags(formatPluralString("BoostingGiveawayHowItWorksIncludeText", quantity, from, giveaway.prize_description)));
            stringBuilder.append("\n\n");
        }

        if (giveaway.only_new_subscribers) {
            if (isSeveralChats) {
                String andStr = formatPluralString("BoostingGiveawayHowItWorksSubTextDateSeveral2", giveaway.channels.size() - 1, fromTime, fromDate);
                stringBuilder.append(replaceTags(formatPluralString("BoostingGiveawayHowItWorksSubTextDateSeveral1", quantity, endDate, quantity, from, andStr)));
            } else {
                stringBuilder.append(replaceTags(formatPluralString("BoostingGiveawayHowItWorksSubTextDate", quantity, endDate, quantity, from, fromTime, fromDate)));
            }
        } else {
            if (isSeveralChats) {
                String andStr = formatPluralString("BoostingGiveawayHowItWorksSubTextSeveral2", giveaway.channels.size() - 1);
                stringBuilder.append(replaceTags(formatPluralString("BoostingGiveawayHowItWorksSubTextSeveral1", quantity, endDate, quantity, from, andStr)));
            } else {
                stringBuilder.append(replaceTags(formatPluralString("BoostingGiveawayHowItWorksSubText", quantity, endDate, quantity, from)));
            }
        }

        stringBuilder.append("\n\n");

        if (giveawayInfo.participating) {
            if (isSeveralChats) {
                stringBuilder.append(replaceTags(formatPluralString("BoostingGiveawayParticipantMultiPlural", giveaway.channels.size() - 1, from)));
            } else {
                stringBuilder.append(replaceTags(formatString("BoostingGiveawayParticipant", R.string.BoostingGiveawayParticipant, from)));
            }
        } else if (giveawayInfo.disallowed_country != null && !giveawayInfo.disallowed_country.isEmpty()) {
            stringBuilder.append(replaceTags(getString("BoostingGiveawayNotEligibleCountry", R.string.BoostingGiveawayNotEligibleCountry)));
        } else if (giveawayInfo.admin_disallowed_chat_id != 0) {
            TLRPC.Chat badChat = MessagesController.getInstance(UserConfig.selectedAccount).getChat(giveawayInfo.admin_disallowed_chat_id);
            String title = badChat != null ? badChat.title : "";
            stringBuilder.append(replaceTags(formatString(isChannel ? R.string.BoostingGiveawayNotEligibleAdmin : R.string.BoostingGiveawayNotEligibleAdminGroup, title)));
        } else if (giveawayInfo.joined_too_early_date != 0) {
            String date = LocaleController.getInstance().getFormatterGiveawayMonthDayYear().format(new Date(giveawayInfo.joined_too_early_date * 1000L));
            stringBuilder.append(replaceTags(formatString("BoostingGiveawayNotEligible", R.string.BoostingGiveawayNotEligible, date)));
        } else {
            if (isSeveralChats) {
                stringBuilder.append(replaceTags(formatPluralString("BoostingGiveawayTakePartMultiPlural", giveaway.channels.size() - 1, from, endDate)));
            } else {
                stringBuilder.append(replaceTags(formatString("BoostingGiveawayTakePart", R.string.BoostingGiveawayTakePart, from, endDate)));
            }
        }

        builder.setMessage(stringBuilder);
        builder.setPositiveButton(getString("OK", R.string.OK), (dialogInterface, i) -> {

        });
        applyDialogStyle(builder.show(), false);
    }

    public static void showAboutEnd(boolean isChannel, String from, long msgDate, TLRPC.TL_payments_giveawayInfoResults giveawayInfo, TLRPC.TL_messageMediaGiveaway giveaway, Context context, Theme.ResourcesProvider resourcesProvider) {
        if (giveaway.until_date == 0) {
            giveaway.until_date = giveawayInfo.finish_date;
        }
        int quantity = giveaway.quantity;
        String months = formatPluralString("BoldMonths", giveaway.months);
        String endDate = LocaleController.getInstance().getFormatterGiveawayMonthDay().format(new Date(giveaway.until_date * 1000L));

        String fromTime = LocaleController.getInstance().getFormatterDay().format(new Date(giveawayInfo.start_date * 1000L));
        String fromDate = LocaleController.getInstance().getFormatterGiveawayMonthDayYear().format(new Date(giveawayInfo.start_date * 1000L));
        boolean isSeveralChats = giveaway.channels.size() > 1;
        final boolean isStars = (giveaway.flags & 32) != 0;
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(getString("BoostingGiveawayEnd", R.string.BoostingGiveawayEnd));
        SpannableStringBuilder stringBuilder = new SpannableStringBuilder();

        if (isStars) {
            stringBuilder.append(replaceTags(formatPluralStringComma(isChannel ? "BoostingStarsGiveawayHowItWorksTextEnd" : "BoostingStarsGiveawayHowItWorksTextEndGroup", (int) giveaway.stars, from)));
        } else {
            stringBuilder.append(replaceTags(formatPluralString(isChannel ? "BoostingGiveawayHowItWorksTextEnd" : "BoostingGiveawayHowItWorksTextEndGroup", quantity, from, quantity, months)));
        }
        stringBuilder.append("\n\n");

        if (giveaway.prize_description != null && !giveaway.prize_description.isEmpty()) {
            stringBuilder.append(replaceTags(formatPluralString("BoostingGiveawayHowItWorksIncludeText", quantity, from, giveaway.prize_description)));
            stringBuilder.append("\n\n");
        }

        if (giveaway.only_new_subscribers) {
            if (isSeveralChats) {
                String andStr = formatPluralString("BoostingGiveawayHowItWorksSubTextDateSeveral2", giveaway.channels.size() - 1, fromTime, fromDate);
                stringBuilder.append(replaceTags(formatPluralString("BoostingGiveawayHowItWorksSubTextDateSeveralEnd1", quantity, endDate, quantity, from, andStr)));
            } else {
                stringBuilder.append(replaceTags(formatPluralString("BoostingGiveawayHowItWorksSubTextDateEnd", quantity, endDate, quantity, from, fromTime, fromDate)));
            }
        } else {
            if (isSeveralChats) {
                String andStr = formatPluralString("BoostingGiveawayHowItWorksSubTextSeveral2", giveaway.channels.size() - 1);
                stringBuilder.append(replaceTags(formatPluralString("BoostingGiveawayHowItWorksSubTextSeveralEnd1", quantity, endDate, quantity, from, andStr)));
            } else {
                stringBuilder.append(replaceTags(formatPluralString("BoostingGiveawayHowItWorksSubTextEnd", quantity, endDate, quantity, from)));
            }
        }

        stringBuilder.append(" ");
        if (giveawayInfo.activated_count > 0) {
            stringBuilder.append(replaceTags(formatPluralString("BoostingGiveawayUsedLinksPlural", giveawayInfo.activated_count)));
        }

        if (giveawayInfo.refunded) {
            String str = getString("BoostingGiveawayCanceledByPayment", R.string.BoostingGiveawayCanceledByPayment);
            TextView bottomTextView = new TextView(context);
            bottomTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            bottomTextView.setTypeface(AndroidUtilities.bold());
            bottomTextView.setGravity(Gravity.CENTER);
            bottomTextView.setText(str);
            bottomTextView.setTextColor(Theme.getColor(Theme.key_text_RedRegular, resourcesProvider));
            bottomTextView.setBackground(Theme.createRoundRectDrawable(dp(10), dp(10), Theme.multAlpha(Theme.getColor(Theme.key_text_RedRegular, resourcesProvider), 0.1f)));
            bottomTextView.setPadding(dp(12), dp(12), dp(12), dp(12));
            builder.addBottomView(bottomTextView);
            builder.setMessage(stringBuilder);
            builder.setPositiveButton(getString("Close", R.string.Close), (dialogInterface, i) -> {

            });
            applyDialogStyle(builder.show(), true);
        } else {
            builder.setMessage(stringBuilder);
            String str;
            if (giveawayInfo.winner) {
                str = getString(R.string.BoostingGiveawayYouWon);
                if ((giveawayInfo.flags & 16) != 0) {

                } else {
                    builder.setPositiveButton(getString("BoostingGiveawayViewPrize", R.string.BoostingGiveawayViewPrize), (dialogInterface, i) -> {
                        BaseFragment fragment = LaunchActivity.getLastFragment();
                        if (fragment == null) {
                            return;
                        }
                        GiftInfoBottomSheet.show(fragment, giveawayInfo.gift_code_slug);
                    });
                }
                builder.setNegativeButton(getString("Close", R.string.Close), (dialogInterface, i) -> {

                });
            } else {
                str = getString("BoostingGiveawayYouNotWon", R.string.BoostingGiveawayYouNotWon);
                builder.setPositiveButton(getString("Close", R.string.Close), (dialogInterface, i) -> {

                });
            }
            EffectsTextView topTextView = new EffectsTextView(context);
            NotificationCenter.listenEmojiLoading(topTextView);
            topTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
            topTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            topTextView.setGravity(Gravity.CENTER);
            topTextView.setText(str);
            topTextView.setBackground(Theme.createRoundRectDrawable(dp(8), dp(8), Theme.getColor(Theme.key_profile_actionPressedBackground, resourcesProvider)));
            topTextView.setPadding(dp(8), dp(8), dp(8), dp(9));
            builder.aboveMessageView(topTextView);
            applyDialogStyle(builder.show(), false);
        }
    }

    public static void applyDialogStyle(AlertDialog dialog, boolean defaultMarginTop) {
        dialog.setTextSize(20, 14);
        dialog.setMessageLineSpacing(2.5f);
        if (!defaultMarginTop) {
            ((ViewGroup.MarginLayoutParams) dialog.getButtonsLayout().getLayoutParams()).topMargin = AndroidUtilities.dp(-14);
        }
    }

    public static void showPrivateChannelAlert(TLRPC.Chat chat, Context context, Theme.ResourcesProvider resourcesProvider, Runnable onCanceled, Runnable onAccepted) {
        final AtomicBoolean isAddButtonClicked = new AtomicBoolean(false);
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        boolean isChannel = ChatObject.isChannelAndNotMegaGroup(chat);
        builder.setTitle(getString(isChannel ? R.string.BoostingGiveawayPrivateChannel : R.string.BoostingGiveawayPrivateGroup));
        builder.setMessage(getString(isChannel ? R.string.BoostingGiveawayPrivateChannelWarning : R.string.BoostingGiveawayPrivateGroupWarning));
        builder.setPositiveButton(getString("Add", R.string.Add), (dialogInterface, i) -> {
            isAddButtonClicked.set(true);
            onAccepted.run();
        });
        builder.setNegativeButton(getString("Cancel", R.string.Cancel), (dialogInterface, i) -> {

        });
        builder.setOnDismissListener(dialog -> {
            if (!isAddButtonClicked.get()) {
                onCanceled.run();
            }
        });
        builder.show();
    }

    public static void openGiveAwayStatusDialog(MessageObject messageObject, Browser.Progress progress, Context context, Theme.ResourcesProvider resourcesProvider) {
        final AtomicBoolean isCanceled = new AtomicBoolean(false);
        progress.init();
        progress.onCancel(() -> isCanceled.set(true));

        final TLRPC.TL_messageMediaGiveaway giveaway;
        if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGiveawayResults) {
            TLRPC.TL_messageMediaGiveawayResults giveawayResults = (TLRPC.TL_messageMediaGiveawayResults) messageObject.messageOwner.media;
            giveaway = new TLRPC.TL_messageMediaGiveaway();
            giveaway.prize_description = giveawayResults.prize_description;
            giveaway.months = giveawayResults.months;
            giveaway.quantity = giveawayResults.winners_count + giveawayResults.unclaimed_count;
            giveaway.only_new_subscribers = giveawayResults.only_new_subscribers;
            giveaway.until_date = giveawayResults.until_date;
            giveaway.stars = giveawayResults.stars;
            giveaway.flags = giveawayResults.flags;
        } else {
            giveaway = (TLRPC.TL_messageMediaGiveaway) messageObject.messageOwner.media;
        }

        final String fromName = getGiveawayCreatorName(messageObject);
        final boolean isChannel = isChannel(messageObject);
        final long msgDate = messageObject.messageOwner.date * 1000L;
        BoostRepository.getGiveawayInfo(messageObject, result -> {
            if (isCanceled.get()) {
                return;
            }
            progress.end();
            if (result instanceof TLRPC.TL_payments_giveawayInfo) {
                TLRPC.TL_payments_giveawayInfo giveawayInfo = (TLRPC.TL_payments_giveawayInfo) result;
                showAbout(isChannel, fromName, msgDate, giveawayInfo, giveaway, context, resourcesProvider);
            } else if (result instanceof TLRPC.TL_payments_giveawayInfoResults) {
                TLRPC.TL_payments_giveawayInfoResults giveawayInfoResults = (TLRPC.TL_payments_giveawayInfoResults) result;
                showAboutEnd(isChannel, fromName, msgDate, giveawayInfoResults, giveaway, context, resourcesProvider);
            }
        }, error -> {
            if (isCanceled.get()) {
                return;
            }
            progress.end();
        });
    }

    private static boolean isChannel(MessageObject messageObject) {
        if (messageObject == null) {
            return false;
        }
        final long chatId = messageObject.getFromChatId();
        TLRPC.Chat chat = MessagesController.getInstance(UserConfig.selectedAccount).getChat(-chatId);
        return chat != null && ChatObject.isChannelAndNotMegaGroup(chat);
    }

    private static String getGiveawayCreatorName(MessageObject messageObject) {
        if (messageObject == null) {
            return "";
        }
        String forwardedName = messageObject.getForwardedName();
        final String name;
        if (forwardedName == null) {
            final long chatId = MessageObject.getPeerId(messageObject.messageOwner.peer_id);
            TLRPC.Chat chat = MessagesController.getInstance(UserConfig.selectedAccount).getChat(-chatId);
            name = chat != null ? chat.title : "";
        } else {
            name = forwardedName;
        }
        return name;
    }

    public static void showBulletinAbout(MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null) {
            return;
        }
        BoostRepository.getGiveawayInfo(messageObject, result -> {
            final TLRPC.TL_messageMediaGiveaway giveaway;
            if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGiveawayResults) {
                TLRPC.TL_messageMediaGiveawayResults giveawayResults = (TLRPC.TL_messageMediaGiveawayResults) messageObject.messageOwner.media;
                giveaway = new TLRPC.TL_messageMediaGiveaway();
                giveaway.prize_description = giveawayResults.prize_description;
                giveaway.months = giveawayResults.months;
                giveaway.quantity = giveawayResults.winners_count + giveawayResults.unclaimed_count;
                giveaway.only_new_subscribers = giveawayResults.only_new_subscribers;
                giveaway.until_date = giveawayResults.until_date;
                if ((giveawayResults.flags & 32) != 0) {
                    giveaway.flags |= 32;
                    giveaway.stars = giveawayResults.stars;
                }
            } else {
                giveaway = (TLRPC.TL_messageMediaGiveaway) messageObject.messageOwner.media;
            }
            final long msgDate = messageObject.messageOwner.date * 1000L;
            BaseFragment fragment = LaunchActivity.getLastFragment();
            if (fragment == null) {
                return;
            }
            final String fromName = getGiveawayCreatorName(messageObject);
            final boolean isChannel = isChannel(messageObject);
            final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(fragment.getParentActivity(), fragment.getResourceProvider());

            if (result instanceof TLRPC.TL_payments_giveawayInfoResults) {
                layout.setAnimation(R.raw.chats_infotip, 30, 30);
                layout.textView.setText(LocaleController.getString(R.string.BoostingGiveawayShortStatusEnded));
            } else if (result instanceof TLRPC.TL_payments_giveawayInfo) {
                TLRPC.TL_payments_giveawayInfo giveawayInfo = (TLRPC.TL_payments_giveawayInfo) result;
                if (giveawayInfo.participating) {
                    layout.setAnimation(R.raw.forward, 30, 30);
                    layout.textView.setText(LocaleController.getString(R.string.BoostingGiveawayShortStatusParticipating));
                } else {
                    layout.setAnimation(R.raw.chats_infotip, 30, 30);
                    layout.textView.setText(LocaleController.getString(R.string.BoostingGiveawayShortStatusNotParticipating));
                }
            }

            layout.textView.setSingleLine(false);
            layout.textView.setMaxLines(2);

            layout.setButton(new Bulletin.UndoButton(fragment.getParentActivity(), true, fragment.getResourceProvider())
                    .setText(LocaleController.getString(R.string.LearnMore))
                    .setUndoAction(() -> {
                        if (result instanceof TLRPC.TL_payments_giveawayInfo) {
                            TLRPC.TL_payments_giveawayInfo giveawayInfo = (TLRPC.TL_payments_giveawayInfo) result;
                            showAbout(isChannel, fromName, msgDate, giveawayInfo, giveaway, fragment.getParentActivity(), fragment.getResourceProvider());
                        } else if (result instanceof TLRPC.TL_payments_giveawayInfoResults) {
                            TLRPC.TL_payments_giveawayInfoResults giveawayInfoResults = (TLRPC.TL_payments_giveawayInfoResults) result;
                            showAboutEnd(isChannel, fromName, msgDate, giveawayInfoResults, giveaway, fragment.getParentActivity(), fragment.getResourceProvider());
                        }
                    }));
            Bulletin.make(fragment, layout, Bulletin.DURATION_LONG).show();
        }, error -> {

        });
    }

    public static void showMoreBoostsNeeded(long dialogId, BottomSheet bottomSheet) {
        TLRPC.Chat chat = MessagesController.getInstance(UserConfig.selectedAccount).getChat(-dialogId);
        BaseFragment baseFragment = LaunchActivity.getLastFragment();
        if (baseFragment == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(baseFragment.getContext(), baseFragment.getResourceProvider());
        builder.setTitle(LocaleController.getString(R.string.BoostingMoreBoostsNeeded));
        builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatPluralString("BoostingGetMoreBoostByGiftingCount", BoostRepository.boostsPerSentGift(), chat.title)));
        builder.setNegativeButton(getString("GiftPremium", R.string.GiftPremium), (dialogInterface, i) -> {
            bottomSheet.dismiss();
            UserSelectorBottomSheet.open();
        });
        builder.setPositiveButton(getString("Close", R.string.Close), (dialogInterface, i) -> {

        });
        builder.show();
    }

    public static void showFloodWait(int time) {
        BaseFragment baseFragment = LaunchActivity.getLastFragment();
        if (baseFragment == null) {
            return;
        }
        String timeString;
        if (time < 60) {
            timeString = LocaleController.formatPluralString("Seconds", time);
        } else if (time < 60 * 60) {
            timeString = LocaleController.formatPluralString("Minutes", time / 60);
        } else if (time / 60 / 60 > 2) {
            timeString = LocaleController.formatPluralString("Hours", time / 60 / 60);
        } else {
            timeString = LocaleController.formatPluralString("Hours", time / 60 / 60) + " " + LocaleController.formatPluralString("Minutes", time % 60);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(baseFragment.getContext(), baseFragment.getResourceProvider());
        builder.setTitle(LocaleController.getString(R.string.CantBoostTooOften));
        builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("CantBoostTooOftenDescription", R.string.CantBoostTooOftenDescription, timeString)));
        builder.setPositiveButton(LocaleController.getString(R.string.OK), (dialog, which) -> {
            dialog.dismiss();
        });
        builder.show();
    }

    public static void showStartGiveawayDialog(Runnable onStart) {
        BaseFragment baseFragment = LaunchActivity.getLastFragment();
        if (baseFragment == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(baseFragment.getContext(), baseFragment.getResourceProvider());
        builder.setTitle(LocaleController.getString(R.string.BoostingStartGiveawayConfirmTitle));
        builder.setMessage(AndroidUtilities.replaceTags(LocaleController.getString(R.string.BoostingStartGiveawayConfirmText)));
        builder.setPositiveButton(LocaleController.getString(R.string.Start), (dialog, which) -> {
            onStart.run();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), (dialog, which) -> {
            dialog.dismiss();
        });
        builder.show();
    }
}
