package org.telegram.ui;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.FrameLayout;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Kite: admin section in Settings with the usage counters from server/stats.
 * The item is shown only to the admin account; the server still asks for the admin password,
 * because a check on the phone alone can be bypassed.
 */
public final class KiteAdmin {

    public static final String ADMIN_USERNAME = "a0059";

    private static final int RESULT_ERROR = 0;
    private static final int RESULT_WRONG_PASSWORD = 1;

    private static boolean loading;

    private KiteAdmin() {
    }

    public static boolean isAdmin(int account) {
        if (TextUtils.isEmpty(BuildConfig.KITE_STATS_URL)) {
            return false;
        }
        final TLRPC.User user = UserConfig.getInstance(account).getCurrentUser();
        if (user == null) {
            return false;
        }
        if (ADMIN_USERNAME.equalsIgnoreCase(user.username)) {
            return true;
        }
        if (user.usernames != null) {
            for (int a = 0; a < user.usernames.size(); a++) {
                final TLRPC.TL_username username = user.usernames.get(a);
                if (username != null && username.active && ADMIN_USERNAME.equalsIgnoreCase(username.username)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("kite_admin", Context.MODE_PRIVATE);
    }

    public static void open(BaseFragment fragment) {
        if (!isAdmin(fragment.getCurrentAccount())) {
            return;
        }
        final String saved = prefs().getString("password", null);
        if (saved == null) {
            askPassword(fragment, false);
        } else {
            load(fragment, saved);
        }
    }

    private static void askPassword(BaseFragment fragment, boolean wrong) {
        final Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        final EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        input.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        input.setHint(getString(R.string.KiteAdminPassword));
        input.setSingleLine(true);
        final FrameLayout container = new FrameLayout(context);
        container.addView(input, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 24, 0, 24, 0));

        final AlertDialog dialog = new AlertDialog.Builder(context)
            .setTitle(getString(R.string.KiteAdmin))
            .setMessage(getString(wrong ? R.string.KiteAdminWrongPassword : R.string.KiteAdminPasswordInfo))
            .setView(container)
            .setPositiveButton(getString(R.string.OK), (d, which) -> {
                final String password = input.getText().toString();
                if (!password.isEmpty()) {
                    load(fragment, password);
                }
            })
            .setNegativeButton(getString(R.string.Cancel), null)
            .create();
        fragment.showDialog(dialog);
        AndroidUtilities.runOnUIThread(() -> {
            input.requestFocus();
            AndroidUtilities.showKeyboard(input);
        }, 200);
    }

    private static void load(BaseFragment fragment, String password) {
        if (loading || fragment.getParentActivity() == null) {
            return;
        }
        loading = true;
        final AlertDialog progress = new AlertDialog(fragment.getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progress.setCanCancel(false);
        progress.show();
        Utilities.globalQueue.postRunnable(() -> {
            int error = RESULT_ERROR;
            JSONObject stats = null;
            HttpURLConnection connection = null;
            try {
                final String base = BuildConfig.KITE_STATS_URL.endsWith("/") ? BuildConfig.KITE_STATS_URL : BuildConfig.KITE_STATS_URL + "/";
                connection = (HttpURLConnection) new URL(base + "stats").openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setRequestProperty("Authorization", "Bearer " + password);
                final int code = connection.getResponseCode();
                if (code == 401) {
                    error = RESULT_WRONG_PASSWORD;
                } else if (code == 200) {
                    final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    try (InputStream in = connection.getInputStream()) {
                        final byte[] buffer = new byte[4096];
                        int read;
                        while ((read = in.read(buffer)) > 0) {
                            bytes.write(buffer, 0, read);
                        }
                    }
                    stats = new JSONObject(bytes.toString("UTF-8"));
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            final JSONObject result = stats;
            final int resultError = error;
            AndroidUtilities.runOnUIThread(() -> {
                loading = false;
                try {
                    progress.dismiss();
                } catch (Exception ignore) {
                }
                if (fragment.getParentActivity() == null) {
                    return;
                }
                if (result != null) {
                    prefs().edit().putString("password", password).apply();
                    showStats(fragment, result);
                } else if (resultError == RESULT_WRONG_PASSWORD) {
                    prefs().edit().remove("password").apply();
                    askPassword(fragment, true);
                } else {
                    fragment.showDialog(new AlertDialog.Builder(fragment.getParentActivity())
                        .setTitle(getString(R.string.KiteAdmin))
                        .setMessage(getString(R.string.KiteAdminLoadFailed))
                        .setPositiveButton(getString(R.string.OK), null)
                        .create());
                }
            });
        });
    }

    private static void showStats(BaseFragment fragment, JSONObject stats) {
        final StringBuilder text = new StringBuilder();
        text.append(formatString(R.string.KiteAdminInstalls, stats.optInt("installs"))).append('\n');
        text.append(formatString(R.string.KiteAdminSignedIn, stats.optInt("signed_in"))).append('\n');
        text.append(formatString(R.string.KiteAdminSignedOut, stats.optInt("signed_out"))).append('\n');
        text.append(formatString(R.string.KiteAdminAccounts, stats.optInt("accounts"))).append('\n');
        text.append(formatString(R.string.KiteAdminActiveDay, stats.optInt("active_day"))).append('\n');
        text.append(formatString(R.string.KiteAdminActiveWeek, stats.optInt("active_week"))).append("\n\n");
        text.append(formatString(R.string.KiteAdminInChannel, stats.optInt("in_channel"))).append('\n');
        text.append(formatString(R.string.KiteAdminNotInChannel, stats.optInt("not_in_channel")));
        final JSONArray days = stats.optJSONArray("days");
        if (days != null && days.length() > 0) {
            text.append("\n\n").append(getString(R.string.KiteAdminDays));
            for (int a = 0; a < days.length(); a++) {
                final JSONObject day = days.optJSONObject(a);
                if (day == null) {
                    continue;
                }
                text.append('\n').append(formatString(R.string.KiteAdminDay,
                    day.optString("day"), day.optInt("installs"), day.optInt("open"), day.optInt("login"), day.optInt("logout"), day.optInt("join")));
            }
        }
        fragment.showDialog(new AlertDialog.Builder(fragment.getParentActivity())
            .setTitle(getString(R.string.KiteAdmin))
            .setMessage(text.toString())
            .setPositiveButton(getString(R.string.OK), null)
            .setNeutralButton(getString(R.string.KiteAdminForgetPassword), (d, which) -> prefs().edit().remove("password").apply())
            .create());
    }
}
