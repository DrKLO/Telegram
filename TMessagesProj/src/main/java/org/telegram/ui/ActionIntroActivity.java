/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.IntDef;
import androidx.core.graphics.ColorUtils;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.LocationController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.ShareLocationDrawable;
import org.telegram.ui.Components.URLSpanNoUnderline;
import org.telegram.ui.Components.voip.CellFlickerDrawable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;

@TargetApi(23)
public class ActionIntroActivity extends BaseFragment implements LocationController.LocationFetchCallback {

    private RLottieImageView imageView;
    private TextView buttonTextView;
    private TextView subtitleTextView;
    private TextView titleTextView;
    private TextView descriptionText;
    private LinearLayout descriptionLayout;
    private TextView[] descriptionLines = new TextView[6];
    private TextView descriptionText2;
    private Drawable drawable1;
    private Drawable drawable2;

    private int[] colors;

    @ActionType
    private int currentType;
    private boolean flickerButton;

    private String currentGroupCreateAddress;
    private String currentGroupCreateDisplayAddress;
    private Location currentGroupCreateLocation;
    private boolean showingAsBottomSheet;

    private ActionIntroQRLoginDelegate qrLoginDelegate;

    public static final int ACTION_TYPE_CHANNEL_CREATE = 0;
    public static final int ACTION_TYPE_CHANGE_PHONE_NUMBER = 3;
    public static final int ACTION_TYPE_QR_LOGIN = 5;
    public static final int ACTION_TYPE_SET_PASSCODE = 6;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            ACTION_TYPE_CHANNEL_CREATE,
            ACTION_TYPE_CHANGE_PHONE_NUMBER,
            ACTION_TYPE_QR_LOGIN,
            ACTION_TYPE_SET_PASSCODE
    })
    public @interface ActionType {}

    public static final int CAMERA_PERMISSION_REQUEST_CODE = 34;

    public interface ActionIntroQRLoginDelegate {
        void didFindQRCode(String code);
    }

    public ActionIntroActivity(@ActionType int type) {
        super();
        currentType = type;
    }

    @Override
    public View createView(Context context) {
        if (actionBar != null) {
            actionBar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            actionBar.setItemsColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2), false);
            actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_actionBarWhiteSelector), false);
            actionBar.setCastShadows(false);
            actionBar.setAddToContainer(false);
            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) {
                        finishFragment();
                    }
                }
            });
        }

        fragmentView = new ViewGroup(context) {

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int width = MeasureSpec.getSize(widthMeasureSpec);
                int height = MeasureSpec.getSize(heightMeasureSpec);

                if (actionBar != null) {
                    actionBar.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightMeasureSpec);
                }
                switch (currentType) {
                    case ACTION_TYPE_CHANNEL_CREATE: {
                        if (width > height) {
                            imageView.measure(MeasureSpec.makeMeasureSpec((int) (width * 0.45f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((int) (height * 0.68f), MeasureSpec.EXACTLY));
                            titleTextView.measure(MeasureSpec.makeMeasureSpec((int) (width * 0.6f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED));
                            descriptionText.measure(MeasureSpec.makeMeasureSpec((int) (width * 0.6f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED));
                            buttonTextView.measure(MeasureSpec.makeMeasureSpec((int) (width * 0.6f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(42), MeasureSpec.EXACTLY));
                        } else {
                            imageView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((int) (height * 0.399f), MeasureSpec.EXACTLY));
                            titleTextView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED));
                            descriptionText.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED));
                            buttonTextView.measure(MeasureSpec.makeMeasureSpec(width - AndroidUtilities.dp(72), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(50), MeasureSpec.EXACTLY));
                        }
                        break;
                    }
                    case ACTION_TYPE_QR_LOGIN: {
                        if (showingAsBottomSheet) {
                            imageView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((int) (height * 0.32f), MeasureSpec.EXACTLY));
                            titleTextView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED));
                            descriptionLayout.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED));
                            buttonTextView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(42), MeasureSpec.EXACTLY));
                            height = imageView.getMeasuredHeight() + titleTextView.getMeasuredHeight() + AndroidUtilities.dp(20) + titleTextView.getMeasuredHeight() + descriptionLayout.getMeasuredHeight() + buttonTextView.getMeasuredHeight();
                        } else if (width > height) {
                            imageView.measure(MeasureSpec.makeMeasureSpec((int) (width * 0.45f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((int) (height * 0.68f), MeasureSpec.EXACTLY));
                            titleTextView.measure(MeasureSpec.makeMeasureSpec((int) (width * 0.6f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED));
                            descriptionLayout.measure(MeasureSpec.makeMeasureSpec((int) (width * 0.6f), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED));
                            buttonTextView.measure(MeasureSpec.makeMeasureSpec((int) (width * 0.6f), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(42), MeasureSpec.EXACTLY));
                        } else {
                            imageView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((int) (height * 0.399f), MeasureSpec.EXACTLY));
                            titleTextView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED));
                            descriptionLayout.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED));
                            buttonTextView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(42), MeasureSpec.EXACTLY));
                        }
                        break;
                    }
                    case ACTION_TYPE_SET_PASSCODE: {
                        if (currentType == ACTION_TYPE_SET_PASSCODE) {
                            imageView.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(140), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(140), MeasureSpec.EXACTLY));
                        } else {
                            imageView.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(100), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(100), MeasureSpec.EXACTLY));
                        }
                        if (width > height) {
                            titleTextView.measure(MeasureSpec.makeMeasureSpec((int) (width * 0.6f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED));
                            descriptionText.measure(MeasureSpec.makeMeasureSpec((int) (width * 0.6f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED));
                            buttonTextView.measure(MeasureSpec.makeMeasureSpec((int) (width * 0.6f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(42), MeasureSpec.EXACTLY));
                        } else {
                            titleTextView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED));
                            descriptionText.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED));
                            if (currentType == ACTION_TYPE_SET_PASSCODE) {
                                buttonTextView.measure(MeasureSpec.makeMeasureSpec(width - AndroidUtilities.dp(24 * 2), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(50), MeasureSpec.EXACTLY));
                            } else {
                                buttonTextView.measure(MeasureSpec.makeMeasureSpec(width - AndroidUtilities.dp(72), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(50), MeasureSpec.EXACTLY));
                            }
                        }
                        break;
                    }
                    case ACTION_TYPE_CHANGE_PHONE_NUMBER: {
                        imageView.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(150), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(150), MeasureSpec.EXACTLY));

                        if (width > height) {
                            subtitleTextView.measure(MeasureSpec.makeMeasureSpec((int) (width * 0.45f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED));
                            titleTextView.measure(MeasureSpec.makeMeasureSpec((int) (width * 0.6f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED));
                            descriptionText.measure(MeasureSpec.makeMeasureSpec((int) (width * 0.6f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED));
                            buttonTextView.measure(MeasureSpec.makeMeasureSpec((int) (width * 0.6f), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(42), MeasureSpec.EXACTLY));
                        } else {
                            titleTextView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED));
                            descriptionText.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED));
                            subtitleTextView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED));
                            buttonTextView.measure(MeasureSpec.makeMeasureSpec(width - AndroidUtilities.dp(24 * 2), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(50), MeasureSpec.EXACTLY));
                        }
                        break;
                    }
                }

                setMeasuredDimension(width, height);
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                if (actionBar != null) {
                    actionBar.layout(0, 0, r, actionBar.getMeasuredHeight());
                }

                int width = r - l;
                int height = b - t;

                switch (currentType) {
                    case ACTION_TYPE_CHANNEL_CREATE: {
                        if (r > b) {
                            int y = (height - imageView.getMeasuredHeight()) / 2;
                            imageView.layout(0, y, imageView.getMeasuredWidth(), y + imageView.getMeasuredHeight());
                            int x = (int) (width * 0.4f);
                            y = (int) (height * 0.22f);
                            titleTextView.layout(x, y, x + titleTextView.getMeasuredWidth(), y + titleTextView.getMeasuredHeight());
                            x = (int) (width * 0.4f);
                            y = (int) (height * 0.39f);
                            descriptionText.layout(x, y, x + descriptionText.getMeasuredWidth(), y + descriptionText.getMeasuredHeight());
                            x = (int) (width * 0.4f + (width * 0.6f - buttonTextView.getMeasuredWidth()) / 2);
                            y = (int) (height * 0.69f);
                            buttonTextView.layout(x, y, x + buttonTextView.getMeasuredWidth(), y + buttonTextView.getMeasuredHeight());
                        } else {
                            int y = (int) (height * 0.188f);
                            imageView.layout(0, y, imageView.getMeasuredWidth(), y + imageView.getMeasuredHeight());
                            y = (int) (height * 0.651f);
                            titleTextView.layout(0, y, titleTextView.getMeasuredWidth(), y + titleTextView.getMeasuredHeight());
                            y = (int) (height * 0.731f);
                            descriptionText.layout(0, y, descriptionText.getMeasuredWidth(), y + descriptionText.getMeasuredHeight());
                            int x = (width - buttonTextView.getMeasuredWidth()) / 2;
                            y = (int) (height * 0.853f);
                            buttonTextView.layout(x, y, x + buttonTextView.getMeasuredWidth(), y + buttonTextView.getMeasuredHeight());
                        }
                        break;
                    }
                    case ACTION_TYPE_QR_LOGIN: {
                        if (showingAsBottomSheet) {
                            int y;

                            y = 0;
                            imageView.layout(0, y, imageView.getMeasuredWidth(), y + imageView.getMeasuredHeight());
                            y = (int) (height * 0.403f);
                            titleTextView.layout(0, y, titleTextView.getMeasuredWidth(), y + titleTextView.getMeasuredHeight());
                            y = (int) (height * 0.631f);

                            int x = (getMeasuredWidth() - descriptionLayout.getMeasuredWidth()) / 2;
                            descriptionLayout.layout(x, y, x + descriptionLayout.getMeasuredWidth(), y + descriptionLayout.getMeasuredHeight());
                            x = (width - buttonTextView.getMeasuredWidth()) / 2;
                            y = (int) (height * 0.853f);
                            buttonTextView.layout(x, y, x + buttonTextView.getMeasuredWidth(), y + buttonTextView.getMeasuredHeight());
                        } else if (r > b) {
                            int y = (height - imageView.getMeasuredHeight()) / 2;
                            imageView.layout(0, y, imageView.getMeasuredWidth(), y + imageView.getMeasuredHeight());
                            int x = (int) (width * 0.4f);
                            y = (int) (height * 0.08f);
                            titleTextView.layout(x, y, x + titleTextView.getMeasuredWidth(), y + titleTextView.getMeasuredHeight());
                            x = (int) (width * 0.4f + (width * 0.6f - descriptionLayout.getMeasuredWidth()) / 2);
                            y = (int) (height * 0.25f);
                            descriptionLayout.layout(x, y, x + descriptionLayout.getMeasuredWidth(), y + descriptionLayout.getMeasuredHeight());
                            x = (int) (width * 0.4f + (width * 0.6f - buttonTextView.getMeasuredWidth()) / 2);
                            y = (int) (height * 0.78f);
                            buttonTextView.layout(x, y, x + buttonTextView.getMeasuredWidth(), y + buttonTextView.getMeasuredHeight());
                        } else {
                            int y;
                            if (AndroidUtilities.displaySize.y < 1800) {
                                y = (int) (height * 0.06f);
                                imageView.layout(0, y, imageView.getMeasuredWidth(), y + imageView.getMeasuredHeight());
                                y = (int) (height * 0.463f);
                                titleTextView.layout(0, y, titleTextView.getMeasuredWidth(), y + titleTextView.getMeasuredHeight());
                                y = (int) (height * 0.543f);
                            } else {
                                y = (int) (height * 0.148f);
                                imageView.layout(0, y, imageView.getMeasuredWidth(), y + imageView.getMeasuredHeight());
                                y = (int) (height * 0.551f);
                                titleTextView.layout(0, y, titleTextView.getMeasuredWidth(), y + titleTextView.getMeasuredHeight());
                                y = (int) (height * 0.631f);
                            }
                            int x = (getMeasuredWidth() - descriptionLayout.getMeasuredWidth()) / 2;
                            descriptionLayout.layout(x, y, x + descriptionLayout.getMeasuredWidth(), y + descriptionLayout.getMeasuredHeight());
                            x = (width - buttonTextView.getMeasuredWidth()) / 2;
                            y = (int) (height * 0.853f);
                            buttonTextView.layout(x, y, x + buttonTextView.getMeasuredWidth(), y + buttonTextView.getMeasuredHeight());
                        }
                        break;
                    }
                    case ACTION_TYPE_SET_PASSCODE: {
                        if (r > b) {
                            int y = (height - imageView.getMeasuredHeight()) / 2;
                            int x = (int) (width * 0.5f - imageView.getMeasuredWidth()) / 2;
                            imageView.layout(x, y, x + imageView.getMeasuredWidth(), y + imageView.getMeasuredHeight());
                            x = (int) (width * 0.4f);
                            y = (int) (height * 0.14f);
                            titleTextView.layout(x, y, x + titleTextView.getMeasuredWidth(), y + titleTextView.getMeasuredHeight());
                            x = (int) (width * 0.4f);
                            y = (int) (height * 0.31f);
                            descriptionText.layout(x, y, x + descriptionText.getMeasuredWidth(), y + descriptionText.getMeasuredHeight());
                            x = (int) (width * 0.4f + (width * 0.6f - buttonTextView.getMeasuredWidth()) / 2);
                            y = (int) (height * 0.78f);
                            buttonTextView.layout(x, y, x + buttonTextView.getMeasuredWidth(), y + buttonTextView.getMeasuredHeight());
                        } else {
                            int y = (int) (height * 0.3f);
                            int x = (width - imageView.getMeasuredWidth()) / 2;
                            imageView.layout(x, y, x + imageView.getMeasuredWidth(), y + imageView.getMeasuredHeight());
                            y += imageView.getMeasuredHeight() + AndroidUtilities.dp(24);
                            titleTextView.layout(0, y, titleTextView.getMeasuredWidth(), y + titleTextView.getMeasuredHeight());
                            y += titleTextView.getTextSize() + AndroidUtilities.dp(16);
                            descriptionText.layout(0, y, descriptionText.getMeasuredWidth(), y + descriptionText.getMeasuredHeight());
                            x = (width - buttonTextView.getMeasuredWidth()) / 2;
                            y = height - buttonTextView.getMeasuredHeight() - AndroidUtilities.dp(48);
                            buttonTextView.layout(x, y, x + buttonTextView.getMeasuredWidth(), y + buttonTextView.getMeasuredHeight());
                        }
                        break;
                    }
                    case ACTION_TYPE_CHANGE_PHONE_NUMBER: {
                        if (r > b) {
                            int y = (int) (height * 0.95f - imageView.getMeasuredHeight()) / 2;
                            int x = (int) (getWidth() * 0.35f - imageView.getMeasuredWidth());
                            imageView.layout(x, y, x + imageView.getMeasuredWidth(), y + imageView.getMeasuredHeight());
                            x = (int) (width * 0.4f);
                            y = (int) (height * 0.12f);
                            titleTextView.layout(x, y, x + titleTextView.getMeasuredWidth(), y + titleTextView.getMeasuredHeight());
                            x = (int) (width * 0.4f);
                            y = (int) (height * 0.24f);
                            descriptionText.layout(x, y, x + descriptionText.getMeasuredWidth(), y + descriptionText.getMeasuredHeight());
                            x = (int) (width * 0.4f + (width * 0.6f - buttonTextView.getMeasuredWidth()) / 2);
                            y = (int) (height * 0.8f);
                            buttonTextView.layout(x, y, x + buttonTextView.getMeasuredWidth(), y + buttonTextView.getMeasuredHeight());

                            x = (int) (width * 0.4f + (width * 0.6f - subtitleTextView.getMeasuredWidth()) / 2);
                            y -= subtitleTextView.getMeasuredHeight() + AndroidUtilities.dp(16);
                            subtitleTextView.layout(x, y, x + subtitleTextView.getMeasuredWidth(), y + subtitleTextView.getMeasuredHeight());
                        } else {
                            int y = (int) (height * 0.3f);
                            int x = (width - imageView.getMeasuredWidth()) / 2;
                            imageView.layout(x, y, x + imageView.getMeasuredWidth(), y + imageView.getMeasuredHeight());
                            y += imageView.getMeasuredHeight() + AndroidUtilities.dp(24);
                            titleTextView.layout(0, y, titleTextView.getMeasuredWidth(), y + titleTextView.getMeasuredHeight());
                            y += titleTextView.getTextSize() + AndroidUtilities.dp(16);
                            descriptionText.layout(0, y, descriptionText.getMeasuredWidth(), y + descriptionText.getMeasuredHeight());
                            x = (width - buttonTextView.getMeasuredWidth()) / 2;
                            y = height - buttonTextView.getMeasuredHeight() - AndroidUtilities.dp(48);
                            buttonTextView.layout(x, y, x + buttonTextView.getMeasuredWidth(), y + buttonTextView.getMeasuredHeight());

                            x = (width - subtitleTextView.getMeasuredWidth()) / 2;
                            y -= subtitleTextView.getMeasuredHeight() + AndroidUtilities.dp(32);
                            subtitleTextView.layout(x, y, x + subtitleTextView.getMeasuredWidth(), y + subtitleTextView.getMeasuredHeight());
                        }
                        break;
                    }
                }
            }
        };
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        ViewGroup viewGroup = (ViewGroup) fragmentView;
        viewGroup.setOnTouchListener((v, event) -> true);

        if (actionBar != null) {
            viewGroup.addView(actionBar);
        }

        imageView = new RLottieImageView(context);
        viewGroup.addView(imageView);

        titleTextView = new TextView(context);
        titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        titleTextView.setPadding(AndroidUtilities.dp(32), 0, AndroidUtilities.dp(32), 0);
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
        viewGroup.addView(titleTextView);

        subtitleTextView = new TextView(context);
        subtitleTextView.setTextColor(Theme.getColor(currentType == ACTION_TYPE_CHANGE_PHONE_NUMBER ? Theme.key_featuredStickers_addButton : Theme.key_windowBackgroundWhiteBlackText));
        subtitleTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        subtitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        subtitleTextView.setSingleLine(true);
        subtitleTextView.setEllipsize(TextUtils.TruncateAt.END);
        subtitleTextView.setPadding(AndroidUtilities.dp(32), 0, AndroidUtilities.dp(32), 0);
        subtitleTextView.setVisibility(View.GONE);
        viewGroup.addView(subtitleTextView);

        descriptionText = new TextView(context);
        descriptionText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
        descriptionText.setGravity(Gravity.CENTER_HORIZONTAL);
        descriptionText.setLineSpacing(AndroidUtilities.dp(2), 1);
        descriptionText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        if (currentType == ACTION_TYPE_SET_PASSCODE || currentType == ACTION_TYPE_CHANGE_PHONE_NUMBER) {
            descriptionText.setPadding(AndroidUtilities.dp(48), 0, AndroidUtilities.dp(48), 0);
        } else {
            descriptionText.setPadding(AndroidUtilities.dp(32), 0, AndroidUtilities.dp(32), 0);
        }
        viewGroup.addView(descriptionText);

        if (currentType == ACTION_TYPE_QR_LOGIN) {
            descriptionLayout = new LinearLayout(context);
            descriptionLayout.setOrientation(LinearLayout.VERTICAL);
            descriptionLayout.setPadding(AndroidUtilities.dp(24), 0, AndroidUtilities.dp(24), 0);
            descriptionLayout.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            viewGroup.addView(descriptionLayout);

            for (int a = 0; a < 3; a++) {
                LinearLayout linearLayout = new LinearLayout(context);
                linearLayout.setOrientation(LinearLayout.HORIZONTAL);
                descriptionLayout.addView(linearLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, a != 2 ? 7 : 0));

                descriptionLines[a * 2] = new TextView(context);
                descriptionLines[a * 2].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                descriptionLines[a * 2].setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                descriptionLines[a * 2].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                descriptionLines[a * 2].setText(String.format(LocaleController.isRTL ? ".%d" : "%d.", a + 1));
                descriptionLines[a * 2].setTypeface(AndroidUtilities.bold());

                descriptionLines[a * 2 + 1] = new TextView(context);
                descriptionLines[a * 2 + 1].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                descriptionLines[a * 2 + 1].setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                descriptionLines[a * 2 + 1].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                if (a == 0) {
                    descriptionLines[a * 2 + 1].setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText));
                    descriptionLines[a * 2 + 1].setHighlightColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkSelection));
                    String text = LocaleController.getString(R.string.AuthAnotherClientInfo1);
                    SpannableStringBuilder spanned = new SpannableStringBuilder(text);
                    int index1 = text.indexOf('*');
                    int index2 = text.lastIndexOf('*');
                    if (index1 != -1 && index2 != -1 && index1 != index2) {
                        descriptionLines[a * 2 + 1].setMovementMethod(new AndroidUtilities.LinkMovementMethodMy());
                        spanned.replace(index2, index2 + 1, "");
                        spanned.replace(index1, index1 + 1, "");
                        spanned.setSpan(new URLSpanNoUnderline(LocaleController.getString(R.string.AuthAnotherClientDownloadClientUrl)), index1, index2 - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    descriptionLines[a * 2 + 1].setText(spanned);
                } else if (a == 1) {
                    descriptionLines[a * 2 + 1].setText(LocaleController.getString(R.string.AuthAnotherClientInfo2));
                } else {
                    descriptionLines[a * 2 + 1].setText(LocaleController.getString(R.string.AuthAnotherClientInfo3));
                }
                if (LocaleController.isRTL) {
                    linearLayout.setGravity(Gravity.RIGHT);
                    linearLayout.addView(descriptionLines[a * 2 + 1], LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));
                    linearLayout.addView(descriptionLines[a * 2], LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 4, 0, 0, 0));
                } else {
                    linearLayout.addView(descriptionLines[a * 2], LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 4, 0));
                    linearLayout.addView(descriptionLines[a * 2 + 1], LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
                }
            }

            descriptionText.setVisibility(View.GONE);
        }

        descriptionText2 = new TextView(context);
        descriptionText2.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
        descriptionText2.setGravity(Gravity.CENTER_HORIZONTAL);
        descriptionText2.setLineSpacing(AndroidUtilities.dp(2), 1);
        descriptionText2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        descriptionText2.setVisibility(View.GONE);
        descriptionText2.setPadding(AndroidUtilities.dp(32), 0, AndroidUtilities.dp(32), 0);
        viewGroup.addView(descriptionText2);

        buttonTextView = new TextView(context) {
            CellFlickerDrawable cellFlickerDrawable;

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                if (flickerButton) {
                    if (cellFlickerDrawable == null) {
                        cellFlickerDrawable = new CellFlickerDrawable();
                        cellFlickerDrawable.drawFrame = false;
                        cellFlickerDrawable.repeatProgress = 2f;
                    }
                    cellFlickerDrawable.setParentWidth(getMeasuredWidth());
                    AndroidUtilities.rectTmp.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                    cellFlickerDrawable.draw(canvas, AndroidUtilities.rectTmp, AndroidUtilities.dp(4), null);
                    invalidate();
                }
            }
        };

        buttonTextView.setPadding(AndroidUtilities.dp(34), 0, AndroidUtilities.dp(34), 0);
        buttonTextView.setGravity(Gravity.CENTER);
        buttonTextView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        buttonTextView.setTypeface(AndroidUtilities.bold());
        int buttonRadiusDp = currentType == ACTION_TYPE_SET_PASSCODE || currentType == ACTION_TYPE_CHANGE_PHONE_NUMBER || currentType == ACTION_TYPE_CHANNEL_CREATE ? 6 : 4;
        buttonTextView.setBackground(Theme.AdaptiveRipple.filledRectByKey(Theme.key_featuredStickers_addButton, buttonRadiusDp));
        viewGroup.addView(buttonTextView);
        buttonTextView.setOnClickListener(v -> {
            if (getParentActivity() == null) {
                return;
            }
            switch (currentType) {
                case ACTION_TYPE_QR_LOGIN: {
                    if (getParentActivity() == null) {
                        return;
                    }
                    if (Build.VERSION.SDK_INT >= 23 && getParentActivity().checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        getParentActivity().requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
                        return;
                    }
                    processOpenQrReader();
                    break;
                }
                case ACTION_TYPE_CHANNEL_CREATE: {
                    Bundle args = new Bundle();
                    args.putInt("step", 0);
                    presentFragment(new ChannelCreateActivity(args), true);
                    break;
                }
                case ACTION_TYPE_SET_PASSCODE: {
                    presentFragment(new PasscodeActivity(PasscodeActivity.TYPE_SETUP_CODE), true);
                    break;
                }
                case ACTION_TYPE_CHANGE_PHONE_NUMBER: {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(LocaleController.getString(R.string.PhoneNumberChangeTitle));
                    builder.setMessage(LocaleController.getString(R.string.PhoneNumberAlert));
                    builder.setPositiveButton(LocaleController.getString(R.string.Change), (dialogInterface, i) -> presentFragment(new LoginActivity().changePhoneNumber(), true));
                    builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
                    showDialog(builder.create());
                    break;
                }
            }
        });

        switch (currentType) {
            case ACTION_TYPE_CHANNEL_CREATE: {
                imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                imageView.setAnimation(R.raw.channel_create, 200, 200);
                titleTextView.setText(LocaleController.getString(R.string.ChannelAlertTitle));
                descriptionText.setText(LocaleController.getString(R.string.ChannelAlertText));
                buttonTextView.setText(LocaleController.getString(R.string.ChannelAlertCreate2));
                imageView.playAnimation();
                flickerButton = true;
                break;
            }
            case ACTION_TYPE_SET_PASSCODE: {
                imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                imageView.setAnimation(R.raw.utyan_passcode, 200, 200);
                imageView.setFocusable(false);
                imageView.setOnClickListener(v -> {
                    if (!imageView.getAnimatedDrawable().isRunning()) {
                        imageView.getAnimatedDrawable().setCurrentFrame(0, false);
                        imageView.playAnimation();
                    }
                });
                titleTextView.setText(LocaleController.getString(R.string.Passcode));
                descriptionText.setText(LocaleController.getString(R.string.ChangePasscodeInfoShort));
                buttonTextView.setText(LocaleController.getString(R.string.EnablePasscode));
                imageView.playAnimation();
                flickerButton = true;
                break;
            }
            case ACTION_TYPE_QR_LOGIN: {
                colors = new int[8];
                updateColors();
                imageView.setAnimation(R.raw.qr_login, 334, 334, colors);
                imageView.setScaleType(ImageView.ScaleType.CENTER);
                titleTextView.setText(LocaleController.getString(R.string.AuthAnotherClient));
                buttonTextView.setText(LocaleController.getString(R.string.AuthAnotherClientScan));
                imageView.playAnimation();
                break;
            }
            case ACTION_TYPE_CHANGE_PHONE_NUMBER: {
                subtitleTextView.setVisibility(View.VISIBLE);
                imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                imageView.setAnimation(R.raw.utyan_change_number, 200, 200);
                imageView.setOnClickListener(v -> {
                    if (!imageView.getAnimatedDrawable().isRunning()) {
                        imageView.getAnimatedDrawable().setCurrentFrame(0, false);
                        imageView.playAnimation();
                    }
                });

                UserConfig userConfig = getUserConfig();
                TLRPC.User user = getMessagesController().getUser(userConfig.clientUserId);
                if (user == null) {
                    user = userConfig.getCurrentUser();
                }
                if (user != null) {
                    subtitleTextView.setText(LocaleController.formatString("PhoneNumberKeepButton", R.string.PhoneNumberKeepButton, PhoneFormat.getInstance().format("+" + user.phone)));
                }
                subtitleTextView.setOnClickListener(v -> getParentLayout().closeLastFragment(true));
                titleTextView.setText(LocaleController.getString(R.string.PhoneNumberChange2));
                descriptionText.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.PhoneNumberHelp)));
                buttonTextView.setText(LocaleController.getString(R.string.PhoneNumberChange2));
                imageView.playAnimation();
                flickerButton = true;
                break;
            }
        }

        if (flickerButton) {
            buttonTextView.setPadding(AndroidUtilities.dp(34), AndroidUtilities.dp(8), AndroidUtilities.dp(34), AndroidUtilities.dp(8));
            buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        }

        return fragmentView;
    }

    @Override
    public void onLocationAddressAvailable(String address, String displayAddress,  TLRPC.TL_messageMediaVenue city, TLRPC.TL_messageMediaVenue street, Location location) {
        if (subtitleTextView == null) {
            return;
        }
        subtitleTextView.setText(address);
        currentGroupCreateAddress = address;
        currentGroupCreateDisplayAddress = displayAddress;
        currentGroupCreateLocation = location;
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    private void updateColors() {
        if (colors == null || imageView == null) {
            return;
        }
        colors[0] = 0x333333;
        colors[1] = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);

        colors[2] = 0xffffff;
        colors[3] = Theme.getColor(Theme.key_windowBackgroundWhite);

        colors[4] = 0x50a7ea;
        colors[5] = Theme.getColor(Theme.key_featuredStickers_addButton);

        colors[6] = 0x212020;
        colors[7] = Theme.getColor(Theme.key_windowBackgroundWhite);
        imageView.replaceColors(colors);
    }

    public void setGroupCreateAddress(String address, String displayAddress, Location location) {
        currentGroupCreateAddress = address;
        currentGroupCreateDisplayAddress = displayAddress;
        currentGroupCreateLocation = location;
        if (location != null && address == null) {
            LocationController.fetchLocationAddress(location, this);
        }
    }

    @Override
    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        if (getParentActivity() == null) {
            return;
        }
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                processOpenQrReader();
            } else {
                new AlertDialog.Builder(getParentActivity())
                        .setMessage(AndroidUtilities.replaceTags(LocaleController.getString(R.string.QRCodePermissionNoCameraWithHint)))
                        .setPositiveButton(LocaleController.getString(R.string.PermissionOpenSettings), (dialogInterface, i) -> {
                            try {
                                Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                intent.setData(Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
                                getParentActivity().startActivity(intent);
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        })
                        .setNegativeButton(LocaleController.getString(R.string.ContactsPermissionAlertNotNow), null)
                        .setTopAnimation(R.raw.permission_request_camera, 72, false, Theme.getColor(Theme.key_dialogTopBackground))
                        .show();
            }
        }
    }

    public void setQrLoginDelegate(ActionIntroQRLoginDelegate actionIntroQRLoginDelegate) {
        qrLoginDelegate = actionIntroQRLoginDelegate;
    }

    private void processOpenQrReader() {
        CameraScanActivity.showAsSheet(this, false, CameraScanActivity.TYPE_QR, new CameraScanActivity.CameraScanActivityDelegate() {
            @Override
            public void didFindQr(String text) {
                finishFragment(false);
                qrLoginDelegate.didFindQRCode(text);
            }
        });
    }

    public int getType() {
        return currentType;
    }

    public void setShowingAsBottomSheet(boolean showingAsBottomSheet) {
        this.showingAsBottomSheet = showingAsBottomSheet;
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        ThemeDescription.ThemeDescriptionDelegate delegate = this::updateColors;

        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, delegate, Theme.key_windowBackgroundWhite));

        if (actionBar != null) {
            themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
            themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
            themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarWhiteSelector));
        }

        themeDescriptions.add(new ThemeDescription(titleTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, delegate, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(subtitleTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(descriptionText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText6));
        themeDescriptions.add(new ThemeDescription(buttonTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_featuredStickers_buttonText));
        themeDescriptions.add(new ThemeDescription(buttonTextView, ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE, null, null, null, delegate, Theme.key_featuredStickers_addButton));
        themeDescriptions.add(new ThemeDescription(buttonTextView, ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_featuredStickers_addButtonPressed));

        themeDescriptions.add(new ThemeDescription(descriptionLines[0], ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(descriptionLines[1], ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(descriptionLines[1], ThemeDescription.FLAG_LINKCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteLinkText));
        themeDescriptions.add(new ThemeDescription(descriptionLines[2], ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(descriptionLines[3], ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(descriptionLines[4], ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(descriptionLines[5], ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));

        themeDescriptions.add(new ThemeDescription(null, ThemeDescription.FLAG_TEXTCOLOR, null, null, new Drawable[]{drawable2}, null, Theme.key_changephoneinfo_image2));

        return themeDescriptions;
    }

    @Override
    public boolean isLightStatusBar() {
        int color = Theme.getColor(Theme.key_windowBackgroundWhite, null, true);
        return ColorUtils.calculateLuminance(color) > 0.7f;
    }
}
