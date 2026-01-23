/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.AdminedChannelCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.LoadingCell;
import org.telegram.ui.Cells.RadioButtonCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextBlockCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CircularProgressDrawable;
import org.telegram.ui.Components.CrossfadeDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.EditTextEmoji;
import org.telegram.ui.Components.ImageUpdater;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkActionView;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.Premium.LimitReachedBottomSheet;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.TypefaceSpan;

import java.util.ArrayList;

public class ChannelCreateActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, ImageUpdater.ImageUpdaterDelegate {

    private View doneButton;
    private CrossfadeDrawable doneButtonDrawable;
    private EditTextEmoji nameTextView;
    private ShadowSectionCell sectionCell;
    private BackupImageView avatarImage;
    private View avatarOverlay;
    private RLottieImageView avatarEditor;
    private AnimatorSet avatarAnimation;
    private RadialProgressView avatarProgressView;
    private AvatarDrawable avatarDrawable;
    private ImageUpdater imageUpdater;
    private EditTextBoldCursor descriptionTextView;
    private TLRPC.FileLocation avatar;
    private TLRPC.FileLocation avatarBig;
    private String nameToSet;
    private LinearLayout linearLayout2;
    private HeaderCell headerCell2;
    private EditTextBoldCursor editText;
    private boolean isGroup;

    private RLottieDrawable cameraDrawable;

    private LinearLayout linearLayout;
    private LinearLayout adminnedChannelsLayout;
    private LinearLayout linkContainer;
    private LinearLayout publicContainer;
    private LinearLayout privateContainer;
    private LinkActionView permanentLinkView;
    private RadioButtonCell radioButtonCell1;
    private RadioButtonCell radioButtonCell2;
    private TextInfoPrivacyCell typeInfoCell;
    private TextView helpTextView;
    private TextView checkTextView;
    private HeaderCell headerCell;
    private int checkReqId;
    private String lastCheckName;
    private Runnable checkRunnable;
    private boolean lastNameAvailable;
    private boolean isPrivate;
    private boolean loadingInvite;
    private TLRPC.TL_chatInviteExported invite;

    private boolean loadingAdminedChannels;
    private TextInfoPrivacyCell adminedInfoCell;
    private ArrayList<AdminedChannelCell> adminedChannelCells = new ArrayList<>();
    private LoadingCell loadingAdminedCell;

    private int currentStep;
    private long chatId;
    private boolean canCreatePublic = true;
    private Boolean forcePublic;
    private TLRPC.InputFile inputPhoto;
    private TLRPC.InputFile inputVideo;
    private TLRPC.VideoSize inputEmojiMarkup;
    private String inputVideoPath;
    private double videoTimestamp;

    private boolean createAfterUpload;
    private boolean donePressed;
    private Integer doneRequestId;
    private Utilities.Callback2<BaseFragment, Long> onFinishListener;

    private final static int done_button = 1;

