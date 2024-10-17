package org.telegram.ui.Business;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.EditTextCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ChatAttachAlert;
import org.telegram.ui.Components.ChatGreetingsView;
import org.telegram.ui.Components.CircularProgressDrawable;
import org.telegram.ui.Components.CrossfadeDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.ui.ContentPreviewViewer;
import org.telegram.ui.Stories.recorder.EmojiBottomSheet;
import org.telegram.ui.Stories.recorder.KeyboardNotifier;
import org.telegram.ui.Stories.recorder.PreviewView;

import java.util.ArrayList;

public class BusinessIntroActivity extends UniversalFragment implements NotificationCenter.NotificationCenterDelegate {

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addObserver(this, NotificationCenter.userInfoDidLoad);
        MediaDataController.getInstance(currentAccount).checkStickers(MediaDataController.TYPE_IMAGE);
        MediaDataController.getInstance(currentAccount).loadRecents(MediaDataController.TYPE_IMAGE, false, true, false);
        MediaDataController.getInstance(currentAccount).loadRecents(MediaDataController.TYPE_FAVE, false, true, false);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        getNotificationCenter().removeObserver(this, NotificationCenter.userInfoDidLoad);
        super.onFragmentDestroy();
    }

    private final static long updateRandomStickerTimeout = 1000 * 5;
    private final Runnable updateRandomStickerRunnable = this::updateRandomSticker;
    private void updateRandomSticker() {
        if (greetingsView == null || !greetingsView.isAttachedToWindow() || !stickerRandom) {
            return;
        }
        greetingsView.setNextSticker(MediaDataController.getInstance(currentAccount).getGreetingsSticker(), () -> {
            AndroidUtilities.cancelRunOnUIThread(updateRandomStickerRunnable);
            AndroidUtilities.runOnUIThread(updateRandomStickerRunnable, updateRandomStickerTimeout);
        });
    }

    private static final int done_button = 1;
    private CrossfadeDrawable doneButtonDrawable;
    private ActionBarMenuItem doneButton;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.BusinessIntro);
    }

    private FrameLayout previewContainer;
    private ChatGreetingsView greetingsView;
    private Drawable greetingsViewBackground;
    private EditTextCell titleEdit;
    private EditTextCell messageEdit;

    private boolean stickerRandom = true;
    private TLRPC.Document sticker = getMediaDataController().getGreetingsSticker();

    private String inputStickerPath;
    private TLRPC.InputDocument inputSticker;

    private boolean keyboardVisible;

    @Override
    public View createView(Context context) {
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);

        greetingsView = new ChatGreetingsView(context, getUserConfig().getCurrentUser(), currentAccount, sticker, getResourceProvider()) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                setPivotX(getMeasuredWidth() / 2f);
                setPivotY(getMeasuredHeight());
            }
        };
        previewContainer = new FrameLayout(context) {
            int minHeight = -1;
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                greetingsView.measure(widthMeasureSpec, heightMeasureSpec);
                invalidate();
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(Math.max(minHeight, greetingsView.getMeasuredHeight() + dp(18 * 2)), MeasureSpec.EXACTLY));
                if (minHeight < 0) {
                    minHeight = getMeasuredHeight();
                }
            }

            private final Rect bg = new Rect();
            private final AnimatedFloat width = new AnimatedFloat(this, 220, CubicBezierInterpolator.EASE_OUT_QUINT);

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                final float cx = getWidth() / 2f;
                final float width = this.width.set(greetingsView.getWidth());
                bg.set(
                        (int) (cx - width / 2f * greetingsView.getScaleX()),
                        (int) (greetingsView.getY() + greetingsView.getHeight() * (1f - greetingsView.getScaleY())),
                        (int) (cx + width / 2f * greetingsView.getScaleX()),
                        (int) (greetingsView.getY() + greetingsView.getHeight())
                );
                greetingsViewBackground.setBounds(bg);
                greetingsViewBackground.draw(canvas);
                return super.drawChild(canvas, child, drawingTime);
            }
        };
        previewContainer.setWillNotDraw(false);
        greetingsViewBackground = Theme.createServiceDrawable(AndroidUtilities.dp(16), greetingsView, previewContainer, getThemedPaint(Theme.key_paint_chatActionBackground));
        greetingsView.setBackground(new ColorDrawable(0));
        ImageView previewBackground = new ImageView(context) {
            // lack of ImageView.ScaleType.CROP_START
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                final Matrix matrix = getImageMatrix();
                float scale;
                final int viewWidth = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
                final int viewHeight = getMeasuredHeight() - getPaddingTop() - getPaddingBottom();
                final int drawableWidth = getDrawable().getIntrinsicWidth();
                final int drawableHeight = getDrawable().getIntrinsicHeight();
                if (drawableWidth * viewHeight > drawableHeight * viewWidth) {
                    scale = (float) viewHeight / (float) drawableHeight;
                } else {
                    scale = (float) viewWidth / (float) drawableWidth;
                }
                matrix.setScale(scale, scale);
                setImageMatrix(matrix);
            }
        };
        previewBackground.setScaleType(ImageView.ScaleType.MATRIX);
        previewBackground.setImageDrawable(PreviewView.getBackgroundDrawable(null, currentAccount, getUserConfig().getClientUserId(), Theme.isCurrentThemeDark()));
        previewContainer.addView(previewBackground, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        previewContainer.addView(greetingsView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 42, 18, 42, 18));

        titleEdit = new EditTextCell(context, getString(R.string.BusinessIntroTitleHint), false, false, getMessagesController().introTitleLengthLimit, resourceProvider) {
            @Override
            protected void onTextChanged(CharSequence newText) {
                greetingsView.setPreview(titleEdit.getText().toString(), messageEdit.getText().toString());
                checkDone(true, true);
            }

            @Override
            protected void onFocusChanged(boolean focused) {
                if (focused && listView != null) {
                    listView.smoothScrollToPosition(2);
                }
            }
        };
        titleEdit.autofocused = true;
        titleEdit.setShowLimitOnFocus(true);
        titleEdit.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        titleEdit.setDivider(true);
        titleEdit.hideKeyboardOnEnter();
        messageEdit = new EditTextCell(context, getString(R.string.BusinessIntroMessageHint), true, false, getMessagesController().introDescriptionLengthLimit, resourceProvider) {
            @Override
            protected void onTextChanged(CharSequence newText) {
                greetingsView.setPreview(titleEdit.getText().toString(), messageEdit.getText().toString());
                checkDone(true, true);
            }

            @Override
            protected void onFocusChanged(boolean focused) {
                if (focused && listView != null) {
                    listView.smoothScrollToPosition(3);
                }
            }
        };
        messageEdit.setShowLimitOnFocus(true);
        messageEdit.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        messageEdit.setDivider(true);
        messageEdit.hideKeyboardOnEnter();

        greetingsView.setPreview("", "");

        super.createView(context);

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (onBackPressed()) {
                        finishFragment();
                    }
                } else if (id == done_button) {
                    processDone();
                }
            }
        });
        Drawable checkmark = context.getResources().getDrawable(R.drawable.ic_ab_done).mutate();
        checkmark.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultIcon), PorterDuff.Mode.MULTIPLY));
        doneButtonDrawable = new CrossfadeDrawable(checkmark, new CircularProgressDrawable(Theme.getColor(Theme.key_actionBarDefaultIcon)));
        doneButton = actionBar.createMenu().addItemWithWidth(done_button, doneButtonDrawable, AndroidUtilities.dp(56), LocaleController.getString(R.string.Done));
        checkDone(false, true);

        listView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                updateGreetingScale();
            }
        });
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                updateGreetingScale();
            }
        });
        listView.doNotDetachViews();
        listView.setClipChildren(false);
        if (fragmentView instanceof ViewGroup) {
            ((ViewGroup) fragmentView).setClipChildren(false);
        }

        setValue();

        new KeyboardNotifier(fragmentView, keyboardHeight -> {
            final boolean keyboardVisible = keyboardHeight > dp(20);
            if (this.keyboardVisible == keyboardVisible) {
                return;
            }
            this.keyboardVisible = keyboardVisible;
            if (!keyboardVisible) {
                listView.smoothScrollToPosition(0);
            }
        });

        return fragmentView;
    }

    private void updateGreetingScale() {
        if (!(previewContainer.getParent() instanceof View)) return;
        View cell = (View) previewContainer.getParent();
        int top = cell.getTop();
        int fullHeight = previewContainer.getMeasuredHeight() - dp(18 * 2);
        int visibleHeight = fullHeight + top;
        float scale = Utilities.clamp(visibleHeight / (float) fullHeight, 1, 0.65f);
        greetingsView.setScaleX(scale);
        greetingsView.setScaleY(scale);
        greetingsView.setAlpha(Utilities.clamp(2 * scale, 1, 0));
        previewContainer.invalidate();
    }

    private static final int BUTTON_STICKER = 1;
    private static final int BUTTON_REMOVE = 2;

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCustom(previewContainer));
        items.add(UItem.asHeader(getString(R.string.BusinessIntroHeader)));
        items.add(UItem.asCustom(titleEdit));
        items.add(UItem.asCustom(messageEdit));
        if (stickerRandom) {
            items.add(UItem.asButton(BUTTON_STICKER, getString(R.string.BusinessIntroSticker), getString(R.string.BusinessIntroStickerRandom)));
        } else if (inputStickerPath != null) {
            items.add(UItem.asStickerButton(BUTTON_STICKER, getString(R.string.BusinessIntroSticker), inputStickerPath));
        } else {
            items.add(UItem.asStickerButton(BUTTON_STICKER, getString(R.string.BusinessIntroSticker), sticker));
        }
        items.add(UItem.asShadow(getString(R.string.BusinessIntroInfo)));
        if (clearVisible = !isEmpty()) {
            items.add(UItem.asShadow(null));
            items.add(UItem.asButton(BUTTON_REMOVE, LocaleController.getString(R.string.BusinessIntroReset)).red());
        }
        items.add(UItem.asLargeShadow(null));
    }

    public boolean isEmpty() {
        if (titleEdit == null || messageEdit == null) return true;
        return TextUtils.isEmpty(titleEdit.getText()) && TextUtils.isEmpty(messageEdit.getText()) && stickerRandom;
    }

    private String currentTitle, currentMessage;
    private long currentSticker;

    private boolean valueSet;
    private void setValue() {
        if (valueSet) return;

        final long selfId = getUserConfig().getClientUserId();
        TLRPC.UserFull userFull = getMessagesController().getUserFull(selfId);
        if (userFull == null) {
            getMessagesController().loadUserInfo(getUserConfig().getCurrentUser(), true, getClassGuid());
            return;
        }

        if (userFull.business_intro != null) {
            titleEdit.setText(currentTitle = userFull.business_intro.title);
            messageEdit.setText(currentMessage = userFull.business_intro.description);
            sticker = userFull.business_intro.sticker;
        } else {
            titleEdit.setText(currentTitle = "");
            messageEdit.setText(currentMessage = "");
            inputSticker = null;
            sticker = null;
        }
        currentSticker = sticker == null ? 0 : sticker.id;
        stickerRandom = sticker == null;

        if (greetingsView != null) {
            greetingsView.setPreview(titleEdit.getText().toString(), messageEdit.getText().toString());
            greetingsView.setSticker(sticker == null || stickerRandom ? MediaDataController.getInstance(currentAccount).getGreetingsSticker() : sticker);
        }

        if (stickerRandom) {
            AndroidUtilities.cancelRunOnUIThread(updateRandomStickerRunnable);
            AndroidUtilities.runOnUIThread(updateRandomStickerRunnable, updateRandomStickerTimeout);
        }

        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
        valueSet = true;
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == BUTTON_STICKER) {
            EmojiBottomSheet sheet = new EmojiBottomSheet(getContext(), true, getResourceProvider(), true);
            sheet.whenDocumentSelected((parentObject, document, a) -> {
                stickerRandom = false;
                AndroidUtilities.cancelRunOnUIThread(updateRandomStickerRunnable);
                greetingsView.setSticker(sticker = document);
                ((TextCell) view).setValueSticker(document);
                checkDone(true, false);
                return true;
            });
            sheet.whenPlusSelected(this::openCustomStickerEditor);
            showDialog(sheet);
        } else if (item.id == BUTTON_REMOVE) {
            titleEdit.setText("");
            messageEdit.setText("");
            AndroidUtilities.hideKeyboard(titleEdit.editText);
            AndroidUtilities.hideKeyboard(messageEdit.editText);
            stickerRandom = true;
            greetingsView.setPreview("", "");
            greetingsView.setSticker(sticker = MediaDataController.getInstance(currentAccount).getGreetingsSticker());
            AndroidUtilities.cancelRunOnUIThread(updateRandomStickerRunnable);
            AndroidUtilities.runOnUIThread(updateRandomStickerRunnable, updateRandomStickerTimeout);
            checkDone(true, false);
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    private boolean clearVisible = isEmpty();

    public boolean hasChanges() {
        return (
            !TextUtils.equals(titleEdit.getText().toString(), currentTitle == null ? "" : currentTitle) ||
            !TextUtils.equals(messageEdit.getText().toString(), currentMessage == null ? "" : currentMessage) ||
            (stickerRandom || sticker == null ? 0 : sticker.id) != currentSticker || (!stickerRandom && inputSticker != null)
        );
    }

    private void checkDone(boolean animated, boolean ignoreClear) {
        if (doneButton == null) return;
        final boolean hasChanges = hasChanges();
        doneButton.setEnabled(hasChanges);
        if (animated) {
            doneButton.animate().alpha(hasChanges ? 1.0f : 0.0f).scaleX(hasChanges ? 1.0f : 0.0f).scaleY(hasChanges ? 1.0f : 0.0f).setDuration(180).start();
        } else {
            doneButton.setAlpha(hasChanges ? 1.0f : 0.0f);
            doneButton.setScaleX(hasChanges ? 1.0f : 0.0f);
            doneButton.setScaleY(hasChanges ? 1.0f : 0.0f);
        }
        if (listView != null && listView.adapter != null && clearVisible != !isEmpty()) {
            saveScrollPosition();
            listView.adapter.update(true);
            applyScrolledPosition();
        }
    }

    private int shiftDp = -4;
    private void processDone() {
        if (doneButtonDrawable.getProgress() > 0f) return;

//        if (!isEmpty()) {
//            if (TextUtils.isEmpty(titleEdit.getText())) {
//                BotWebViewVibrationEffect.APP_ERROR.vibrate();
//                AndroidUtilities.shakeViewSpring(titleEdit, shiftDp = -shiftDp);
//                return;
//            }
//            if (TextUtils.isEmpty(messageEdit.getText())) {
//                BotWebViewVibrationEffect.APP_ERROR.vibrate();
//                AndroidUtilities.shakeViewSpring(messageEdit, shiftDp = -shiftDp);
//                return;
//            }
//        }

        doneButtonDrawable.animateToProgress(1f);
        TLRPC.UserFull userFull = getMessagesController().getUserFull(getUserConfig().getClientUserId());
        TLRPC.TL_account_updateBusinessIntro req = new TLRPC.TL_account_updateBusinessIntro();
        if (!isEmpty()) {
            req.flags |= 1;
            req.intro = new TLRPC.TL_inputBusinessIntro();
            req.intro.title = titleEdit.getText().toString();
            req.intro.description = messageEdit.getText().toString();
            if (!stickerRandom && (sticker != null || inputSticker != null)) {
                req.intro.flags |= 1;
                if (inputSticker != null) {
                    req.intro.sticker = inputSticker;
                } else {
                    req.intro.sticker = getMessagesController().getInputDocument(sticker);
                }
            }

            if (userFull != null) {
                userFull.flags2 |= 16;
                userFull.business_intro = new TLRPC.TL_businessIntro();
                userFull.business_intro.title = req.intro.title;
                userFull.business_intro.description = req.intro.description;
                if (!stickerRandom && sticker != null) {
                    userFull.business_intro.flags |= 1;
                    userFull.business_intro.sticker = sticker;
                }
            }
        } else {
            if (userFull != null) {
                userFull.flags2 &=~ 16;
                userFull.business_intro = null;
            }
        }

        getConnectionsManager().sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (err != null) {
                doneButtonDrawable.animateToProgress(0f);
                BulletinFactory.showError(err);
            } else if (res instanceof TLRPC.TL_boolFalse) {
                doneButtonDrawable.animateToProgress(0f);
                BulletinFactory.of(this).createErrorBulletin(LocaleController.getString(R.string.UnknownError)).show();
            } else {
                if (inputSticker != null) {
                    getMessagesController().loadFullUser(getUserConfig().getCurrentUser(), 0, true);
                }
                finishFragment();
            }
        }));
        getMessagesStorage().updateUserInfo(userFull, false);
    }

    @Override
    public boolean onBackPressed() {
        if (hasChanges()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(LocaleController.getString(R.string.UnsavedChanges));
            builder.setMessage(LocaleController.getString(R.string.BusinessIntroUnsavedChanges));
            builder.setPositiveButton(LocaleController.getString(R.string.ApplyTheme), (dialogInterface, i) -> processDone());
            builder.setNegativeButton(LocaleController.getString(R.string.PassportDiscard), (dialog, which) -> finishFragment());
            showDialog(builder.create());
            return false;
        }
        return super.onBackPressed();
    }


    private void openCustomStickerEditor() {
        ContentPreviewViewer.getInstance().setStickerSetForCustomSticker(null);
        if (getParentActivity() == null) {
            return;
        }
        createChatAttachView();
        chatAttachAlert.getPhotoLayout().loadGalleryPhotos();
        chatAttachAlert.setMaxSelectedPhotos(1, false);
        chatAttachAlert.setOpenWithFrontFaceCamera(true);
        chatAttachAlert.enableStickerMode(this::setCustomSticker);
        chatAttachAlert.init();
        chatAttachAlert.parentThemeDelegate = null;
        if (visibleDialog != null) {
            chatAttachAlert.show();
        } else {
            showDialog(chatAttachAlert);
        }
    }

    private ChatAttachAlert chatAttachAlert;
    private void createChatAttachView() {
        if (getParentActivity() == null || getContext() == null) {
            return;
        }
        if (chatAttachAlert == null) {
            chatAttachAlert = new ChatAttachAlert(getParentActivity(), this, false, false, true, resourceProvider) {
                @Override
                public void dismissInternal() {
                    if (chatAttachAlert != null && chatAttachAlert.isShowing()) {
                        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
                    }
                    super.dismissInternal();
                }

                @Override
                public void onDismissAnimationStart() {
                    if (chatAttachAlert != null) {
                        chatAttachAlert.setFocusable(false);
                    }
                    if (chatAttachAlert != null && chatAttachAlert.isShowing()) {
                        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
                    }
                }
            };
            chatAttachAlert.setDelegate(new ChatAttachAlert.ChatAttachViewDelegate() {
                @Override
                public void didPressedButton(int button, boolean arg, boolean notify, int scheduleDate, long effectId, boolean invertMedia, boolean forceDocument) {

                }
                @Override
                public void doOnIdle(Runnable runnable) {
                    NotificationCenter.getInstance(currentAccount).doOnIdle(runnable);
                }
            });
        }
    }

    private void setCustomSticker(String localPath, TLRPC.InputDocument inputDocument) {
        chatAttachAlert.dismiss();

        inputStickerPath = localPath;
        inputSticker = inputDocument;

        stickerRandom = false;
        AndroidUtilities.cancelRunOnUIThread(updateRandomStickerRunnable);
        greetingsView.setSticker(inputStickerPath);
        checkDone(true, false);
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.userInfoDidLoad) {
            setValue();
        }
    }

}
