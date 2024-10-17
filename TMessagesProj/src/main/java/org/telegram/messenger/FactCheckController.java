package org.telegram.messenger;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.LongSparseArray;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.AlertDialogDecor;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedColor;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextCaption;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;
import java.util.HashMap;

public class FactCheckController {

    private static volatile FactCheckController[] Instance = new FactCheckController[UserConfig.MAX_ACCOUNT_COUNT];
    private static final Object[] lockObjects = new Object[UserConfig.MAX_ACCOUNT_COUNT];
    static {
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            lockObjects[i] = new Object();
        }
    }

    public static FactCheckController getInstance(int num) {
        FactCheckController localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (lockObjects[num]) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new FactCheckController(num);
                }
            }
        }
        return localInstance;
    }

    public final int currentAccount;

    private FactCheckController(int account) {
        currentAccount = account;
    }

    private final LongSparseArray<TLRPC.TL_factCheck> localCache = new LongSparseArray<>();
    private final LongSparseArray<HashMap<Key, Utilities.Callback<TLRPC.TL_factCheck>>> toload = new LongSparseArray<>();
    private final ArrayList<Key> loading = new ArrayList<>();

    public TLRPC.TL_factCheck getFactCheck(MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null) {
            return null;
        }
        if (messageObject.messageOwner.factcheck == null) {
            return null;
        }
        if (!messageObject.messageOwner.factcheck.need_check) {
            if (localCache.get(messageObject.messageOwner.factcheck.hash) == null) {
                localCache.put(messageObject.messageOwner.factcheck.hash, messageObject.messageOwner.factcheck);
                saveToDatabase(messageObject.messageOwner.factcheck);
            }
            return messageObject.messageOwner.factcheck;
        }

        final Key key = Key.of(messageObject);
        if (key == null) return null;
        if (key.messageId < 0) return null;

        TLRPC.TL_factCheck cached = localCache.get(key.hash);
        if (cached != null) {
            return messageObject.messageOwner.factcheck = cached;
        }

        if (loading.contains(key)) {
            return messageObject.messageOwner.factcheck;
        }

        HashMap<Key, Utilities.Callback<TLRPC.TL_factCheck>> msgs = toload.get(key.dialogId);
        if (msgs == null) {
            toload.put(key.dialogId, msgs = new HashMap<>());
        }
        if (!msgs.containsKey(key)) {
            msgs.put(key, factCheck -> {
                localCache.put(key.hash, factCheck);
                messageObject.messageOwner.factcheck = factCheck;
            });
            scheduleLoadMissing();
        }

        return messageObject.messageOwner.factcheck;
    }

    private void scheduleLoadMissing() {
        AndroidUtilities.cancelRunOnUIThread(loadMissingRunnable);
        AndroidUtilities.runOnUIThread(loadMissingRunnable, 80);
    }

    private final Runnable loadMissingRunnable = this::loadMissing;

    private void loadMissing() {
        for (int i = 0; i < this.toload.size(); ++i) {
            long dialogId = this.toload.keyAt(i);

            HashMap<Key, Utilities.Callback<TLRPC.TL_factCheck>> msgs = this.toload.valueAt(i);
            this.toload.removeAt(i);
            i--;

            ArrayList<Key> keys = new ArrayList<>(msgs.keySet());
            loading.addAll(keys);
            getFromDatabase(keys, results -> {
                TLRPC.TL_getFactCheck req = new TLRPC.TL_getFactCheck();
                req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);

                ArrayList<Key> reqKeys = new ArrayList<>();

                int processed = 0;
                for (int k = 0; k < results.size(); ++k) {
                    final Key key = keys.get(k);
                    final TLRPC.TL_factCheck factCheck = results.get(k);
                    if (factCheck == null) {
                        reqKeys.add(key);
                        req.msg_id.add(key.messageId);
                    } else {
                        loading.remove(key);
                        final Utilities.Callback<TLRPC.TL_factCheck> callback = msgs.get(key);
                        if (callback != null) {
                            callback.run(factCheck);
                            processed++;
                        }
                    }
                }
                if (processed > 0) {
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.factCheckLoaded);
                }

                if (!req.msg_id.isEmpty()) {
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                        ArrayList<TLRPC.TL_factCheck> factChecks = new ArrayList<>();
                        if (res instanceof TLRPC.Vector) {
                            ArrayList<Object> objects = ((TLRPC.Vector) res).objects;
                            for (int k = 0; k < objects.size(); ++k) {
                                if (objects.get(k) instanceof TLRPC.TL_factCheck) {
                                    factChecks.add((TLRPC.TL_factCheck) objects.get(k));
                                }
                            }
                        }

                        HashMap<Integer, TLRPC.TL_factCheck> map = new HashMap<>();
                        for (int k = 0; k < Math.min(req.msg_id.size(), factChecks.size()); ++k) {
                            final int messageId = req.msg_id.get(k);
                            final TLRPC.TL_factCheck factCheck = factChecks.get(k);
                            map.put(messageId, factCheck);
                        }

                        int processed2 = 0;
                        for (int k = 0; k < req.msg_id.size(); ++k) {
                            final Key key = reqKeys.get(k);
                            final int messageId = req.msg_id.get(k);
                            final TLRPC.TL_factCheck factCheck = map.get(messageId);
                            final Utilities.Callback<TLRPC.TL_factCheck> callback = msgs.get(key);
                            if (factCheck != null && !factCheck.need_check && callback != null) {
                                callback.run(factCheck);
                                processed2++;
                                loading.remove(key);
                            }
                        }

                        if (processed2 > 0) {
                            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.factCheckLoaded);
                        }
                    }));
                }
            });
        }
        this.toload.clear();
    }

    private static class Key {
        public final long dialogId;
        public final int messageId;
        public final long hash;

        private Key(long did, int mid, long hash) {
            dialogId = did;
            messageId = mid;
            this.hash = hash;
        }

        @Override
        public int hashCode() {
            return Long.hashCode(hash);
        }

        public static Key of(MessageObject msg) {
            if (msg == null) return null;
            if (msg.messageOwner == null) return null;
            if (msg.messageOwner.factcheck == null) return null;
            return new Key(msg.getDialogId(), msg.getId(), msg.messageOwner.factcheck.hash);
        }
    }

    private void getFromDatabase(ArrayList<Key> keys, Utilities.Callback<ArrayList<TLRPC.TL_factCheck>> whenGot) {
        if (whenGot == null) {
            return;
        }
        if (keys == null || keys.isEmpty()) {
            whenGot.run(new ArrayList<>());
            return;
        }
        final MessagesStorage storage = MessagesStorage.getInstance(currentAccount);
        storage.getStorageQueue().postRunnable(() -> {
            ArrayList<TLRPC.TL_factCheck> result = new ArrayList<>();
            SQLiteCursor cursor = null;
            try {
                SQLiteDatabase db = storage.getDatabase();

                ArrayList<Long> hashes = new ArrayList<>();
                for (Key key : keys) {
                    hashes.add(key.hash);
                    result.add(null);
                }

                cursor = db.queryFinalized("SELECT data FROM fact_checks WHERE hash IN (" + TextUtils.join(", ", hashes) + ")");
                while (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    TLRPC.TL_factCheck factCheck = TLRPC.TL_factCheck.TLdeserialize(data, data.readInt32(false), false);
                    if (factCheck == null) {
                        continue;
                    }

                    int index = -1;
                    for (int i = 0; i < keys.size(); ++i) {
                        final Key key = keys.get(i);
                        if (factCheck.hash == key.hash) {
                            index = i;
                        }
                    }
                    if (index >= 0 && index < result.size()) {
                        result.set(index, factCheck);
                    }

                    break;
                }
                cursor.dispose();
                cursor = null;

            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }

            AndroidUtilities.runOnUIThread(() -> {
                whenGot.run(result);
            });
        });
    }

    private void saveToDatabase(TLRPC.TL_factCheck factCheck) {
        if (factCheck == null) return;
        final MessagesStorage storage = MessagesStorage.getInstance(currentAccount);
        storage.getStorageQueue().postRunnable(() -> {
            SQLitePreparedStatement state = null;
            try {
                SQLiteDatabase db = storage.getDatabase();

                state = db.executeFast("REPLACE INTO fact_checks VALUES(?, ?, ?)");
                state.requery();
                state.bindLong(1, factCheck.hash);
                NativeByteBuffer buffer = new NativeByteBuffer(factCheck.getObjectSize());
                factCheck.serializeToStream(buffer);
                state.bindByteBuffer(2, buffer);
                state.bindLong(3, System.currentTimeMillis() + 1000 * 60 * 60 * 24 * 60);
                state.step();
                state.dispose();
                state = null;

            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (state != null) {
                    state.dispose();
                }
            }
        });
        clearExpiredInDatabase();
    }

    private boolean clearedExpiredInDatabase;
    private void clearExpiredInDatabase() {
        if (clearedExpiredInDatabase) return;
        clearedExpiredInDatabase = true;

        final MessagesStorage storage = MessagesStorage.getInstance(currentAccount);
        storage.getStorageQueue().postRunnable(() -> {
            try {
                SQLiteDatabase db = storage.getDatabase();
                db.executeFast("DELETE FROM fact_checks WHERE expires > " + System.currentTimeMillis()).stepThis().dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private static AlertDialog currentDialog;
    public void openFactCheckEditor(Context context, Theme.ResourcesProvider resourcesProvider, MessageObject messageObject, boolean forceNotAdaptive) {
        BaseFragment fragment = LaunchActivity.getLastFragment();
        Activity activity = AndroidUtilities.findActivity(context);
        View currentFocus = activity != null ? activity.getCurrentFocus() : null;
        final boolean isKeyboardVisible = fragment != null && fragment.getFragmentView() instanceof SizeNotifierFrameLayout && ((SizeNotifierFrameLayout) fragment.getFragmentView()).measureKeyboardHeight() > dp(20);
        final boolean adaptive = isKeyboardVisible && !forceNotAdaptive;
        AlertDialog[] dialog = new AlertDialog[1];
        AlertDialog.Builder builder;
        if (adaptive) {
            builder = new AlertDialogDecor.Builder(context, resourcesProvider);
        } else {
            builder = new AlertDialog.Builder(context, resourcesProvider);
        }
        TextView[] positiveButton = new TextView[1];

        final boolean creating = messageObject == null || messageObject.messageOwner == null || messageObject.messageOwner.factcheck == null;
        builder.setTitle(getString(R.string.FactCheckDialog));
        final int MAX_LENGTH = MessagesController.getInstance(currentAccount).factcheckLengthLimit;
        EditTextCaption editText = new EditTextCaption(context, resourcesProvider) {
            AnimatedColor limitColor = new AnimatedColor(this);
            private int limitCount;
            AnimatedTextView.AnimatedTextDrawable limit = new AnimatedTextView.AnimatedTextDrawable(false, true, true); {
                limit.setAnimationProperties(.2f, 0, 160, CubicBezierInterpolator.EASE_OUT_QUINT);
                limit.setTextSize(dp(15.33f));
                limit.setCallback(this);
                limit.setGravity(Gravity.RIGHT);
            }

            @Override
            protected boolean verifyDrawable(@NonNull Drawable who) {
                return who == limit || super.verifyDrawable(who);
            }

            @Override
            protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
                super.onTextChanged(text, start, lengthBefore, lengthAfter);

                if (limit != null) {
                    limitCount = MAX_LENGTH - text.length();
                    limit.cancelAnimation();
                    limit.setText(limitCount > 4 ? "" : "" + limitCount);
                }
            }

            @Override
            protected void extendActionMode(ActionMode actionMode, Menu menu) {
                if (menu.findItem(R.id.menu_bold) != null) {
                    return;
                }
                if (Build.VERSION.SDK_INT >= 23) {
                    menu.removeItem(android.R.id.shareText);
                }
                int order = 6;
                SpannableStringBuilder stringBuilder = new SpannableStringBuilder(getString(R.string.Bold));
                stringBuilder.setSpan(new TypefaceSpan(AndroidUtilities.bold()), 0, stringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                menu.add(R.id.menu_groupbolditalic, R.id.menu_bold, order++, stringBuilder);
                stringBuilder = new SpannableStringBuilder(getString(R.string.Italic));
                stringBuilder.setSpan(new TypefaceSpan(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM_ITALIC)), 0, stringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                menu.add(R.id.menu_groupbolditalic, R.id.menu_italic, order++, stringBuilder);
                menu.add(R.id.menu_groupbolditalic, R.id.menu_link, order++, getString(R.string.CreateLink));
                menu.add(R.id.menu_groupbolditalic, R.id.menu_regular, order++, getString(R.string.Regular));
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);

                limit.setTextColor(limitColor.set(Theme.getColor(limitCount < 0 ? Theme.key_text_RedRegular : Theme.key_dialogSearchHint, resourcesProvider)));
                limit.setBounds(getScrollX(), 0, getScrollX() + getWidth(), getHeight());
                limit.draw(canvas);
            }
        };
        editText.lineYFix = true;
        editText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    String text = editText.getText().toString();
                    if (text.length() > MAX_LENGTH) {
                        AndroidUtilities.shakeView(editText);
                        return true;
                    }

                    TLRPC.TL_textWithEntities textObj = new TLRPC.TL_textWithEntities();
                    CharSequence[] message = new CharSequence[] { editText.getText() };
                    textObj.entities = MediaDataController.getInstance(currentAccount).getEntities(message, true);
                    textObj.text = message[0] == null ? "" : message[0].toString();
                    applyFactCheck(messageObject, textObj, creating);

                    if (dialog[0] != null) {
                        dialog[0].dismiss();
                    }
                    if (dialog[0] == currentDialog) {
                        currentDialog = null;
                    }
                    if (currentFocus != null) {
                        currentFocus.requestFocus();
                    }
                    return true;
                }
                return false;
            }
        });
        MediaDataController.getInstance(currentAccount).fetchNewEmojiKeywords(AndroidUtilities.getCurrentKeyboardLanguage(), true);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        editText.setHintColor(Theme.getColor(Theme.key_groupcreate_hintText, resourcesProvider));
        editText.setHintText(getString(R.string.FactCheckPlaceholder));
        editText.setFocusable(true);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editText.setLineColors(Theme.getColor(Theme.key_windowBackgroundWhiteInputField, resourcesProvider), Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated, resourcesProvider), Theme.getColor(Theme.key_text_RedRegular, resourcesProvider));
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editText.setBackgroundDrawable(null);
        editText.setPadding(0, dp(6), 0, dp(6));

        TLRPC.TL_factCheck factCheck = messageObject.getFactCheck();
        if (factCheck != null && factCheck.text != null) {
            CharSequence text = SpannableStringBuilder.valueOf(factCheck.text.text);
            MessageObject.addEntitiesToText(text, factCheck.text.entities, false, true, false, false);
            editText.setText(text);
        }

        editText.addTextChangedListener(new TextWatcher() {
            boolean ignoreTextChange;
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (ignoreTextChange) {
                    return;
                }
                if (s.length() > MAX_LENGTH) {
                    ignoreTextChange = true;
                    s.delete(MAX_LENGTH, s.length());
                    AndroidUtilities.shakeView(editText);
                    editText.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                    ignoreTextChange = false;
                }

                if (positiveButton[0] != null) {
                    final boolean showDone = s.length() > 0 || factCheck == null;
                    positiveButton[0].setText(getString(showDone ? R.string.Done : R.string.Remove));
                    positiveButton[0].setTextColor(Theme.getColor(showDone ? Theme.key_dialogButton : Theme.key_text_RedBold));
                }
            }
        });

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);

        container.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 0, 24, 10));
        builder.makeCustomMaxHeight();
        builder.setView(container);
        builder.setWidth(dp(292));

        builder.setPositiveButton(getString(R.string.Done), (dialogInterface, i) -> {
            String text = editText.getText().toString();
            if (text.length() > MAX_LENGTH) {
                AndroidUtilities.shakeView(editText);
                return;
            }

            TLRPC.TL_textWithEntities textObj = new TLRPC.TL_textWithEntities();
            CharSequence[] message = new CharSequence[] { editText.getText() };
            textObj.entities = MediaDataController.getInstance(currentAccount).getEntities(message, true);
            textObj.text = message[0] == null ? "" : message[0].toString();
            applyFactCheck(messageObject, textObj, creating);

            dialogInterface.dismiss();
        });
        builder.setNegativeButton(getString("Cancel", R.string.Cancel), (dialogInterface, i) -> {
            dialogInterface.dismiss();
        });
        if (adaptive) {
            dialog[0] = currentDialog = builder.create();
            currentDialog.setOnDismissListener(d -> {
                currentDialog = null;
                currentFocus.requestFocus();
            });
            currentDialog.setOnShowListener(d -> {
                editText.requestFocus();
                AndroidUtilities.showKeyboard(editText);
            });
            currentDialog.showDelayed(250);
        } else {
            dialog[0] = builder.create();
            dialog[0].setOnDismissListener(d -> {
                AndroidUtilities.hideKeyboard(editText);
            });
            dialog[0].setOnShowListener(d -> {
                editText.requestFocus();
                AndroidUtilities.showKeyboard(editText);
            });
            dialog[0].show();
        }
        dialog[0].setDismissDialogByButtons(false);
        View button = dialog[0].getButton(DialogInterface.BUTTON_POSITIVE);
        if (button instanceof TextView) {
            positiveButton[0] = (TextView) button;
        }
        editText.setSelection(editText.getText().length());
    }

    public void applyFactCheck(MessageObject messageObject, TLRPC.TL_textWithEntities text, boolean created) {
        TLObject req;

        if (text == null || TextUtils.isEmpty(text.text)) {
            if (created) {
                return;
            }
            TLRPC.TL_deleteFactCheck r = new TLRPC.TL_deleteFactCheck();
            r.peer = MessagesController.getInstance(currentAccount).getInputPeer(messageObject.getDialogId());
            r.msg_id = messageObject.getId();
            req = r;
        } else {
            TLRPC.TL_editFactCheck r = new TLRPC.TL_editFactCheck();
            r.peer = MessagesController.getInstance(currentAccount).getInputPeer(messageObject.getDialogId());
            r.msg_id = messageObject.getId();
            r.text = text;
            req = r;
        }

        final Context context = LaunchActivity.instance != null ? LaunchActivity.instance : ApplicationLoader.applicationContext;
        final AlertDialog progressDialog = new AlertDialog(context, AlertDialog.ALERT_TYPE_SPINNER);
        progressDialog.showDelayed(320);
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (res instanceof TLRPC.Updates) {
                MessagesController.getInstance(currentAccount).processUpdates((TLRPC.Updates) res, false);

                BaseFragment fragment = LaunchActivity.getSafeLastFragment();
                if (fragment != null) {
                    final boolean deleted = text == null || TextUtils.isEmpty(text.text);
                    if (deleted || !created) {
                        BulletinFactory.of(fragment).createSimpleBulletin(deleted ? R.raw.ic_delete : R.raw.contact_check, getString(deleted ? R.string.FactCheckDeleted : R.string.FactCheckEdited)).show();
                    }
                }
            }
            progressDialog.dismiss();
        }));
    }

}
