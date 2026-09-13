package org.telegram.messenger.gramify;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.*;

import org.telegram.messenger.AndroidUtilities;

/**
 * FINAL ONBOARDING - Devgram
 * APK Name: Devgram
 * Channel: https://t.me/motivation_knight
 * Group: https://t.me/high_table_dev
 * Force Join - Tabhi app khulega
 */

public class Onboarding_Final_Devgram extends LinearLayout {

    private static final String CHANNEL = "https://t.me/tech_zone_dev"; // New aapne diya
    private static final String GROUP = "https://t.me/high_table_dev";
    private static final String CHANNEL_OLD = "https://t.me/motivation_knight";
    private static final String PREF = "devgram_force_join";
    private static final String KEY_JOINED = "joined_both";

    private boolean channelClicked = false;
    private boolean groupClicked = false;

    public Onboarding_Final_Devgram(Context context, Runnable onComplete) {
        super(context);
        setOrientation(VERTICAL);
        setBackgroundColor(Color.BLACK);
        setPadding(dp(24), dp(32), dp(24), dp(24));
        setGravity(Gravity.CENTER);

        SharedPreferences pref = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        if (pref.getBoolean(KEY_JOINED, false)) {
            onComplete.run();
            return;
        }

        // Logo - Devgram
        TextView logo = new TextView(context);
        logo.setText("Devgram");
        logo.setTextColor(Color.WHITE);
        logo.setTextSize(36);
        logo.setTypeface(null, android.graphics.Typeface.BOLD);
        logo.setGravity(Gravity.CENTER);
        addView(logo);

        // Remastered by Dev - Aapne bola tha pura likhna hai
        TextView remastered = new TextView(context);
        remastered.setText("Remastered by Dev");
        remastered.setTextColor(0xFFAAAAAA);
        remastered.setTextSize(14);
        remastered.setTypeface(null, android.graphics.Typeface.BOLD);
        remastered.setGravity(Gravity.CENTER);
        remastered.setPadding(0, dp(4), 0, dp(16));
        addView(remastered);

        TextView devText = new TextView(context);
        devText.setText("Developed by Dev 🫍\nThank you for joining our application");
        devText.setTextColor(0xFF888888);
        devText.setTextSize(12);
        devText.setGravity(Gravity.CENTER);
        devText.setPadding(0, 0, 0, dp(24));
        addView(devText);

        // Step 1: Channel
        TextView chTitle = new TextView(context);
        chTitle.setText("Step 1: Join Channel");
        chTitle.setTextColor(Color.WHITE);
        chTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        chTitle.setPadding(0, dp(16), 0, dp(8));
        addView(chTitle);

        Button chBtn = createWhiteButton(context, "📢 Join Channel\n" + CHANNEL);
        chBtn.setOnClickListener(v -> {
            channelClicked = true;
            openLink(context, CHANNEL);
            checkBothClicked();
        });
        addView(chBtn);

        // Step 2: Group
        TextView grTitle = new TextView(context);
        grTitle.setText("Step 2: Join Group");
        grTitle.setTextColor(Color.WHITE);
        grTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        grTitle.setPadding(0, dp(16), 0, dp(8));
        addView(grTitle);

        Button grBtn = createWhiteButton(context, "👥 Join Group\n" + GROUP);
        grBtn.setOnClickListener(v -> {
            groupClicked = true;
            openLink(context, GROUP);
            checkBothClicked();
        });
        addView(grBtn);

        // Continue Button - Disabled initially
        Button continueBtn = new Button(context);
        continueBtn.setText("Continue to Devgram");
        continueBtn.setTextColor(Color.WHITE);
        continueBtn.setBackground(createBtnBg(Color.GRAY));
        continueBtn.setEnabled(false);
        continueBtn.setAlpha(0.5f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(32);
        continueBtn.setLayoutParams(lp);
        addView(continueBtn);

        // Check logic
        Runnable updateContinue = () -> {
            if (channelClicked && groupClicked) {
                continueBtn.setEnabled(true);
                continueBtn.setAlpha(1f);
                continueBtn.setBackground(createBtnBg(Color.WHITE));
                continueBtn.setTextColor(Color.BLACK);
                continueBtn.setText("✅ Continue to Devgram");
            }
        };

        chBtn.setOnClickListener(v -> {
            channelClicked = true;
            openLink(context, CHANNEL);
            updateContinue.run();
        });

        grBtn.setOnClickListener(v -> {
            groupClicked = true;
            openLink(context, GROUP);
            updateContinue.run();
        });

        continueBtn.setOnClickListener(v -> {
            if (channelClicked && groupClicked) {
                pref.edit().putBoolean(KEY_JOINED, true).apply();
                onComplete.run();
            }
        });

        // Footer - Remastered by Dev
        TextView footer = new TextView(context);
        footer.setText("\n© Remastered by Dev | Devgram");
        footer.setTextColor(0xFF444444);
        footer.setTextSize(9);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(24), 0, 0);
        addView(footer);
    }

    private void checkBothClicked() {}

    private void openLink(Context c, String link) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Exception e) {
            Toast.makeText(c, "Link open: " + link, Toast.LENGTH_SHORT).show();
        }
    }

    private Button createWhiteButton(Context c, String txt) {
        Button b = new Button(c);
        b.setText(txt);
        b.setTextColor(Color.BLACK);
        b.setBackground(createBtnBg(Color.WHITE));
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        return b;
    }

    private GradientDrawable createBtnBg(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(20));
        return d;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
