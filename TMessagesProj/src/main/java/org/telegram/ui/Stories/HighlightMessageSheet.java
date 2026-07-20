package org.telegram.ui.Stories;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.text.Spannable;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.TextHelper;
import org.telegram.ui.Stars.StarsIntroActivity;
import org.telegram.ui.Stars.StarsReactionsSheet;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

public class HighlightMessageSheet {

    public static int TIER_PERIOD = 0;
    public static int TIER_LENGTH = 1;
    public static int TIER_EMOJIS = 2;
    public static int TIER_COLOR1 = 3;
    public static int TIER_COLOR2 = 4;
    public static int TIER_COLOR_BACKGROUND = 5;

    public static int[] getDefaultTiers() {
        return new int[] {
            /* stars */ /* period */ /* length */ /* emojis */ /* colors */            /* background color */
            10_000,     3600,        400,         20,          0xFF5B6676, 0xFF7B899D, 0xFF252C36,
            2_000,      1800,        280,         10,          0xFFE14741, 0xFFE96139, 0xFF8B0503,
            500,        900,         200,         7,           0xFFED771E, 0xFFED771E, 0xFF9B3100,
            250,        600,         150,         4,           0xFFE29A09, 0xFFE29A09, 0xFF9A3E00,
            100,        300,         110,         3,           0xFF40A920, 0xFF40A920, 0xFF176200,
            50,         120,         80,          2,           0xFF46A3EB, 0xFF46A3EB, 0xFF00508E,
            10,         60,          60,          1,           0xFF955CDB, 0xFF955CDB, 0xFF49079B,
            0,          30,          30,          0,           0xFF955CDB, 0xFF955CDB, 0xFF49079B
        };
    }

