package org.telegram.messenger.gramify;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import java.io.IOException;
import java.util.*;

import okhttp3.*;

/**
 * FINAL SAFE PANEL - Aapke Flow Diagram ke hisab se
 * 
 * Flow: Settings 10 slots -> Save -> ON -> Permission -> Bubble -> Tap -> Panel
 * Panel: Group Link + NAME CHANGE | MESSAGE SEND + START/STOP + Outside tap close
 * 
 * SAFE LIMITS (Ban se bachne ke liye):
 * - Threads input hai par use 1 hi hoga (multi-thread same group = spam = ban)
 * - Delay 0 daloge to auto 65 sec (name) / 3 sec (msg) ho jayega
 * - Fancy font allowed hai
 * - START = 1 baar hi kaam karega, loop nahi (loop = spam)
 */

public class DevgramBotPanelFinalSafe extends LinearLayout {

    // Fancy Font Map (aapke code se - allowed)
    private static final Map<Character, String> FANCY = new HashMap<>();
    static {
        FANCY.put('a', "ᴀ"); FANCY.put('b', "ʙ"); FANCY.put('c', "ᴄ"); FANCY.put('d', "ᴅ");
        FANCY.put('e', "ᴇ"); FANCY.put('f', "ғ"); FANCY.put('g', "ɢ"); FANCY.put('h', "ʜ");
        FANCY.put('i', "ɪ"); FANCY.put('j', "ᴊ"); FANCY.put('k', "ᴋ"); FANCY.put('l', "ʟ");
        FANCY.put('m', "ᴍ"); FANCY.put('n', "ɴ"); FANCY.put('o', "ᴏ"); FANCY.put('p', "ᴘ");
        FANCY.put('q', "ǫ"); FANCY.put('r', "ʀ"); FANCY.put('s', "s"); FANCY.put('t', "ᴛ");
        FANCY.put('u', "ᴜ"); FANCY.put('v', "ᴠ"); FANCY.put('w', "ᴡ"); FANCY.put('x', "x");
        FANCY.put('y', "ʏ"); FANCY.put('z', "ᴢ");
        FANCY.put('A', "𝐀"); FANCY.put('B', "𝐁"); FANCY.put('C', "𝐂"); FANCY.put('D', "𝐃");
        FANCY.put('E', "𝐄"); FANCY.put('F', "𝐅"); FANCY.put('G', "𝐆"); FANCY.put('H', "𝐇");
        FANCY.put('I', "𝐈"); FANCY.put('J', "𝐉"); FANCY.put('K', "𝐊"); FANCY.put('L', "𝐋");
        FANCY.put('M', "𝐌"); FANCY.put('N', "𝐍"); FANCY.put('O', "𝐎"); FANCY.put('P', "𝐏");
        FANCY.put('Q', "𝐐"); FANCY.put('R', "𝐑"); FANCY.put('S', "𝐒"); FANCY.put('T', "𝐓");
        FANCY.put('U', "𝐔"); FANCY.put('V', "𝐕"); FANCY.put('W', "𝐖"); FANCY.put('X', "𝐗");
        FANCY.put('Y', "𝐘"); FANCY.put('Z', "𝐙");
    }

