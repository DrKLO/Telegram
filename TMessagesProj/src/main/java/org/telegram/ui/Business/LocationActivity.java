package org.telegram.ui.Business;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Geocoder;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import org.checkerframework.checker.units.qual.A;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.WebFile;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AnimatedColor;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ChatAttachAlert;
import org.telegram.ui.Components.ChatAttachAlertLocationLayout;
import org.telegram.ui.Components.CircularProgressDrawable;
import org.telegram.ui.Components.ClipRoundedDrawable;
import org.telegram.ui.Components.CrossfadeDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LoadingDrawable;
import org.telegram.ui.Components.Paint.Views.LocationView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Stories.recorder.PaintView;

import java.util.ArrayList;
import java.util.List;

public class LocationActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private UniversalRecyclerView listView;

    private static final int done_button = 1;
    private CrossfadeDrawable doneButtonDrawable;
    private ActionBarMenuItem doneButton;

    private boolean ignoreEditText;
    private FrameLayout editTextContainer;
    private EditTextBoldCursor editText;

    private FrameLayout mapPreviewContainer;
    private View mapMarker;
    private ClipRoundedDrawable mapLoadingDrawable;
    private BackupImageView mapPreview;

    final int MAX_NAME_LENGTH = 96;
    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.BusinessLocation));
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
        checkDone(false);

        FrameLayout contentView = new FrameLayout(context);
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        editText = new EditTextBoldCursor(getContext()) {
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
                    limitCount = MAX_NAME_LENGTH - text.length();
                    limit.cancelAnimation();
                    limit.setText(limitCount > 12 ? "" : "" + limitCount);
                }
            }
            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                limit.setTextColor(limitColor.set(Theme.getColor(limitCount < 0 ? Theme.key_text_RedRegular : Theme.key_dialogSearchHint, getResourceProvider())));
                limit.setBounds(getScrollX(), 0, getScrollX() + getWidth(), getHeight());
                limit.draw(canvas);
            }
        };
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        editText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        editText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setBackgroundDrawable(null);
        editText.setMaxLines(5);
        editText.setSingleLine(false);
        editText.setPadding(0, 0, dp(42), 0);
        editText.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        editText.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        editText.setHint(getString(R.string.BusinessLocationAddress));
        editText.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setCursorSize(dp(19));
        editText.setCursorWidth(1.5f);
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {}
            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {}
            @Override
            public void afterTextChanged(Editable editable) {
                if (!ignoreEditText) {
                    mapAddress = false;
                    address = editable.toString();
                    checkDone(true);
                }
            }
        });
        editText.setFilters(new InputFilter[] {
                new InputFilter() {
                    @Override
                    public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                        if (source != null) {
                            String s = source.toString();
                            if (s.contains("\n")) {
                                return s.replaceAll("\n", "");
                            }
                        }
                        return null;
                    }
                }
        });
        editTextContainer = new FrameLayout(context);
        editTextContainer.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 21, 15, 21, 15));
        editTextContainer.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        if (editText != null) {
            ignoreEditText = true;
            editText.setText(address);
            editText.setSelection(editText.getText().length());
            ignoreEditText = false;
        }