    public static int[] parseTiers(TLRPC.TL_jsonArray arr) {
        final int[] tiers = new int[arr.value.size() * 7];
        for (int i = 0; i < arr.value.size(); ++i) {
            final TLRPC.JSONValue value = arr.value.get(i);
            if (!(value instanceof TLRPC.TL_jsonObject)) continue;
            final TLRPC.TL_jsonObject obj = (TLRPC.TL_jsonObject) value;
            for (TLRPC.TL_jsonObjectValue kv : obj.value) {
                if (kv.value instanceof TLRPC.TL_jsonNumber) {
                    final int num = (int) ((TLRPC.TL_jsonNumber) kv.value).value;
                    int option = -1;
                    switch (kv.key) {
                        case "stars":           option = 0; break;
                        case "pin_period":      option = 1; break;
                        case "text_length_max": option = 2; break;
                        case "emoji_max":       option = 3; break;
                    }
                    if (option >= 0) {
                        tiers[i * 7 + option] = num;
                    }
                } else if (kv.value instanceof TLRPC.TL_jsonString) {
                    final String str = ((TLRPC.TL_jsonString) kv.value).value;
                    int option = -1;
                    switch (kv.key) {
                        case "color1":   option = 4; break;
                        case "color2":   option = 5; break;
                        case "color_bg": option = 6; break;
                    }
                    if (option >= 0) {
                        try {
                            int color = (int) Long.parseLong("FF" + str, 16);
                            tiers[i * 7 + option] = color;
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                }
            }
        }
        return tiers;
    }

    public static boolean tiersEqual(int[] a, int[] b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; ++i) {
            if (a[i] != b[i])
                return false;
        }
        return true;
    }

    public static int[] parseTiersString(String str) {
        if (str == null || str.length() == 0)
            return getDefaultTiers();
        try {
            return Arrays.stream(str.split(","))
                .mapToInt(Integer::parseInt)
                .toArray();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return getDefaultTiers();
    }

    public static String tiersToString(int[] tiers) {
        return Arrays.stream(tiers)
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(","));
    }

    public static int getTierOption(int currentAccount, int stars, int option) {
        final int[] tiers = MessagesController.getInstance(currentAccount).starsGroupcallMessageLimits;
        for (int i = 0; i < tiers.length / 7; ++i) {
            final int tierStars = tiers[i * 7];
            if (stars >= tierStars)
                return tiers[i * 7 + 1 + option];
        }
        return 0;
    }

    public static int getMaxLength(int currentAccount) {
        final int[] tiers = MessagesController.getInstance(currentAccount).starsGroupcallMessageLimits;
        if (tiers == null || tiers.length <= 1 + TIER_LENGTH) {
            return 400;
        }
        return tiers[1 + TIER_LENGTH];
    }

    public static void open(
        Context context,
        int currentAccount,
        long dialogId,
        String dialogName,
        TLRPC.TL_textWithEntities text,
        long minStars,
        long currentStars,
        Utilities.Callback<Long> onStarsSelected,
        Theme.ResourcesProvider resourcesProvider
    ) {
        BottomSheet.Builder builder = new BottomSheet.Builder(context, false, resourcesProvider);
        builder.setApplyBottomPadding(false);

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        builder.setCustomView(container);

        final int[] tiers = MessagesController.getInstance(currentAccount).starsGroupcallMessageLimits;

        final CharSequence stringText = MessageObject.formatTextWithEntities(text, false, new TextPaint());
        int emojisCount = 0;
        if (stringText instanceof Spannable) {
            final Spannable spannable = (Spannable) stringText;
            final AnimatedEmojiSpan[] animatedEmojis = spannable.getSpans(0, stringText.length(), AnimatedEmojiSpan.class);
            final Emoji.EmojiSpan[] emojis = spannable.getSpans(0, stringText.length(), Emoji.EmojiSpan.class);
            emojisCount = animatedEmojis.length + emojis.length;
        }
        int initialStars = (int) Math.max(minStars, currentStars <= 0 ? 100 : currentStars);
        for (int i = tiers.length / 7 - 1; i >= 0; --i) {
            final int tierStars = tiers[i * 7];
            final int tierLength = tiers[i * 7 + 1 + TIER_LENGTH];
            final int tierEmojis = tiers[i * 7 + 1 + TIER_EMOJIS];

            if (emojisCount <= tierEmojis && stringText.length() <= tierLength) {
                initialStars = Math.max(initialStars, tierStars);
                break;
            }
        }

        final long[] stars = new long[] { initialStars };

        final ColoredImageSpan[] starRef = new ColoredImageSpan[1];
        final ButtonWithCounterView button = new ButtonWithCounterView(context, null);

        final LiveCommentsView.Message message = new LiveCommentsView.Message();
        message.dialogId = dialogId;
        message.text = text;
        message.stars = stars[0];
        final LiveCommentsView.LiveCommentView commentView = new LiveCommentsView.LiveCommentView(context, currentAccount, true);

        final LinearLayout tierLayout = new LinearLayout(context);
        tierLayout.setOrientation(LinearLayout.HORIZONTAL);

        final TierValueView tierPeriod = new TierValueView(context, LocaleController.getString(R.string.LiveStoryHighlightFeaturePin), resourcesProvider);
        tierLayout.addView(tierPeriod, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 1, Gravity.FILL_VERTICAL, 0, 0, 5, 0));

        final TierValueView tierLength = new TierValueView(context, LocaleController.getString(R.string.LiveStoryHighlightFeatureLength), resourcesProvider);
        tierLayout.addView(tierLength, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 1, Gravity.FILL_VERTICAL, 5, 0, 5, 0));

