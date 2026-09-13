package org.telegram.messenger.gramify;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.*;
import org.telegram.messenger.AndroidUtilities;

/**
 * Devgram Onboarding - Channel + Group Join before app opens
 * Aapki requirement: App kholte hi channel join karwaye, fir group join karwaye, tabhi app khule
 * SAFE: Force nahi, par Continue tab tak disable rahega jab tak join na kare
 */

public class Onboarding_Channel_Group {

    public static void show(Context context, String channelLink, String groupLink, Runnable onComplete) {
        SharedPreferences pref = context.getSharedPreferences("devgram_onboard", Context.MODE_PRIVATE);
        if (pref.getBoolean("joined", false)) {
            onComplete.run();
            return;
        }

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(32), AndroidUtilities.dp(24), AndroidUtilities.dp(24));
        root.setGravity(Gravity.CENTER);

        TextView logo = new TextView(context);
        logo.setText("Devgram");
        logo.setTextColor(Color.WHITE);
        logo.setTextSize(32);
        logo.setTypeface(null, android.graphics.Typeface.BOLD);
        logo.setGravity(Gravity.CENTER);
        root.addView(logo);

        TextView dev = new TextView(context);
        dev.setText("Developed by Dev 🫍\nThank you for joining our application");
        dev.setTextColor(0xFFAAAAAA);
        dev.setGravity(Gravity.CENTER);
        dev.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(24));
        root.addView(dev);

        // Channel Join
        TextView chLabel = new TextView(context);
        chLabel.setText("Step 1: Join Our Channel");
        chLabel.setTextColor(Color.WHITE);
        chLabel.setPadding(0, AndroidUtilities.dp(16), 0, AndroidUtilities.dp(8));
        root.addView(chLabel);

        Button joinChannelBtn = new Button(context);
        joinChannelBtn.setText("Join Channel: " + channelLink);
        joinChannelBtn.setTextColor(Color.BLACK);
        joinChannelBtn.setBackgroundColor(Color.WHITE);
        joinChannelBtn.setOnClickListener(v -> {
            // Intent to open Telegram channel
            try {
                android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
                intent.setData(android.net.Uri.parse(channelLink));
                context.startActivity(intent);
            } catch (Exception e) {}
        });
        root.addView(joinChannelBtn);

        // Group Join
        TextView grLabel = new TextView(context);
        grLabel.setText("Step 2: Join Our Group");
        grLabel.setTextColor(Color.WHITE);
        grLabel.setPadding(0, AndroidUtilities.dp(16), 0, AndroidUtilities.dp(8));
        root.addView(grLabel);

        Button joinGroupBtn = new Button(context);
        joinGroupBtn.setText("Join Group: " + groupLink);
        joinGroupBtn.setTextColor(Color.BLACK);
        joinGroupBtn.setBackgroundColor(Color.WHITE);
        joinGroupBtn.setOnClickListener(v -> {
            try {
                android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
                intent.setData(android.net.Uri.parse(groupLink));
                context.startActivity(intent);
            } catch (Exception e) {}
        });
        root.addView(joinGroupBtn);

        // Continue - tabhi enable hoga jab dono join kare (aap check via API kar sakte ho)
        Button continueBtn = new Button(context);
        continueBtn.setText("Continue to Devgram");
        continueBtn.setTextColor(Color.WHITE);
        continueBtn.setBackgroundColor(Color.GRAY);
        continueBtn.setEnabled(false);
        continueBtn.setAlpha(0.5f);
        continueBtn.setPadding(0, AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = AndroidUtilities.dp(24);
        continueBtn.setLayoutParams(lp);
        root.addView(continueBtn);

        // Simple logic: dono button click karne ke baad continue enable
        final boolean[] chClicked = {false};
        final boolean[] grClicked = {false};

        joinChannelBtn.setOnClickListener(v -> {
            chClicked[0] = true;
            try {
                android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(channelLink));
                context.startActivity(intent);
            } catch (Exception ignore) {}
            if (chClicked[0] && grClicked[0]) {
                continueBtn.setEnabled(true);
                continueBtn.setAlpha(1f);
                continueBtn.setBackgroundColor(Color.WHITE);
                continueBtn.setTextColor(Color.BLACK);
            }
        });

        joinGroupBtn.setOnClickListener(v -> {
            grClicked[0] = true;
            try {
                android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(groupLink));
                context.startActivity(intent);
            } catch (Exception ignore) {}
            if (chClicked[0] && grClicked[0]) {
                continueBtn.setEnabled(true);
                continueBtn.setAlpha(1f);
                continueBtn.setBackgroundColor(Color.WHITE);
                continueBtn.setTextColor(Color.BLACK);
            }
        });

        continueBtn.setOnClickListener(v -> {
            pref.edit().putBoolean("joined", true).apply();
            onComplete.run();
        });

        // Show as dialog or activity
        // Aap isko LaunchActivity me show kar sakte ho
    }
}
