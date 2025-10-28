package org.telegram.ui.bots;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Environment;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.TextUtils;
import android.util.Pair;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.gms.auth.api.signin.internal.Storage;
import com.google.firebase.platforminfo.UserAgentPublisher;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadioButton;
import org.telegram.ui.Components.TextHelper;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.ui.Stories.recorder.GallerySheet;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class BotStorage {

    public static boolean isSecuredSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    }

    public static File getDir() {
        try {
            File file = ApplicationLoader.applicationContext.getFilesDir();
            if (file != null) {
                File storageFile = new File(file, "apps_storage/");
                storageFile.mkdirs();
                if ((file.exists() || file.mkdirs()) && file.canWrite()) {
                    return storageFile;
                }
            }
        } catch (Exception e) {}
        return new File("");
    }

    public final Context context;
    public final int account;
    public final long bot_id;
    public final long user_id;
    public final boolean secured;
    public String storage_id;

    public BotStorage(Context context, int currentAccount, long user_id, long bot_id, boolean secured) {
        this.context = context;
        this.account = currentAccount;
        this.bot_id = bot_id;
        this.user_id = user_id;
        this.secured = secured;
    }

    private File getFile(String storage_id) {
        final File file = new File(getDir(), (secured ? storage_id : user_id) + "_" + bot_id + (secured ? "_s" : ""));
        final File oldFile = new File(getDir(), bot_id + (secured ? "_s" : ""));
        if (!file.exists() && oldFile.exists()) {
            oldFile.renameTo(file);
        } else if (secured) {
            final File oldFile2 = new File(getDir(), user_id + "_" + bot_id + "_s");
            if (!file.exists() && oldFile2.exists()) {
                oldFile2.renameTo(file);
            }
        }
        return file;
    }

    public File getFile() {
        if (secured && TextUtils.isEmpty(storage_id)) {
            final HashMap<String, StorageConfig> config = readConfig();
            for (final Map.Entry<String, StorageConfig> e : config.entrySet()) {
                if (e.getValue().user_id == user_id) {
                    storage_id = e.getKey();
                    break;
                }
            }
            if (TextUtils.isEmpty(storage_id)) {
                storage_id = java.util.UUID.randomUUID().toString();
                final StorageConfig newConfig = new StorageConfig();
                newConfig.storage_id = storage_id;
                newConfig.user_id = user_id;
                newConfig.user_name = DialogObject.getName(UserConfig.getInstance(account).getCurrentUser());
                newConfig.created_at = newConfig.edited_at = System.currentTimeMillis();
                config.put(storage_id, newConfig);
                saveConfig(config);
            }
        }
        return getFile(storage_id);
    }

    private File getConfigFile() {
        return new File(getDir(), "secure_config.json");
    }

    private SecretKey getSecretKey() throws KeyStoreException, CertificateException, IOException, NoSuchAlgorithmException, UnrecoverableKeyException, NoSuchProviderException, InvalidAlgorithmParameterException {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            throw new RuntimeException("UNSUPPORTED");
        }

        final KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (!keyStore.containsAlias("MiniAppsKey")) {
            final KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            final KeyGenParameterSpec keyGenParameterSpec =
                new KeyGenParameterSpec.Builder("MiniAppsKey", KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setUserAuthenticationRequired(false)
                    .build();
            keyGenerator.init(keyGenParameterSpec);
            keyGenerator.generateKey();
        }

        return (SecretKey) keyStore.getKey("MiniAppsKey", null);
    }

    private byte[] getBytes(File file) throws IOException {
        final FileInputStream fis = new FileInputStream(file);
        int length = (int) file.length();
        byte[] iv = null;
        if (secured) {
            int iv_size = fis.read(); length--;
            iv = new byte[iv_size]; length -= iv_size;
            fis.read(iv);
        }
        final byte[] buffer;
        try {
            buffer = new byte[length];
        } catch (OutOfMemoryError e) {
            FileLog.e(e);
            throw new RuntimeException("QUOTA_EXCEEDED");
        }
        fis.read(buffer);
        fis.close();
        if (secured) {
            try {
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                GCMParameterSpec spec = new GCMParameterSpec(128, iv);
                cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec);
                return cipher.doFinal(buffer);
            } catch (Exception e) {
                FileLog.e(e);
                setBytes(file, "{}".getBytes());
                throw new RuntimeException("UNKNOWN_ERROR");
            }
        }
        return buffer;
    }

    private void setBytes(File file, byte[] bytes) throws IOException {
        final FileOutputStream fos = new FileOutputStream(file);
        if (secured) {
            try {
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE, getSecretKey());
                byte[] iv = cipher.getIV();
                fos.write(iv.length);
                fos.write(iv);
                bytes = cipher.doFinal(bytes);
            } catch (Exception e) {
                FileLog.e(e);
                throw new RuntimeException("UNKNOWN_ERROR");
            }
        }
        fos.write(bytes);
        fos.close();
    }

    private JSONObject getJSON() {
        return getJSON(getFile());
    }

    private JSONObject getJSON(File file) {
        if (!file.exists() || file.length() > 5 * 1024 * 1024)
            return new JSONObject();
        try {
            return new JSONObject(new String(getBytes(file)));
        } catch (Exception e) {
            FileLog.e(e);
            return new JSONObject();
        }
    }

    private void setJSON(JSONObject obj) {
        byte[] bytes;
        try {
            bytes = obj.toString().getBytes();
        } catch (OutOfMemoryError e) {
            FileLog.e(e);
            throw new RuntimeException("QUOTA_EXCEEDED");
        } catch (Exception e) {
            FileLog.e(e);
            throw new RuntimeException("UNKNOWN_ERROR");
        }
        if (bytes.length > 5 * 1024 * 1024) {
            throw new RuntimeException("QUOTA_EXCEEDED");
        }
        try {
            setBytes(getFile(), bytes);
        } catch (Exception e) {
            FileLog.e(e);
            throw new RuntimeException("UNKNOWN_ERROR");
        }
    }

    public void setKey(String key, String value) {
        if (secured && !isSecuredSupported())
            throw new RuntimeException("UNSUPPORTED");
        if (key.length() + value.length() > 5 * 1024 * 1024)
            throw new RuntimeException("QUOTA_EXCEEDED");
        final JSONObject object = getJSON();
        try {
            if (value == null) {
                object.remove(key);
            } else {
                object.put(key, value);
            }
        } catch (Exception e) {
            FileLog.e(e);
            throw new RuntimeException("UNKNOWN_ERROR");
        }
        if (object.length() > 10 && secured)
            throw new RuntimeException("QUOTA_EXCEEDED");
        setJSON(object);
        if (secured) {
            try {
                final HashMap<String, StorageConfig> config = readConfig();
                final StorageConfig storageConfig = config.get(storage_id);
                if (storageConfig != null) {
                    storageConfig.edited_at = System.currentTimeMillis();
                    saveConfig(config);
                }
            } catch (Exception e) {}
        }
    }

    public Pair<String, Boolean> getKey(String key) {
        if (secured && !isSecuredSupported())
            throw new RuntimeException("UNSUPPORTED");
        final JSONObject thisJSON = getJSON();
        final String value = thisJSON.optString(key);
        boolean can_restore = false;
        if (secured && value == null && !thisJSON.keys().hasNext()) {
            final HashSet<Long> activeUsers = new HashSet<>();
            for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; ++i) {
                final UserConfig userConfig = UserConfig.getInstance(i);
                if (userConfig.isClientActivated()) {
                    activeUsers.add(userConfig.getClientUserId());
                }
            }
            final HashMap<String, StorageConfig> config = readConfig();
            final Set<StorageConfig> lostConfigs =
                config.values().stream()
                    .filter(c -> !activeUsers.contains(c.user_id))
                    .collect(Collectors.toSet());
            for (StorageConfig c : lostConfigs) {
                try {
                    final File file = getFile(c.storage_id);
                    if (file.exists()) {
                        final JSONObject json = getJSON(file);
                        if (json != null && json.has(key)) {
                            can_restore = true;
                            break;
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }
        return new Pair<>(value, can_restore);
    }

    public List<StorageConfig> getStoragesWithKey(String key) {
        if (secured && !isSecuredSupported())
            throw new RuntimeException("UNSUPPORTED");
        final JSONObject thisJSON = getJSON();
        if (thisJSON.keys().hasNext()) {
            throw new RuntimeException("STORAGE_NOT_EMPTY");
        }

        final ArrayList<StorageConfig> result = new ArrayList<>();

        final HashSet<Long> activeUsers = new HashSet<>();
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; ++i) {
            final UserConfig userConfig = UserConfig.getInstance(i);
            if (userConfig.isClientActivated()) {
                activeUsers.add(userConfig.getClientUserId());
            }
        }
        final HashMap<String, StorageConfig> config = readConfig();
        final Set<StorageConfig> lostConfigs =
            config.values().stream()
                .filter(c -> !activeUsers.contains(c.user_id))
                .collect(Collectors.toSet());
        for (StorageConfig c : lostConfigs) {
            try {
                final File file = getFile(c.storage_id);
                if (file.exists()) {
                    final JSONObject json = getJSON(file);
                    if (json != null && json.has(key)) {
                        result.add(c);
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        return result;
    }

    public void restoreFrom(String id) {
        if (secured && !isSecuredSupported())
            throw new RuntimeException("UNSUPPORTED");
        final JSONObject thisJSON = getJSON();
        if (thisJSON.keys().hasNext()) {
            throw new RuntimeException("STORAGE_NOT_EMPTY");
        }

        final HashSet<Long> activeUsers = new HashSet<>();
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; ++i) {
            final UserConfig userConfig = UserConfig.getInstance(i);
            if (userConfig.isClientActivated()) {
                activeUsers.add(userConfig.getClientUserId());
            }
        }

        final HashMap<String, StorageConfig> config = readConfig();
        final StorageConfig storageConfig = config.get(id);
        if (storageConfig == null/* || activeUsers.contains(storageConfig.user_id)*/) {
            throw new RuntimeException("STORAGE_NOT_FOUND");
        }
        storageConfig.user_id = user_id;
        storageConfig.user_name = DialogObject.getName(UserConfig.getInstance(account).getCurrentUser());
        storageConfig.edited_at = System.currentTimeMillis();
        saveConfig(config);
        storage_id = storageConfig.storage_id;
    }

    public void clear() {
        setJSON(new JSONObject());
    }

    private byte[] getRawBytes(File file) throws IOException {
        final FileInputStream fis = new FileInputStream(file);
        int length = (int) file.length();
        final byte[] buffer;
        try {
            buffer = new byte[length];
        } catch (OutOfMemoryError e) {
            FileLog.e(e);
            throw new RuntimeException("QUOTA_EXCEEDED");
        }
        fis.read(buffer);
        fis.close();
        return buffer;
    }

    private void saveRawBytes(File file, byte[] bytes) throws IOException {
        final FileOutputStream fos = new FileOutputStream(file);
        fos.write(bytes);
        fos.close();
    }

    public static class StorageConfig {
        String storage_id;
        long user_id;
        String user_name;
        long created_at;
        long edited_at;
    }

    private HashMap<String, StorageConfig> readConfig() {
        final HashMap<String, StorageConfig> config = new HashMap<>();
        try {
            final JSONObject object = new JSONObject(new String(getRawBytes(getConfigFile())));
            Iterator<String> it = object.keys();
            while (it.hasNext()) {
                final String id = it.next();
                final JSONObject json = object.getJSONObject(id);
                final StorageConfig storageConfig = new StorageConfig();
                storageConfig.storage_id = id;
                storageConfig.user_id = json.getLong("user_id");
                storageConfig.user_name = json.getString("user_name");
                storageConfig.created_at = json.getLong("created_at");
                storageConfig.edited_at = json.getLong("edited_at");
                config.put(id, storageConfig);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return config;
    }

    private void saveConfig(HashMap<String, StorageConfig> map) {
        try {
            final JSONObject object = new JSONObject();
            for (Map.Entry<String, StorageConfig> e : map.entrySet()) {
                final JSONObject json = new JSONObject();
                json.put("user_id", e.getValue().user_id);
                json.put("user_name", e.getValue().user_name);
                json.put("created_at", e.getValue().created_at);
                json.put("edited_at", e.getValue().edited_at);
                object.put(e.getKey(), json);
            }
            saveRawBytes(getConfigFile(), object.toString().getBytes());
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void showChooseStorage(Context context, List<StorageConfig> storages, Utilities.Callback<String> whenDone) {
        final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
        final Theme.ResourcesProvider resourcesProvider = lastFragment != null ? lastFragment.getResourceProvider() : null;
        final String[] selected = new String[1];
        final boolean[] sentDone = new boolean[1];

        final BottomSheet.Builder b = new BottomSheet.Builder(context, false, resourcesProvider);

        final LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        final BackupImageView imageView = new BackupImageView(context);
        final TLRPC.User bot = MessagesController.getInstance(account).getUser(bot_id);
        final AvatarDrawable avatarDrawable = new AvatarDrawable();
        avatarDrawable.setInfo(bot);
        imageView.setForUserOrChat(bot, avatarDrawable);
        linearLayout.addView(imageView, LayoutHelper.createLinear(80, 80, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 21, 0, 13));

        TextView textView = TextHelper.makeTextView(context, 20, Theme.key_windowBackgroundWhiteBlackText, true);
        textView.setText(getString(R.string.BotRestoreStorageTitle));
        textView.setGravity(Gravity.CENTER);
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL, 32, 0, 32, 10));

        textView = TextHelper.makeTextView(context, 14, Theme.key_windowBackgroundWhiteBlackText, false);
        textView.setText(AndroidUtilities.replaceTags(formatString(R.string.BotRestoreStorageText, DialogObject.getDialogTitle(bot))));
        textView.setGravity(Gravity.CENTER);
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL, 32, 0, 32, 19));

        TextInfoPrivacyCell separator = new TextInfoPrivacyCell(context, resourcesProvider);
        separator.setBackground(new CombinedDrawable(
            new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider)),
            Theme.getThemedDrawableByKey(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow, resourcesProvider)
        ));
        separator.setFixedSize(12);
        linearLayout.addView(separator, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 12, Gravity.FILL_HORIZONTAL, 0, 0, 0, 0));

        HeaderCell headerCell = new HeaderCell(context, resourcesProvider);
        headerCell.setText(getString(R.string.BotRestoreStorageHeader));
        linearLayout.addView(headerCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL, 0, 0, 0, 0));

        class StorageCell extends FrameLayout {
            private final String id;
            private final RadioButton radioButton;
            private final boolean needDivider;
            public StorageCell(StorageConfig storage, boolean divider) {
                super(context);

                id = storage.storage_id;

                radioButton = new RadioButton(context);
                radioButton.setSize(dp(20));
                radioButton.setColor(Theme.getColor(Theme.key_dialogRadioBackground), Theme.getColor(Theme.key_dialogRadioBackgroundChecked));
                addView(radioButton, LayoutHelper.createFrame(22, 22, Gravity.LEFT | Gravity.CENTER_VERTICAL, 20, 0, 0, 0));

                TextView textView = TextHelper.makeTextView(context, 16, Theme.key_windowBackgroundWhiteBlackText, true);
                textView.setText(storage.user_name);
                addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL, 62, 9, 8, 0));

                textView = TextHelper.makeTextView(context, 14, Theme.key_windowBackgroundWhiteGrayText, false);
                textView.setText(LocaleController.formatString(R.string.BotRestoreStorageCreatedAt, LocaleController.formatString(R.string.formatDateAtTime, LocaleController.formatSmallDateChat(storage.created_at / 1000L), LocaleController.getInstance().getFormatterDay().format(new Date(storage.created_at / 1000L)))));
                addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL, 62, 32, 8, 0));

                setWillNotDraw(!(this.needDivider = divider));
            }

            public void setChecked(boolean checked) {
                radioButton.setChecked(checked, true);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                if (needDivider) {
                    canvas.drawLine(dp(62), getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, Theme.dividerPaint);
                }
            }
        }

        ButtonWithCounterView button = new ButtonWithCounterView(context, resourcesProvider);

        ArrayList<StorageCell> cells = new ArrayList();
        for (int i = 0; i < storages.size(); ++i) {
            final StorageConfig storage = storages.get(i);
            StorageCell cell = new StorageCell(storage, i < storages.size() - 1);
            cell.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), Theme.RIPPLE_MASK_ALL));
            cell.setOnClickListener(v -> {
                selected[0] = storage.storage_id;
                for (StorageCell _cell : cells) {
                    _cell.setChecked(TextUtils.equals(_cell.id, selected[0]));
                }
                button.setEnabled(selected[0] != null);
            });
            linearLayout.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 56));
            cells.add(cell);
        }

        button.setText(getString(R.string.BotRestoreStorageButton), false);
        button.setEnabled(selected[0] != null);
        linearLayout.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.FILL_HORIZONTAL, 8, 8, 8, 4));

        b.setCustomView(linearLayout);

        final BottomSheet sheet = b.create();

        sheet.fixNavigationBar(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
        button.setOnClickListener(v -> {
            if (!sentDone[0] && whenDone != null) {
                sentDone[0] = true;
                whenDone.run(selected[0]);
            }
            sheet.dismiss();
        });
        sheet.setOnDismissListener(di -> {
            if (!sentDone[0] && whenDone != null) {
                sentDone[0] = true;
                whenDone.run(null);
            }
        });
        sheet.show();
    }

}