//        mapLoadingDrawable = new LoadingDrawable(resourceProvider);
//        mapLoadingDrawable.setColors(
//            Theme.multAlpha(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), .025f),
//            Theme.multAlpha(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), Theme.isCurrentThemeDark() ? .25f : .12f)
//        );
        mapPreview = new BackupImageView(context) {
            @Override
            protected ImageReceiver createImageReciever() {
                return new ImageReceiver(this) {
                    @Override
                    protected boolean setImageBitmapByKey(Drawable drawable, String key, int type, boolean memCache, int guid) {
                        if (drawable != null && type != TYPE_THUMB) {
                            mapMarker.animate().alpha(1f).translationY(0).setInterpolator(CubicBezierInterpolator.EASE_OUT_BACK).setDuration(250).start();
                        }
                        return super.setImageBitmapByKey(drawable, key, type, memCache, guid);
                    }
                };
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(240), MeasureSpec.EXACTLY));
            }
            @Override
            protected boolean verifyDrawable(@NonNull Drawable who) {
                return who == mapLoadingDrawable || super.verifyDrawable(who);
            }
        };
        SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(R.raw.map_placeholder, Theme.key_chat_outLocationIcon, .2f);
        svgThumb.setColorKey(Theme.key_windowBackgroundWhiteBlackText, getResourceProvider());
        svgThumb.setAspectCenter(true);
        svgThumb.setParent(mapPreview.getImageReceiver());
        mapLoadingDrawable = new ClipRoundedDrawable(svgThumb);
        mapLoadingDrawable.setCallback(mapPreview);
        mapPreview.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));

        mapMarker = new View(context) {
            final Drawable pin = getContext().getResources().getDrawable(R.drawable.map_pin_photo).mutate();
            final AvatarDrawable avatarDrawable = new AvatarDrawable();
            final ImageReceiver avatarImage = new ImageReceiver(this);
            {
                avatarDrawable.setInfo(getUserConfig().getCurrentUser());
                avatarImage.setForUserOrChat(getUserConfig().getCurrentUser(), avatarDrawable);
            }
            @Override
            protected void dispatchDraw(Canvas canvas) {
                pin.setBounds(0, 0, dp(62), dp(85));
                pin.draw(canvas);
                avatarImage.setRoundRadius(dp(62));
                avatarImage.setImageCoords(dp(6), dp(6), dp(50), dp(50));
                avatarImage.draw(canvas);
            }
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(MeasureSpec.makeMeasureSpec(dp(62), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(85), MeasureSpec.EXACTLY));
            }
        };

        mapPreviewContainer = new FrameLayout(context);
        mapPreviewContainer.addView(mapPreview, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        mapPreviewContainer.addView(mapMarker, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, -31, 0, 0));
        updateMapPreview();

        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, null);
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        setValue();

        return fragmentView = contentView;
    }

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addObserver(this, NotificationCenter.userInfoDidLoad);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        getNotificationCenter().removeObserver(this, NotificationCenter.userInfoDidLoad);
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.userInfoDidLoad) {
            setValue();
        }
    }

    private boolean valueSet;
    private void setValue() {
        if (valueSet) return;

        final long selfId = getUserConfig().getClientUserId();
        TLRPC.UserFull userFull = getMessagesController().getUserFull(selfId);
        if (userFull == null) {
            getMessagesController().loadUserInfo(getUserConfig().getCurrentUser(), true, getClassGuid());
            return;
        }

        currentLocation = userFull.business_location;
        if (currentLocation != null) {
            geo = currentLocation.geo_point;
            address = currentLocation.address;
        } else {
            geo = null;
            address = "";
        }

        if (editText != null) {
            ignoreEditText = true;
            editText.setText(address);
            editText.setSelection(editText.getText().length());
            ignoreEditText = false;
        }
        updateMapPreview();
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
        valueSet = true;
    }

    private void updateMapPreview() {
        if (mapMarker == null || mapPreview == null) return;
        if (geo != null) {
            mapMarker.setAlpha(0f);
            mapMarker.setTranslationY(-dp(12));
            final int w = (int) ((mapPreview.getMeasuredWidth() <= 0 ? AndroidUtilities.displaySize.x : mapPreview.getMeasuredWidth()) / AndroidUtilities.density);
            final int h = 240;
            final int scale = Math.min(2, (int) Math.ceil(AndroidUtilities.density));
            mapPreview.setImage(ImageLocation.getForWebFile(WebFile.createWithGeoPoint(geo.lat, geo._long, 0, scale * w, scale * h, 15, scale)), w + "_" + h, mapLoadingDrawable, 0, null);
        } else {
            mapPreview.setImageBitmap(null);
        }
    }

    private TLRPC.TL_businessLocation currentLocation;
    private TLRPC.GeoPoint geo;
    private String address;
    private boolean mapAddress;

    public boolean hasChanges() {
        if ((geo != null || !TextUtils.isEmpty(address)) != (currentLocation != null)) {
            return true;
        }
        if ((geo != null || !TextUtils.isEmpty(address)) != (currentLocation != null && !(currentLocation.geo_point instanceof TLRPC.TL_geoPointEmpty))) {
            return true;
        }
        if (!TextUtils.equals(address, currentLocation != null ? currentLocation.address : "")) {
            return true;
        }
        if ((geo != null) != (currentLocation != null && currentLocation.geo_point != null)) {
            return true;
        }
        if (geo != null && (currentLocation == null || currentLocation.geo_point == null || !(currentLocation.geo_point instanceof TLRPC.TL_geoPointEmpty) && (geo.lat != currentLocation.geo_point.lat || geo._long != currentLocation.geo_point._long))) {
            return true;
        }
        return false;
    }

    private void checkDone(boolean animated) {
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
        if (listView != null && listView.adapter != null && clearVisible != (currentLocation != null && (geo != null || !TextUtils.isEmpty(address)))) {
            listView.adapter.update(true);
        }
    }

    private int shiftDp = -4;
    private void processDone() {
        if (doneButtonDrawable.getProgress() > 0f) return;

        final boolean empty = geo == null && TextUtils.isEmpty(address);
        if (!empty) {
            if (!hasChanges()) {
                finishFragment();
                return;
            }

            final String address = this.address == null ? "" : this.address.trim();
            if (TextUtils.isEmpty(address) || address.length() > MAX_NAME_LENGTH) {
                BotWebViewVibrationEffect.APP_ERROR.vibrate();
                AndroidUtilities.shakeViewSpring(editText, shiftDp = -shiftDp);
                return;
            }
        }

        doneButtonDrawable.animateToProgress(1f);
        TLRPC.UserFull userFull = getMessagesController().getUserFull(getUserConfig().getClientUserId());
        TLRPC.TL_account_updateBusinessLocation req = new TLRPC.TL_account_updateBusinessLocation();
        if (!empty) {
            if (geo != null) {
                req.flags |= 2;
                req.geo_point = new TLRPC.TL_inputGeoPoint();
                req.geo_point.lat = geo.lat;
                req.geo_point._long = geo._long;
            }
            req.flags |= 1;
            req.address = address;

            if (userFull != null) {
                userFull.flags2 |= 2;
                userFull.business_location = new TLRPC.TL_businessLocation();
                userFull.business_location.address = address;
                if (geo != null) {
                    userFull.business_location.flags |= 1;
                    userFull.business_location.geo_point = new TLRPC.TL_geoPoint();
                    userFull.business_location.geo_point.lat = geo.lat;
                    userFull.business_location.geo_point._long = geo._long;
                }
            }
        } else {
            if (userFull != null) {
                userFull.flags2 &=~ 2;
                userFull.business_location = null;
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
                finishFragment();
            }
        }));
        getMessagesStorage().updateUserInfo(userFull, false);
    }

    @Override
    public boolean onBackPressed() {
        final boolean empty = geo == null && TextUtils.isEmpty(address);
        if (hasChanges() && !empty) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(LocaleController.getString(R.string.UnsavedChanges));
            builder.setMessage(LocaleController.getString(R.string.BusinessLocationUnsavedChanges));
            builder.setPositiveButton(LocaleController.getString(R.string.ApplyTheme), (dialogInterface, i) -> processDone());
            builder.setNegativeButton(LocaleController.getString(R.string.PassportDiscard), (dialog, which) -> finishFragment());
            showDialog(builder.create());
            return false;
        }
        return super.onBackPressed();
    }

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        return !hasChanges();
    }

    private boolean clearVisible;
    private final int BUTTON_MAP = 1;
    private final int BUTTON_CLEAR = 2;

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asTopView(getString(R.string.BusinessLocationInfo), R.raw.biz_map));
        items.add(UItem.asCustom(editTextContainer));
        items.add(UItem.asShadow(null));
        items.add(UItem.asCheck(BUTTON_MAP, getString(R.string.BusinessLocationMap)).setChecked(geo != null));
        if (geo != null) {
            items.add(UItem.asCustom(mapPreviewContainer));
        }
        items.add(UItem.asShadow(null));
        if (clearVisible = (currentLocation != null && (geo != null || !TextUtils.isEmpty(address)))) {
            items.add(UItem.asButton(BUTTON_CLEAR, LocaleController.getString(R.string.BusinessLocationClear)).red());
            items.add(UItem.asShadow(null));
        }
        checkDone(true);
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == BUTTON_MAP || item.view == mapPreviewContainer) {
            if (geo == null || item.view == mapPreviewContainer) {
                showLocationAlert();
            } else {
                geo = null;
                listView.adapter.update(true);
            }
        } else if (item.id == BUTTON_CLEAR) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(LocaleController.getString(R.string.BusinessLocationClearTitle));
            builder.setMessage(LocaleController.getString(R.string.BusinessLocationClearMessage));
            builder.setPositiveButton(LocaleController.getString(R.string.Remove), (di, w) -> {
                doneButtonDrawable.animateToProgress(1f);
                TLRPC.UserFull userFull = getMessagesController().getUserFull(getUserConfig().getClientUserId());
                TLRPC.TL_account_updateBusinessLocation req = new TLRPC.TL_account_updateBusinessLocation();
                if (userFull != null) {
                    userFull.business_location = null;
                    userFull.flags2 &=~ 2;
                }
                getConnectionsManager().sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                    doneButtonDrawable.animateToProgress(0f);
                    if (err != null) {
                        BulletinFactory.showError(err);
                    } else if (res instanceof TLRPC.TL_boolFalse) {
                        BulletinFactory.of(this).createErrorBulletin(LocaleController.getString(R.string.UnknownError)).show();
                    } else {
                        finishFragment();
                    }
                }));
            });
            builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            showDialog(builder.create());
        }
    }

    private void showLocationAlert() {
        org.telegram.ui.LocationActivity fragment = new org.telegram.ui.LocationActivity(ChatAttachAlertLocationLayout.LOCATION_TYPE_BIZ);
        if (geo != null) {
            TLRPC.TL_channelLocation initialLocation = new TLRPC.TL_channelLocation();
            initialLocation.address = address;
            initialLocation.geo_point = geo;
            fragment.setInitialLocation(initialLocation);
        }
        fragment.setDelegate((location, live, notify, scheduleDate) -> {
            geo = location.geo;
            if (TextUtils.isEmpty(address) && !TextUtils.isEmpty(fragment.getAddressName()) || mapAddress) {
                mapAddress = true;
                address = fragment.getAddressName();
                if (address == null) address = "";
                if (editText != null) {
                    ignoreEditText = true;
                    editText.setText(address);
                    editText.setSelection(editText.getText().length());
                    ignoreEditText = false;
                }
            }
            updateMapPreview();
            listView.adapter.update(true);
            checkDone(true);
        });
        if (geo == null && !TextUtils.isEmpty(address)) {
            AlertDialog progressDialog = new AlertDialog(getContext(), AlertDialog.ALERT_TYPE_SPINNER);
            progressDialog.setCanCancel(false);
            progressDialog.showDelayed(200);
            Utilities.searchQueue.postRunnable(() -> {
                try {
                    Geocoder geocoder = new Geocoder(getContext(), LocaleController.getInstance().getCurrentLocale());
                    List<Address> addresses = geocoder.getFromLocationName(address, 1);
                    if (!addresses.isEmpty()) {
                        Address geoAddress = addresses.get(0);
                        TLRPC.TL_channelLocation initialLocation = new TLRPC.TL_channelLocation();
                        initialLocation.address = address;
                        initialLocation.geo_point = new TLRPC.TL_geoPoint();
                        initialLocation.geo_point.lat = geoAddress.getLatitude();
                        initialLocation.geo_point._long = geoAddress.getLongitude();
                        fragment.setInitialLocation(initialLocation);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
                AndroidUtilities.runOnUIThread(() -> {
                    progressDialog.dismiss();
                    presentFragment(fragment);
                });
            });
        } else {
            presentFragment(fragment);
        }
    }

}