    private static String toFancy(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            String f = FANCY.get(c);
            sb.append(f != null ? f : c);
        }
        return sb.toString();
    }

    // UI
    private EditText groupLinkEt;
    private EditText nameThreadsEt, nameTextEt, nameDelayEt;
    private Spinner namePosSpinner;
    private CheckBox nameFancyCb;
    private TextView nameStatus;
    
    private EditText msgThreadsEt, msgTextEt, msgDelayEt;
    private CheckBox msgFancyCb;
    private TextView msgStatus;

    private OkHttpClient client = new OkHttpClient();
    private Handler uiHandler = new Handler(Looper.getMainLooper());
    private Runnable onClose;

    public DevgramBotPanelFinalSafe(Context context, Runnable onClose) {
        super(context);
        this.onClose = onClose;
        setOrientation(VERTICAL);
        setBackground(bg(0xEE000000, dp(16)));
        setPadding(dp(12), dp(12), dp(12), dp(12));
        buildUI(context);
    }

    private void buildUI(Context c) {
        ScrollView scroll = new ScrollView(c);
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(VERTICAL);

        TextView title = new TextView(c);
        title.setText("Devgram Bot Manager");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView sub = new TextView(c);
        sub.setText("iOS Style | Black & White | Safe Mode");
        sub.setTextColor(0xFFAAAAAA);
        sub.setTextSize(10);
        root.addView(sub);

        // Group Link
        groupLinkEt = edit(c, "Group Link / Chat ID (-100...)");
        SharedPreferences pref = c.getSharedPreferences("devgram_panel_pref", Context.MODE_PRIVATE);
        groupLinkEt.setText(pref.getString("group_link", ""));
        root.addView(label(c, "Group Link"));
        root.addView(groupLinkEt);

        // Parallel Sections
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(HORIZONTAL);
        row.setPadding(0, dp(8), 0, 0);

        LinearLayout nameSec = buildNameSection(c);
        LinearLayout msgSec = buildMsgSection(c);

        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        half.rightMargin = dp(6);
        nameSec.setLayoutParams(half);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        half2.leftMargin = dp(6);
        msgSec.setLayoutParams(half2);

        row.addView(nameSec);
        row.addView(msgSec);
        root.addView(row);

        scroll.addView(root);
        addView(scroll);
    }

    private LinearLayout buildNameSection(Context c) {
        LinearLayout sec = new LinearLayout(c);
        sec.setOrientation(VERTICAL);
        sec.setPadding(dp(8), dp(8), dp(8), dp(8));
        sec.setBackground(bg(0xFF1A1A1A, dp(12)));

        sec.addView(header(c, "NAME CHANGE"));

        sec.addView(label(c, "Threads [1-10] (Safe: 1 use hoga)"));
        nameThreadsEt = edit(c, "ex: 1");
        nameThreadsEt.setInputType(InputType.TYPE_CLASS_NUMBER);
        nameThreadsEt.setText("1");
        sec.addView(nameThreadsEt);

        sec.addView(label(c, "Text [Dev]"));
        nameTextEt = edit(c, "ex: Dev");
        sec.addView(nameTextEt);

        sec.addView(label(c, "Pos [both/prefix/suffix]"));
        namePosSpinner = new Spinner(c);
        ArrayAdapter<String> ad = new ArrayAdapter<>(c, android.R.layout.simple_spinner_dropdown_item, new String[]{"both", "prefix", "suffix"});
        namePosSpinner.setAdapter(ad);
        sec.addView(namePosSpinner);

        sec.addView(label(c, "Delay [0] -> Safe min 65 sec"));
        nameDelayEt = edit(c, "ex: 65");
        nameDelayEt.setInputType(InputType.TYPE_CLASS_NUMBER);
        nameDelayEt.setText("65");
        sec.addView(nameDelayEt);

        nameFancyCb = new CheckBox(c);
        nameFancyCb.setText("Fancy Font");
        nameFancyCb.setTextColor(Color.WHITE);
        nameFancyCb.setChecked(true);
        sec.addView(nameFancyCb);

        LinearLayout btnRow = new LinearLayout(c);
        btnRow.setOrientation(HORIZONTAL);
        Button start = whiteBtn(c, "START");
        Button stop = blackBtn(c, "STOP");
        start.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        stop.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        btnRow.addView(start);
        btnRow.addView(stop);
        sec.addView(btnRow);

        nameStatus = new TextView(c);
        nameStatus.setTextColor(0xFFFFAA00);
        nameStatus.setTextSize(10);
        sec.addView(nameStatus);

        start.setOnClickListener(v -> doNameChange());
        stop.setOnClickListener(v -> nameStatus.setText("⏹ Stopped"));

        return sec;
    }

    private LinearLayout buildMsgSection(Context c) {
        LinearLayout sec = new LinearLayout(c);
        sec.setOrientation(VERTICAL);
        sec.setPadding(dp(8), dp(8), dp(8), dp(8));
        sec.setBackground(bg(0xFF1A1A1A, dp(12)));

        sec.addView(header(c, "MESSAGE SEND"));

        sec.addView(label(c, "Threads [1-10] (Safe: 1 use)"));
        msgThreadsEt = edit(c, "ex: 1");
        msgThreadsEt.setInputType(InputType.TYPE_CLASS_NUMBER);
        msgThreadsEt.setText("1");
        sec.addView(msgThreadsEt);

        sec.addView(label(c, "Text [Hello,Hi,Bye]"));
        msgTextEt = edit(c, "Hello,Hi,Bye");
        sec.addView(msgTextEt);

        sec.addView(label(c, "Delay [0] -> Safe min 3 sec"));
        msgDelayEt = edit(c, "ex: 3");
        msgDelayEt.setInputType(InputType.TYPE_CLASS_NUMBER);
        msgDelayEt.setText("3");
        sec.addView(msgDelayEt);

        msgFancyCb = new CheckBox(c);
        msgFancyCb.setText("Fancy Font");
        msgFancyCb.setTextColor(Color.WHITE);
        msgFancyCb.setChecked(true);
        sec.addView(msgFancyCb);

        LinearLayout btnRow = new LinearLayout(c);
        btnRow.setOrientation(HORIZONTAL);
        Button start = whiteBtn(c, "START");
        Button stop = blackBtn(c, "STOP");
        start.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        stop.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        btnRow.addView(start);
        btnRow.addView(stop);
        sec.addView(btnRow);

        msgStatus = new TextView(c);
        msgStatus.setTextColor(0xFFFFAA00);
        msgStatus.setTextSize(10);
        sec.addView(msgStatus);

        start.setOnClickListener(v -> doMsgSend());
        stop.setOnClickListener(v -> msgStatus.setText("⏹ Stopped"));

        return sec;
    }

    // SAFE ACTIONS - Single execution, no loop, no flood bypass

    private void doNameChange() {
        String groupLink = groupLinkEt.getText().toString().trim();
        String text = nameTextEt.getText().toString().trim();
        String pos = (String) namePosSpinner.getSelectedItem();
        String delayStr = nameDelayEt.getText().toString().trim();
        boolean fancy = nameFancyCb.isChecked();

        if (groupLink.isEmpty() || text.isEmpty()) {
            nameStatus.setText("❌ Group + Text required");
            return;
        }

        // SAFE: Delay min 65 sec
        int delay = 65;
        try { delay = Integer.parseInt(delayStr); } catch (Exception ignore) {}
        if (delay < 65) {
            delay = 65;
            nameStatus.setText("⚠️ Delay 0 not allowed, auto 65 sec (Telegram limit)");
        }

        // Get first available token from 10 slots
        String token = getFirstToken();
        if (token == null) {
            nameStatus.setText("❌ Pehle Settings -> 10 slots me token save karo");
            return;
        }

        String chatId = extractChatId(groupLink);
        if (chatId == null) {
            nameStatus.setText("❌ Invalid Group Link / Chat ID");
            return;
        }

        String finalText = fancy ? toFancy(text) : text;
        // Position logic simple
        String finalTitle = finalText;
        if ("prefix".equals(pos)) finalTitle = "✦ " + finalText;
        else if ("suffix".equals(pos)) finalTitle = finalText + " ✦";
        else finalTitle = "✦ " + finalText + " ✦";

        if (finalTitle.length() > 128) finalTitle = finalTitle.substring(0, 128);

        nameStatus.setText("⏳ Changing to: " + finalTitle);

        HttpUrl url = HttpUrl.parse("https://api.telegram.org/bot" + token + "/setChatTitle").newBuilder()
                .addQueryParameter("chat_id", chatId)
                .addQueryParameter("title", finalTitle)
                .build();
        Request req = new Request.Builder().url(url).get().build();
        client.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                uiHandler.post(() -> nameStatus.setText("❌ Error: " + e.getMessage()));
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                uiHandler.post(() -> {
                    if (body.contains("\"ok\":true")) nameStatus.setText("✅ Changed: " + finalTitle);
                    else if (body.contains("retry_after")) nameStatus.setText("⏳ FloodWait: " + body);
                    else nameStatus.setText("❌ Failed: " + body);
                });
            }
        });
    }

    private void doMsgSend() {
        String groupLink = groupLinkEt.getText().toString().trim();
        String text = msgTextEt.getText().toString().trim();
        String delayStr = msgDelayEt.getText().toString().trim();
        boolean fancy = msgFancyCb.isChecked();

        if (groupLink.isEmpty() || text.isEmpty()) {
            msgStatus.setText("❌ Group + Text required");
            return;
        }

        int delay = 3;
        try { delay = Integer.parseInt(delayStr); } catch (Exception ignore) {}
        if (delay < 3) delay = 3;

        String token = getFirstToken();
        if (token == null) {
            msgStatus.setText("❌ Pehle Settings me token save karo");
            return;
        }

        String chatId = extractChatId(groupLink);
        if (chatId == null) {
            msgStatus.setText("❌ Invalid Group Link");
            return;
        }

        // Take first part if comma separated
        String firstMsg = text.split(",")[0].trim();
        String finalMsg = fancy ? toFancy(firstMsg) : firstMsg;

        msgStatus.setText("⏳ Sending: " + finalMsg);

        RequestBody form = new FormBody.Builder().add("chat_id", chatId).add("text", finalMsg).build();
        Request req = new Request.Builder().url("https://api.telegram.org/bot" + token + "/sendMessage").post(form).build();
        client.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                uiHandler.post(() -> msgStatus.setText("❌ Error: " + e.getMessage()));
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                uiHandler.post(() -> {
                    if (body.contains("\"ok\":true")) msgStatus.setText("✅ Sent: " + finalMsg);
                    else msgStatus.setText("❌ Failed: " + body);
                });
            }
        });
    }

    private String getFirstToken() {
        SharedPreferences s = getContext().getSharedPreferences("devgram_bot_10_pref", Context.MODE_PRIVATE);
        for (int i = 0; i < 10; i++) {
            String t = s.getString("token_" + i, "").trim();
            if (!t.isEmpty() && t.contains(":")) return t;
        }
        return null;
    }

    private String extractChatId(String link) {
        link = link.trim();
        if (link.matches("-?\\d+")) return link;
        if (link.startsWith("@")) return link;
        if (link.contains("t.me/")) {
            String last = link.substring(link.lastIndexOf("/") + 1);
            if (!last.startsWith("+")) return "@" + last;
        }
        return null;
    }

    // UI Helpers
    private TextView header(Context c, String s) {
        TextView tv = new TextView(c); tv.setText(s); tv.setTextColor(Color.WHITE);
        tv.setTypeface(null, android.graphics.Typeface.BOLD); tv.setTextSize(13); return tv;
    }
    private TextView label(Context c, String s) {
        TextView tv = new TextView(c); tv.setText(s); tv.setTextColor(0xFFCCCCCC); tv.setTextSize(10);
        tv.setPadding(0, dp(6), 0, dp(2)); return tv;
    }
    private EditText edit(Context c, String hint) {
        EditText et = new EditText(c); et.setHint(hint); et.setHintTextColor(0xFF888888);
        et.setTextColor(Color.WHITE); et.setTextSize(12);
        et.setBackground(bg(0xFF222222, dp(8))); et.setPadding(dp(8), dp(8), dp(8), dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(4); et.setLayoutParams(lp); return et;
    }
    private Button whiteBtn(Context c, String t) {
        Button b = new Button(c); b.setText(t); b.setTextColor(Color.BLACK); b.setAllCaps(false);
        b.setBackground(bg(Color.WHITE, dp(16))); return b;
    }
    private Button blackBtn(Context c, String t) {
        Button b = new Button(c); b.setText(t); b.setTextColor(Color.WHITE); b.setAllCaps(false);
        b.setBackground(bg(0xFF333333, dp(16))); return b;
    }
    private GradientDrawable bg(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius); return d;
    }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }
}
