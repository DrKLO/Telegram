/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.InputFilter;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.PhotoPickerAlbumsCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.EditTextEmoji;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;

import java.util.ArrayList;
import java.util.HashMap;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class PhotoAlbumPickerActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    public interface PhotoAlbumPickerActivityDelegate {
        void didSelectPhotos(ArrayList<SendMessagesHelper.SendingMediaInfo> photos, boolean notify, int scheduleDate);
        void startPhotoSelectActivity();
    }

    private CharSequence caption;
    private HashMap<Object, Object> selectedPhotos = new HashMap<>();
    private ArrayList<Object> selectedPhotosOrder = new ArrayList<>();

    private ArrayList<MediaController.AlbumEntry> albumsSorted = null;

    private boolean loading = false;

    private int columnsCount = 2;
    private RecyclerListView listView;
    private ListAdapter listAdapter;
    private FrameLayout progressView;
    private TextView emptyView;
    private boolean sendPressed;
    private int selectPhotoType;
    private boolean allowSearchImages = true;
    private boolean allowGifs;
    private boolean allowCaption;
    private ChatActivity chatActivity;
    private int maxSelectedPhotos;
    private boolean allowOrder = true;

    private ActionBarPopupWindow sendPopupWindow;
    private ActionBarPopupWindow.ActionBarPopupWindowLayout sendPopupLayout;
    private ActionBarMenuSubItem[] itemCells;

    private FrameLayout frameLayout2;
    private EditTextEmoji commentTextView;
    private FrameLayout writeButtonContainer;
    private ImageView writeButton;
    private Drawable writeButtonDrawable;
    private SizeNotifierFrameLayout sizeNotifierFrameLayout;
    private View selectedCountView;
    private View shadow;
    private TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private RectF rect = new RectF();
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private PhotoAlbumPickerActivityDelegate delegate;

    public static int SELECT_TYPE_ALL = 0;
    public static int SELECT_TYPE_AVATAR = 1;
    public static int SELECT_TYPE_WALLPAPER = 2;
    public static int SELECT_TYPE_AVATAR_VIDEO = 3;
    public static int SELECT_TYPE_QR = 10;

    public PhotoAlbumPickerActivity(int selectPhotoType, boolean allowGifs, boolean allowCaption, ChatActivity chatActivity) {
        super();
        this.chatActivity = chatActivity;
        this.selectPhotoType = selectPhotoType;
        this.allowGifs = allowGifs;
        this.allowCaption = allowCaption;
    }

    @Override
    public boolean onFragmentCreate() {
        if (selectPhotoType == SELECT_TYPE_AVATAR || selectPhotoType == SELECT_TYPE_WALLPAPER || selectPhotoType == SELECT_TYPE_QR || !allowSearchImages) {
            albumsSorted = MediaController.allPhotoAlbums;
        } else {
            albumsSorted = MediaController.allMediaAlbums;
        }
        loading = albumsSorted == null;
        MediaController.loadGalleryPhotosAlbums(classGuid);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.albumsDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.closeChats);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        if (commentTextView != null) {
            commentTextView.onDestroy();
        }
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.albumsDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.closeChats);
        super.onFragmentDestroy();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));
        actionBar.setTitleColor(Theme.getColor(Theme.key_dialogTextBlack));
        actionBar.setItemsColor(Theme.getColor(Theme.key_dialogTextBlack), false);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_dialogButtonSelector), false);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == 1) {
                    if (delegate != null) {
                        finishFragment(false);
                        delegate.startPhotoSelectActivity();
                    }
                } else if (id == 2) {
                    openPhotoPicker(null, 0);
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        if (allowSearchImages) {
            menu.addItem(2, R.drawable.ic_ab_search).setContentDescription(LocaleController.getString("Search", R.string.Search));
        }
        ActionBarMenuItem menuItem = menu.addItem(0, R.drawable.ic_ab_other);
        menuItem.setContentDescription(LocaleController.getString("AccDescrMoreOptions", R.string.AccDescrMoreOptions));
        menuItem.addSubItem(1, R.drawable.msg_openin, LocaleController.getString("OpenInExternalApp", R.string.OpenInExternalApp));

        sizeNotifierFrameLayout = new SizeNotifierFrameLayout(context) {

            private int lastNotifyWidth;
            private boolean ignoreLayout;

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int widthSize = MeasureSpec.getSize(widthMeasureSpec);
                int heightSize = MeasureSpec.getSize(heightMeasureSpec);

                setMeasuredDimension(widthSize, heightSize);

                int keyboardSize = SharedConfig.smoothKeyboard ? 0 : measureKeyboardHeight();
                if (keyboardSize <= AndroidUtilities.dp(20)) {
                    if (!AndroidUtilities.isInMultiwindow) {
                        heightSize -= commentTextView.getEmojiPadding();
                        heightMeasureSpec = MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY);
                    }
                } else {
                    ignoreLayout = true;
                    commentTextView.hideEmojiView();
                    ignoreLayout = false;
                }

                int childCount = getChildCount();
                for (int i = 0; i < childCount; i++) {
                    View child = getChildAt(i);
                    if (child == null || child.getVisibility() == GONE) {
                        continue;
                    }
                    if (commentTextView != null && commentTextView.isPopupView(child)) {
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
                if (lastNotifyWidth != r - l) {
                    lastNotifyWidth = r - l;
                    if (sendPopupWindow != null && sendPopupWindow.isShowing()) {
                        sendPopupWindow.dismiss();
                    }
                }
                final int count = getChildCount();

                int keyboardSize = SharedConfig.smoothKeyboard ? 0 : measureKeyboardHeight();
                int paddingBottom = keyboardSize <= AndroidUtilities.dp(20) && !AndroidUtilities.isInMultiwindow && !AndroidUtilities.isTablet() ? commentTextView.getEmojiPadding() : 0;
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
                            childLeft = (r - l) - width - lp.rightMargin - getPaddingRight();
                            break;
                        case Gravity.LEFT:
                        default:
                            childLeft = lp.leftMargin + getPaddingLeft();
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

                    if (commentTextView != null && commentTextView.isPopupView(child)) {
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
        sizeNotifierFrameLayout.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));
        fragmentView = sizeNotifierFrameLayout;

        actionBar.setTitle(LocaleController.getString("Gallery", R.string.Gallery));

        listView = new RecyclerListView(context);
        listView.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(4), AndroidUtilities.dp(6), AndroidUtilities.dp(54));
        listView.setClipToPadding(false);
        listView.setHorizontalScrollBarEnabled(false);
        listView.setVerticalScrollBarEnabled(false);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setDrawingCacheEnabled(false);
        sizeNotifierFrameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));
        listView.setAdapter(listAdapter = new ListAdapter(context));
        listView.setGlowColor(Theme.getColor(Theme.key_dialogBackground));

        emptyView = new TextView(context);
        emptyView.setTextColor(0xff808080);
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setVisibility(View.GONE);
        emptyView.setText(LocaleController.getString("NoPhotos", R.string.NoPhotos));
        sizeNotifierFrameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 48));
        emptyView.setOnTouchListener((v, event) -> true);

        progressView = new FrameLayout(context);
        progressView.setVisibility(View.GONE);
        sizeNotifierFrameLayout.addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 48));

        RadialProgressView progressBar = new RadialProgressView(context);
        progressBar.setProgressColor(0xff527da3);
        progressView.addView(progressBar, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        shadow = new View(context);
        shadow.setBackgroundResource(R.drawable.header_shadow_reverse);
        shadow.setTranslationY(AndroidUtilities.dp(48));
        sizeNotifierFrameLayout.addView(shadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3, Gravity.BOTTOM | Gravity.LEFT, 0, 0, 0, 48));

        frameLayout2 = new FrameLayout(context);
        frameLayout2.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));
        frameLayout2.setVisibility(View.INVISIBLE);
        frameLayout2.setTranslationY(AndroidUtilities.dp(48));
        sizeNotifierFrameLayout.addView(frameLayout2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.BOTTOM));
        frameLayout2.setOnTouchListener((v, event) -> true);

        if (commentTextView != null) {
            commentTextView.onDestroy();
        }
        commentTextView = new EditTextEmoji(context, sizeNotifierFrameLayout, null, EditTextEmoji.STYLE_DIALOG);
        InputFilter[] inputFilters = new InputFilter[1];
        inputFilters[0] = new InputFilter.LengthFilter(MessagesController.getInstance(UserConfig.selectedAccount).maxCaptionLength);
        commentTextView.setFilters(inputFilters);
        commentTextView.setHint(LocaleController.getString("AddCaption", R.string.AddCaption));
        EditTextBoldCursor editText = commentTextView.getEditText();
        editText.setMaxLines(1);
        editText.setSingleLine(true);
        frameLayout2.addView(commentTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 0, 84, 0));
        if (caption != null) {
            commentTextView.setText(caption);
        }

        writeButtonContainer = new FrameLayout(context) {
            @Override
            public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info);
                info.setText(LocaleController.formatPluralString("AccDescrSendPhotos", selectedPhotos.size()));
                info.setClassName(Button.class.getName());
                info.setLongClickable(true);
                info.setClickable(true);
            }
        };
        writeButtonContainer.setFocusable(true);
        writeButtonContainer.setFocusableInTouchMode(true);
        writeButtonContainer.setVisibility(View.INVISIBLE);
        writeButtonContainer.setScaleX(0.2f);
        writeButtonContainer.setScaleY(0.2f);
        writeButtonContainer.setAlpha(0.0f);
        sizeNotifierFrameLayout.addView(writeButtonContainer, LayoutHelper.createFrame(60, 60, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 12, 10));

        writeButton = new ImageView(context);
        writeButtonDrawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(56), Theme.getColor(Theme.key_dialogFloatingButton), Theme.getColor(Build.VERSION.SDK_INT >= 21 ? Theme.key_dialogFloatingButtonPressed : Theme.key_dialogFloatingButton));
        if (Build.VERSION.SDK_INT < 21) {
            Drawable shadowDrawable = context.getResources().getDrawable(R.drawable.floating_shadow_profile).mutate();
            shadowDrawable.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
            CombinedDrawable combinedDrawable = new CombinedDrawable(shadowDrawable, writeButtonDrawable, 0, 0);
            combinedDrawable.setIconSize(AndroidUtilities.dp(56), AndroidUtilities.dp(56));
            writeButtonDrawable = combinedDrawable;
        }
        writeButton.setBackgroundDrawable(writeButtonDrawable);
        writeButton.setImageResource(R.drawable.attach_send);
        writeButton.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        writeButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogFloatingIcon), PorterDuff.Mode.MULTIPLY));
        writeButton.setScaleType(ImageView.ScaleType.CENTER);
        if (Build.VERSION.SDK_INT >= 21) {
            writeButton.setOutlineProvider(new ViewOutlineProvider() {
                @SuppressLint("NewApi")
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, AndroidUtilities.dp(56), AndroidUtilities.dp(56));
                }
            });
        }
        writeButtonContainer.addView(writeButton, LayoutHelper.createFrame(Build.VERSION.SDK_INT >= 21 ? 56 : 60, Build.VERSION.SDK_INT >= 21 ? 56 : 60, Gravity.LEFT | Gravity.TOP, Build.VERSION.SDK_INT >= 21 ? 2 : 0, 0, 0, 0));
        writeButton.setOnClickListener(v -> {
            if (chatActivity != null && chatActivity.isInScheduleMode()) {
                AlertsCreator.createScheduleDatePickerDialog(getParentActivity(), chatActivity.getDialogId(), (notify, scheduleDate) -> {
                    sendSelectedPhotos(selectedPhotos, selectedPhotosOrder, notify, scheduleDate);
                    finishFragment();
                });
            } else {
                sendSelectedPhotos(selectedPhotos, selectedPhotosOrder, true, 0);
                finishFragment();
            }
        });
        writeButton.setOnLongClickListener(view -> {
            if (chatActivity == null || maxSelectedPhotos == 1) {
                return false;
            }
            TLRPC.Chat chat = chatActivity.getCurrentChat();
            TLRPC.User user = chatActivity.getCurrentUser();

            if (sendPopupLayout == null) {
                sendPopupLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(getParentActivity());
                sendPopupLayout.setAnimationEnabled(false);
                sendPopupLayout.setOnTouchListener(new View.OnTouchListener() {

                    private android.graphics.Rect popupRect = new android.graphics.Rect();

                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                            if (sendPopupWindow != null && sendPopupWindow.isShowing()) {
                                v.getHitRect(popupRect);
                                if (!popupRect.contains((int) event.getX(), (int) event.getY())) {
                                    sendPopupWindow.dismiss();
                                }
                            }
                        }
                        return false;
                    }
                });
                sendPopupLayout.setDispatchKeyEventListener(keyEvent -> {
                    if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_BACK && keyEvent.getRepeatCount() == 0 && sendPopupWindow != null && sendPopupWindow.isShowing()) {
                        sendPopupWindow.dismiss();
                    }
                });
                sendPopupLayout.setShownFromBotton(false);

                itemCells = new ActionBarMenuSubItem[2];
                for (int a = 0; a < 2; a++) {
                    if (a == 0 && !chatActivity.canScheduleMessage() || a == 1 && UserObject.isUserSelf(user)) {
                        continue;
                    }
                    int num = a;
                    itemCells[a] = new ActionBarMenuSubItem(getParentActivity(), a == 0, a ==  1);
                    if (num == 0) {
                        if (UserObject.isUserSelf(user)) {
                            itemCells[a].setTextAndIcon(LocaleController.getString("SetReminder", R.string.SetReminder), R.drawable.msg_schedule);
                        } else {
                            itemCells[a].setTextAndIcon(LocaleController.getString("ScheduleMessage", R.string.ScheduleMessage), R.drawable.msg_schedule);
                        }
                    } else {
                        itemCells[a].setTextAndIcon(LocaleController.getString("SendWithoutSound", R.string.SendWithoutSound), R.drawable.input_notify_off);
                    }
                    itemCells[a].setMinimumWidth(AndroidUtilities.dp(196));

                    sendPopupLayout.addView(itemCells[a], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
                    itemCells[a].setOnClickListener(v -> {
                        if (sendPopupWindow != null && sendPopupWindow.isShowing()) {
                            sendPopupWindow.dismiss();
                        }
                        if (num == 0) {
                            AlertsCreator.createScheduleDatePickerDialog(getParentActivity(), chatActivity.getDialogId(), (notify, scheduleDate) -> {
                                sendSelectedPhotos(selectedPhotos, selectedPhotosOrder, notify, scheduleDate);
                                finishFragment();
                            });
                        } else {
                            sendSelectedPhotos(selectedPhotos, selectedPhotosOrder, true, 0);
                            finishFragment();
                        }
                    });
                }
                sendPopupLayout.setupRadialSelectors(Theme.getColor(Theme.key_dialogButtonSelector));

                sendPopupWindow = new ActionBarPopupWindow(sendPopupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
                sendPopupWindow.setAnimationEnabled(false);
                sendPopupWindow.setAnimationStyle(R.style.PopupContextAnimation2);
                sendPopupWindow.setOutsideTouchable(true);
                sendPopupWindow.setClippingEnabled(true);
                sendPopupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
                sendPopupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
                sendPopupWindow.getContentView().setFocusableInTouchMode(true);
            }

            sendPopupLayout.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST));
            sendPopupWindow.setFocusable(true);
            int[] location = new int[2];
            view.getLocationInWindow(location);
            sendPopupWindow.showAtLocation(view, Gravity.LEFT | Gravity.TOP, location[0] + view.getMeasuredWidth() - sendPopupLayout.getMeasuredWidth() + AndroidUtilities.dp(8), location[1] - sendPopupLayout.getMeasuredHeight() - AndroidUtilities.dp(2));
            sendPopupWindow.dimBehind();
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);

            return false;
        });

        textPaint.setTextSize(AndroidUtilities.dp(12));
        textPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

        selectedCountView = new View(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                String text = String.format("%d", Math.max(1, selectedPhotosOrder.size()));
                int textSize = (int) Math.ceil(textPaint.measureText(text));
                int size = Math.max(AndroidUtilities.dp(16) + textSize, AndroidUtilities.dp(24));
                int cx = getMeasuredWidth() / 2;
                int cy = getMeasuredHeight() / 2;

                textPaint.setColor(Theme.getColor(Theme.key_dialogRoundCheckBoxCheck));
                paint.setColor(Theme.getColor(Theme.key_dialogBackground));
                rect.set(cx - size / 2, 0, cx + size / 2, getMeasuredHeight());
                canvas.drawRoundRect(rect, AndroidUtilities.dp(12), AndroidUtilities.dp(12), paint);

                paint.setColor(Theme.getColor(Theme.key_dialogRoundCheckBox));
                rect.set(cx - size / 2 + AndroidUtilities.dp(2), AndroidUtilities.dp(2), cx + size / 2 - AndroidUtilities.dp(2), getMeasuredHeight() - AndroidUtilities.dp(2));
                canvas.drawRoundRect(rect, AndroidUtilities.dp(10), AndroidUtilities.dp(10), paint);

                canvas.drawText(text, cx - textSize / 2, AndroidUtilities.dp(16.2f), textPaint);
            }
        };
        selectedCountView.setAlpha(0.0f);
        selectedCountView.setScaleX(0.2f);
        selectedCountView.setScaleY(0.2f);
        sizeNotifierFrameLayout.addView(selectedCountView, LayoutHelper.createFrame(42, 24, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, -2, 9));
        if (selectPhotoType != SELECT_TYPE_ALL) {
            commentTextView.setVisibility(View.GONE);
        }

        if (loading && (albumsSorted == null || albumsSorted.isEmpty())) {
            progressView.setVisibility(View.VISIBLE);
            listView.setEmptyView(null);
        } else {
            progressView.setVisibility(View.GONE);
            listView.setEmptyView(emptyView);
        }

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        if (commentTextView != null) {
            commentTextView.onResume();
        }
        fixLayout();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        fixLayout();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.albumsDidLoad) {
            int guid = (Integer) args[0];
            if (classGuid == guid) {
                if (selectPhotoType == SELECT_TYPE_AVATAR || selectPhotoType == SELECT_TYPE_WALLPAPER || selectPhotoType == SELECT_TYPE_QR || !allowSearchImages) {
                    albumsSorted = (ArrayList<MediaController.AlbumEntry>) args[2];
                } else {
                    albumsSorted = (ArrayList<MediaController.AlbumEntry>) args[1];
                }
                if (progressView != null) {
                    progressView.setVisibility(View.GONE);
                }
                if (listView != null && listView.getEmptyView() == null) {
                    listView.setEmptyView(emptyView);
                }
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
                loading = false;
            }
        } else if (id == NotificationCenter.closeChats) {
            removeSelfFromStack();
        }
    }

    @Override
    public boolean onBackPressed() {
        if (commentTextView != null && commentTextView.isPopupShowing()) {
            commentTextView.hidePopup(true);
            return false;
        }
        return super.onBackPressed();
    }

    public void setMaxSelectedPhotos(int value, boolean order) {
        maxSelectedPhotos = value;
        allowOrder = order;
    }

    public void setAllowSearchImages(boolean value) {
        allowSearchImages = value;
    }

    public void setDelegate(PhotoAlbumPickerActivityDelegate delegate) {
        this.delegate = delegate;
    }

    private void sendSelectedPhotos(HashMap<Object, Object> photos, ArrayList<Object> order, boolean notify, int scheduleDate) {
        if (photos.isEmpty() || delegate == null || sendPressed) {
            return;
        }
        sendPressed = true;

        ArrayList<SendMessagesHelper.SendingMediaInfo> media = new ArrayList<>();
        for (int a = 0; a < order.size(); a++) {
            Object object = photos.get(order.get(a));
            SendMessagesHelper.SendingMediaInfo info = new SendMessagesHelper.SendingMediaInfo();
            media.add(info);
            if (object instanceof MediaController.PhotoEntry) {
                MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) object;
                if (photoEntry.imagePath != null) {
                    info.path = photoEntry.imagePath;
                } else {
                    info.path = photoEntry.path;
                }
                info.thumbPath = photoEntry.thumbPath;
                info.videoEditedInfo = photoEntry.editedInfo;
                info.isVideo = photoEntry.isVideo;
                info.caption = photoEntry.caption != null ? photoEntry.caption.toString() : null;
                info.entities = photoEntry.entities;
                info.masks = photoEntry.stickers;
                info.ttl = photoEntry.ttl;
            } else if (object instanceof MediaController.SearchImage) {
                MediaController.SearchImage searchImage = (MediaController.SearchImage) object;
                if (searchImage.imagePath != null) {
                    info.path = searchImage.imagePath;
                } else {
                    info.searchImage = searchImage;
                }
                info.thumbPath = searchImage.thumbPath;
                info.videoEditedInfo = searchImage.editedInfo;
                info.caption = searchImage.caption != null ? searchImage.caption.toString() : null;
                info.entities = searchImage.entities;
                info.masks = searchImage.stickers;
                info.ttl = searchImage.ttl;
                if (searchImage.inlineResult != null && searchImage.type == 1) {
                    info.inlineResult = searchImage.inlineResult;
                    info.params = searchImage.params;
                }

                searchImage.date = (int) (System.currentTimeMillis() / 1000);
            }
        }

        delegate.didSelectPhotos(media, notify, scheduleDate);
    }

    private void fixLayout() {
        if (listView != null) {
            ViewTreeObserver obs = listView.getViewTreeObserver();
            obs.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    fixLayoutInternal();
                    if (listView != null) {
                        listView.getViewTreeObserver().removeOnPreDrawListener(this);
                    }
                    return true;
                }
            });
        }
    }

    private void applyCaption() {
        if (commentTextView.length() <= 0) {
            return;
        }
        int imageId = (Integer) selectedPhotosOrder.get(0);
        Object entry = selectedPhotos.get(imageId);
        if (entry instanceof MediaController.PhotoEntry) {
            MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) entry;
            photoEntry.caption = commentTextView.getText().toString();
        } else if (entry instanceof MediaController.SearchImage) {
            MediaController.SearchImage searchImage = (MediaController.SearchImage) entry;
            searchImage.caption = commentTextView.getText().toString();
        }
    }

    private void fixLayoutInternal() {
        if (getParentActivity() == null) {
            return;
        }

        WindowManager manager = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Activity.WINDOW_SERVICE);
        int rotation = manager.getDefaultDisplay().getRotation();
        columnsCount = 2;
        if (!AndroidUtilities.isTablet() && (rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90)) {
            columnsCount = 4;
        }
        listAdapter.notifyDataSetChanged();
    }

    private boolean showCommentTextView(boolean show) {
        if (show == (frameLayout2.getTag() != null)) {
            return false;
        }
        frameLayout2.setTag(show ? 1 : null);
        if (commentTextView.getEditText().isFocused()) {
            AndroidUtilities.hideKeyboard(commentTextView.getEditText());
        }
        commentTextView.hidePopup(true);
        if (show) {
            frameLayout2.setVisibility(View.VISIBLE);
            writeButtonContainer.setVisibility(View.VISIBLE);
        } else {
            frameLayout2.setVisibility(View.INVISIBLE);
            writeButtonContainer.setVisibility(View.INVISIBLE);
        }
        writeButtonContainer.setScaleX(show ? 1.0f : 0.2f);
        writeButtonContainer.setScaleY(show ? 1.0f : 0.2f);
        writeButtonContainer.setAlpha(show ? 1.0f : 0.0f);
        selectedCountView.setScaleX(show ? 1.0f : 0.2f);
        selectedCountView.setScaleY(show ? 1.0f : 0.2f);
        selectedCountView.setAlpha(show ? 1.0f : 0.0f);
        frameLayout2.setTranslationY(show ? 0 : AndroidUtilities.dp(48));
        shadow.setTranslationY(show ? 0 : AndroidUtilities.dp(48));
        return true;
    }

    private void updatePhotosButton() {
        int count = selectedPhotos.size();

        if (count == 0) {
            selectedCountView.setPivotX(0);
            selectedCountView.setPivotY(0);
            showCommentTextView(false);
        } else {
            selectedCountView.invalidate();
            showCommentTextView(true);
        }
    }

    private void openPhotoPicker(MediaController.AlbumEntry albumEntry, int type) {
        if (albumEntry != null) {
            PhotoPickerActivity fragment = new PhotoPickerActivity(type, albumEntry, selectedPhotos, selectedPhotosOrder, selectPhotoType, allowCaption, chatActivity, false);
            fragment.setCaption(caption = commentTextView.getText());
            fragment.setDelegate(new PhotoPickerActivity.PhotoPickerActivityDelegate() {
                @Override
                public void selectedPhotosChanged() {
                    updatePhotosButton();
                }

                @Override
                public void actionButtonPressed(boolean canceled, boolean notify, int scheduleDate) {
                    removeSelfFromStack();
                    if (!canceled) {
                        sendSelectedPhotos(selectedPhotos, selectedPhotosOrder, notify, scheduleDate);
                    }
                }

                @Override
                public void onCaptionChanged(CharSequence text) {
                    commentTextView.setText(caption = text);
                }
            });
            fragment.setMaxSelectedPhotos(maxSelectedPhotos, allowOrder);
            presentFragment(fragment);
        } else {
            final HashMap<Object, Object> photos = new HashMap<>();
            final ArrayList<Object> order = new ArrayList<>();
            if (allowGifs) {
                PhotoPickerSearchActivity fragment = new PhotoPickerSearchActivity(photos, order, selectPhotoType, allowCaption, chatActivity);
                fragment.setCaption(caption = commentTextView.getText());
                fragment.setDelegate(new PhotoPickerActivity.PhotoPickerActivityDelegate() {
                    @Override
                    public void selectedPhotosChanged() {

                    }

                    @Override
                    public void actionButtonPressed(boolean canceled, boolean notify, int scheduleDate) {
                        removeSelfFromStack();
                        if (!canceled) {
                            sendSelectedPhotos(photos, order, notify, scheduleDate);
                        }
                    }

                    @Override
                    public void onCaptionChanged(CharSequence text) {
                        commentTextView.setText(caption = text);
                    }
                });
                fragment.setMaxSelectedPhotos(maxSelectedPhotos, allowOrder);
                presentFragment(fragment);
            } else {
                PhotoPickerActivity fragment = new PhotoPickerActivity(0, albumEntry, photos, order, selectPhotoType, allowCaption, chatActivity, false);
                fragment.setCaption(caption = commentTextView.getText());
                fragment.setDelegate(new PhotoPickerActivity.PhotoPickerActivityDelegate() {
                    @Override
                    public void selectedPhotosChanged() {

                    }

                    @Override
                    public void actionButtonPressed(boolean canceled, boolean notify, int scheduleDate) {
                        removeSelfFromStack();
                        if (!canceled) {
                            sendSelectedPhotos(photos, order, notify, scheduleDate);
                        }
                    }

                    @Override
                    public void onCaptionChanged(CharSequence text) {
                        commentTextView.setText(caption = text);
                    }
                });
                fragment.setMaxSelectedPhotos(maxSelectedPhotos, allowOrder);
                presentFragment(fragment);
            }
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public int getItemCount() {
            return albumsSorted != null ? (int) Math.ceil(albumsSorted.size() / (float) columnsCount) : 0;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            PhotoPickerAlbumsCell cell = new PhotoPickerAlbumsCell(mContext);
            cell.setDelegate(albumEntry -> openPhotoPicker(albumEntry, 0));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            PhotoPickerAlbumsCell photoPickerAlbumsCell = (PhotoPickerAlbumsCell) holder.itemView;
            photoPickerAlbumsCell.setAlbumsCount(columnsCount);
            for (int a = 0; a < columnsCount; a++) {
                int index = position * columnsCount + a;
                if (index < albumsSorted.size()) {
                    MediaController.AlbumEntry albumEntry = albumsSorted.get(index);
                    photoPickerAlbumsCell.setAlbum(a, albumEntry);
                } else {
                    photoPickerAlbumsCell.setAlbum(a, null);
                }
            }
            photoPickerAlbumsCell.requestLayout();
        }

        @Override
        public int getItemViewType(int i) {
            return 0;
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_dialogBackground));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_dialogBackground));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_dialogTextBlack));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_dialogTextBlack));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_dialogButtonSelector));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_dialogBackground));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, null, new Drawable[]{Theme.chat_attachEmptyDrawable}, null, Theme.key_chat_attachEmptyImage));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, null, null, null, Theme.key_chat_attachPhotoBackground));

        return themeDescriptions;
    }
}
