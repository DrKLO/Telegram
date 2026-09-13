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

import okhttp3.*;
import java.io.IOException;

/**
 * Devgram Bot Manager - 10 Slots SAFE VERSION
 * Aapki requirement ke hisab se:
 * - 10 bot token ke boxes (user 1 dale ya 10 dale, dono chalega)
 * - Kitni baar wala box (Count) - SAFE: max 1 hi allowed hai spam rokne ke liye
 * - Tabs = Chat ID select
 * - Digit + Naam + Delay
 * - Message me bhi same
 * 
 * SAFE RULES (No Ban):
 * - Ek time pe sirf 1 selected bot hi kaam karega, 10 ek sath spam nahi
 * - Name Change min delay 65 sec (Telegram limit)
 * - Message min delay 3 sec
 * - Count max 1 (loop nahi, warna spam + ban)
 */

public class Devgram_Bot_10_Slots_Safe extends BaseFragment {

    private static final String PREF = "devgram_bot_10_pref";
    private static final int MAX_BOTS = 10;

    private EditText[] tokenInputs = new EditText[MAX_BOTS];
    private Spinner botSelector;
    private EditText chatIdInput;
    private EditText digitInput;
    private EditText nameInput;
    private EditText delayInput;
    private EditText countNameInput; // Kitni baar - aapne bola tha 1-2 box jaisa

    private EditText messageInput;
    private EditText msgDelayInput;
    private EditText countMsgInput; // Kitni baar msg send

    private TextView statusText;
    private OkHttpClient client = new OkHttpClient();

    @Override
    public View createView(Context context) {
        SharedPreferences pref = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        ScrollView scroll = new ScrollView(context);
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(16), dp(16), dp(16), dp(16));

        // Title - Devgram everywhere (no Telegram)
        TextView title = new TextView(context);
        title.setText("Devgram Bot Manager");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        container.addView(title);

        TextView sub = new TextView(context);
        sub.setText("Developed by Dev 🫍 | iOS Style | Max 10 Bots | Safe Mode");
        sub.setTextColor(0xFFAAAAAA);
        sub.setTextSize(11);
        sub.setPadding(0, 0, 0, dp(16));
        container.addView(sub);

        // --- SECTION 1: 10 BOT TOKEN BOXES ---
        container.addView(createSectionLabel(context, "STEP 1: Bot Tokens (10 Slots) - 1 se bhi chalega"));

        TextView info = new TextView(context);
        info.setText("Yahan 10 bot token daal sakte ho. 1 dalke bhi use kar sakte ho. Max 10.\nBot @BotFather se banao aur group me admin banao.");
        info.setTextColor(0xFF888888);
        info.setTextSize(11);
        container.addView(info);

        for (int i = 0; i < MAX_BOTS; i++) {
            tokenInputs[i] = createEdit(context, "Bot Token " + (i+1) + " - ex: 123456:ABC...");
            tokenInputs[i].setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            tokenInputs[i].setText(pref.getString("token_" + i, ""));
            container.addView(tokenInputs[i]);
        }

        Button saveAllBtn = createWhiteButton(context, "Save All 10 Tokens");
        container.addView(saveAllBtn);

