package org.telegram.ui.web;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatPluralStringComma;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.RadioColorCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Text;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.ui.Stars.StarsIntroActivity;
import org.telegram.ui.Stories.bots.BotPreviewsEditContainer;
import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

public class WebBrowserSettings extends UniversalFragment implements NotificationCenter.NotificationCenterDelegate {

    private Drawable addIcon;

    private Utilities.Callback<BrowserHistory.Entry> whenHistoryClicked;
    public WebBrowserSettings(Utilities.Callback<BrowserHistory.Entry> whenHistoryClicked) {
        this.whenHistoryClicked = whenHistoryClicked;
    }

    private long cacheSize, cookiesSize, historySize;

    @Override
    public boolean onFragmentCreate() {
        loadSizes();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.webViewResolved);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.webViewResolved);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.webViewResolved) {
            if (listView != null) {
                listView.adapter.update(true);
            }
        }
    }

    private void loadSizes() {
        ArrayList<BrowserHistory.Entry> history = BrowserHistory.getHistory(loadedHistory -> {
            this.historySize = loadedHistory.size();
            if (listView != null && listView.adapter != null && listView.isAttachedToWindow()) {
                listView.adapter.update(true);
            }
        });
        if (history != null) {
            this.historySize = history.size();
            if (listView != null && listView.adapter != null && listView.isAttachedToWindow()) {
                listView.adapter.update(true);
            }
        }

        Utilities.globalQueue.postRunnable(() -> {
            long cacheSize = 0;
            File cache1 = ApplicationLoader.applicationContext.getDatabasePath("webview.db");
            if (cache1 != null && cache1.exists()) {
                cacheSize += cache1.length();
            }
            File cache2 = ApplicationLoader.applicationContext.getDatabasePath("webviewCache.db");
            if (cache2 != null && cache2.exists()) {
                cacheSize += cache2.length();
            }
            File dir = new File(ApplicationLoader.applicationContext.getApplicationInfo().dataDir, "app_webview");
            if (dir.exists()) {
                cacheSize += getDirectorySize(dir, false);
            }
            File dir2 = new File(ApplicationLoader.applicationContext.getApplicationInfo().dataDir, "cache/WebView");
            if (dir2.exists()) {
                cacheSize += getDirectorySize(dir2, null);
            }

            long cookieSize = 0;
            File dir3 = new File(ApplicationLoader.applicationContext.getApplicationInfo().dataDir, "app_webview");
            if (dir3.exists()) {
                cookieSize += getDirectorySize(dir3, true);
            }

            final long finalCacheSize = cacheSize;
            final long finalCookiesSize = cookieSize;

            AndroidUtilities.runOnUIThread(() -> {
                this.cacheSize = finalCacheSize;
                this.cookiesSize = finalCookiesSize;
                if (listView != null && listView.adapter != null && listView.isAttachedToWindow()) {
                    listView.adapter.update(true);
                }
            });
        });
    }

    @Override
    public View createView(Context context) {

//        if (parentLayout != null && parentLayout.isSheet()) {
//            actionBar.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
//            actionBar.setActionModeColor(Theme.getColor(Theme.key_windowBackgroundWhite));
//            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
//            actionBar.setTitleColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
//            actionBar.setItemsBackgroundColor(getThemedColor(Theme.key_actionBarActionModeDefaultSelector), false);
//            actionBar.setItemsColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), false);
//            actionBar.setItemsColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), true);
//            actionBar.setCastShadows(true);
//        }

        Drawable drawable1 = context.getResources().getDrawable(R.drawable.poll_add_circle).mutate();
        Drawable drawable2 = context.getResources().getDrawable(R.drawable.poll_add_plus).mutate();
        drawable1.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_switchTrackChecked), PorterDuff.Mode.MULTIPLY));
        drawable2.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_checkboxCheck), PorterDuff.Mode.MULTIPLY));
        addIcon = new CombinedDrawable(drawable1, drawable2) {
            { translateX = dp(2); }
            @Override
            public void setColorFilter(ColorFilter colorFilter) {

            }
        };

        return super.createView(context);
    }

    @Override
    public boolean isLightStatusBar() {
//        if (parentLayout != null && parentLayout.isSheet()) {
//            return AndroidUtilities.computePerceivedBrightness(getThemedColor(Theme.key_windowBackgroundWhite)) > .721f;
//        }
        return super.isLightStatusBar();
    }

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.BrowserSettingsTitle);
    }

    public static final int BUTTON_TOGGLE = 1;
    public static final int BUTTON_CLEAR_CACHE = 2;
    public static final int BUTTON_CLEAR_COOKIES = 3;
    public static final int BUTTON_ADD = 4;
    public static final int BUTTON_CLEAR_LIST = 5;
    public static final int BUTTON_SEARCH_ENGINE = 6;
    public static final int BUTTON_CLEAR_HISTORY = 7;
    public static final int BUTTON_OPEN_HISTORY = 9;
    public static final int BUTTON_CUSTOMTABS_ON = 10;
    public static final int BUTTON_CUSTOMTABS_OFF = 11;

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asRippleCheck(BUTTON_TOGGLE, getString(R.string.BrowserSettingsEnable)).setChecked(SharedConfig.inappBrowser));
        items.add(UItem.asShadow(LocaleController.getString(R.string.BrowserSettingsEnableInfo)));
        items.add(UItem.asButton(BUTTON_CLEAR_COOKIES, R.drawable.menu_clear_cookies, LocaleController.getString(R.string.BrowserSettingsCookiesClear), cookiesSize > 0 ? AndroidUtilities.formatFileSize(cookiesSize) : ""));
        items.add(UItem.asButton(BUTTON_CLEAR_CACHE, R.drawable.menu_clear_cache, LocaleController.getString(R.string.BrowserSettingsCacheClear), cacheSize > 0 ? AndroidUtilities.formatFileSize(cacheSize) : ""));
        items.add(UItem.asShadow(getString(R.string.BrowserSettingsCookiesInfo)));
        if (historySize > 0) {
            items.add(UItem.asButton(BUTTON_OPEN_HISTORY, R.drawable.menu_clear_recent, getString(R.string.BrowserSettingsHistoryShow)));
            items.add(UItem.asButton(BUTTON_CLEAR_HISTORY, R.drawable.menu_clear_cache, getString(R.string.BrowserSettingsHistoryClear), formatPluralStringComma("BrowserSettingsHistoryPages", (int) historySize, ',')));
            items.add(UItem.asShadow(null));
        }
        items.add(UItem.asHeader(LocaleController.getString(R.string.BrowserSettingsNeverOpenInTitle)));
        items.add(UItem.asButton(BUTTON_ADD, addIcon, LocaleController.getString(R.string.BrowserSettingsNeverOpenInAdd)).accent());
        RestrictedDomainsList.getInstance().load();
        ArrayList<ArrayList<String>> allDomains = RestrictedDomainsList.getInstance().restrictedDomains;
        for (ArrayList<String> domains : allDomains) {
            WebMetadataCache.WebMetadata meta = null;
            for (String domain : domains) {
                meta = WebMetadataCache.getInstance().get(domain);
                if (meta != null) break;
            }
            items.add(WebsiteView.Factory.as(domains, meta == null ? "" : (TextUtils.isEmpty(meta.sitename) ? (TextUtils.isEmpty(meta.title) ? "" : meta.title) : meta.sitename), meta == null ? null : meta.favicon));
        }
        if (!allDomains.isEmpty()) {
            items.add(UItem.asButton(BUTTON_CLEAR_LIST, R.drawable.msg_clearcache, LocaleController.getString(R.string.BrowserSettingsNeverOpenInClearList)).red());
        }
        items.add(UItem.asShadow(LocaleController.getString(R.string.BrowserSettingsNeverOpenInInfo)));
        items.add(UItem.asButton(BUTTON_SEARCH_ENGINE, R.drawable.msg_search, LocaleController.getString(R.string.SearchEngine), SearchEngine.getCurrent().name));
        items.add(UItem.asShadow(LocaleController.getString(R.string.BrowserSettingsSearchEngineInfo)));
        if (!SharedConfig.inappBrowser) {
            items.add(UItem.asHeader(getString(R.string.BrowserSettingsCustomTabsTitle)));
            items.add(UItem.asRadio(BUTTON_CUSTOMTABS_ON, getString(R.string.BrowserSettingsCustomTabs)).setChecked(SharedConfig.customTabs));
            items.add(UItem.asRadio(BUTTON_CUSTOMTABS_OFF, getString(R.string.BrowserSettingsNoCustomTabs)).setChecked(!SharedConfig.customTabs));
            items.add(UItem.asShadow(getString(R.string.BrowserSettingsNoCustomTabsInfo)));
        }
        if (BuildVars.DEBUG_PRIVATE_VERSION) {
            items.add(UItem.asCheck(12, "adaptable colors").setChecked(SharedConfig.adaptableColorInBrowser));
            items.add(UItem.asCheck(13, "only local IV").setChecked(SharedConfig.onlyLocalInstantView));
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == 12) {
            SharedConfig.toggleBrowserAdaptableColors();
            ((TextCheckCell) view).setChecked(SharedConfig.adaptableColorInBrowser);
        } else if (item.id == 13) {
            SharedConfig.toggleLocalInstantView();
            ((TextCheckCell) view).setChecked(SharedConfig.onlyLocalInstantView);
        } else if (item.id == BUTTON_TOGGLE) {
            SharedConfig.toggleInappBrowser();
            ((TextCheckCell) view).setChecked(SharedConfig.inappBrowser);
            ((TextCheckCell) view).setBackgroundColorAnimated(SharedConfig.inappBrowser, Theme.getColor(SharedConfig.inappBrowser ? Theme.key_windowBackgroundChecked : Theme.key_windowBackgroundUnchecked));
            listView.adapter.update(true);
        } else if (item.id == BUTTON_CUSTOMTABS_ON) {
            SharedConfig.toggleCustomTabs(true);
            listView.adapter.update(true);
        } else if (item.id == BUTTON_CUSTOMTABS_OFF) {
            SharedConfig.toggleCustomTabs(false);
            listView.adapter.update(true);
        } else if (item.id == BUTTON_CLEAR_CACHE) {
            new AlertDialog.Builder(getContext(), getResourceProvider())
                .setTitle(getString(R.string.BrowserSettingsCacheClear))
                .setMessage(formatString(R.string.BrowserSettingsCacheClearText, cacheSize == 0 ? "" : " (" + AndroidUtilities.formatFileSize(cacheSize)+")"))
                .setPositiveButton(getString(R.string.Clear), (di, w) -> {
                    ApplicationLoader.applicationContext.deleteDatabase("webview.db");
                    ApplicationLoader.applicationContext.deleteDatabase("webviewCache.db");
                    WebStorage.getInstance().deleteAllData();
                    try {
                        WebView webView = new WebView(getContext());
                        webView.clearCache(true);
                        webView.clearHistory();
                        webView.destroy();
                    } catch (Exception e) {}
                    try {
                        File dir = new File(ApplicationLoader.applicationContext.getApplicationInfo().dataDir, "app_webview");
                        if (dir.exists()) {
                            deleteDirectory(dir, false);
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    try {
                        File dir = new File(ApplicationLoader.applicationContext.getApplicationInfo().dataDir, "cache/WebView");
                        if (dir.exists()) {
                            deleteDirectory(dir, null);
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    WebMetadataCache.getInstance().clear();
                    loadSizes();
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .makeRed(AlertDialog.BUTTON_POSITIVE)
                .show();
        } else if (item.id == BUTTON_CLEAR_COOKIES) {
            new AlertDialog.Builder(getContext(), getResourceProvider())
                .setTitle(getString(R.string.BrowserSettingsCookiesClear))
                .setMessage(formatString(R.string.BrowserSettingsCookiesClearText, cookiesSize == 0 ? "" : " (" + AndroidUtilities.formatFileSize(cookiesSize)+")"))
                .setPositiveButton(getString(R.string.Clear), (di, w) -> {
                    CookieManager cookieManager = CookieManager.getInstance();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        cookieManager.removeAllCookies(null);
                        cookieManager.flush();
                    }
                    try {
                        File dir = new File(ApplicationLoader.applicationContext.getApplicationInfo().dataDir, "app_webview");
                        if (dir.exists()) {
                            deleteDirectory(dir, true);
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    loadSizes();
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .makeRed(AlertDialog.BUTTON_POSITIVE)
                .show();
        } else if (item.id == BUTTON_CLEAR_HISTORY) {
            long firstDate = Long.MAX_VALUE;
            ArrayList<BrowserHistory.Entry> entries = BrowserHistory.getHistory();
            for (BrowserHistory.Entry e : entries) {
                firstDate = Math.min(firstDate, e.time);
            }
            new AlertDialog.Builder(getContext(), getResourceProvider())
                .setTitle(getString(R.string.BrowserSettingsHistoryClear))
                .setMessage(formatString(R.string.BrowserSettingsHistoryClearText, LocaleController.formatDateChat(firstDate / 1000L)))
                .setPositiveButton(getString(R.string.Clear), (di, w) -> {
                    BrowserHistory.clearHistory();
                    historySize = 0;
                    listView.adapter.update(true);
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .makeRed(AlertDialog.BUTTON_POSITIVE)
                .show();
        } else if (item.id == BUTTON_OPEN_HISTORY) {
            final HistoryFragment[] fragment = new HistoryFragment[] { null };
            fragment[0] = new HistoryFragment(null, e -> {
                fragment[0].finishFragment();
                if (whenHistoryClicked != null) {
                    finishFragment();
                    whenHistoryClicked.run(e);
                } else {
                    Browser.openUrl(getContext(), e.url);
                }
            });
            presentFragment(fragment[0]);
        } else if (item.id == BUTTON_CLEAR_LIST) {
            RestrictedDomainsList.getInstance().restrictedDomains.clear();
            RestrictedDomainsList.getInstance().scheduleSave();
            listView.adapter.update(true);
        } else if (item.instanceOf(WebsiteView.Factory.class)) {
            final WebsiteView websiteView = (WebsiteView) view;
            final ArrayList<String> domains = websiteView.domains;
            ItemOptions.makeOptions((ViewGroup) fragmentView, websiteView)
                .add(R.drawable.menu_delete_old, LocaleController.getString(R.string.Remove), () -> {
                    RestrictedDomainsList.getInstance().setRestricted(false, domains.toArray(new String[0]));
                    listView.adapter.update(true);
                })
                .show();
        } else if (item.id == BUTTON_SEARCH_ENGINE) {
            if (getParentActivity() == null) {
                return;
            }
            AtomicReference<Dialog> dialogRef = new AtomicReference<>();

            LinearLayout linearLayout = new LinearLayout(getContext());
            linearLayout.setOrientation(LinearLayout.VERTICAL);

            ArrayList<SearchEngine> searchEngines = SearchEngine.getSearchEngines();
            CharSequence[] items = new CharSequence[ searchEngines.size() ];

            for (int i = 0; i < items.length; ++i) {
                final SearchEngine engine = searchEngines.get(i);
                items[i] = engine.name;

                final int index = i;
                RadioColorCell cell = new RadioColorCell(getParentActivity());
                cell.setPadding(dp(4), 0, dp(4), 0);
                cell.setCheckColor(Theme.getColor(Theme.key_radioBackground), Theme.getColor(Theme.key_dialogRadioBackgroundChecked));
                cell.setTextAndValue(items[index], index == SharedConfig.searchEngineType);
                cell.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ALL));
                linearLayout.addView(cell);
                cell.setOnClickListener(v -> {
                    SharedConfig.setSearchEngineType(index);
                    ((TextCell) view).setValue(SearchEngine.getCurrent().name, true);
                    dialogRef.get().dismiss();
                });
            }

            Dialog dialog = new AlertDialog.Builder(getParentActivity())
                    .setTitle(getString(R.string.SearchEngine))
                    .setView(linearLayout)
                    .setNegativeButton(getString("Cancel", R.string.Cancel), null)
                    .create();
            dialogRef.set(dialog);
            showDialog(dialog);
        } else if (item.id == BUTTON_ADD) {
            final AlertDialog[] dialog = new AlertDialog[1];
            final AlertDialog.Builder b = new AlertDialog.Builder(getContext(), getResourceProvider());
            b.setTitle(getString(R.string.BrowserSettingsAddTitle));

            LinearLayout container = new LinearLayout(getContext());
            container.setOrientation(LinearLayout.VERTICAL);

            TextView textView = new TextView(getContext());
            textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, getResourceProvider()));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textView.setText(getString(R.string.BrowserSettingsAddText));
            container.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 5, 24, 12));

            EditTextBoldCursor editText = new EditTextBoldCursor(getContext()) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(36), MeasureSpec.EXACTLY));
                }
            };
            final Runnable done = () -> {
                final String text = editText.getText().toString();
                Uri uri = Uri.parse(text);
                if (uri == null || uri.getHost() == null) {
                    uri = Uri.parse("https://" + text);
                }
                if (uri == null || uri.getHost() == null) {
                    AndroidUtilities.shakeView(editText);
                    return;
                }
                String _domain = uri.getHost().toLowerCase();
                if (_domain.startsWith("www.")) _domain = _domain.substring(4);
                final String domain = _domain;
                RestrictedDomainsList.getInstance().setRestricted(true, domain);
                final WebMetadataCache.WebMetadata cached_meta = WebMetadataCache.getInstance().get(domain);
                if (cached_meta != null && !TextUtils.isEmpty(cached_meta.sitename) && cached_meta.favicon != null) {
                    if (dialog[0] != null) {
                        dialog[0].dismiss();
                    }
                    listView.adapter.update(true);
                } else {
                    final AlertDialog progressDialog = new AlertDialog(getContext(), AlertDialog.ALERT_TYPE_SPINNER);
                    final Runnable dismiss = () -> {
                        dialog[0].dismiss();
                        progressDialog.dismissUnless(800);
                        listView.adapter.update(true);
                    };
                    AndroidUtilities.runOnUIThread(dismiss, 5_000);
                    progressDialog.showDelayed(300);
                    WebMetadataCache.retrieveFaviconAndSitename("https://" + text + "/", (sitename, favicon) -> {
                        AndroidUtilities.cancelRunOnUIThread(dismiss);
                        progressDialog.dismissUnless(800);
                        WebMetadataCache.WebMetadata meta = WebMetadataCache.getInstance().get(domain);
                        if (meta != null) {
                            listView.adapter.update(true);
                        }
                    });
                }
            };
            editText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        done.run();
                        return true;
                    }
                    return false;
                }
            });
            editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            editText.setText("");
            editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, getResourceProvider()));
            editText.setHintColor(Theme.getColor(Theme.key_groupcreate_hintText, getResourceProvider()));
            editText.setHintText(LocaleController.getString(R.string.BrowserSettingsAddHint));
            editText.setSingleLine(true);
            editText.setFocusable(true);
            editText.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            editText.setLineColors(Theme.getColor(Theme.key_windowBackgroundWhiteInputField, getResourceProvider()), Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated, getResourceProvider()), Theme.getColor(Theme.key_text_RedRegular, getResourceProvider()));
            editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
            editText.setBackgroundDrawable(null);
            editText.setPadding(0, 0, dp(42), 0);
            container.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 0, 24, 10));
            b.setView(container);
            b.setWidth(dp(292));


            b.setPositiveButton(LocaleController.getString(R.string.Done), (dialogInterface, i) -> {
                done.run();
            });
            b.setNegativeButton(LocaleController.getString(R.string.Cancel), (dialogInterface, i) -> {
                dialogInterface.dismiss();
            });

            dialog[0] = b.create();
            dialog[0].setOnDismissListener(d -> {
                AndroidUtilities.hideKeyboard(editText);
            });
            dialog[0].setOnShowListener(d -> {
                editText.requestFocus();
                AndroidUtilities.showKeyboard(editText);
            });
            dialog[0].setDismissDialogByButtons(false);
            dialog[0].show();
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    public static class WebsiteView extends FrameLayout {
        public final ImageView imageView;
        public final TextView titleView;
        public final TextView subtitleView;
        public final ImageView optionsView;

        public WebsiteView(Context context) {
            super(context);

            imageView = new ImageView(context);
            addView(imageView, LayoutHelper.createFrame(28, 28, Gravity.CENTER_VERTICAL | Gravity.LEFT, 18, 0, 0, 0));

            titleView = new TextView(context);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            titleView.setTypeface(AndroidUtilities.bold());
            titleView.setMaxLines(1);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 64 + 4, 7, 54, 0));

            subtitleView = new TextView(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                    subtitleView.setPivotY(getMeasuredHeight() / 2f);
                }
            };
            subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            subtitleView.setMaxLines(1);
            subtitleView.setEllipsize(TextUtils.TruncateAt.END);
            subtitleView.setPivotX(0);
            addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 64 + 4, 30, 54, 0));

            optionsView = new ImageView(context);
            optionsView.setScaleType(ImageView.ScaleType.CENTER);
            optionsView.setImageResource(R.drawable.ic_ab_other);
            optionsView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.SRC_IN));
            addView(optionsView, LayoutHelper.createFrame(32, 32, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 18, 0));
        }

        private ArrayList<String> domains;
        private boolean needDivider;
        public void set(
            CharSequence title,
            ArrayList<String> domains,
            Bitmap favicon,
            boolean divider
        ) {
            titleView.setText(title);
            StringBuilder subtitle = new StringBuilder();
            for (String domain : domains) {
                if (subtitle.length() > 0) {
                    subtitle.append(", ");
                }
                subtitle.append(domain);
            }
            subtitleView.setText(subtitle);
            if (TextUtils.isEmpty(title)) {
                subtitleView.setTranslationY(-dp(14));
                subtitleView.setScaleX(1.3f);
                subtitleView.setScaleY(1.3f);
            } else {
                subtitleView.setTranslationY(0);
                subtitleView.setScaleX(1f);
                subtitleView.setScaleY(1f);
            }
            this.domains = domains;
            String s = (TextUtils.isEmpty(title) ? domains.isEmpty() || TextUtils.isEmpty(domains.get(0)) ? "" : domains.get(0) : title).toString();
            if (favicon != null) {
                imageView.setImageBitmap(favicon);
            } else {
                CombinedDrawable drawable = new CombinedDrawable(
                    Theme.createRoundRectDrawable(dp(6), Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), .1f)),
                    new Drawable() {
                        private final Text text = new Text(s.substring(0, !s.isEmpty() ? 1 : 0), 14, AndroidUtilities.bold());
                        @Override
                        public void draw(@NonNull Canvas canvas) {
                            text.draw(canvas, getBounds().centerX() - text.getCurrentWidth() / 2f, getBounds().centerY(), Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), 1f);
                        }
                        @Override
                        public void setAlpha(int alpha) {}
                        @Override
                        public void setColorFilter(@Nullable ColorFilter colorFilter) {}
                        @Override
                        public int getOpacity() {
                            return PixelFormat.TRANSPARENT;
                        }
                    }
                );
                drawable.setCustomSize(dp(28), dp(28));
                imageView.setImageDrawable(drawable);
            }
            if (needDivider != divider) invalidate();
            setWillNotDraw(!(needDivider = divider));
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            canvas.drawRect(dp(64), getHeight() - 1, getWidth(), getHeight(), Theme.dividerPaint);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(56), MeasureSpec.EXACTLY)
            );
        }

        public static class Factory extends UItem.UItemFactory<WebsiteView> {
            static { setup(new Factory()); }
            @Override
            public WebsiteView createView(Context context, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new WebsiteView(context);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider) {
                ((WebsiteView) view).set(item.text, (ArrayList<String>) item.object2, item.object instanceof Bitmap ? ((Bitmap) item.object) : null, divider);
            }

            public static UItem as(ArrayList<String> domains, String sitename, Bitmap favicon) {
                UItem i = UItem.ofFactory(WebsiteView.Factory.class);
                i.text = sitename;
                i.object = favicon;
                i.object2 = domains;
                return i;
            }
        }
    }

    private static long getDirectorySize(File dir, Boolean cookies) {
        if (dir == null || !dir.exists()) {
            return 0;
        }
        long size = 0;
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    size += getDirectorySize(file, cookies);
                }
            }
        } else {
            if (cookies != null && (cookies != dir.getName().startsWith("Cookies"))) {
                return size;
            }
            size += dir.length();
        }
        return size;
    }

    private static boolean deleteDirectory(File dir, Boolean cookies) {
        if (dir == null || !dir.exists()) {
            return false;
        }
        if (dir.isDirectory()) {
            boolean allDeleted = true;
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (cookies != null && (cookies != file.getName().startsWith("Cookies"))) {
                        continue;
                    }
                    if (!deleteDirectory(file, cookies)) {
                        allDeleted = false;
                    }
                }
            }
            if (allDeleted) {
                dir.delete();
            }
        } else {
            if (cookies != null && (cookies != dir.getName().startsWith("Cookies"))) {
                return false;
            }
            dir.delete();
        }
        return true;
    }
}
