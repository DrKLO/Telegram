package org.telegram.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell2;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.Switch;

public class SessionBottomSheet extends BottomSheet {

    TLRPC.TL_authorization session;
    BaseFragment parentFragment;
    RLottieImageView imageView;

    public SessionBottomSheet(BaseFragment fragment, TLRPC.TL_authorization session, boolean isCurrentSession, Callback callback) {
        super(fragment.getParentActivity(), false);
        setOpenNoDelay(true);
        Context context = fragment.getParentActivity();
        this.session = session;
        this.parentFragment = fragment;

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        imageView = new RLottieImageView(context);
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!imageView.isPlaying() && imageView.getAnimatedDrawable() != null) {
                    imageView.getAnimatedDrawable().setCurrentFrame(40);
                    imageView.playAnimation();
                }
            }
        });
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        linearLayout.addView(imageView, LayoutHelper.createLinear(70, 70, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 0));

        TextView nameView = new TextView(context);
        nameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        nameView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        nameView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        nameView.setGravity(Gravity.CENTER);
        linearLayout.addView(nameView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 21, 12, 21, 0));

        TextView timeView = new TextView(context);
        timeView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        timeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        timeView.setGravity(Gravity.CENTER);
        linearLayout.addView(timeView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 21, 4, 21, 21));


        String timeText;
        if ((session.flags & 1) != 0) {
            timeText = LocaleController.getString("Online", R.string.Online);
        } else {
            timeText = LocaleController.formatDateTime(session.date_active);
        }
        timeView.setText(timeText);

        StringBuilder stringBuilder = new StringBuilder();
        if (session.device_model.length() != 0) {
            stringBuilder.append(session.device_model);
        }
        if (stringBuilder.length() == 0) {
            if (session.platform.length() != 0) {
                stringBuilder.append(session.platform);
            }
            if (session.system_version.length() != 0) {
                if (session.platform.length() != 0) {
                    stringBuilder.append(" ");
                }
                stringBuilder.append(session.system_version);
            }
        }
        nameView.setText(stringBuilder);
        setAnimation(session, imageView);

        ItemView applicationItemView = new ItemView(context, false);
        stringBuilder = new StringBuilder();
        stringBuilder.append(session.app_name);
        stringBuilder.append(" ").append(session.app_version);
        applicationItemView.valueText.setText(stringBuilder);
        Drawable drawable = ContextCompat.getDrawable(context, R.drawable.menu_devices).mutate();
        drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.SRC_IN));
        applicationItemView.iconView.setImageDrawable(drawable);
        applicationItemView.descriptionText.setText(LocaleController.getString("Application", R.string.Application));

        linearLayout.addView(applicationItemView);

        ItemView prevItem = applicationItemView;
        if (session.country.length() != 0) {
            ItemView locationItemView = new ItemView(context, false);
            locationItemView.valueText.setText(session.country);
            drawable = ContextCompat.getDrawable(context, R.drawable.menu_location).mutate();
            drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.SRC_IN));
            locationItemView.iconView.setImageDrawable(drawable);
            locationItemView.descriptionText.setText(LocaleController.getString("Location", R.string.Location));

            locationItemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    copyText(session.country);
                }
            });
            locationItemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    copyText(session.country);
                    return true;
                }
            });
            locationItemView.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 2));

            linearLayout.addView(locationItemView);
            if (prevItem != null) {
                prevItem.needDivider = true;
            }
            prevItem = locationItemView;
        }

        if (session.ip.length() != 0) {
            ItemView locationItemView = new ItemView(context, false);
            locationItemView.valueText.setText(session.ip);
            drawable = ContextCompat.getDrawable(context, R.drawable.menu_language).mutate();
            drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.SRC_IN));
            locationItemView.iconView.setImageDrawable(drawable);
            locationItemView.descriptionText.setText(LocaleController.getString("IpAddress", R.string.IpAddress));


            locationItemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    copyText(session.ip);
                }
            });
            locationItemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    copyText(session.country);
                    return true;
                }
            });

            locationItemView.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 2));

            linearLayout.addView(locationItemView);

            if (prevItem != null) {
                prevItem.needDivider = true;
            }
            prevItem = locationItemView;
        }

        if (secretChatsEnabled(session)) {
            ItemView acceptSecretChats = new ItemView(context, true);
            acceptSecretChats.valueText.setText(LocaleController.getString("AcceptSecretChats", R.string.AcceptSecretChats));
            drawable = ContextCompat.getDrawable(context, R.drawable.menu_secret).mutate();
            drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.SRC_IN));
            acceptSecretChats.iconView.setImageDrawable(drawable);
            acceptSecretChats.switchView.setChecked(!session.encrypted_requests_disabled, false);
            acceptSecretChats.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 7));
            acceptSecretChats.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    acceptSecretChats.switchView.setChecked(!acceptSecretChats.switchView.isChecked(), true);
                    session.encrypted_requests_disabled = !acceptSecretChats.switchView.isChecked();
                    uploadSessionSettings();
                }
            });

            if (prevItem != null) {
                prevItem.needDivider = true;
            }
            acceptSecretChats.descriptionText.setText(LocaleController.getString("AcceptSecretChatsDescription", R.string.AcceptSecretChatsDescription));
            linearLayout.addView(acceptSecretChats);
            prevItem = acceptSecretChats;
        }

        ItemView acceptCalls = new ItemView(context, true);
        acceptCalls.valueText.setText(LocaleController.getString("AcceptCalls", R.string.AcceptCalls));
        drawable = ContextCompat.getDrawable(context, R.drawable.menu_calls).mutate();
        drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.SRC_IN));
        acceptCalls.iconView.setImageDrawable(drawable);
        acceptCalls.switchView.setChecked(!session.call_requests_disabled, false);
        acceptCalls.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 7));
        acceptCalls.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                acceptCalls.switchView.setChecked(!acceptCalls.switchView.isChecked(), true);
                session.call_requests_disabled = !acceptCalls.switchView.isChecked();
                uploadSessionSettings();
            }
        });

        if (prevItem != null) {
            prevItem.needDivider = true;
        }
        acceptCalls.descriptionText.setText(LocaleController.getString("AcceptCallsChatsDescription", R.string.AcceptCallsChatsDescription));
        linearLayout.addView(acceptCalls);

        if (!isCurrentSession) {
            TextView buttonTextView = new TextView(context);
            buttonTextView.setPadding(AndroidUtilities.dp(34), 0, AndroidUtilities.dp(34), 0);
            buttonTextView.setGravity(Gravity.CENTER);
            buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            buttonTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            buttonTextView.setText(LocaleController.getString("TerminateSession", R.string.TerminateSession));

            buttonTextView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
            buttonTextView.setBackgroundDrawable(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(6), Theme.getColor(Theme.key_chat_attachAudioBackground), ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_windowBackgroundWhite), 120)));

            linearLayout.addView(buttonTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, 0, 16, 15, 16, 16));

            buttonTextView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(parentFragment.getParentActivity());
                    final boolean[] param = new boolean[1];
                    String buttonText;
                    builder.setMessage(LocaleController.getString("TerminateSessionText", R.string.TerminateSessionText));
                    builder.setTitle(LocaleController.getString("AreYouSureSessionTitle", R.string.AreYouSureSessionTitle));
                    buttonText = LocaleController.getString("Terminate", R.string.Terminate);

                    builder.setPositiveButton(buttonText, (dialogInterface, option) -> {
                        callback.onSessionTerminated(session);
                        dismiss();
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    AlertDialog alertDialog = builder.create();
                    fragment.showDialog(alertDialog);
                    TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                    if (button != null) {
                        button.setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
                    }
                }
            });
        }

        ScrollView scrollView = new ScrollView(context);
        scrollView.addView(linearLayout);
        setCustomView(scrollView);
    }

    private boolean secretChatsEnabled(TLRPC.TL_authorization session) {
        if (session.api_id == 2040 || session.api_id == 2496) {
            return false;
        }
        return true;
    }

    private void uploadSessionSettings() {
        TLRPC.TL_account_changeAuthorizationSettings req = new TLRPC.TL_account_changeAuthorizationSettings();
        req.encrypted_requests_disabled = session.encrypted_requests_disabled;
        req.call_requests_disabled = session.call_requests_disabled;
        req.flags = 1 | 2;
        req.hash = session.hash;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {

        });
    }

    private void copyText(String text) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setItems(new CharSequence[]{LocaleController.getString("Copy", R.string.Copy)}, (dialogInterface, i) -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("label", text);
            clipboard.setPrimaryClip(clip);
            BulletinFactory.of(getContainer(), null).createCopyBulletin(LocaleController.getString("TextCopied", R.string.TextCopied)).show();
        });
        builder.show();

    }

    private void setAnimation(TLRPC.TL_authorization session, RLottieImageView imageView) {
        String platform = session.platform.toLowerCase();
        if (platform.isEmpty()) {
            platform = session.system_version.toLowerCase();
        }
        String deviceModel = session.device_model.toLowerCase();
        int iconId;
        String colorKey;
        boolean animation = true;


        if (deviceModel.contains("safari")) {
            iconId = R.raw.safari_30;
            colorKey = Theme.key_avatar_backgroundPink;
        } else if (deviceModel.contains("edge")) {
            iconId = R.raw.edge_30;
            colorKey = Theme.key_avatar_backgroundPink;
        } else if (deviceModel.contains("chrome")) {
            iconId = R.raw.chrome_30;
            colorKey = Theme.key_avatar_backgroundPink;
        } else if (deviceModel.contains("opera") || deviceModel.contains("firefox") || deviceModel.contains("vivaldi")) {
            animation = false;
            if (deviceModel.contains("opera")) {
                iconId = R.drawable.device_web_opera;
            } else if (deviceModel.contains("firefox")) {
                iconId = R.drawable.device_web_firefox;
            } else {
                iconId = R.drawable.device_web_other;
            }
            colorKey = Theme.key_avatar_backgroundPink;
        } else if (platform.contains("ubuntu")) {
            iconId = R.raw.ubuntu_30;
            colorKey = Theme.key_avatar_backgroundBlue;
        } else if (platform.contains("ios")) {
            iconId = deviceModel.contains("ipad") ? R.raw.ipad_30 : R.raw.iphone_30;
            colorKey = Theme.key_avatar_backgroundBlue;
        } else if (platform.contains("windows")) {
            iconId = R.raw.windows_30;
            colorKey = Theme.key_avatar_backgroundCyan;
        } else if (platform.contains("macos")) {
            iconId = R.raw.mac_30;
            colorKey = Theme.key_avatar_backgroundCyan;
        } else if (platform.contains("android")) {
            iconId = R.raw.android_30;
            colorKey = Theme.key_avatar_backgroundGreen;
        } else {
            if (session.app_name.toLowerCase().contains("desktop")) {
                iconId = R.raw.windows_30;
                colorKey = Theme.key_avatar_backgroundCyan;
            } else {
                iconId = R.raw.chrome_30;
                colorKey = Theme.key_avatar_backgroundPink;
            }
        }

        imageView.setBackground(Theme.createCircleDrawable(AndroidUtilities.dp(42), Theme.getColor(colorKey)));
        if (animation) {
            int[] colors = new int[]{0x000000, Theme.getColor(colorKey)};
            imageView.setAnimation(iconId, 50, 50, colors);
        } else {
            imageView.setImageDrawable(ContextCompat.getDrawable(getContext(), iconId));
        }
    }

    private static class ItemView extends FrameLayout {

        ImageView iconView;
        TextView valueText;
        TextView descriptionText;
        Switch switchView;
        boolean needDivider = false;

        public ItemView(Context context, boolean needSwitch) {
            super(context);
            iconView = new ImageView(context);
            addView(iconView, LayoutHelper.createFrame(28, 28, 0, 16, 8, 0, 0));

            LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(LinearLayout.VERTICAL);
            addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 64, 4, 0, 4));

            valueText = new TextView(context);
            valueText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            valueText.setGravity(Gravity.LEFT);
            valueText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            linearLayout.addView(valueText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, needSwitch ? 46 : 0, 0));

            descriptionText = new TextView(context);
            descriptionText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            descriptionText.setGravity(Gravity.LEFT);
            descriptionText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            linearLayout.addView(descriptionText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 4, needSwitch ? 46 : 0, 0));
            setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4));

            if (needSwitch) {
                switchView = new Switch(context);
                switchView.setDrawIconType(1);
                addView(switchView, LayoutHelper.createFrame(37, 40, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 21, 0, 21, 0));
            }
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            if (needDivider) {
                canvas.drawRect(AndroidUtilities.dp(64), getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight(), Theme.dividerPaint);
            }
        }
    }

    public interface Callback {
        void onSessionTerminated(TLRPC.TL_authorization session);
    }

    @Override
    public void show() {
        super.show();
        imageView.playAnimation();
    }
}
