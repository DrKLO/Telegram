package org.lilchill.lilsettings;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.Editable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.LongSparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;
import androidx.core.util.Consumer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.exoplayer2.util.Log;

import org.lilchill.RozZzmiThemer.Main;
import org.lilchill.RozZzmiThemer.ThemeModel;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Paint.ColorPickerBottomSheet;
import org.telegram.ui.Components.Paint.PersistColorPalette;
import org.telegram.ui.Components.Paint.Views.LPhotoPaintView;
import org.telegram.ui.Components.Paint.Views.PaintColorsListView;
import org.telegram.ui.Components.Paint.Views.PipettePickerView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SeekBarView;
import org.telegram.ui.Components.ViewPagerFixed;
import org.telegram.ui.ThemePreviewActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public class LilSettingsActivity extends BaseFragment {
    private RecyclerListView listView;
    private ListAdapter adapter;
    private LinearLayoutManager layoutManager;
    private int rowCount = 0;

    private boolean isPackageInstalled(String packageName, PackageManager packageManager) {
        try {
            packageManager.getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static final String ls = "LilSettings";
    public static final String isBlurInChatEnabled = "isBlurInChatEnabled";
    public static final String isBlurInModalsEnabled = "isBlurInModalsEnabled";
    public static final String isBlurInSlidingMenusEnabled = "isBlurInSlidingMenusEnabled";
    public static final String areMaterialTabsEnabled = "areMaterialTabsEnabled";
    public static final String isTrendingStickersViewHidden = "isTrendingStickersViewHidden";
    public static final String areLeftSidedStickersTabsEnabled  = "areLeftSidedStickersTabsEnabled";
    public static final String areNewMenusEnabled = "areNewMenusEnabled";
    public static final String areDividersRemoved = "areDividersRemoved";
    public static final String isImmersiveBarEnabled = "isImmersiveBarEnabled";
    public static final String areMaterialButtonsEnabled = "areMaterialButtonsEnabled";
    public static final String isRoundedAvatarEnabled = "isRoundedAvatarEnabled";
    public static final String isNumberHidden = "isNumberHidden";
    public static final String isMessageSavingEnabled = "isMessageSavingEnabled";
    public static final String isHideOnScrollEnabled = "isHideOnScrollEnabled";
    public static final String font = "font";
    public static final String messagesStyle = "messagesStyle";
    public static final String blurAlpha = "blurAlpha";
    public static final String mentionAll = "isMentionAllEnabled";
    private static final String darkTheme = "darkTheme";
    private static final String amoledTheme  = "amoledTheme";
    private static final String soza = "soza";
    private static final String monet =  "monet";
    private static final String monetBg =  "monetBg";
    private static final String gradient = "gradient";
    private static final String colorS = "color";

    private int themingSectorRow;
    private int colorPickerRow;
    private int darkThemeRow;
    private int amoledRow;
    private int sozaRow;
    private int monetRow;
    private int monetBgRow;
    private int gradientRow;
    private int applyRow;
    private int blurSectionRow;
    private int blurInModalsRow;
    private int blurInChatRow;
    private int blurInSlidingMenusRow;
    private int blurAlphaSectorRow;
    private int blurAlphaSliderRow;
    private int designSectionRow;
    private int immersiveNavBarRow;
    private int materialTabsRow;
    private int leftSidedStickersTabsRow;

    private int hideTrendingStickersRow;
    private int messagesStyleRow;
    private int fontRow;
    private int newMenuStyleRow;
    private int removeDividersRow;
    private int materialFloatingButtonRow;
    private int roundedAvatarInDrawerRow;
    private int featuresSectionRow;
    private int numberToUsernameInDrawerRow;
    private int saveTheMessageRow;
    private int mentionAllRow;
    public int hideKeyboardOnScroll;

    private static boolean helper = false;

    @Override
    public boolean onFragmentCreate() {
        themingSectorRow = rowCount++;
        darkThemeRow = rowCount++;
        amoledRow = rowCount++;
        sozaRow = rowCount++;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S){
            monetRow= rowCount++;
        }
        gradientRow = rowCount++;
        colorPickerRow = rowCount++;
        applyRow = rowCount++;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S){
            blurSectionRow = rowCount++;
            blurInModalsRow = rowCount++;
            blurInChatRow = rowCount++;
            blurInSlidingMenusRow = rowCount++;
            blurAlphaSectorRow = rowCount++;
            blurAlphaSliderRow = rowCount++;
        }
        designSectionRow = rowCount++;
        messagesStyleRow = rowCount++;
        fontRow = rowCount++;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {immersiveNavBarRow = rowCount++;}
        materialTabsRow = rowCount++;
        leftSidedStickersTabsRow = rowCount++;
        hideTrendingStickersRow = rowCount++;
        newMenuStyleRow = rowCount++;
        removeDividersRow = rowCount++;
        materialFloatingButtonRow = rowCount++;
        roundedAvatarInDrawerRow = rowCount++;
        featuresSectionRow = rowCount++;
        numberToUsernameInDrawerRow = rowCount++;
        saveTheMessageRow = rowCount++;
        mentionAllRow = rowCount++;
        hideKeyboardOnScroll = rowCount++;
        return super.onFragmentCreate();
    }

    public static void setHelper(boolean b){
        helper = b;
    }

    public static boolean getHelper(){
        return helper;
    }


    @Override
    public void onFragmentDestroy() {
        SharedPreferences sp = ApplicationLoader.applicationContext.getSharedPreferences(ls, Context.MODE_PRIVATE);
        SharedPreferences.Editor spe = sp.edit();
        spe.putBoolean(darkTheme, false);
        spe.putBoolean(amoledTheme, false);
        spe.putBoolean(soza, false);
        spe.putBoolean(monet, false);
        spe.putBoolean(gradient, false);
        spe.apply();
        super.onFragmentDestroy();
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    @SuppressLint("ApplySharedPref")
    @Override
    public View createView(Context context) {
        actionBar.setTitle(LocaleController.getString(ls, R.string.LilSettings));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {if (id == -1) {finishFragment();}}});
        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        listView = new RecyclerListView(context);
        listView.setItemAnimator(null);
        listView.setLayoutAnimation(null);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        });
        listView.setVerticalScrollBarEnabled(false);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setAdapter(adapter = new ListAdapter(context));
        SharedPreferences sp = context.getSharedPreferences(ls, Context.MODE_PRIVATE);
        SharedPreferences.Editor spe = sp.edit();
        listView.setOnItemClickListener((view, position, x, y) -> {
            boolean enabled;
            if (position == fontRow){
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString("fontSetting", R.string.fontSetting));
                builder.setItems(new CharSequence[]{
                        LocaleController.getString("defaultFont",R.string.defaultFont),
                        LocaleController.getString("sanFrancisco", R.string.sanFrancisco),
                        LocaleController.getString("gsans", R.string.gsans),
                        "Overpass"
                }, (dialog, which) -> {
                    spe.putInt(font, (which)).apply();
                    AndroidUtilities.removeCachedTypefaces();
                    adapter.notifyItemChanged(position);
                });
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                showDialog(builder.create());
            } else if (position == messagesStyleRow){
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString("messagesStyle", R.string.messagesStyle));
                builder.setItems(new CharSequence[]{
                        LocaleController.getString("stockTg", R.string.stockTg),
                        LocaleController.getString("removedTail", R.string.removedTail),
                        LocaleController.getString("fullRounded", R.string.fullRounded)
                }, (dialog, which) -> {
                    spe.putInt(messagesStyle, (which)).apply();
                    adapter.notifyItemChanged(position);
                    getNotificationCenter().postNotificationName(NotificationCenter.didUpdateMessagesViews);
                    parentLayout.rebuildAllFragmentViews(false, false);
                });
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                showDialog(builder.create());
            } else if (position == colorPickerRow) {
                boolean b = sp.getBoolean(isBlurInSlidingMenusEnabled, Build.VERSION.SDK_INT >= Build.VERSION_CODES.S);
                float bi = sp.getFloat(blurAlpha, 20F);
                ColorPickerBottomSheet colorPickerBottomSheet = new ColorPickerBottomSheet(getContext(), getResourceProvider()){
                    @Override
                    public void onDismissAnimationStart() {
                        super.onDismissAnimationStart();
                        if (b){
                            ValueAnimator va = ValueAnimator.ofFloat(bi, 0.1F);
                            va.setDuration(250);
                            va.addUpdateListener(a -> {
                                if ((float) a.getAnimatedValue() != 0.1F){
                                    getFragmentView().setRenderEffect(RenderEffect.createBlurEffect((float) a.getAnimatedValue(), (float) a.getAnimatedValue(), Shader.TileMode.MIRROR));
                                } else {
                                    getFragmentView().setRenderEffect(null);
                                }
                            });
                            va.start();
                        }
                    }
                }.setColor(Color.WHITE).setPipetteDelegate(new ColorPickerBottomSheet.PipetteDelegate(){
                    @Override
                    public void onStartColorPipette() {}
                    @Override
                    public void onStopColorPipette() {}
                    @Override
                    public ViewGroup getContainerView() {return null;}
                    @Override
                    public View getSnapshotDrawingView() {return null;}
                    @Override
                    public void onDrawImageOverCanvas(Bitmap bitmap, Canvas canvas) {}
                    @Override
                    public boolean isPipetteVisible() {return false;}
                    @Override
                    public boolean isPipetteAvailable() {return false;}
                    @Override
                    public void onColorSelected(int color) {}
                }).setColorListener(color -> {
                    PersistColorPalette.getInstance(currentAccount).selectColor(color);
                    PersistColorPalette.getInstance(currentAccount).saveColors();
                    spe.putString(colorS, Integer.toHexString(color).substring(2)).apply();
                });
                if (sp.getBoolean(isBlurInSlidingMenusEnabled, Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)){
                    ValueAnimator va = ValueAnimator.ofFloat(0.1F, bi);
                    va.setDuration(220);
                    va.addUpdateListener(a -> getFragmentView().setRenderEffect(RenderEffect.createBlurEffect((float) a.getAnimatedValue(), (float) a.getAnimatedValue(), Shader.TileMode.MIRROR)));
                    va.start();
                }
                colorPickerBottomSheet.show();
            } else if (position == darkThemeRow) {
                enabled = ((TextCheckCell) view).isChecked();
                ((TextCheckCell) view).setChecked(!enabled);
                spe.putBoolean(darkTheme, !enabled).apply();
            } else if (position == amoledRow) {
                if (sp.getBoolean(darkTheme, false)){
                    enabled = ((TextCheckCell) view).isChecked();
                    ((TextCheckCell) view).setChecked(!enabled);
                    spe.putBoolean(amoledTheme, !enabled).apply();
                } else {
                   Toast.makeText(context, LocaleController.getString("enableDarkTheme", R.string.enableDarkTheme), Toast.LENGTH_SHORT).show();
                }
            } else if (position == sozaRow) {
                enabled = ((TextCheckCell) view).isChecked();
                ((TextCheckCell) view).setChecked(!enabled);
                spe.putBoolean(soza, !enabled).apply();
            } else if (position == monetRow) {
                enabled = ((TextCheckCell) view).isChecked();
                ((TextCheckCell) view).setChecked(!enabled);
                spe.putBoolean(monet, !enabled).apply();
            }  else if (position == gradientRow) {
                enabled = ((TextCheckCell) view).isChecked();
                ((TextCheckCell) view).setChecked(!enabled);
                spe.putBoolean(gradient, !enabled).apply();
            } else if (position == applyRow) {
                final int demoTries = sp.getInt("demoTries", 3);
                final String packageName = "com.therxmv.telegramthemer";
                if ((demoTries > 0 && demoTries <= 3) && isPackageInstalled(packageName, getContext().getPackageManager())){
                    try {
                        String color = sp.getString(colorS, "ff6698");;
                        boolean isDefault = !(sp.getBoolean(soza, false));
                        boolean isDark = sp.getBoolean(darkTheme, false);
                        boolean isAmoled = sp.getBoolean(amoledTheme, false);
                        boolean isMonet = sp.getBoolean(monet, false);
                        if (isMonet){color = Integer.toHexString(ContextCompat.getColor(context, R.color.monet_accent)).substring(2);}
                        boolean isGradient = sp.getBoolean(gradient, false);
                        ThemeModel themeProps = new ThemeModel(color, isDefault, isDark, isAmoled, isMonet, isGradient);
                        Main main = new Main();
                        String name = main.getTheme(getContext(), themeProps);
                        File locFile = new File(name);
                        if (locFile.exists()){
                            Theme.ThemeInfo themeInfo = Theme.applyThemeFile(locFile, locFile.getName(), null, true);
                            presentFragment(new ThemePreviewActivity(themeInfo));
                            setHelper(true);
                        }
                    } catch (Exception e){
                        Log.d("LILCHILL", e.getMessage());
                    }
                } else {
                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    builder.setMessage(LocaleController.getString("alertText", R.string.alertText))
                            .setBlurredBackground(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                            .setPositiveButton(LocaleController.getString("alertPositive", R.string.alertPositive), (dialogInterface, i) -> {
                                try {
                                    Intent appStoreIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + packageName));
                                    appStoreIntent.setPackage("com.android.vending");
                                    context.startActivity(appStoreIntent);
                                } catch (android.content.ActivityNotFoundException exception) {
                                    context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + packageName)));
                                }
                            })
                            .setNegativeButton(LocaleController.getString("alertNegative", R.string.alertNegative), (dialogInterface, i) -> dialogInterface.dismiss())
                            .setTitle(LocaleController.getString("alertTitle", R.string.alertTitle));
                    showDialog(builder.create());
                }
            } else if (position == blurInChatRow) {
                enabled = ((TextCheckCell) view).isChecked();
                ((TextCheckCell) view).setChecked(!enabled);
                spe.putBoolean(isBlurInChatEnabled, !enabled).apply();
            }else if (position == blurInModalsRow) {
                enabled = ((TextCheckCell) view).isChecked();
                ((TextCheckCell) view).setChecked(!enabled);
                spe.putBoolean(isBlurInModalsEnabled, !enabled).apply();
            } else if (position == blurInSlidingMenusRow) {
                enabled = ((TextCheckCell) view).isChecked();
                ((TextCheckCell) view).setChecked(!enabled);
                spe.putBoolean(isBlurInSlidingMenusEnabled, !enabled).apply();
            } else if (position == immersiveNavBarRow){
                Toast.makeText(getContext(), "Not available yet :(", Toast.LENGTH_SHORT).show();
//                enabled = ((TextCheckCell) view).isChecked();
//                ((TextCheckCell) view).setChecked(!enabled);
//                spe.putBoolean(isImmersiveBarEnabled, !enabled).apply();
            } else if (position == materialTabsRow) {
                enabled = ((TextCheckCell) view).isChecked();
                ((TextCheckCell) view).setChecked(!enabled);
                spe.putBoolean(areMaterialTabsEnabled, !enabled).apply();
            } else if (position == leftSidedStickersTabsRow) {
                enabled = ((TextCheckCell) view).isChecked();
                ((TextCheckCell) view).setChecked(!enabled);
                spe.putBoolean(areLeftSidedStickersTabsEnabled, !enabled).apply();
            } else if (position == hideTrendingStickersRow) {
                enabled = ((TextCheckCell) view).isChecked();
                ((TextCheckCell) view).setChecked(!enabled);
                spe.putBoolean(isTrendingStickersViewHidden, !enabled).apply();
            } else if (position == newMenuStyleRow) {
                enabled = ((TextCheckCell) view).isChecked();
                ((TextCheckCell) view).setChecked(!enabled);
                spe.putBoolean(areNewMenusEnabled, !enabled).apply();
            } else if (position == removeDividersRow){
                enabled = ((TextCheckCell) view).isChecked();
                ((TextCheckCell) view).setChecked(!enabled);
                spe.putBoolean(areDividersRemoved, !enabled).apply();
            }  else if (position == materialFloatingButtonRow) {
                enabled = ((TextCheckCell) view).isChecked();
                ((TextCheckCell) view).setChecked(!enabled);
                spe.putBoolean(areMaterialButtonsEnabled, !enabled).apply();
                Parcelable positionHelper = null;
                try {positionHelper = listView.getLayoutManager().onSaveInstanceState();
                } catch (NullPointerException nullPointerException){Log.d("LILCHILL", nullPointerException.getMessage());}
                parentLayout.rebuildFragments(1);
                try {listView.getLayoutManager().onRestoreInstanceState(positionHelper);
                } catch (NullPointerException nullPointerException) {Log.d("LILCHILL", nullPointerException.getMessage());}
            } else if (position == roundedAvatarInDrawerRow) {
                enabled = ((TextCheckCell) view).isChecked();
                ((TextCheckCell) view).setChecked(!enabled);
                spe.putBoolean(isRoundedAvatarEnabled, !enabled).apply();
                getNotificationCenter().postNotificationName(NotificationCenter.mainUserInfoChanged);
            } else if (position == numberToUsernameInDrawerRow) {
                enabled = ((TextCheckCell) view).isChecked();
                ((TextCheckCell) view).setChecked(!enabled);
                spe.putBoolean(isNumberHidden, !enabled).apply();
                getNotificationCenter().postNotificationName(NotificationCenter.mainUserInfoChanged);
            } else if (position == saveTheMessageRow){
                enabled = ((TextCheckCell) view).isChecked();
                ((TextCheckCell) view).setChecked(!enabled);
                spe.putBoolean(isMessageSavingEnabled, !enabled).apply();
            } else if (position == mentionAllRow){
                enabled = ((TextCheckCell) view).isChecked();
                ((TextCheckCell) view).setChecked(!enabled);
                spe.putBoolean(mentionAll, !enabled).apply();
            } else if (position == hideKeyboardOnScroll){
                enabled = ((TextCheckCell) view).isChecked();
                ((TextCheckCell) view).setChecked(!enabled);
                spe.putBoolean(isHideOnScrollEnabled, !enabled).apply();
            }
        });
        return fragmentView;
    }
    public class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return !(position == themingSectorRow || position == darkThemeRow || position ==  amoledRow || position ==  sozaRow || position == monetRow || position == gradientRow || position == colorPickerRow || position == applyRow || position == blurSectionRow || position == blurInChatRow || position == blurInModalsRow ||
                    position == blurInSlidingMenusRow || position == blurAlphaSectorRow || position == blurAlphaSliderRow || position == designSectionRow || position == materialTabsRow ||
                    position == leftSidedStickersTabsRow  || position == hideTrendingStickersRow || position == messagesStyleRow || position == fontRow || position == newMenuStyleRow ||
                    position == removeDividersRow ||
                    position == materialFloatingButtonRow || position == roundedAvatarInDrawerRow || position == featuresSectionRow ||
                    position == numberToUsernameInDrawerRow|| position == saveTheMessageRow || position == mentionAllRow || position == hideKeyboardOnScroll || position == immersiveNavBarRow);
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                    view = new TextCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 2:
                    view = new BlurRadius(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 3:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 4:
                    view = new ShadowSectionCell(mContext);
                    break;
                case 5:
                    view = new TextDetailSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                default:
                    view = new TextInfoPrivacyCell(mContext);
                    view.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            SharedPreferences lilSettings = getContext().getSharedPreferences(ls, Context.MODE_PRIVATE);
            switch(holder.getItemViewType()){
                case 0:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == blurSectionRow){
                        headerCell.setText(LocaleController.getString("blurTitle", R.string.blurTitle));
                    } else if (position == designSectionRow){
                        headerCell.setText(LocaleController.getString("designTitle", R.string.designTitle));
                    } else if (position == featuresSectionRow){
                        headerCell.setText(LocaleController.getString("featuresTitle", R.string.featuresTitle));
                    } else if (position == blurAlphaSectorRow){
                        headerCell.setText(LocaleController.getString("blurRadiusTitle", R.string.blurRadiusTitle));
                    } else if (position == themingSectorRow) {
                        headerCell.setText(LocaleController.getString("themingTitle", R.string.themingTitle));
                    }
                    break;
                case 1:
                    TextCheckCell textCheckCell = (TextCheckCell) holder.itemView;
                    if (position == darkThemeRow){
                        textCheckCell.setTextAndCheck(LocaleController.getString("darkThemeToggle", R.string.darkThemeToggle), lilSettings.getBoolean(darkTheme, false), false);
                    } else if (position == amoledRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("amoledTheme", R.string.amoledTheme), lilSettings.getBoolean(amoledTheme, false), false);
                    } else if (position == monetRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("monetColors", R.string.monetColors), lilSettings.getBoolean(monet, false), false);
                    } else if (position == sozaRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("sozaTheme", R.string.sozaTheme), lilSettings.getBoolean(soza, false), false);
                    } else if (position == gradientRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("enableGradient", R.string.enableGradient), lilSettings.getBoolean(gradient, false), false);
                    } else if (position == blurInChatRow){
                        textCheckCell.setTextAndCheck(LocaleController.getString("blurInChat", R.string.blurInChat), lilSettings.getBoolean(isBlurInChatEnabled, Build.VERSION.SDK_INT >= Build.VERSION_CODES.S), false);
                    } else if (position == blurInModalsRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("blurInModals", R.string.blurInModals), lilSettings.getBoolean(isBlurInModalsEnabled, Build.VERSION.SDK_INT >= Build.VERSION_CODES.S), false);
                    } else if (position == blurInSlidingMenusRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("blurInSlidingMenus", R.string.blurInSlidingMenus), lilSettings.getBoolean(isBlurInSlidingMenusEnabled, Build.VERSION.SDK_INT >= Build.VERSION_CODES.S), false);
                    } else if (position == materialTabsRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("materialYouTabs", R.string.materialYouTabs), lilSettings.getBoolean(areMaterialTabsEnabled, true), false);
                    } else if (position == leftSidedStickersTabsRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("leftSidedTabs", R.string.leftSidedTabs), lilSettings.getBoolean(areLeftSidedStickersTabsEnabled, false), false);
                    } else if (position == hideTrendingStickersRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("hideTrendingStickers", R.string.hideTrendingStickers), lilSettings.getBoolean(areLeftSidedStickersTabsEnabled, false), false);
                    } else if (position == newMenuStyleRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("newContextMenus", R.string.newContextMenus), lilSettings.getBoolean(areNewMenusEnabled, true), false);
                    } else if (position == removeDividersRow){
                        textCheckCell.setTextAndCheck(LocaleController.getString("removeSomeDividers", R.string.removeSomeDividers), lilSettings.getBoolean(areDividersRemoved, true), false);
                    } else if (position == immersiveNavBarRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("immersiveNavigationbar", R.string.immersiveNavigationbar), lilSettings.getBoolean(isImmersiveBarEnabled, false), false);
                    } else if (position == materialFloatingButtonRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("materialFAB", R.string.materialFAB), lilSettings.getBoolean(areMaterialButtonsEnabled, true), false);
                    } else if (position == roundedAvatarInDrawerRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("roundedAvatarInDrawer", R.string.roundedAvatarInDrawer), lilSettings.getBoolean(isRoundedAvatarEnabled, true), false);
                    } else if (position == numberToUsernameInDrawerRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("hideNumber", R.string.hideNumber), lilSettings.getBoolean(isNumberHidden, true), false);
                    } else if (position == saveTheMessageRow){
                        textCheckCell.setTextAndCheck(LocaleController.getString("messageSaving", R.string.messageSaving), lilSettings.getBoolean(isMessageSavingEnabled, true), false);
                    } else if (position == mentionAllRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("mentionAll", R.string.mentionAll), lilSettings.getBoolean(mentionAll, true), false);
                    } else if (position == hideKeyboardOnScroll){
                        textCheckCell.setTextAndCheck(LocaleController.getString("hideKeyboard", R.string.hideKeyboard), lilSettings.getBoolean(isHideOnScrollEnabled, true), false);
                    }
                    break;
                case 3:
                    TextSettingsCell textSettingsCell = (TextSettingsCell) holder.itemView;
                    int i = lilSettings.getInt(font, 2);
                    String s;
                    switch (i){
                        case 0:
                           s = LocaleController.getString("defaultFont", R.string.defaultFont);
                           break;
                        case 1:
                            s = LocaleController.getString("sanFrancisco", R.string.sanFrancisco);
                            break;
                        case 3:
                            s = "Overpass";
                            break;
                        default:
                            s = LocaleController.getString("gsans", R.string.gsans);
                            break;
                    }
                    if (position == fontRow){
                        textSettingsCell.setTextAndValue(LocaleController.getString("fontSetting", R.string.fontSetting), s, false);
                    } else {
                        int ii = lilSettings.getInt(messagesStyle, 2);
                        switch (ii){
                            case 0:
                                s = LocaleController.getString("stockTg", R.string.stockTg);
                                break;
                            case 1:
                                s = LocaleController.getString("removedTail", R.string.removedTail);
                                break;
                            default:
                                s = LocaleController.getString("fullRounded", R.string.fullRounded);
                                break;
                        }
                        textSettingsCell.setTextAndValue(LocaleController.getString("messagesStyle", R.string.messagesStyle), s, false);
                    }
                    break;
                case 4:
                    holder.itemView.setBackgroundColor(Theme.getColor(Theme.key_avatar_backgroundPink));
                    break;
                case 5:
                    TextDetailSettingsCell settingsCell = (TextDetailSettingsCell) holder.itemView;
                    final int demoTries = ApplicationLoader.applicationContext.getSharedPreferences(ls, 0).getInt("demoTries", 3);
                    String tries = demoTries == 1 ? LocaleController.getString("tries2", R.string.tries2) : LocaleController.getString("tries1", R.string.tries1);
                    if (position == applyRow){
                        settingsCell.setMultilineDetail(true);
                        settingsCell.setTextAndValue(LocaleController.getString("applyTheme", R.string.applyTheme), (demoTries == 0 ? LocaleController.getString("noDemoTries", R.string.noDemoTries) : LocaleController.formatString("applyThemeSubtitle", R.string.applyThemeSubtitle, demoTries, tries)), false);
                    } else {
                        settingsCell.setMultilineDetail(false);
                        settingsCell.setTextAndValue(LocaleController.getString("colorChooser", R.string.colorChooser), (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? LocaleController.getString("colorChooserSubtitle", R.string.colorChooserSubtitle) : ""), false);
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == themingSectorRow || position == blurSectionRow || position == designSectionRow || position == featuresSectionRow || position == blurAlphaSectorRow) {
                return 0;
            } else if (position == darkThemeRow || position ==  amoledRow || position ==  sozaRow || position == monetRow || position == gradientRow || position == blurInChatRow || position == blurInModalsRow || position == blurInSlidingMenusRow || position == materialTabsRow ||
                    position == leftSidedStickersTabsRow || position == hideTrendingStickersRow || position == newMenuStyleRow || position == removeDividersRow ||
                     position == materialFloatingButtonRow || position == roundedAvatarInDrawerRow ||
                    position == numberToUsernameInDrawerRow || position == saveTheMessageRow || position == mentionAllRow || position == hideKeyboardOnScroll || position == immersiveNavBarRow) {
                return 1;
            } else if (position == blurAlphaSliderRow) {
                return 2;
            } else if (position == messagesStyleRow || position == fontRow) {
                return 3;
            } else if (position == applyRow || position == colorPickerRow) {
                return 5;
            } else {
                return 4;
            }
        }
    }
    private static class BlurRadius extends FrameLayout {

        private final SeekBarView radiusBar;
        private final int startRadius = 1;
        private final int endRadius = 35;

        private final TextPaint textPaint;
        SharedPreferences sharedPreferences = getContext().getSharedPreferences(ls, Context.MODE_PRIVATE);
        SharedPreferences.Editor spe = sharedPreferences.edit();


        public BlurRadius(Context context) {
            super(context);
            setWillNotDraw(false);
            textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setTextSize(AndroidUtilities.dp(16));
            radiusBar = new SeekBarView(context);
            radiusBar.setReportChanges(true);
            radiusBar.setSeparatorsCount((endRadius - startRadius + 1));
            radiusBar.setProgress(((sharedPreferences.getFloat(blurAlpha, 20F) - startRadius) / (float) (endRadius - startRadius)));
            radiusBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
                @Override
                public void onSeekBarDrag(boolean stop, float progress) {
                    spe.putFloat(blurAlpha, startRadius + (endRadius - startRadius) * progress);
                    spe.apply();
                    BlurRadius.this.invalidate();
                }

                @Override
                public void onSeekBarPressed(boolean pressed) {}

                @Override
                public CharSequence getContentDescription() {
                    return String.valueOf(Math.round(startRadius + (endRadius - startRadius) * radiusBar.getProgress()));
                }

                @Override
                public int getStepsCount() {
                    return (endRadius - startRadius);
                }
            });
            radiusBar.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(radiusBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, Gravity.LEFT | Gravity.TOP, 5, 5, 39, 0));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
            canvas.drawText(String.valueOf((int) Math.ceil(sharedPreferences.getFloat(blurAlpha, 20F))), getMeasuredWidth() - AndroidUtilities.dp(39), AndroidUtilities.dp(28), textPaint);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), heightMeasureSpec);
        }

        @Override
        public void invalidate() {
            super.invalidate();
            radiusBar.invalidate();
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            radiusBar.getSeekBarAccessibilityDelegate().onInitializeAccessibilityNodeInfoInternal(this, info);
        }

        @Override
        public boolean performAccessibilityAction(int action, Bundle arguments) {
            return super.performAccessibilityAction(action, arguments) || radiusBar.getSeekBarAccessibilityDelegate().performAccessibilityActionInternal(this, action, arguments);
        }
    }
}

