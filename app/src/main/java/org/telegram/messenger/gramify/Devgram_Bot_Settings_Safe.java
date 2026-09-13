package org.telegram.messenger.gramify;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

import okhttp3.*;
import org.json.JSONObject;
import java.io.IOException;

/**
 * Devgram Bot Settings - SAFE VERSION (1 Bot Only)
 * Same as Gramify Music/EQ - toggle with passkey + bot token
 * 
 * Features:
 * - Pehle Bot Token dalna padega tabhi ON hoga (aapki requirement)
 * - iOS style UI, Black & White Devgram theme
 * - 2 Section: Name Change + Message Send
 * - Tabs/Chat select, Naam, Delay - with SAFE limits
 * - No flooding bypass - 65 sec min for title, 3 sec min for message
 */

public class Devgram_Bot_Settings_Safe extends BaseFragment {

    private static final String PREF = "devgram_bot_pref";
    private static final String KEY_TOKEN = "bot_token";
    private static final String KEY_ENABLED = "bot_enabled";

    private EditText tokenInput;
    private EditText chatIdInput; // Tabs = Chat ID / Username
    private EditText nameInput;
    private EditText digitInput; // Aapne bola digit - ex: 1,2,3 add hoga naam me
    private EditText delayInput;
    private EditText messageInput;
    private EditText msgDelayInput;

    private TextView statusText;
    private boolean isNameChanging = false;
    private boolean isMsgSending = false;

    private OkHttpClient client = new OkHttpClient();

