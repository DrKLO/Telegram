package org.telegram.messenger.gramify;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.*;

import org.telegram.messenger.AndroidUtilities;

/**
 * FINAL - Devgram Remastered by Dev + Pin Lock on Bot Feature
 * 
 * Aapki 2 requirements:
 * 1. Pura Remastered by Dev likh dena
 * 2. Bot wale feature pe bhi Pin Lock System (56530)
 */

public class Devgram_Final_RemasteredByDev_PinLock {

    public static final String REMASTERED_TEXT = "Remastered by Dev";
    public static final String FULL_BRAND = "Devgram Remastered by Dev";
    public static final String PIN_CODE = "56530"; // Same as Gramify wala
    public static final String PREF = "devgram_remastered_pref";
    public static final String KEY_PIN_UNLOCKED = "pin_unlocked_bot";

    // Call this before opening any Bot feature
    public static void checkPinAndOpen(Context context, Runnable onUnlocked) {
        SharedPreferences pref = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        
        // Agar pehle se unlocked hai to direct open
        if (pref.getBoolean(KEY_PIN_UNLOCKED, false)) {
            onUnlocked.run();
            return;
        }

        // Pin Lock Dialog - Black & White Devgram Theme
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(context, 20), dp(context, 20), dp(context, 20), dp(context, 20));
        layout.setBackgroundColor(Color.BLACK);

        TextView title = new TextView(context);
        title.setText(FULL_BRAND);
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        TextView sub = new TextView(context);
        sub.setText("🔒 Bot Feature Locked\nEnter Pin to Unlock");
        sub.setTextColor(0xFFAAAAAA);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(context, 12), 0, dp(context, 12));
        layout.addView(sub);

        EditText pinInput = new EditText(context);
        pinInput.setHint("Enter Pin: 56530");
        pinInput.setHintTextColor(0xFF888888);
        pinInput.setTextColor(Color.WHITE);
        pinInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pinInput.setBackground(createEditBg());
        pinInput.setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12));
        layout.addView(pinInput);

        TextView footer = new TextView(context);
        footer.setText(REMASTERED_TEXT + " 🫍");
        footer.setTextColor(0xFF666666);
        footer.setTextSize(10);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(context, 12), 0, 0);
        layout.addView(footer);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(layout)
                .setPositiveButton("Unlock", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> {
            Button unlockBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            unlockBtn.setTextColor(Color.BLACK);
            unlockBtn.setBackgroundColor(Color.WHITE);
            unlockBtn.setOnClickListener(v -> {
                String entered = pinInput.getText().toString().trim();
                if (PIN_CODE.equals(entered)) {
                    pref.edit().putBoolean(KEY_PIN_UNLOCKED, true).apply();
                    Toast.makeText(context, "✅ Unlocked - " + FULL_BRAND, Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    onUnlocked.run();
                } else {
                    pinInput.setError("❌ Wrong Pin! Hint: 56530");
                    Toast.makeText(context, "❌ Wrong Pin - " + REMASTERED_TEXT, Toast.LENGTH_SHORT).show();
                }
            });
            Button cancelBtn = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            cancelBtn.setTextColor(Color.WHITE);
        });

        dialog.show();
    }

    // Branding helper - har jagah use karo
    public static TextView createBrandingLabel(Context c) {
        TextView tv = new TextView(c);
        tv.setText(FULL_BRAND + " | Developed by Dev 🫍");
        tv.setTextColor(0xFFAAAAAA);
        tv.setTextSize(10);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, dp(c, 8), 0, dp(c, 8));
        return tv;
    }

    public static TextView createFooterLabel(Context c) {
        TextView tv = new TextView(c);
        tv.setText("© " + REMASTERED_TEXT + " | Thank you for joining our application");
        tv.setTextColor(0xFF666666);
        tv.setTextSize(9);
        tv.setGravity(Gravity.CENTER);
        return tv;
    }

    // Call this in your Bubble Service notification
    public static String getNotificationTitle() {
        return FULL_BRAND + " - Bot Active";
    }

    public static String getNotificationText() {
        return "Bubble tap karke panel kholo | " + REMASTERED_TEXT;
    }

    private static GradientDrawable createEditBg() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(0xFF1A1A1A);
        d.setCornerRadius(12);
        d.setStroke(2, Color.WHITE);
        return d;
    }

    private static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density);
    }

    // Example usage in Settings:
    /*
    // Settings -> 10 slots pe click karte hi:
    Devgram_Final_RemasteredByDev_PinLock.checkPinAndOpen(context, () -> {
        // Pin sahi hai tabhi ye khulega
        presentFragment(new Devgram_Bot_10_Slots_Safe());
    });

    // Bubble tap pe:
    Devgram_Final_RemasteredByDev_PinLock.checkPinAndOpen(context, () -> {
        openPanel(); // aapka panel
    });

    // Har screen pe branding add karo:
    container.addView(Devgram_Final_RemasteredByDev_PinLock.createBrandingLabel(context));
    container.addView(Devgram_Final_RemasteredByDev_PinLock.createFooterLabel(context));
    */
}