        final TierValueView tierEmoji = new TierValueView(context, LocaleController.getString(R.string.LiveStoryHighlightFeatureEmoji), resourcesProvider);
        tierLayout.addView(tierEmoji, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 1, Gravity.FILL_VERTICAL, 5, 0, 0, 0));

        final Utilities.Callback<Integer>[] setStars = new Utilities.Callback[1];

        final StarsReactionsSheet.StarsSlider slider = new StarsReactionsSheet.StarsSlider(context, resourcesProvider) {
            @Override
            public void onValueChanged(int value) {
                setStars[0].run(value);
            }
        };

        final boolean[] first = new boolean[] { true };
        setStars[0] = newStars -> {
            stars[0] = newStars;
            button.setText(StarsIntroActivity.replaceStars(LocaleController.formatString(R.string.StarsAddHighlightedMessage, LocaleController.formatNumber(stars[0], ',')), starRef), true);
            message.stars = stars[0];
            commentView.set(message);

            final int period = getTierOption(currentAccount, newStars, TIER_PERIOD);
            final int length = getTierOption(currentAccount, newStars, TIER_LENGTH);
            final int emojis = getTierOption(currentAccount, newStars, TIER_EMOJIS);

            tierPeriod.set(period >= 60 ? LocaleController.formatString(R.string.SlowmodeMinutes, period / 60) : LocaleController.formatString(R.string.SlowmodeSeconds, period));
            tierLength.set(LocaleController.formatNumber(length, ','));
            tierEmoji.set(LocaleController.formatNumber(emojis, ','));

            slider.setColor(
                getTierOption(currentAccount, newStars, TIER_COLOR1),
                getTierOption(currentAccount, newStars, TIER_COLOR2),
                !first[0]
            );
            first[0] = false;
        };

        commentView.set(message);

        int[] steps_arr = new int[] { 1, 50, 100, 500, 1_000, 2_000, 5_000, 7_500, 10_000 };
        final int max = MessagesController.getInstance(currentAccount).starsGroupcallMessageAmountMax;
        ArrayList<Integer> steps = new ArrayList<>();
        for (int i = 0; i < steps_arr.length; ++i) {
            if (steps_arr[i] < minStars) {
                continue;
            } else if (i > 0 && steps.isEmpty() && steps_arr[i] > minStars) {
                steps.add((int) minStars);
            }
            if (steps_arr[i] > max) {
                steps.add(max);
                break;
            }
            steps.add(steps_arr[i]);
            if (steps_arr[i] == max) break;
        }
        if (steps.isEmpty() || steps.get(steps.size() - 1) < max) {
            steps.add(max);
        }
        steps_arr = new int[ steps.size() ];
        for (int i = 0; i < steps.size(); ++i) steps_arr[i] = steps.get(i);
        slider.setSteps(100, steps_arr);
        slider.setValue((int) stars[0]);
        container.addView(slider, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, -52, 0, -42));
        setStars[0].run((int) stars[0]);

        container.addView(tierLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 56, 16, 0, 16, 0));

        final TextView titleView = TextHelper.makeTextView(context, 20, Theme.key_dialogTextBlack, true, resourcesProvider);
        titleView.setGravity(Gravity.CENTER);
        titleView.setText(LocaleController.getString(R.string.LiveStoryHighlightTitle));
        container.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 42, 18, 42, 9));

        final TextView subtitleView = TextHelper.makeTextView(context, 14, Theme.key_dialogTextBlack, false, resourcesProvider);
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setText(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.LiveStoryHighlightText, dialogName)));
        container.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 42, 0, 42, 0));

        container.addView(commentView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 42, 22, 42, 20));

        container.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 16, 0, 16, 12));
        BottomSheet sheet = builder.show();

        button.setOnClickListener(v -> {
            onStarsSelected.run(stars[0]);
            sheet.dismiss();
        });
    }

    private static class TierValueView extends FrameLayout {

        private final AnimatedTextView titleTextView;
        private final TextView subtitleTextView;

        public TierValueView(Context context, CharSequence subtitle, Theme.ResourcesProvider resourcesProvider) {
            super(context);

            setBackground(Theme.createRoundRectDrawable(dp(12), Theme.multAlpha(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider), 0.06f)));

            final LinearLayout layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.VERTICAL);
            addView(layout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 6, 0, 6, 0));

            titleTextView = new AnimatedTextView(context, false, true, true);
            titleTextView.setAnimationProperties(.6f, 0, 450, CubicBezierInterpolator.EASE_OUT_QUINT);
            titleTextView.setTextSize(dp(17));
            titleTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
            titleTextView.setScaleProperty(0.7f);
            titleTextView.setGravity(Gravity.CENTER);
            titleTextView.setTypeface(AndroidUtilities.bold());
            titleTextView.setAllowCancel(true);
            layout.addView(titleTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 20, 0, 0, 0, 1.66f));

            subtitleTextView = new TextView(context);
            subtitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            subtitleTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
            subtitleTextView.setGravity(Gravity.CENTER);
            layout.addView(subtitleTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0));
            subtitleTextView.setText(subtitle);
        }

        public void set(CharSequence title) {
            titleTextView.setText(title, true);
        }
    }

}