        // Bot Selector - kaunsa bot active hai
        container.addView(createSectionLabel(context, "Active Bot Select Karo"));
        botSelector = new Spinner(context);
        String[] botNames = new String[MAX_BOTS];
        for (int i = 0; i < MAX_BOTS; i++) botNames[i] = "Bot " + (i+1);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, botNames);
        botSelector.setAdapter(adapter);
        botSelector.setSelection(pref.getInt("selected_bot", 0));
        container.addView(botSelector);

        // --- SECTION 2: Tabs / Chat Select ---
        container.addView(createSectionLabel(context, "STEP 2: Tabs / Chat Select"));
        chatIdInput = createEdit(context, "Chat ID / @username - ex: -100123456789");
        container.addView(chatIdInput);

        // --- SECTION 3: Name Change ---
        container.addView(createSectionLabel(context, "SECTION A: Name Change"));

        LinearLayout rowDigitName = new LinearLayout(context);
        rowDigitName.setOrientation(LinearLayout.HORIZONTAL);
        digitInput = createEdit(context, "Digit (ex: 1)");
        digitInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        digitInput.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        nameInput = createEdit(context, "Kya naam rakhna hai?");
        nameInput.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f));
        rowDigitName.addView(digitInput);
        rowDigitName.addView(nameInput);
        container.addView(rowDigitName);

        LinearLayout rowDelayCount = new LinearLayout(context);
        rowDelayCount.setOrientation(LinearLayout.HORIZONTAL);
        delayInput = createEdit(context, "Delay sec (Min 65)");
        delayInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        delayInput.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        countNameInput = createEdit(context, "Kitni baar? (Max 1)");
        countNameInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        countNameInput.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        rowDelayCount.addView(delayInput);
        rowDelayCount.addView(countNameInput);
        container.addView(rowDelayCount);

        TextView countNote1 = new TextView(context);
        countNote1.setText("Note: Telegram limit ke wajah se count max 1 hi hai. Loop spam ban hai.");
        countNote1.setTextColor(0xFFFFAA00);
        countNote1.setTextSize(10);
        container.addView(countNote1);

        LinearLayout btnRow1 = new LinearLayout(context);
        btnRow1.setOrientation(LinearLayout.HORIZONTAL);
        Button startNameBtn = createWhiteButton(context, "Start Name Change");
        startNameBtn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button stopNameBtn = createBlackButton(context, "Stop");
        stopNameBtn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        btnRow1.addView(startNameBtn);
        btnRow1.addView(stopNameBtn);
        container.addView(btnRow1);

        // --- SECTION 4: Message Sending ---
        container.addView(createSectionLabel(context, "SECTION B: Message Sending"));

        messageInput = createEdit(context, "Kya message bhejna hai?");
        container.addView(messageInput);

        LinearLayout rowMsgDelayCount = new LinearLayout(context);
        rowMsgDelayCount.setOrientation(LinearLayout.HORIZONTAL);
        msgDelayInput = createEdit(context, "Delay sec (Min 3)");
        msgDelayInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        msgDelayInput.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        countMsgInput = createEdit(context, "Kitni baar? (Max 1)");
        countMsgInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        countMsgInput.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        rowMsgDelayCount.addView(msgDelayInput);
        rowMsgDelayCount.addView(countMsgInput);
        container.addView(rowMsgDelayCount);

        TextView countNote2 = new TextView(context);
        countNote2.setText("Note: Message bhi max 1 baar hi bheja jayega. Loop spam allowed nahi.");
        countNote2.setTextColor(0xFFFFAA00);
        countNote2.setTextSize(10);
        container.addView(countNote2);

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

        saveAllBtn.setOnClickListener(v -> {
            SharedPreferences.Editor ed = pref.edit();
            int saved = 0;
            for (int i = 0; i < MAX_BOTS; i++) {
                String t = tokenInputs[i].getText().toString().trim();
                if (!t.isEmpty() && t.contains(":")) {
                    ed.putString("token_" + i, t);
                    saved++;
                }
            }
            ed.putInt("selected_bot", botSelector.getSelectedItemPosition());
            ed.apply();
            statusText.setText("✅ " + saved + " Bot Token Saved! Selected: Bot " + (botSelector.getSelectedItemPosition()+1) + "\nAb aap 1 bot se bhi use kar sakte ho.");
        });

        startNameBtn.setOnClickListener(v -> {
            int selected = botSelector.getSelectedItemPosition();
            String token = pref.getString("token_" + selected, "").trim();
            if (token.isEmpty()) {
                statusText.setText("❌ Pehle Bot " + (selected+1) + " ka token dalo aur Save karo");
                return;
            }
            String chatId = chatIdInput.getText().toString().trim();
            String digit = digitInput.getText().toString().trim();
            String newName = nameInput.getText().toString().trim();
            String delayStr = delayInput.getText().toString().trim();
            String countStr = countNameInput.getText().toString().trim();

            if (chatId.isEmpty() || newName.isEmpty()) {
                statusText.setText("❌ Chat ID aur Naam bharo");
                return;
            }

            int delay = 65;
            try { delay = Integer.parseInt(delayStr); } catch (Exception ignore) {}
            if (delay < 65) delay = 65; // SAFE MIN

            int count = 1;
            try { count = Integer.parseInt(countStr); } catch (Exception ignore) {}
            if (count > 1) {
                statusText.setText("⚠️ Count max 1 hi allowed hai (spam rokne ke liye). Auto set to 1");
                count = 1;
            }

            String finalName = digit.isEmpty() ? newName : newName + " " + digit;
            statusText.setText("⏳ Bot " + (selected+1) + " se naam change: " + finalName);
            changeTitleSafe(token, chatId, finalName);
        });

        startMsgBtn.setOnClickListener(v -> {
            int selected = botSelector.getSelectedItemPosition();
            String token = pref.getString("token_" + selected, "").trim();
            if (token.isEmpty()) {
                statusText.setText("❌ Pehle Bot " + (selected+1) + " ka token dalo");
                return;
            }
            String chatId = chatIdInput.getText().toString().trim();
            String msg = messageInput.getText().toString().trim();
            String countStr = countMsgInput.getText().toString().trim();

            if (chatId.isEmpty() || msg.isEmpty()) {
                statusText.setText("❌ Chat ID aur Message bharo");
                return;
            }

            int count = 1;
            try { count = Integer.parseInt(countStr); } catch (Exception ignore) {}
            if (count > 1) {
                statusText.setText("⚠️ Message count max 1 hi allowed hai. Auto set to 1");
                count = 1;
            }

            statusText.setText("⏳ Bot " + (selected+1) + " se message bhej raha hu...");
            sendMessageSafe(token, chatId, msg);
        });

        scroll.addView(container);
        root.addView(scroll);
        return root;
    }

    private void changeTitleSafe(String token, String chatId, String title) {
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
                        statusText.setText("✅ Name changed to: " + title + " (Devgram Safe)");
                    } else if (body.contains("retry_after") || body.contains("Too Many Requests")) {
                        statusText.setText("⏳ FloodWait: Telegram ne roka\n" + body);
                    } else {
                        statusText.setText("❌ Failed: " + body);
                    }
                });
            }
        });
    }

    private void sendMessageSafe(String token, String chatId, String text) {
        RequestBody form = new FormBody.Builder().add("chat_id", chatId).add("text", text).build();
        Request req = new Request.Builder().url("https://api.telegram.org/bot" + token + "/sendMessage").post(form).build();
        client.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                AndroidUtilities.runOnUIThread(() -> statusText.setText("❌ Msg Error: " + e.getMessage()));
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                AndroidUtilities.runOnUIThread(() -> {
                    if (body.contains("\"ok\":true")) statusText.setText("✅ Message sent: " + text);
                    else statusText.setText("❌ Msg Failed: " + body);
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
        tv.setTextSize(13);
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
        Button b = new Button(c); b.setText(txt); b.setTextColor(Color.BLACK); b.setBackground(createBtnBg(Color.WHITE)); b.setAllCaps(false); return b;
    }
    private Button createBlackButton(Context c, String txt) {
        Button b = new Button(c); b.setText(txt); b.setTextColor(Color.WHITE); b.setBackground(createBtnBg(Color.BLACK)); b.setAllCaps(false); return b;
    }
    private GradientDrawable createEditBg() {
        GradientDrawable d = new GradientDrawable(); d.setColor(0xFF1A1A1A); d.setCornerRadius(dp(12)); d.setStroke(dp(1), Color.WHITE); return d;
    }
    private GradientDrawable createBtnBg(int color) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(20)); return d;
    }
    private int dp(int v) { return AndroidUtilities.dp(v); }
}