    public ChannelCreateActivity(Bundle args) {
        super(args);
        currentStep = args.getInt("step", 0);
        if (args.containsKey("forcePublic")) {
            forcePublic = args.getBoolean("forcePublic", false);
        }
        if (currentStep == 0) {
            avatarDrawable = new AvatarDrawable();
            imageUpdater = new ImageUpdater(true, ImageUpdater.FOR_TYPE_CHANNEL, true);

            TLRPC.TL_channels_checkUsername req = new TLRPC.TL_channels_checkUsername();
            req.username = "1";
            req.channel = new TLRPC.TL_inputChannelEmpty();
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> canCreatePublic = error == null || !error.text.equals("CHANNELS_ADMIN_PUBLIC_TOO_MUCH")));
        } else {
            if (currentStep == 1) {
                canCreatePublic = args.getBoolean("canCreatePublic", true);
                isPrivate = !canCreatePublic;
                if (!canCreatePublic) {
                    loadAdminedChannels();
                }
            }
            chatId = args.getLong("chat_id", 0);
        }
    }

    public void setOnFinishListener(Utilities.Callback2<BaseFragment, Long> onFinishListener) {
        this.onFinishListener = onFinishListener;
    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.chatDidCreated);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.chatDidFailCreate);
        if (currentStep == 1) {
            generateLink();
        }
        if (imageUpdater != null) {
            imageUpdater.parentFragment = this;
            imageUpdater.setDelegate(this);
        }
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (doneRequestId != null) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(doneRequestId, true);
            doneRequestId = null;
        }
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.chatDidCreated);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.chatDidFailCreate);
        if (imageUpdater != null) {
            imageUpdater.clear();
        }
        AndroidUtilities.removeAdjustResize(getParentActivity(), classGuid);
        if (nameTextView != null) {
            nameTextView.onDestroy();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (nameTextView != null) {
            nameTextView.onResume();
        }
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
        if (imageUpdater != null) {
            imageUpdater.onResume();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (nameTextView != null) {
            nameTextView.onPause();
        }
        if (imageUpdater != null) {
            imageUpdater.onPause();
        }
    }

    @Override
    public void dismissCurrentDialog() {
        if (imageUpdater != null && imageUpdater.dismissCurrentDialog(visibleDialog)) {
            return;
        }
        super.dismissCurrentDialog();
    }

    @Override
    public boolean dismissDialogOnPause(Dialog dialog) {
        return (imageUpdater == null || imageUpdater.dismissDialogOnPause(dialog)) && super.dismissDialogOnPause(dialog);
    }

    @Override
    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        if (imageUpdater != null) {
            imageUpdater.onRequestPermissionsResultFragment(requestCode, permissions, grantResults);
        }
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (nameTextView != null && nameTextView.isPopupShowing()) {
            if (invoked) nameTextView.hidePopup(true);
            return false;
        }
        return true;
    }

    private AlertDialog cancelDialog;
    private void showDoneCancelDialog() {
        if (cancelDialog != null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.StopLoadingTitle));
        builder.setMessage(LocaleController.getString(R.string.StopLoading));
        builder.setPositiveButton(LocaleController.getString(R.string.WaitMore), null);
        builder.setNegativeButton(LocaleController.getString(R.string.Stop), (dialogInterface, i) -> {
            donePressed = false;
            createAfterUpload = false;
            if (doneRequestId != null) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(doneRequestId, true);
                doneRequestId = null;
            }
            updateDoneProgress(false);
            dialogInterface.dismiss();
        });
        cancelDialog = builder.show();
    }

    private Runnable enableDoneLoading = () -> updateDoneProgress(true);
    private ValueAnimator doneButtonDrawableAnimator;
    private void updateDoneProgress(boolean loading) {
        if (!loading) {
            AndroidUtilities.cancelRunOnUIThread(enableDoneLoading);
        }
        if (doneButtonDrawable != null) {
            if (doneButtonDrawableAnimator != null) {
                doneButtonDrawableAnimator.cancel();
            }
            doneButtonDrawableAnimator = ValueAnimator.ofFloat(doneButtonDrawable.getProgress(), loading ? 1f : 0);
            doneButtonDrawableAnimator.addUpdateListener(a -> {
                doneButtonDrawable.setProgress((float) a.getAnimatedValue());
                doneButtonDrawable.invalidateSelf();
            });
            doneButtonDrawableAnimator.setDuration((long) (200 * Math.abs(doneButtonDrawable.getProgress() - (loading ? 1f : 0))));
            doneButtonDrawableAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            doneButtonDrawableAnimator.start();
        }
    }

    @Override
    public View createView(Context context) {
        if (nameTextView != null) {
            nameTextView.onDestroy();
        }

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (donePressed) {
                        showDoneCancelDialog();
                        return;
                    }
                    finishFragment();
                } else if (id == done_button) {
                    if (currentStep == 0) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        if (donePressed) {
                            showDoneCancelDialog();
                            return;
                        }
                        if (nameTextView.length() == 0) {
                            Vibrator v = (Vibrator) getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
                            if (v != null) {
                                v.vibrate(200);
                            }
                            AndroidUtilities.shakeView(nameTextView);
                            return;
                        }
                        donePressed = true;
                        AndroidUtilities.runOnUIThread(enableDoneLoading, 200);
                        if (imageUpdater.isUploadingImage()) {
                            createAfterUpload = true;
                            return;
                        }
                        doneRequestId = MessagesController.getInstance(currentAccount).createChat(nameTextView.getText().toString(), new ArrayList<>(), descriptionTextView.getText().toString(), ChatObject.CHAT_TYPE_CHANNEL, false, null, null, -1, ChannelCreateActivity.this);
                    } else if (currentStep == 1) {
                        if (!isPrivate) {
                            if (descriptionTextView.length() == 0) {
                                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                                builder.setTitle(LocaleController.getString(R.string.ChannelPublicEmptyUsernameTitle));
                                builder.setMessage(LocaleController.getString(R.string.ChannelPublicEmptyUsername));
                                builder.setPositiveButton(LocaleController.getString(R.string.Close), null);
                                showDialog(builder.create());
                                return;
                            } else {
                                if (!lastNameAvailable) {
                                    Vibrator v = (Vibrator) getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
                                    if (v != null) {
                                        v.vibrate(200);
                                    }
                                    AndroidUtilities.shakeView(checkTextView);
                                    return;
                                } else {
                                    AndroidUtilities.runOnUIThread(enableDoneLoading, 200);
                                    MessagesController.getInstance(currentAccount).updateChannelUserName(ChannelCreateActivity.this, chatId, lastCheckName, () -> {
                                        updateDoneProgress(false);
                                        if (onFinishListener != null) {
                                            onFinishListener.run(ChannelCreateActivity.this, chatId);
                                        }
                                    }, () -> {
                                        updateDoneProgress(false);
                                        if (onFinishListener != null) {
                                            onFinishListener.run(ChannelCreateActivity.this, chatId);
                                        }
                                    });
                                }
                            }
                        } else if (onFinishListener != null) {
                            onFinishListener.run(ChannelCreateActivity.this, chatId);
                        }
                        if (onFinishListener == null) {
                            Bundle args = new Bundle();
                            args.putInt("step", 2);
                            args.putLong("chatId", chatId);
                            args.putInt("chatType", ChatObject.CHAT_TYPE_CHANNEL);
                            presentFragment(new GroupCreateActivity(args), true);
                        }
                    }
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        Drawable checkmark = context.getResources().getDrawable(R.drawable.ic_ab_done).mutate();
        checkmark.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultIcon), PorterDuff.Mode.MULTIPLY));
        doneButtonDrawable = new CrossfadeDrawable(checkmark, new CircularProgressDrawable(Theme.getColor(Theme.key_actionBarDefaultIcon)));
        doneButton = menu.addItemWithWidth(done_button, doneButtonDrawable, AndroidUtilities.dp(56), LocaleController.getString(R.string.Done));

        if (currentStep == 0) {
            actionBar.setTitle(LocaleController.getString(R.string.NewChannel));

            SizeNotifierFrameLayout sizeNotifierFrameLayout = new SizeNotifierFrameLayout(context) {

                private boolean ignoreLayout;

                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    int widthSize = MeasureSpec.getSize(widthMeasureSpec);
                    int heightSize = MeasureSpec.getSize(heightMeasureSpec);

                    setMeasuredDimension(widthSize, heightSize);
                    heightSize -= getPaddingTop();

                    measureChildWithMargins(actionBar, widthMeasureSpec, 0, heightMeasureSpec, 0);

                    int keyboardSize = measureKeyboardHeight();
                    if (keyboardSize > AndroidUtilities.dp(20)) {
                        ignoreLayout = true;
                        nameTextView.hideEmojiView();
                        ignoreLayout = false;
                    }

                    int childCount = getChildCount();
                    for (int i = 0; i < childCount; i++) {
                        View child = getChildAt(i);
                        if (child == null || child.getVisibility() == GONE || child == actionBar) {
                            continue;
                        }
                        if (nameTextView != null && nameTextView.isPopupView(child)) {
                            if (AndroidUtilities.isInMultiwindow || AndroidUtilities.isTablet()) {
                                if (AndroidUtilities.isTablet()) {
                                    child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Math.min(AndroidUtilities.dp(AndroidUtilities.isTablet() ? 200 : 320), heightSize - AndroidUtilities.statusBarHeight + getPaddingTop()), MeasureSpec.EXACTLY));
                                } else {
                                    child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightSize - AndroidUtilities.statusBarHeight + getPaddingTop(), MeasureSpec.EXACTLY));
                                }
                            } else {
                                child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(child.getLayoutParams().height, MeasureSpec.EXACTLY));
                            }
                        } else {
                            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                        }
                    }
                }

                @Override
                protected void onLayout(boolean changed, int l, int t, int r, int b) {
                    final int count = getChildCount();

                    int keyboardSize = measureKeyboardHeight();
                    int paddingBottom = keyboardSize <= AndroidUtilities.dp(20) && !AndroidUtilities.isInMultiwindow && !AndroidUtilities.isTablet() ? nameTextView.getEmojiPadding() : 0;
                    setBottomClip(paddingBottom);

                    for (int i = 0; i < count; i++) {
                        final View child = getChildAt(i);
                        if (child.getVisibility() == GONE) {
                            continue;
                        }
                        final LayoutParams lp = (LayoutParams) child.getLayoutParams();

                        final int width = child.getMeasuredWidth();
                        final int height = child.getMeasuredHeight();

                        int childLeft;
                        int childTop;

                        int gravity = lp.gravity;
                        if (gravity == -1) {
                            gravity = Gravity.TOP | Gravity.LEFT;
                        }

                        final int absoluteGravity = gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
                        final int verticalGravity = gravity & Gravity.VERTICAL_GRAVITY_MASK;

                        switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                            case Gravity.CENTER_HORIZONTAL:
                                childLeft = (r - l - width) / 2 + lp.leftMargin - lp.rightMargin;
                                break;
                            case Gravity.RIGHT:
                                childLeft = r - width - lp.rightMargin;
                                break;
                            case Gravity.LEFT:
                            default:
                                childLeft = lp.leftMargin;
                        }

                        switch (verticalGravity) {
                            case Gravity.TOP:
                                childTop = lp.topMargin + getPaddingTop();
                                break;
                            case Gravity.CENTER_VERTICAL:
                                childTop = ((b - paddingBottom) - t - height) / 2 + lp.topMargin - lp.bottomMargin;
                                break;
                            case Gravity.BOTTOM:
                                childTop = ((b - paddingBottom) - t) - height - lp.bottomMargin;
                                break;
                            default:
                                childTop = lp.topMargin;
                        }

                        if (nameTextView != null && nameTextView.isPopupView(child)) {
                            if (AndroidUtilities.isTablet()) {
                                childTop = getMeasuredHeight() - child.getMeasuredHeight();
                            } else {
                                childTop = getMeasuredHeight() + keyboardSize - child.getMeasuredHeight();
                            }
                        }
                        child.layout(childLeft, childTop, childLeft + width, childTop + height);
                    }

                    notifyHeightChanged();
                }

                @Override
                public void requestLayout() {
                    if (ignoreLayout) {
                        return;
                    }
                    super.requestLayout();
                }
            };
            sizeNotifierFrameLayout.setOnTouchListener((v, event) -> true);
            fragmentView = sizeNotifierFrameLayout;
            fragmentView.setTag(Theme.key_windowBackgroundWhite);
            fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

            linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(LinearLayout.VERTICAL);
            sizeNotifierFrameLayout.addView(linearLayout, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            FrameLayout frameLayout = new FrameLayout(context);
            linearLayout.addView(frameLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            avatarImage = new BackupImageView(context) {
                @Override
                public void invalidate() {
                    if (avatarOverlay != null) {
                        avatarOverlay.invalidate();
                    }
                    super.invalidate();
                }

                @Override
                public void invalidate(int l, int t, int r, int b) {
                    if (avatarOverlay != null) {
                        avatarOverlay.invalidate();
                    }
                    super.invalidate(l, t, r, b);
                }
            };
            avatarImage.setRoundRadius(AndroidUtilities.dp(32));
            avatarDrawable.setInfo(5, null, null);
            avatarImage.setImageDrawable(avatarDrawable);
            frameLayout.addView(avatarImage, LayoutHelper.createFrame(64, 64, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 0 : 16, 12, LocaleController.isRTL ? 16 : 0, 12));

            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(0x55000000);

            avatarOverlay = new View(context) {
                @Override
                protected void onDraw(Canvas canvas) {
                    if (avatarImage != null && avatarImage.getImageReceiver().hasNotThumb()) {
                        paint.setAlpha((int) (0x55 * avatarImage.getImageReceiver().getCurrentAlpha() * avatarProgressView.getAlpha()));
                        canvas.drawCircle(getMeasuredWidth() / 2.0f, getMeasuredHeight() / 2.0f, getMeasuredWidth() / 2.0f, paint);
                    }
                }
            };
            avatarOverlay.setContentDescription(LocaleController.getString(R.string.ChatSetPhotoOrVideo));
            frameLayout.addView(avatarOverlay, LayoutHelper.createFrame(64, 64, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 0 : 16, 12, LocaleController.isRTL ? 16 : 0, 12));
            avatarOverlay.setOnClickListener(view -> {
                imageUpdater.openMenu(avatar != null, () -> {
                    avatar = null;
                    avatarBig = null;
                    inputPhoto = null;
                    inputVideo = null;
                    inputVideoPath = null;
                    inputEmojiMarkup = null;
                    videoTimestamp = 0;
                    showAvatarProgress(false, true);
                    avatarImage.setImage(null, null, avatarDrawable, null);
                    avatarEditor.setAnimation(cameraDrawable);
                    cameraDrawable.setCurrentFrame(0);
                }, dialog -> {
                    if (!imageUpdater.isUploadingImage()) {
                        cameraDrawable.setCustomEndFrame(86);
                        avatarEditor.playAnimation();
                    } else {
                        cameraDrawable.setCurrentFrame(0, false);
                    }
                }, 0);
                cameraDrawable.setCurrentFrame(0);
                cameraDrawable.setCustomEndFrame(43);
                avatarEditor.playAnimation();
            });

            cameraDrawable = new RLottieDrawable(R.raw.camera, "" + R.raw.camera, AndroidUtilities.dp(60), AndroidUtilities.dp(60), false, null);

            avatarEditor = new RLottieImageView(context) {
                @Override
                public void invalidate(int l, int t, int r, int b) {
                    super.invalidate(l, t, r, b);
                    avatarOverlay.invalidate();
                }

                @Override
                public void invalidate() {
                    super.invalidate();
                    avatarOverlay.invalidate();
                }
            };
            avatarEditor.setScaleType(ImageView.ScaleType.CENTER);
            avatarEditor.setAnimation(cameraDrawable);
            avatarEditor.setEnabled(false);
            avatarEditor.setClickable(false);
            avatarEditor.setPadding(AndroidUtilities.dp(0), 0, 0, AndroidUtilities.dp(1));
            frameLayout.addView(avatarEditor, LayoutHelper.createFrame(64, 64, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 0 : 15, 12, LocaleController.isRTL ? 15 : 0, 12));

            avatarProgressView = new RadialProgressView(context) {
                @Override
                public void setAlpha(float alpha) {
                    super.setAlpha(alpha);
                    avatarOverlay.invalidate();
                }
            };
             avatarProgressView.setSize(AndroidUtilities.dp(30));
            avatarProgressView.setProgressColor(0xffffffff);
            avatarProgressView.setNoProgress(false);
            frameLayout.addView(avatarProgressView, LayoutHelper.createFrame(64, 64, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 0 : 16, 12, LocaleController.isRTL ? 16 : 0, 12));

            showAvatarProgress(false, false);

            nameTextView = new EditTextEmoji(context, sizeNotifierFrameLayout, this, EditTextEmoji.STYLE_FRAGMENT, false);
            nameTextView.setHint(LocaleController.getString(R.string.EnterChannelName));
            if (nameToSet != null) {
                nameTextView.setText(nameToSet);
                nameToSet = null;
            }
            InputFilter[] inputFilters = new InputFilter[1];
            inputFilters[0] = new InputFilter.LengthFilter(100);
            nameTextView.setFilters(inputFilters);
            nameTextView.getEditText().setSingleLine(true);
            nameTextView.getEditText().setImeOptions(EditorInfo.IME_ACTION_NEXT);
            nameTextView.getEditText().setOnEditorActionListener((textView, i, keyEvent) -> {
                if (i == EditorInfo.IME_ACTION_NEXT && !TextUtils.isEmpty(nameTextView.getEditText().getText())) {
                    descriptionTextView.requestFocus();
                    return true;
                }
                return false;
            });
            frameLayout.addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, LocaleController.isRTL ? 5 : 96, 0, LocaleController.isRTL ? 96 : 5, 0));

            descriptionTextView = new EditTextBoldCursor(context);
            descriptionTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            descriptionTextView.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
            descriptionTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            descriptionTextView.setBackgroundDrawable(null);
            descriptionTextView.setLineColors(getThemedColor(Theme.key_windowBackgroundWhiteInputField), getThemedColor(Theme.key_windowBackgroundWhiteInputFieldActivated), getThemedColor(Theme.key_text_RedRegular));
            descriptionTextView.setPadding(0, 0, 0, AndroidUtilities.dp(6));
            descriptionTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            descriptionTextView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
            descriptionTextView.setImeOptions(EditorInfo.IME_ACTION_DONE);
            inputFilters = new InputFilter[1];
            inputFilters[0] = new InputFilter.LengthFilter(120);
            descriptionTextView.setFilters(inputFilters);
            descriptionTextView.setHint(LocaleController.getString(R.string.DescriptionPlaceholder));
            descriptionTextView.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            descriptionTextView.setCursorSize(AndroidUtilities.dp(20));
            descriptionTextView.setCursorWidth(1.5f);
            linearLayout.addView(descriptionTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 18, 24, 0));
            descriptionTextView.setOnEditorActionListener((textView, i, keyEvent) -> {
                if (i == EditorInfo.IME_ACTION_DONE && doneButton != null) {
                    doneButton.performClick();
                    return true;
                }
                return false;
            });
            descriptionTextView.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {

                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {

                }

                @Override
                public void afterTextChanged(Editable editable) {

                }
            });

            helpTextView = new TextView(context);
            helpTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            helpTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText8));
            helpTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            helpTextView.setText(LocaleController.getString(R.string.DescriptionInfo));
            linearLayout.addView(helpTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 24, 10, 24, 20));
        } else if (currentStep == 1) {
            fragmentView = new ScrollView(context);
            ScrollView scrollView = (ScrollView) fragmentView;
            scrollView.setFillViewport(true);
            linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(LinearLayout.VERTICAL);
            scrollView.addView(linearLayout, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TLRPC.Chat chat = getMessagesController().getChat(chatId);
            isGroup = chat != null && (!ChatObject.isChannel(chat) || ChatObject.isMegagroup(chat));
            actionBar.setTitle(isGroup ? LocaleController.getString(R.string.GroupSettingsTitle) : LocaleController.getString(R.string.ChannelSettingsTitle));
            fragmentView.setTag(Theme.key_windowBackgroundGray);
            fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

            headerCell2 = new HeaderCell(context, 23);
            headerCell2.setHeight(46);
            headerCell2.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            headerCell2.setText(isGroup ? LocaleController.getString(R.string.GroupTypeHeader) : LocaleController.getString(R.string.ChannelTypeHeader));
            linearLayout.addView(headerCell2);

            linearLayout2 = new LinearLayout(context);
            linearLayout2.setOrientation(LinearLayout.VERTICAL);
            linearLayout2.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            linearLayout.addView(linearLayout2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            radioButtonCell1 = new RadioButtonCell(context);
            radioButtonCell1.setBackgroundDrawable(Theme.getSelectorDrawable(false));
            if (forcePublic != null && !forcePublic) {
                isPrivate = true;
            }
            if (isGroup) {
                radioButtonCell1.setTextAndValue(LocaleController.getString(R.string.MegaPublic), LocaleController.getString(R.string.MegaPublicInfo), false, !isPrivate);
            } else {
                radioButtonCell1.setTextAndValue(LocaleController.getString(R.string.ChannelPublic), LocaleController.getString(R.string.ChannelPublicInfo), false, !isPrivate);
            }
            radioButtonCell1.setOnClickListener(v -> {
                if (!canCreatePublic) {
                    showPremiumIncreaseLimitDialog();
                    return;
                }
                if (!isPrivate) {
                    return;
                }
                isPrivate = false;
                updatePrivatePublic();
            });
            if (forcePublic == null || forcePublic) {
                linearLayout2.addView(radioButtonCell1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            }

            radioButtonCell2 = new RadioButtonCell(context);
            radioButtonCell2.setBackgroundDrawable(Theme.getSelectorDrawable(false));
            if (forcePublic != null && forcePublic) {
                isPrivate = false;
            }
            if (isGroup) {
                radioButtonCell2.setTextAndValue(LocaleController.getString(R.string.MegaPrivate), LocaleController.getString(R.string.MegaPrivateInfo), false, isPrivate);
            } else {
                radioButtonCell2.setTextAndValue(LocaleController.getString(R.string.ChannelPrivate), LocaleController.getString(R.string.ChannelPrivateInfo), false, isPrivate);
            }
            radioButtonCell2.setOnClickListener(v -> {
                if (isPrivate) {
                    return;
                }
                isPrivate = true;
                updatePrivatePublic();
            });
            if (forcePublic == null || !forcePublic) {
                linearLayout2.addView(radioButtonCell2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            }

            sectionCell = new ShadowSectionCell(context);
            linearLayout.addView(sectionCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            linkContainer = new LinearLayout(context);
            linkContainer.setOrientation(LinearLayout.VERTICAL);
            linkContainer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            linearLayout.addView(linkContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            headerCell = new HeaderCell(context);
            linkContainer.addView(headerCell);

            publicContainer = new LinearLayout(context);
            publicContainer.setOrientation(LinearLayout.HORIZONTAL);
            linkContainer.addView(publicContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, 21, 7, 21, 0));

            editText = new EditTextBoldCursor(context);
            editText.setText(MessagesController.getInstance(currentAccount).linkPrefix + "/");
            editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            editText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
            editText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            editText.setMaxLines(1);
            editText.setLines(1);
            editText.setEnabled(false);
            editText.setBackgroundDrawable(null);
            editText.setPadding(0, 0, 0, 0);
            editText.setSingleLine(true);
            editText.setInputType(InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
            editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
            publicContainer.addView(editText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 36));

            descriptionTextView = new EditTextBoldCursor(context);
            descriptionTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            descriptionTextView.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
            descriptionTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            descriptionTextView.setMaxLines(1);
            descriptionTextView.setLines(1);
            descriptionTextView.setBackgroundDrawable(null);
            descriptionTextView.setPadding(0, 0, 0, 0);
            descriptionTextView.setSingleLine(true);
            descriptionTextView.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
            descriptionTextView.setImeOptions(EditorInfo.IME_ACTION_DONE);
            descriptionTextView.setHint(LocaleController.getString(R.string.ChannelUsernamePlaceholder));
            descriptionTextView.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            descriptionTextView.setCursorSize(AndroidUtilities.dp(20));
            descriptionTextView.setCursorWidth(1.5f);
            publicContainer.addView(descriptionTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36));
            descriptionTextView.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {

                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                    checkUserName(descriptionTextView.getText().toString());
                }

                @Override
                public void afterTextChanged(Editable editable) {

                }
            });

            privateContainer = new LinearLayout(context);
            privateContainer.setOrientation(LinearLayout.VERTICAL);
            linkContainer.addView(privateContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            permanentLinkView = new LinkActionView(context, this, null, chatId, true, ChatObject.isChannel(getMessagesController().getChat(chatId)));
            //permanentLinkView.showOptions(false);
            permanentLinkView.hideRevokeOption(true);
            permanentLinkView.setUsers(0, null);
            privateContainer.addView(permanentLinkView);

            checkTextView = new LinkSpanDrawable.LinksTextView(context) {
                @Override
                public void setText(CharSequence text, BufferType type) {
                    if (text != null) {
                        SpannableStringBuilder tagsString = AndroidUtilities.replaceTags(text.toString());
                        int index = tagsString.toString().indexOf('\n');
                        if (index >= 0) {
                            tagsString.replace(index, index + 1, " ");
                            tagsString.setSpan(new ForegroundColorSpan(getThemedColor(Theme.key_text_RedRegular)), 0, index, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                        TypefaceSpan[] spans = tagsString.getSpans(0, tagsString.length(), TypefaceSpan.class);
                        final String username = descriptionTextView == null || descriptionTextView.getText() == null ? "" : descriptionTextView.getText().toString();
                        for (int i = 0; i < spans.length; ++i) {
                            tagsString.setSpan(
                                new ClickableSpan() {
                                    @Override
                                    public void onClick(@NonNull View view) {
                                        Browser.openUrl(getContext(), "https://fragment.com/username/" + username);
                                    }

                                    @Override
                                    public void updateDrawState(@NonNull TextPaint ds) {
                                        super.updateDrawState(ds);
                                        ds.setUnderlineText(false);
                                    }
                                },
                                tagsString.getSpanStart(spans[i]),
                                tagsString.getSpanEnd(spans[i]),
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            );
                            tagsString.removeSpan(spans[i]);
                        }
                        text = tagsString;
                    }
                    super.setText(text, type);
                }
            };
            checkTextView.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText));
            checkTextView.setHighlightColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkSelection));
            checkTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            checkTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            checkTextView.setVisibility(View.GONE);
            checkTextView.setPadding(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(3), 0);
            linkContainer.addView(checkTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 18, 3, 18, 7));

            typeInfoCell = new TextInfoPrivacyCell(context);
            typeInfoCell.setBackgroundDrawable(Theme.getThemedDrawableByKey(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
            linearLayout.addView(typeInfoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            loadingAdminedCell = new LoadingCell(context);
            linearLayout.addView(loadingAdminedCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            adminnedChannelsLayout = new LinearLayout(context);
            adminnedChannelsLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            adminnedChannelsLayout.setOrientation(LinearLayout.VERTICAL);
            linearLayout.addView(adminnedChannelsLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            adminedInfoCell = new TextInfoPrivacyCell(context);
            adminedInfoCell.setBackgroundDrawable(Theme.getThemedDrawableByKey(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
            linearLayout.addView(adminedInfoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            updatePrivatePublic();
        }

        return fragmentView;
    }

    private void generateLink() {
        if (loadingInvite || invite != null) {
            return;
        }
        TLRPC.ChatFull chatFull = getMessagesController().getChatFull(chatId);
        if (chatFull != null) {
            invite = chatFull.exported_invite;
        }
        if (invite != null) {
            return;
        }
        loadingInvite = true;
        TLRPC.TL_messages_getExportedChatInvites req = new TLRPC.TL_messages_getExportedChatInvites();
        req.peer = getMessagesController().getInputPeer(-chatId);
        req.admin_id = getMessagesController().getInputUser(getUserConfig().getCurrentUser());
        req.limit = 1;

        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                TLRPC.TL_messages_exportedChatInvites invites = (TLRPC.TL_messages_exportedChatInvites) response;
                invite = (TLRPC.TL_chatInviteExported) invites.invites.get(0);
            }
            loadingInvite = false;
            permanentLinkView.setLink(invite != null ? invite.link : null);
        }));
    }

    private void updatePrivatePublic() {
        if (sectionCell == null) {
            return;
        }
        if (!isPrivate && !canCreatePublic) {
            typeInfoCell.setText(LocaleController.getString(R.string.ChangePublicLimitReached));
            typeInfoCell.setTag(Theme.key_text_RedRegular);
            typeInfoCell.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
            linkContainer.setVisibility(View.GONE);
            sectionCell.setVisibility(View.GONE);
            if (loadingAdminedChannels) {
                loadingAdminedCell.setVisibility(View.VISIBLE);
                adminnedChannelsLayout.setVisibility(View.GONE);
                typeInfoCell.setBackgroundDrawable(Theme.getThemedDrawableByKey(typeInfoCell.getContext(), R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                adminedInfoCell.setVisibility(View.GONE);
            } else {
                typeInfoCell.setBackgroundDrawable(Theme.getThemedDrawableByKey(typeInfoCell.getContext(), R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                loadingAdminedCell.setVisibility(View.GONE);
                adminnedChannelsLayout.setVisibility(View.VISIBLE);
                adminedInfoCell.setVisibility(View.VISIBLE);
            }
        } else {
            typeInfoCell.setTag(Theme.key_windowBackgroundWhiteGrayText4);
            typeInfoCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
            sectionCell.setVisibility(View.VISIBLE);
            adminedInfoCell.setVisibility(View.GONE);
            adminnedChannelsLayout.setVisibility(View.GONE);
            typeInfoCell.setBackgroundDrawable(Theme.getThemedDrawableByKey(typeInfoCell.getContext(), R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
            linkContainer.setVisibility(View.VISIBLE);
            loadingAdminedCell.setVisibility(View.GONE);
            if (isGroup) {
                typeInfoCell.setText(isPrivate ? LocaleController.getString(R.string.MegaPrivateLinkHelp) : LocaleController.getString(R.string.MegaUsernameHelp));
                headerCell.setText(isPrivate ? LocaleController.getString(R.string.ChannelInviteLinkTitle) : LocaleController.getString(R.string.ChannelLinkTitle));
            } else {
                typeInfoCell.setText(isPrivate ? LocaleController.getString(R.string.ChannelPrivateLinkHelp) : LocaleController.getString(R.string.ChannelUsernameHelp));
                headerCell.setText(isPrivate ? LocaleController.getString(R.string.ChannelInviteLinkTitle) : LocaleController.getString(R.string.ChannelLinkTitle));
            }
            publicContainer.setVisibility(isPrivate ? View.GONE : View.VISIBLE);
            privateContainer.setVisibility(isPrivate ? View.VISIBLE : View.GONE);
            linkContainer.setPadding(0, 0, 0, isPrivate ? 0 : AndroidUtilities.dp(7));
            permanentLinkView.setLink(invite != null ? invite.link : null);
            checkTextView.setVisibility(!isPrivate && checkTextView.length() != 0 ? View.VISIBLE : View.GONE);
        }
        radioButtonCell1.setChecked(!isPrivate, true);
        radioButtonCell2.setChecked(isPrivate, true);
        descriptionTextView.clearFocus();
        AndroidUtilities.hideKeyboard(descriptionTextView);
    }

    @Override
    public void onUploadProgressChanged(float progress) {
        if (avatarProgressView == null) {
            return;
        }
        avatarProgressView.setProgress(progress);
    }

    @Override
    public void didStartUpload(boolean fromAvatarConstructor, boolean isVideo) {
        if (avatarProgressView == null) {
            return;
        }
        avatarProgressView.setProgress(0.0f);
    }

    @Override
    public void didUploadPhoto(final TLRPC.InputFile photo, final TLRPC.InputFile video, double videoStartTimestamp, String videoPath, final TLRPC.PhotoSize bigSize, final TLRPC.PhotoSize smallSize, boolean isVideo, TLRPC.VideoSize emojiMarkup) {
        AndroidUtilities.runOnUIThread(() -> {
            if (photo != null || video != null) {
                inputPhoto = photo;
                inputVideo = video;
                inputEmojiMarkup = emojiMarkup;
                inputVideoPath = videoPath;
                videoTimestamp = videoStartTimestamp;
                if (createAfterUpload) {
                    if (cancelDialog != null) {
                        try {
                            cancelDialog.dismiss();
                            cancelDialog = null;
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                    updateDoneProgress(false);
                    donePressed = false;
                    doneButton.performClick();
                }
                showAvatarProgress(false, true);
                avatarEditor.setImageDrawable(null);
            } else {
                avatar = smallSize.location;
                avatarBig = bigSize.location;
                avatarImage.setImage(ImageLocation.getForLocal(avatar), "50_50", avatarDrawable, null);
                showAvatarProgress(true, false);
            }
        });
    }

    @Override
    public String getInitialSearchString() {
        return nameTextView.getText().toString();
    }

    private void showAvatarProgress(boolean show, boolean animated) {
        if (avatarEditor == null) {
            return;
        }
        if (avatarAnimation != null) {
            avatarAnimation.removeAllListeners();
            avatarAnimation.cancel();
            avatarAnimation = null;
        }
        if (animated) {
            avatarAnimation = new AnimatorSet();
            if (show) {
                avatarProgressView.setVisibility(View.VISIBLE);

                avatarAnimation.playTogether(ObjectAnimator.ofFloat(avatarEditor, View.ALPHA, 0.0f),
                        ObjectAnimator.ofFloat(avatarProgressView, View.ALPHA, 1.0f));
            } else {
                if (avatarEditor.getVisibility() != View.VISIBLE) {
                    avatarEditor.setAlpha(0f);
                }
                avatarEditor.setVisibility(View.VISIBLE);

                avatarAnimation.playTogether(ObjectAnimator.ofFloat(avatarEditor, View.ALPHA, 1.0f),
                        ObjectAnimator.ofFloat(avatarProgressView, View.ALPHA, 0.0f));
            }
            avatarAnimation.setDuration(180);
            avatarAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (avatarAnimation == null || avatarEditor == null) {
                        return;
                    }
                    if (show) {
                        avatarEditor.setVisibility(View.INVISIBLE);
                    } else {
                        avatarProgressView.setVisibility(View.INVISIBLE);
                    }
                    avatarAnimation = null;
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    avatarAnimation = null;
                }
            });
            avatarAnimation.start();
        } else {
            if (show) {
                avatarEditor.setAlpha(1.0f);
                avatarEditor.setVisibility(View.INVISIBLE);
                avatarProgressView.setAlpha(1.0f);
                avatarProgressView.setVisibility(View.VISIBLE);
            } else {
                avatarEditor.setAlpha(1.0f);
                avatarEditor.setVisibility(View.VISIBLE);
                avatarProgressView.setAlpha(0.0f);
                avatarProgressView.setVisibility(View.INVISIBLE);
            }
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (imageUpdater != null) {
            imageUpdater.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void saveSelfArgs(Bundle args) {
        if (currentStep == 0) {
            if (imageUpdater != null && imageUpdater.currentPicturePath != null) {
                args.putString("path", imageUpdater.currentPicturePath);
            }
            if (nameTextView != null) {
                String text = nameTextView.getText().toString();
                if (text.length() != 0) {
                    args.putString("nameTextView", text);
                }
            }
        }
    }

    @Override
    public void restoreSelfArgs(Bundle args) {
        if (currentStep == 0) {
            if (imageUpdater != null) {
                imageUpdater.currentPicturePath = args.getString("path");
            }
            String text = args.getString("nameTextView");
            if (text != null) {
                if (nameTextView != null) {
                    nameTextView.setText(text);
                } else {
                    nameToSet = text;
                }
            }
        }
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen && currentStep != 1) {
            nameTextView.requestFocus();
            nameTextView.openKeyboard();
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.chatDidFailCreate) {
            if (cancelDialog != null) {
                try {
                    cancelDialog.dismiss();
                    cancelDialog = null;
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            updateDoneProgress(false);
            donePressed = false;
        } else if (id == NotificationCenter.chatDidCreated) {
            if (cancelDialog != null) {
                try {
                    cancelDialog.dismiss();
                    cancelDialog = null;
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            long chat_id = (Long) args[0];
            Bundle bundle = new Bundle();
            bundle.putInt("step", 1);
            bundle.putLong("chat_id", chat_id);
            bundle.putBoolean("canCreatePublic", canCreatePublic);
            if (forcePublic != null) {
                bundle.putBoolean("forcePublic", forcePublic);
            }
            if (inputPhoto != null || inputVideo != null || inputEmojiMarkup != null) {
                MessagesController.getInstance(currentAccount).changeChatAvatar(chat_id, null, inputPhoto, inputVideo, inputEmojiMarkup, videoTimestamp, inputVideoPath, avatar, avatarBig, null);
            }
            ChannelCreateActivity activity = new ChannelCreateActivity(bundle);
            activity.setOnFinishListener(onFinishListener);
            presentFragment(activity, true);
        }
    }

    private void loadAdminedChannels() {
        if (loadingAdminedChannels) {
            return;
        }
        loadingAdminedChannels = true;
        updatePrivatePublic();
        TLRPC.TL_channels_getAdminedPublicChannels req = new TLRPC.TL_channels_getAdminedPublicChannels();
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            loadingAdminedChannels = false;
            if (response != null) {
                if (getParentActivity() == null) {
                    return;
                }
                for (int a = 0; a < adminedChannelCells.size(); a++) {
                    linearLayout.removeView(adminedChannelCells.get(a));
                }
                adminedChannelCells.clear();
                TLRPC.TL_messages_chats res = (TLRPC.TL_messages_chats) response;

                for (int a = 0; a < res.chats.size(); a++) {
                    AdminedChannelCell adminedChannelCell = new AdminedChannelCell(getParentActivity(), view -> {
                        AdminedChannelCell cell = (AdminedChannelCell) view.getParent();
                        final TLRPC.Chat channel = cell.getCurrentChannel();
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString(R.string.AppName));
                        if (channel.megagroup) {
                            builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("RevokeLinkAlert", R.string.RevokeLinkAlert, MessagesController.getInstance(currentAccount).linkPrefix + "/" + ChatObject.getPublicUsername(channel), channel.title)));
                        } else {
                            builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("RevokeLinkAlertChannel", R.string.RevokeLinkAlertChannel, MessagesController.getInstance(currentAccount).linkPrefix  + "/" + ChatObject.getPublicUsername(channel), channel.title)));
                        }
                        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
                        builder.setPositiveButton(LocaleController.getString(R.string.RevokeButton), (dialogInterface, i) -> {
                            TLRPC.TL_channels_updateUsername req1 = new TLRPC.TL_channels_updateUsername();
                            req1.channel = MessagesController.getInputChannel(channel);
                            req1.username = "";
                            ConnectionsManager.getInstance(currentAccount).sendRequest(req1, (response1, error1) -> {
                                if (response1 instanceof TLRPC.TL_boolTrue) {
                                    AndroidUtilities.runOnUIThread(() -> {
                                        canCreatePublic = true;
                                        if (descriptionTextView.length() > 0) {
                                            checkUserName(descriptionTextView.getText().toString());
                                        }
                                        updatePrivatePublic();
                                    });
                                }
                            }, ConnectionsManager.RequestFlagInvokeAfter);
                        });
                        showDialog(builder.create());
                    }, false, 0);
                    adminedChannelCell.setChannel(res.chats.get(a), a == res.chats.size() - 1);
                    adminedChannelCells.add(adminedChannelCell);
                    adminnedChannelsLayout.addView(adminedChannelCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 72));
                }
                updatePrivatePublic();
            }
        }));
    }

    private boolean checkUserName(final String name) {
        if (name != null && name.length() > 0) {
            checkTextView.setVisibility(View.VISIBLE);
        } else {
            checkTextView.setVisibility(View.GONE);
        }
        if (checkRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(checkRunnable);
            checkRunnable = null;
            lastCheckName = null;
            if (checkReqId != 0) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(checkReqId, true);
            }
        }
        lastNameAvailable = false;
        if (name != null) {
            if (name.startsWith("_") || name.endsWith("_")) {
                checkTextView.setText(LocaleController.getString(R.string.LinkInvalid));
                checkTextView.setTag(Theme.key_text_RedRegular);
                checkTextView.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
                return false;
            }
            for (int a = 0; a < name.length(); a++) {
                char ch = name.charAt(a);
                if (a == 0 && ch >= '0' && ch <= '9') {
                    checkTextView.setText(LocaleController.getString(R.string.LinkInvalidStartNumber));
                    checkTextView.setTag(Theme.key_text_RedRegular);
                    checkTextView.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
                    return false;
                }
                if (!(ch >= '0' && ch <= '9' || ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z' || ch == '_')) {
                    checkTextView.setText(LocaleController.getString(R.string.LinkInvalid));
                    checkTextView.setTag(Theme.key_text_RedRegular);
                    checkTextView.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
                    return false;
                }
            }
        }
        if (name == null || name.length() < 4) {
            checkTextView.setText(LocaleController.getString(R.string.LinkInvalidShort));
            checkTextView.setTag(Theme.key_text_RedRegular);
            checkTextView.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
            return false;
        }
        if (name.length() > 32) {
            checkTextView.setText(LocaleController.getString(R.string.LinkInvalidLong));
            checkTextView.setTag(Theme.key_text_RedRegular);
            checkTextView.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
            return false;
        }

        checkTextView.setText(LocaleController.getString(R.string.LinkChecking));
        checkTextView.setTag(Theme.key_windowBackgroundWhiteGrayText8);
        checkTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText8));
        lastCheckName = name;
        checkRunnable = () -> {
            TLRPC.TL_channels_checkUsername req = new TLRPC.TL_channels_checkUsername();
            req.username = name;
            req.channel = MessagesController.getInstance(currentAccount).getInputChannel(chatId);
            checkReqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                checkReqId = 0;
                if (lastCheckName != null && lastCheckName.equals(name)) {
                    if (error == null && response instanceof TLRPC.TL_boolTrue) {
                        checkTextView.setText(LocaleController.formatString("LinkAvailable", R.string.LinkAvailable, name));
                        checkTextView.setTag(Theme.key_windowBackgroundWhiteGreenText);
                        checkTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGreenText));
                        lastNameAvailable = true;
                    } else {
                        if (error != null && "USERNAME_INVALID".equals(error.text) && req.username.length() == 4) {
                            checkTextView.setText(LocaleController.getString(R.string.UsernameInvalidShort));
                            checkTextView.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
                        } else if (error != null && "USERNAME_PURCHASE_AVAILABLE".equals(error.text)) {
                            if (req.username.length() == 4) {
                                checkTextView.setText(LocaleController.getString(R.string.UsernameInvalidShortPurchase));
                            } else {
                                checkTextView.setText(LocaleController.getString(R.string.UsernameInUsePurchase));
                            }
                            checkTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText8));
                        } else if (error != null && "CHANNELS_ADMIN_PUBLIC_TOO_MUCH".equals(error.text)) {
                            checkTextView.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
                            canCreatePublic = false;
                            showPremiumIncreaseLimitDialog();
                        } else {
                            checkTextView.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
                            checkTextView.setText(LocaleController.getString(R.string.LinkInUse));
                        }
                        lastNameAvailable = false;
                    }
                }
            }), ConnectionsManager.RequestFlagFailOnServerErrors);
        };
        AndroidUtilities.runOnUIThread(checkRunnable, 300);
        return true;
    }

    private void showErrorAlert(String error) {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.AppName));
        switch (error) {
            case "USERNAME_INVALID":
                builder.setMessage(LocaleController.getString(R.string.LinkInvalid));
                break;
            case "USERNAME_OCCUPIED":
                builder.setMessage(LocaleController.getString(R.string.LinkInUse));
                break;
            default:
                builder.setMessage(LocaleController.getString(R.string.ErrorOccurred));
                break;
        }
        builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
        showDialog(builder.create());
    }

    private void showPremiumIncreaseLimitDialog() {
        if (getParentActivity() == null) {
            return;
        }
        LimitReachedBottomSheet limitReachedBottomSheet = new LimitReachedBottomSheet(this, getParentActivity(), LimitReachedBottomSheet.TYPE_PUBLIC_LINKS, currentAccount, null);
        limitReachedBottomSheet.parentIsChannel = true;
        limitReachedBottomSheet.onSuccessRunnable = () -> {
            canCreatePublic = true;
            updatePrivatePublic();
        };
        showDialog(limitReachedBottomSheet);
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        ThemeDescription.ThemeDescriptionDelegate cellDelegate = () -> {
            if (adminnedChannelsLayout != null) {
                int count = adminnedChannelsLayout.getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = adminnedChannelsLayout.getChildAt(a);
                    if (child instanceof AdminedChannelCell) {
                        ((AdminedChannelCell) child).update();
                    }
                }
            }
        };

        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(nameTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(nameTextView, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText));
        themeDescriptions.add(new ThemeDescription(nameTextView, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputField));
        themeDescriptions.add(new ThemeDescription(nameTextView, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated));
        themeDescriptions.add(new ThemeDescription(descriptionTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(descriptionTextView, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText));
        themeDescriptions.add(new ThemeDescription(descriptionTextView, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputField));
        themeDescriptions.add(new ThemeDescription(descriptionTextView, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated));
        themeDescriptions.add(new ThemeDescription(helpTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText8));

        themeDescriptions.add(new ThemeDescription(linearLayout2, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(linkContainer, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(sectionCell, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(headerCell, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));
        themeDescriptions.add(new ThemeDescription(headerCell2, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));
        themeDescriptions.add(new ThemeDescription(editText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(editText, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText));
        themeDescriptions.add(new ThemeDescription(checkTextView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_text_RedRegular));
        themeDescriptions.add(new ThemeDescription(checkTextView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText8));
        themeDescriptions.add(new ThemeDescription(checkTextView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundWhiteGreenText));

        themeDescriptions.add(new ThemeDescription(typeInfoCell, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(typeInfoCell, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));
        themeDescriptions.add(new ThemeDescription(typeInfoCell, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_text_RedRegular));

        themeDescriptions.add(new ThemeDescription(adminedInfoCell, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(adminnedChannelsLayout, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(privateContainer, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
        themeDescriptions.add(new ThemeDescription(privateContainer, 0, new Class[]{TextBlockCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(loadingAdminedCell, 0, new Class[]{LoadingCell.class}, new String[]{"progressBar"}, null, null, null, Theme.key_progressCircle));
        themeDescriptions.add(new ThemeDescription(radioButtonCell1, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
        themeDescriptions.add(new ThemeDescription(radioButtonCell1, ThemeDescription.FLAG_CHECKBOX, new Class[]{RadioButtonCell.class}, new String[]{"radioButton"}, null, null, null, Theme.key_radioBackground));
        themeDescriptions.add(new ThemeDescription(radioButtonCell1, ThemeDescription.FLAG_CHECKBOXCHECK, new Class[]{RadioButtonCell.class}, new String[]{"radioButton"}, null, null, null, Theme.key_radioBackgroundChecked));
        themeDescriptions.add(new ThemeDescription(radioButtonCell1, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{RadioButtonCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(radioButtonCell1, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{RadioButtonCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(radioButtonCell2, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
        themeDescriptions.add(new ThemeDescription(radioButtonCell2, ThemeDescription.FLAG_CHECKBOX, new Class[]{RadioButtonCell.class}, new String[]{"radioButton"}, null, null, null, Theme.key_radioBackground));
        themeDescriptions.add(new ThemeDescription(radioButtonCell2, ThemeDescription.FLAG_CHECKBOXCHECK, new Class[]{RadioButtonCell.class}, new String[]{"radioButton"}, null, null, null, Theme.key_radioBackgroundChecked));
        themeDescriptions.add(new ThemeDescription(radioButtonCell2, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{RadioButtonCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(radioButtonCell2, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{RadioButtonCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));

        themeDescriptions.add(new ThemeDescription(adminnedChannelsLayout, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{AdminedChannelCell.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(adminnedChannelsLayout, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{AdminedChannelCell.class}, new String[]{"statusTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText));
        themeDescriptions.add(new ThemeDescription(adminnedChannelsLayout, ThemeDescription.FLAG_LINKCOLOR, new Class[]{AdminedChannelCell.class}, new String[]{"statusTextView"}, null, null, null, Theme.key_windowBackgroundWhiteLinkText));
        themeDescriptions.add(new ThemeDescription(adminnedChannelsLayout, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{AdminedChannelCell.class}, new String[]{"deleteButton"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, Theme.avatarDrawables, cellDelegate, Theme.key_avatar_text));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundRed));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundOrange));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundViolet));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundGreen));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundCyan));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundBlue));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundPink));

        return themeDescriptions;
    }
}