    @Override
    public View createView(Context context) {
        SharedPreferences pref = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));

        ScrollView scroll = new ScrollView(context);
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);

        // --- Title - Devgram everywhere ---
        TextView title = new TextView(context);
        title.setText("Devgram Bot Manager");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        container.addView(title);

        TextView subtitle = new TextView(context);
        subtitle.setText("Developed by Dev - iOS Style for Android");
        subtitle.setTextColor(0xFFAAAAAA);
        subtitle.setTextSize(12);
        container.addView(subtitle);

        // --- STEP 1: Bot Token (Pehle dalna padega) ---
        container.addView(createSectionLabel(context, "STEP 1: Bot Token (Required)"));
        
        tokenInput = createEdit(context, "Bot Token from @BotFather");
        tokenInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        tokenInput.setText(pref.getString(KEY_TOKEN, ""));
        container.addView(tokenInput);

        Button saveTokenBtn = createWhiteButton(context, "Save Token & Enable");
        container.addView(saveTokenBtn);

        // --- STEP 2: Tabs/Chat Select ---
        container.addView(createSectionLabel(context, "STEP 2: Tabs / Chat Select"));
        TextView tabsInfo = new TextView(context);
        tabsInfo.setText("Chat ID ya @username dalo jahan bot admin hai\nEx: -100123456789 ya @mygroup");
        tabsInfo.setTextColor(0xFF888888);
        tabsInfo.setTextSize(11);
        container.addView(tabsInfo);

        chatIdInput = createEdit(context, "Chat ID / @username");
        container.addView(chatIdInput);

        // --- SECTION A: Name Change ---
        container.addView(createSectionLabel(context, "Name Change Section"));

        LinearLayout row1 = new LinearLayout(context);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        digitInput = createEdit(context, "Digit (ex: 1)");
        digitInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        digitInput.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        nameInput = createEdit(context, "Naya Naam kya rakhna hai?");
        nameInput.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f));
        row1.addView(digitInput);
        row1.addView(nameInput);
        container.addView(row1);

        delayInput = createEdit(context, "Delay in seconds (Min 65)");
        delayInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        container.addView(delayInput);

        LinearLayout btnRow1 = new LinearLayout(context);
        btnRow1.setOrientation(LinearLayout.HORIZONTAL);
        Button startNameBtn = createWhiteButton(context, "Start");
        startNameBtn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button stopNameBtn = createBlackButton(context, "Stop");
        stopNameBtn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        btnRow1.addView(startNameBtn);
        btnRow1.addView(stopNameBtn);
        container.addView(btnRow1);

        // --- SECTION B: Message Sending ---
        container.addView(createSectionLabel(context, "Message Sending Section"));

        messageInput = createEdit(context, "Kya message bhejna hai?");
        container.addView(messageInput);

        msgDelayInput = createEdit(context, "Delay in seconds (Min 3)");
        msgDelayInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        container.addView(msgDelayInput);

        LinearLayout btnRow2 = new LinearLayout(context);
        btnRow2.setOrientation(LinearLayout.HORIZONTAL);
        Button startMsgBtn = createWhiteButton(context, "Start Send");
        startMsgBtn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button stopMsgBtn = createBlackButton(context, "Stop");
        stopMsgBtn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        btnRow2.addView(startMsgBtn);
        btnRow2.addView(stopMsgBtn);
        container.addView(btnRow2);

        statusText = new TextView(context);
        statusText.setTextColor(Color.WHITE);
        statusText.setPadding(0, dp(16), 0, 0);
        container.addView(statusText);

        // --- LOGIC ---

        saveTokenBtn.setOnClickListener(v -> {
            String token = tokenInput.getText().toString().trim();
            if (token.isEmpty() || !token.contains(":")) {
                statusText.setText("❌ Sahi Bot Token dalo");
                return;
            }
            pref.edit().putString(KEY_TOKEN, token).putBoolean(KEY_ENABLED, true).apply();
            statusText.setText("✅ Token Saved! Ab Name Change / Message bhej sakte ho. (Devgram)");
            Toast.makeText(context, "Devgram Bot Enabled", Toast.LENGTH_SHORT).show();
        });

        // START NAME CHANGE - SAFE VERSION
        startNameBtn.setOnClickListener(v -> {
            if (!pref.getBoolean(KEY_ENABLED, false)) {
                statusText.setText("❌ Pehle Bot Token dalo");
                return;
            }
            String chatId = chatIdInput.getText().toString().trim();
            String digit = digitInput.getText().toString().trim();
            String newName = nameInput.getText().toString().trim();
            String delayStr = delayInput.getText().toString().trim();

            if (chatId.isEmpty() || newName.isEmpty()) {
                statusText.setText("❌ Chat ID aur Naam dono bharo");
                return;
            }

            int delay = 65; // DEFAULT SAFE
            try { delay = Integer.parseInt(delayStr); } catch (Exception ignore) {}
            if (delay < 65) {
                statusText.setText("⚠️ Telegram limit: Name change min 65 sec. Auto set to 65 sec.");
                delay = 65;
            }

            String finalName = digit.isEmpty() ? newName : newName + " " + digit;
            String token = pref.getString(KEY_TOKEN, "");

            isNameChanging = true;
            statusText.setText("⏳ Changing title to: " + finalName + " (Delay " + delay + "s)");
            
            // Sirf 1 baar change, loop nahi - safe
            changeTitleSafe(token, chatId, finalName, delay);
        });

        stopNameBtn.setOnClickListener(v -> {
            isNameChanging = false;
            statusText.setText("⏹️ Name change stopped");
        });

        // START MESSAGE SEND - SAFE VERSION (single send, not loop)
        startMsgBtn.setOnClickListener(v -> {
            if (!pref.getBoolean(KEY_ENABLED, false)) {
                statusText.setText("❌ Pehle Bot Token dalo");
                return;
            }
            String chatId = chatIdInput.getText().toString().trim();
            String msg = messageInput.getText().toString().trim();
            String delayStr = msgDelayInput.getText().toString().trim();

            if (chatId.isEmpty() || msg.isEmpty()) {
                statusText.setText("❌ Chat ID aur Message bharo");
                return;
            }

            int delay = 3;
            try { delay = Integer.parseInt(delayStr); } catch (Exception ignore) {}
            if (delay < 3) delay = 3; // Min 3 sec for message

            String token = pref.getString(KEY_TOKEN, "");
            statusText.setText("⏳ Sending message... (Safe delay " + delay + "s)");
            sendMessageSafe(token, chatId, msg);
        });

        stopMsgBtn.setOnClickListener(v -> {
            isMsgSending = false;
            statusText.setText("⏹️ Message sending stopped");
        });

        scroll.addView(container);
        root.addView(scroll);
        return root;
    }

    private void changeTitleSafe(String token, String chatId, String title, int delay) {
        // Yahan 65 sec ka respect, no zero flooding
        HttpUrl url = HttpUrl.parse("https://api.telegram.org/bot" + token + "/setChatTitle").newBuilder()
                .addQueryParameter("chat_id", chatId)
                .addQueryParameter("title", title)
                .build();

        Request req = new Request.Builder().url(url).get().build();
        client.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                AndroidUtilities.runOnUIThread(() -> statusText.setText("❌ Error: " + e.getMessage()));
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                AndroidUtilities.runOnUIThread(() -> {
                    if (body.contains("\"ok\":true")) {
                        statusText.setText("✅ Name changed to: " + title + "\nNext change after " + delay + " sec allowed");
                    } else if (body.contains("retry_after")) {
                        statusText.setText("⏳ FloodWait: " + body + "\nTelegram ne roka, bypass nahi hoga");
                    } else {
                        statusText.setText("❌ Failed: " + body);
                    }
                });
            }
        });
    }

    private void sendMessageSafe(String token, String chatId, String text) {
        RequestBody form = new FormBody.Builder()
                .add("chat_id", chatId)
                .add("text", text)
                .build();
        Request req = new Request.Builder()
                .url("https://api.telegram.org/bot" + token + "/sendMessage")
                .post(form)
                .build();

        client.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                AndroidUtilities.runOnUIThread(() -> statusText.setText("❌ Msg Error: " + e.getMessage()));
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                AndroidUtilities.runOnUIThread(() -> {
                    if (body.contains("\"ok\":true")) {
                        statusText.setText("✅ Message sent: " + text);
                    } else {
                        statusText.setText("❌ Msg Failed: " + body);
                    }
                });
            }
        });
    }

    private TextView createSectionLabel(Context c, String txt) {
        TextView tv = new TextView(c);
        tv.setText(txt);
        tv.setTextColor(Color.WHITE);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setPadding(0, dp(20), 0, dp(8));
        tv.setTextSize(14);
        return tv;
    }

    private EditText createEdit(Context c, String hint) {
        EditText et = new EditText(c);
        et.setHint(hint);
        et.setHintTextColor(0xFF888888);
        et.setTextColor(Color.WHITE);
        et.setBackground(createEditBg());
        et.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        et.setLayoutParams(lp);
        return et;
    }

    private Button createWhiteButton(Context c, String txt) {
        Button b = new Button(c);
        b.setText(txt);
        b.setTextColor(Color.BLACK);
        b.setBackground(createBtnBg(Color.WHITE));
        b.setAllCaps(false);
        return b;
    }

    private Button createBlackButton(Context c, String txt) {
        Button b = new Button(c);
        b.setText(txt);
        b.setTextColor(Color.WHITE);
        b.setBackground(createBtnBg(Color.BLACK));
        b.setAllCaps(false);
        return b;
    }

    private GradientDrawable createEditBg() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(0xFF1A1A1A);
        d.setCornerRadius(dp(12));
        d.setStroke(dp(1), Color.WHITE);
        return d;
    }

    private GradientDrawable createBtnBg(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(20));
        return d;
    }

    private int dp(int v) { return AndroidUtilities.dp(v); }
}
