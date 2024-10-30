package org.telegram.ui.web;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.google.android.gms.safetynet.SafeBrowsingThreat;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BottomSheetTabs;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.Text;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Stars.StarsIntroActivity;
import org.telegram.ui.WrappedResourceProvider;

import java.net.IDN;
import java.net.URLDecoder;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Collections;

public class AddressBarList extends FrameLayout {

    public static final int MAX_RECENTS = 20;
    public final int currentAccount = UserConfig.selectedAccount;

    public final WrappedResourceProvider resourceProvider;

    public boolean hideCurrent;
    private final Drawable currentViewBackground;
    public final FrameLayout currentContainer;
    public final FrameLayout currentView;
    public final ImageView currentIconView;
    public final Drawable currentCopyBackground;
    public final ImageView currentCopyView;
    public final LinearLayout currentTextContainer;
    public final TextView currentTitleView, currentLinkView;

    public final View space;

    public UniversalRecyclerView listView;

    public final ArrayList<String> suggestions = new ArrayList<>();

    private final BookmarksList bookmarksList;

    public AddressBarList(Context context) {
        super(context);

        setWillNotDraw(false);

        listView = new UniversalRecyclerView(context, UserConfig.selectedAccount, 0, this::fillItems, this::itemClick, null, resourceProvider = new WrappedResourceProvider(null)) {
            @Override
            public void onScrolled(int dx, int dy) {
                super.onScrolled(dx, dy);
                if (!canScrollVertically(1) && bookmarksList != null && bookmarksList.attached) {
                    bookmarksList.load();
                }
            }
        };
        listView.adapter.setApplyBackground(false);
        listView.setOverScrollMode(OVER_SCROLL_NEVER);
        listView.setPadding(0, 0, 0, 0);
        addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        currentContainer = new FrameLayout(context);

        currentView = new FrameLayout(context);
        currentView.setBackground(currentViewBackground = Theme.createRadSelectorDrawable(grayBackgroundColor, rippleColor, 15, 15));
        ScaleStateListAnimator.apply(currentView, .04f, 1.25f);
        currentContainer.addView(currentView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL, 12, 0, 12, 15));

        currentIconView = new ImageView(context);
        currentView.addView(currentIconView, LayoutHelper.createFrame(24, 24, Gravity.LEFT | Gravity.CENTER_VERTICAL, 16, 16, 16, 16));

        currentCopyView = new ImageView(context);
        ScaleStateListAnimator.apply(currentCopyView);
        currentCopyView.setScaleType(ImageView.ScaleType.CENTER);
        currentCopyView.setImageResource(R.drawable.msg_copy);
        currentCopyView.setBackground(currentCopyBackground = Theme.createRadSelectorDrawable(0, 0, 6, 6));
        currentView.addView(currentCopyView, LayoutHelper.createFrame(32, 32, Gravity.TOP | Gravity.RIGHT, 14, 14, 14, 14));

        currentTextContainer = new LinearLayout(context);
        currentTextContainer.setOrientation(LinearLayout.VERTICAL);
        currentView.addView(currentTextContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 54, 9, 54, 9));

        currentTitleView = new TextView(context);
        currentTitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        currentTitleView.setTypeface(AndroidUtilities.bold());
        currentTitleView.setMaxLines(4);
        currentTitleView.setEllipsize(TextUtils.TruncateAt.END);
        currentTextContainer.addView(currentTitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP, 0, 0, 0, 2));

        currentLinkView = new TextView(context);
        currentLinkView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        currentLinkView.setMaxLines(3);
        currentLinkView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        currentTextContainer.addView(currentLinkView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP, 0, 0, 0, 0));

        bookmarksList = new BookmarksList(currentAccount, () -> listView.adapter.update(true));

        space = new View(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(6), MeasureSpec.EXACTLY));
            }
        };

        setColors(Theme.getColor(Theme.key_iv_background), AndroidUtilities.computePerceivedBrightness(Theme.getColor(Theme.key_iv_background)) >= .721f ? Color.BLACK : Color.WHITE);
        setOpenProgress(0f);
    }

    private void clearRecentSearches(View view) {
        new AlertDialog.Builder(getContext())
            .setTitle(getString(R.string.WebRecentClearTitle))
            .setMessage(getString(R.string.WebRecentClearText))
            .setPositiveButton(getString(R.string.OK), (di, w) -> {
                AddressBarList.clearRecentSearches(getContext());
                listView.adapter.update(true);
            }).setNegativeButton(getString(R.string.Cancel), null)
            .show();
    }

    public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (!hideCurrent && suggestions.isEmpty()) {
            items.add(UItem.asCustom(currentContainer));
        }

        final ArrayList<String> queries = getRecentSearches(getContext());
        final int count = suggestions.size() + queries.size();
        if (!suggestions.isEmpty()) {
            items.add(UItem.asCustom(space));
        }
        for (int i = 0; i < suggestions.size(); ++i) {
            final String query = suggestions.get(i);
            final boolean top = i == 0;
            final boolean bottom = i == suggestions.size() - 1;
            items.add(Address2View.Factory.as(1, query, v -> {
                if (onQueryInsertClick != null) {
                    onQueryInsertClick.run(query);
                }
            }, top, bottom, this));
        }
        if (!queries.isEmpty()) {
            items.add(UItem.asGraySection(getString(R.string.WebSectionRecent), getString(R.string.WebRecentClear), this::clearRecentSearches));
            for (int i = 0; i < queries.size(); ++i) {
                final String query = queries.get(i);
                final boolean top = i == 0  ;
                final boolean bottom = i == queries.size() - 1;
                items.add(Address2View.Factory.as(0, query, v -> {
                    if (onQueryInsertClick != null) {
                        onQueryInsertClick.run(query);
                    }
                }, top, bottom, this));
            }
        }
        if (bookmarksList != null && !bookmarksList.links.isEmpty()) {
            items.add(UItem.asGraySection(getString(R.string.WebSectionBookmarks)));
            for (int i = 0; i < bookmarksList.links.size(); ++i) {
                final MessageObject msg = bookmarksList.links.get(i);
                final String url = getLink(msg);
                if (TextUtils.isEmpty(url)) continue;
                items.add(BookmarkView.Factory.as(msg, true));
            }
            if (!bookmarksList.endReached) {
                items.add(UItem.asFlicker(items.size(), FlickerLoadingView.BROWSER_BOOKMARK));
                items.add(UItem.asFlicker(items.size(), FlickerLoadingView.BROWSER_BOOKMARK));
                items.add(UItem.asFlicker(items.size(), FlickerLoadingView.BROWSER_BOOKMARK));
            }
        }
//        items.add(UItem.asCustom(footer));
//        if (clearView != null) {
//            clearView.setVisibility(count > 0 && !queries.isEmpty() ? View.VISIBLE : View.GONE);
//        }
//        if (poweredView != null) {
//            poweredView.setVisibility(View.VISIBLE);
//            poweredView.setText(formatString(R.string.WebPoweredBy, SearchEngine.getCurrent().name));
//        }
    }

    public static String getLink(MessageObject msg) {
        String url = null;
        if (msg.messageOwner != null && msg.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage) {
            url = msg.messageOwner.media.webpage.url;
        } else if (msg.messageText != null && msg.messageText.length() > 0) {
            SpannableStringBuilder ssb = new SpannableStringBuilder(msg.messageText);
            URLSpan[] links = ssb.getSpans(0, ssb.length(), URLSpan.class);
            for (int i = 0; i < links.length; ++i) {
                String this_url = links[i].getURL();
                if (this_url != null && !(this_url.startsWith("@") || this_url.startsWith("#") || this_url.startsWith("$"))) {
                    return this_url;
                }
            }
        }
        return url;
    }

    public void itemClick(UItem item, View view, int position, float x, float y) {
        if (item.instanceOf(Address2View.Factory.class)) {
            String query = item.text.toString();
            if (onQueryClick != null) {
                onQueryClick.run(query);
            }
        } else if (item.instanceOf(BookmarkView.Factory.class)) {
            if (onURLClick != null) {
                try {
                    onURLClick.run(getLink((MessageObject) item.object2));
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }
    }

    private int backgroundColor, listBackgroundColor, grayBackgroundColor, textColor, rippleColor;

    @Override
    protected void dispatchDraw(Canvas canvas) {
        canvas.save();
        canvas.clipRect(0, 0, getWidth(), getHeight() * openProgress);
        canvas.drawColor(listBackgroundColor);
        super.dispatchDraw(canvas);
        canvas.restore();
    }

    private float openProgress = 0f;
    public void setOpenProgress(float progress) {
        if (Math.abs(openProgress - progress) > 0.0001f) {
            this.openProgress = progress;
//            for (int i = 0; i < listView.getChildCount(); ++i) {
//                View child = listView.getChildAt(i);
//                final float alpha = AndroidUtilities.cascade(openProgress, i, listView.getChildCount(), 3);
//                child.setAlpha(alpha);
//                child.setTranslationY(-dp(Math.min(48, 8 + 6 * i)) * (1 - alpha));
//            }
            invalidate();
        }
    }

    public boolean opened;
    public void setOpened(boolean value) {
        if (opened = value && bookmarksList != null) {
            bookmarksList.attach();
        }
    }

    private float[] hsv = new float[3];

    public void setColors(int backgroundColor, int textColor) {
        if (this.backgroundColor != backgroundColor) {
            this.backgroundColor = backgroundColor;
            invalidate();
        }
        this.textColor = textColor;

        final float dark = AndroidUtilities.computePerceivedBrightness(backgroundColor) >= .721f ? 0f : 1f;
        grayBackgroundColor = ColorUtils.blendARGB(backgroundColor, textColor, lerp(.05f, .12f, dark));
        listBackgroundColor = backgroundColor;
        rippleColor = ColorUtils.blendARGB(backgroundColor, textColor, lerp(.12f, .22f, dark));

        Theme.setSelectorDrawableColor(currentViewBackground, grayBackgroundColor, false);
        Theme.setSelectorDrawableColor(currentViewBackground, rippleColor, true);
        currentView.invalidate();
        currentTitleView.setTextColor(textColor);
        currentLinkView.setTextColor(Theme.multAlpha(textColor, .6f));
        if (currentIconView.getColorFilter() != null) {
            currentIconView.setColorFilter(new PorterDuffColorFilter(textColor, PorterDuff.Mode.SRC_IN));
        }
        currentCopyView.setColorFilter(new PorterDuffColorFilter(textColor, PorterDuff.Mode.SRC_IN));
        Theme.setSelectorDrawableColor(currentCopyBackground, Theme.multAlpha(rippleColor, 1.5f), true);

        final int greySectionBackground = Theme.blendOver(backgroundColor, Theme.multAlpha(textColor, 0.05f));
        final int greySectionText = Theme.blendOver(backgroundColor, Theme.multAlpha(textColor, 0.55f));
        resourceProvider.sparseIntArray.put(Theme.key_windowBackgroundWhite, listBackgroundColor);
        resourceProvider.sparseIntArray.put(Theme.key_windowBackgroundWhiteBlackText, textColor);
        resourceProvider.sparseIntArray.put(Theme.key_graySection, greySectionBackground);
        resourceProvider.sparseIntArray.put(Theme.key_graySectionText, greySectionText);
        resourceProvider.sparseIntArray.put(Theme.key_actionBarDefaultSubmenuBackground, Theme.multAlpha(textColor, .2f));
        resourceProvider.sparseIntArray.put(Theme.key_listSelector, Theme.multAlpha(textColor, lerp(0.05f, 0.12f, dark)));
        listView.invalidateViews();
    }

    private Runnable onCurrentClick;
    private Utilities.Callback<String> onQueryClick;
    private Utilities.Callback<String> onQueryInsertClick;
    private Utilities.Callback<String> onURLClick;

    public void setCurrent(
        Bitmap favicon,
        String title,
        String url,
        Runnable onCurrentClick,
        Utilities.Callback<String> onQueryClick,
        Utilities.Callback<String> onQueryInsertClick,
        Utilities.Callback<String> onURLClick,
        View.OnClickListener onCopyClick
    ) {
        if (favicon == null) {
            currentIconView.setImageResource(R.drawable.msg_language);
            currentIconView.setColorFilter(new PorterDuffColorFilter(textColor, PorterDuff.Mode.SRC_IN));
        } else {
            currentIconView.setImageDrawable(new BitmapDrawable(getContext().getResources(), favicon));
            currentIconView.setColorFilter(null);
        }
        currentTitleView.setText(Emoji.replaceEmoji(title, currentTitleView.getPaint().getFontMetricsInt(), false));

        String formattedUrl = url;
        try {
            try {
                Uri uri = Uri.parse(formattedUrl);
                formattedUrl = Browser.replaceHostname(uri, Browser.IDN_toUnicode(uri.getHost()), null);
            } catch (Exception e) {
                FileLog.e(e, false);
            }
            formattedUrl = URLDecoder.decode(formattedUrl.replaceAll("\\+", "%2b"), "UTF-8");
        } catch (Exception e) {
            FileLog.e(e);
        }
        currentLinkView.setText(Emoji.replaceEmoji(formattedUrl, currentLinkView.getPaint().getFontMetricsInt(), false));

        this.onCurrentClick = onCurrentClick;
        this.onQueryClick = onQueryClick;
        this.onQueryInsertClick = onQueryInsertClick;
        this.onURLClick = onURLClick;
        currentView.setOnClickListener(v -> {
            hideCurrent = true;
            if (onCurrentClick != null) {
                onCurrentClick.run();
            }
            listView.adapter.update(true);
        });
        currentCopyView.setOnClickListener(onCopyClick);

        hideCurrent = false;
        setInput(null);
        listView.adapter.update(true);
        listView.scrollToPosition(0);
    }

    private AsyncTask<String, Void, String> lastTask;
    public void setInput(String input) {
        if (lastTask != null) {
            lastTask.cancel(true);
            lastTask = null;
        }

        final boolean hadSuggestions = !suggestions.isEmpty();

        if (TextUtils.isEmpty(input)) {
            suggestions.clear();
            listView.adapter.update(true);
            if (hadSuggestions != !suggestions.isEmpty()) {
                listView.layoutManager.scrollToPositionWithOffset(0, 0);
            }
            return;
        }

        lastTask = new HttpGetTask(result -> AndroidUtilities.runOnUIThread(() -> {
            suggestions.clear();
            suggestions.addAll(SearchEngine.getCurrent().extractSuggestions(result));
            listView.adapter.update(true);
            if (hadSuggestions != !suggestions.isEmpty()) {
                listView.layoutManager.scrollToPositionWithOffset(0, 0);
            }
        })).execute(SearchEngine.getCurrent().getAutocompleteURL(input));
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (openProgress < .3f) {
            return false;
        }
        return super.dispatchTouchEvent(ev);
    }

//    public static class AddressView extends FrameLayout {
//
//        public final ImageView iconView;
//        public final BackupImageView iconView2;
//        public final TextView textView;
//        public final ImageView insertView;
//
//        public AddressView(Context context) {
//            super(context);
//
//            ScaleStateListAnimator.apply(this, .04f, 1.25f);
//
//            iconView = new ImageView(context);
//            iconView.setScaleType(ImageView.ScaleType.CENTER);
//            iconView.setImageResource(R.drawable.msg_clear_recent);
//            addView(iconView, LayoutHelper.createFrame(32, 32, Gravity.CENTER_VERTICAL | Gravity.LEFT, 10, 8, 8, 8));
//
//            iconView2 = new BackupImageView(context);
//            iconView2.setVisibility(View.GONE);
//            addView(iconView2, LayoutHelper.createFrame(32, 32, Gravity.CENTER_VERTICAL | Gravity.LEFT, 10, 8, 8, 8));
//
//            textView = new TextView(context);
//            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
//            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 59, 8, 54, 8));
//
//            insertView = new ImageView(context);
//            insertView.setScaleType(ImageView.ScaleType.CENTER);
//            insertView.setImageResource(R.drawable.menu_browser_arrowup);
//            addView(insertView, LayoutHelper.createFrame(32, 32, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 8, 8, 8, 8));
//        }
//
//        public void setTopBottom(int grayBackgroundColor, int rippleColor, boolean top, boolean bottom) {
//            setBackground(Theme.createRadSelectorDrawable(grayBackgroundColor, rippleColor, top ? 15 : 1, bottom ? 15 : 1));
//        }
//
//        public void setColors(int backgroundColor, int textColor) {
//            textView.setTextColor(textColor);
//            iconView.setColorFilter(new PorterDuffColorFilter(textColor, PorterDuff.Mode.SRC_IN));
//            insertView.setColorFilter(new PorterDuffColorFilter(textColor, PorterDuff.Mode.SRC_IN));
//            insertView.setBackground(Theme.createRadSelectorDrawable(0, Theme.multAlpha(textColor, .15f), dp(4), dp(4)));
//        }
//
//        public void set(int type, String query, View.OnClickListener onInsertClick, boolean top, boolean bottom, AddressBarList parent, boolean divider) {
//            iconView.setVisibility(View.VISIBLE);
//            iconView2.setVisibility(View.GONE);
//            setColors(parent.listBackgroundColor, parent.textColor);
//            iconView.setImageResource(type == 0 ? R.drawable.msg_clear_recent : R.drawable.msg_search);
//            textView.setText(query);
//            insertView.setOnClickListener(onInsertClick);
//            setTopBottom(parent.grayBackgroundColor, parent.rippleColor, top, bottom);
//            dividerPaint.setColor(parent.listBackgroundColor);
//            setWillNotDraw(!(needDivider = divider));
//        }
//
//        private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
//        private boolean needDivider;
//
//        @Override
//        protected void onDraw(Canvas canvas) {
//            super.onDraw(canvas);
//            if (needDivider) {
//                canvas.drawRect(dp(59), getHeight() - Math.max(dp(.66f), 1), getWidth(), getHeight(), dividerPaint);
//            }
//        }
//
//        @Override
//        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
//            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), heightMeasureSpec);
//        }
//
//        public static class Factory extends UItem.UItemFactory<AddressView> {
//            @Override
//            public AddressView createView(Context context, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
//                return new AddressView(context);
//            }
//
//            @Override
//            public void bindView(View view, UItem item, boolean divider) {
//                ((AddressView) view).set(item.intValue, item.text.toString(), item.clickCallback, item.accent, item.red, (AddressBarList) item.object, divider);
//            }
//
//            public static UItem as(int type, String query, View.OnClickListener onInsertClick, boolean top, boolean bottom, AddressBarList parent) {
//                UItem item = UItem.ofFactory(AddressView.Factory.class);
//                item.intValue = type;
//                item.text = query;
//                item.clickCallback = onInsertClick;
//                item.accent = top;
//                item.red = bottom;
//                item.object = parent;
//                return item;
//            }
//        }
//
//    }


    public static class Address2View extends FrameLayout {

        public final ImageView iconView;
        public final TextView textView;
        public final ImageView insertView;

        public Address2View(Context context) {
            super(context);

//            ScaleStateListAnimator.apply(this, .04f, 1.25f);

            iconView = new ImageView(context);
            iconView.setScaleType(ImageView.ScaleType.CENTER);
            iconView.setImageResource(R.drawable.menu_clear_recent);
            addView(iconView, LayoutHelper.createFrame(32, 32, Gravity.CENTER_VERTICAL | Gravity.LEFT, 10, 8, 8, 8));

            textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 64, 8, 64, 8));

            insertView = new ImageView(context);
            insertView.setScaleType(ImageView.ScaleType.CENTER);
            insertView.setImageResource(R.drawable.menu_browser_arrowup);
            addView(insertView, LayoutHelper.createFrame(32, 32, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 8, 8, 10, 8));
        }

        public void setTopBottom(int grayBackgroundColor, int rippleColor, boolean top, boolean bottom) {
//            setBackground(Theme.createRadSelectorDrawable(grayBackgroundColor, rippleColor, top ? 15 : 1, bottom ? 15 : 1));
        }

        public void setColors(int backgroundColor, int textColor) {
            textView.setTextColor(textColor);
            iconView.setColorFilter(new PorterDuffColorFilter(Theme.multAlpha(textColor, .6f), PorterDuff.Mode.SRC_IN));
            insertView.setColorFilter(new PorterDuffColorFilter(Theme.multAlpha(textColor, .6f), PorterDuff.Mode.SRC_IN));
            insertView.setBackground(Theme.createRadSelectorDrawable(0, Theme.multAlpha(textColor, .15f), dp(4), dp(4)));
        }

        public void set(int type, String query, View.OnClickListener onInsertClick, boolean top, boolean bottom, AddressBarList parent, boolean divider) {
            iconView.setVisibility(View.VISIBLE);
            setColors(parent.listBackgroundColor, parent.textColor);
            iconView.setImageResource(type == 0 ? R.drawable.msg_clear_recent : R.drawable.msg_search);
            textView.setText(query);
            insertView.setOnClickListener(onInsertClick);
            setTopBottom(parent.grayBackgroundColor, parent.rippleColor, top, bottom);
            dividerPaint.setColor(Theme.multAlpha(parent.textColor, .1f));
            setWillNotDraw(!(needDivider = divider));
        }

        public void setAsShowMore(AddressBarList parent) {
            iconView.setImageResource(R.drawable.arrow_more);
            iconView.setColorFilter(new PorterDuffColorFilter(parent.textColor, PorterDuff.Mode.SRC_IN));
        }

        private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean needDivider;

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (needDivider) {
                canvas.drawRect(dp(64), getHeight() - Math.max(dp(.66f), 1), getWidth(), getHeight(), dividerPaint);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), heightMeasureSpec);
        }

        public static class Factory extends UItem.UItemFactory<Address2View> {
            static { setup(new Factory()); }
            @Override
            public Address2View createView(Context context, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new Address2View(context);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider) {
                Address2View cell = (Address2View) view;
                if (item.object == null) {
                    cell.setAsShowMore((AddressBarList) item.object2);
                } else {
                    cell.set(item.intValue, item.text.toString(), item.clickCallback, item.accent, item.red, (AddressBarList) item.object2, divider);
                }
            }

            public static UItem as(int type, String query, View.OnClickListener onInsertClick, boolean top, boolean bottom, AddressBarList parent) {
                UItem item = UItem.ofFactory(Address2View.Factory.class);
                item.intValue = type;
                item.text = query;
                item.clickCallback = onInsertClick;
                item.accent = top;
                item.red = bottom;
                item.object = true;
                item.object2 = parent;
                return item;
            }

            public static UItem asMore() {
                UItem item = UItem.ofFactory(Address2View.Factory.class);
                item.object = null;
                return item;
            }
        }

    }


    public static class BookmarkView extends FrameLayout implements Theme.Colorable {

        private final Theme.ResourcesProvider resourcesProvider;

        public final BackupImageView iconView;
        public final LinearLayout textLayout;
        public final FrameLayout.LayoutParams textLayoutParams;
        public final TextView textView, subtextView, timeView;
        public final ImageView insertView;

        public final CheckBox2 checkBox;

        public BookmarkView(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);

            this.resourcesProvider = resourcesProvider;
            ScaleStateListAnimator.apply(this, .03f, 1.25f);

            iconView = new BackupImageView(context);
            iconView.setRoundRadius(dp(6));
            addView(iconView, LayoutHelper.createFrame(32, 32, Gravity.CENTER_VERTICAL | Gravity.LEFT, 10, 8, 8, 8));

            textLayout = new LinearLayout(context);
            textLayout.setOrientation(LinearLayout.VERTICAL);

            textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textView.setMaxLines(1);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT));

            subtextView = new TextView(context);
            subtextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            subtextView.setMaxLines(1);
            subtextView.setEllipsize(TextUtils.TruncateAt.END);
            textLayout.addView(subtextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 0, 3, 0, 0));

            addView(textLayout, textLayoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 64, 0, 70, 0));

            timeView = new TextView(context);
            timeView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            timeView.setMaxLines(1);
            timeView.setEllipsize(TextUtils.TruncateAt.END);
            timeView.setGravity(Gravity.RIGHT);
            timeView.setTextAlignment(TEXT_ALIGNMENT_VIEW_END);
            addView(timeView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 64, -10, 12, 0));

            insertView = new ImageView(context);
            insertView.setScaleType(ImageView.ScaleType.CENTER);
            insertView.setImageResource(R.drawable.attach_arrow_right);
            addView(insertView, LayoutHelper.createFrame(32, 32, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 8, 8, 8, 8));

            checkBox = new CheckBox2(getContext(), 21, resourcesProvider) {
                @Override
                public void invalidate() {
                    super.invalidate();
                    BookmarkView.this.invalidate();
                }
            };
            checkBox.setColor(-1, Theme.key_windowBackgroundWhite, Theme.key_checkboxCheck);
            checkBox.setDrawUnchecked(false);
            checkBox.setDrawBackgroundAsArc(3);
            addView(checkBox, LayoutHelper.createFrame(24, 24, Gravity.CENTER_VERTICAL | Gravity.LEFT, 10 + 16, 12, 0, 0));
        }

        @Override
        public void updateColors() {
            final int backgroundColor = Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider);
            final int textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);

            setColors(backgroundColor, textColor);
            dividerPaint.setColor(Theme.multAlpha(textColor, .1f));
            iconView.invalidate();
        }

        private int textColor;
        public void setColors(int backgroundColor, int textColor) {
            this.textColor = textColor;
            textView.setTextColor(textColor);
            subtextView.setTextColor(Theme.blendOver(backgroundColor, Theme.multAlpha(textColor, .55f)));
            timeView.setTextColor(Theme.multAlpha(textColor, .55f));
            insertView.setColorFilter(new PorterDuffColorFilter(Theme.multAlpha(textColor, .6f), PorterDuff.Mode.SRC_IN));
        }

        public void set(MessageObject messageObject, boolean withArrow, String query, boolean checked, boolean divider) {
            updateColors();
            TLRPC.WebPage webPage = MessageObject.getMedia(messageObject) != null ? MessageObject.getMedia(messageObject).webpage : null;
            final String url = webPage != null ? webPage.url : getLink(messageObject);
            final String domain = AndroidUtilities.getHostAuthority(url, true);
            final WebMetadataCache.WebMetadata meta = WebMetadataCache.getInstance().get(domain);
            if (webPage != null && webPage.title != null) {
                textView.setText(webPage.title);
            } else if (webPage != null && webPage.site_name != null) {
                textView.setText(webPage.site_name);
            } else if (meta != null && !TextUtils.isEmpty(meta.title)) {
                textView.setText(meta.title);
            } else if (meta != null && !TextUtils.isEmpty(meta.sitename)) {
                textView.setText(meta.sitename);
            } else {
                try {
                    String[] segments = Uri.parse(url).getHost().split("\\.");
                    String host = segments[segments.length - 2];
                    textView.setText(host.substring(0, 1).toUpperCase() + host.substring(1));
                } catch (Exception e) {
                    textView.setText("");
                }
            }
            iconView.clearImage();
            if (meta != null && meta.favicon != null) {
                iconView.setImageBitmap(meta.favicon);
            } else if (webPage != null && webPage.photo != null) {
                iconView.setImage(
                    ImageLocation.getForPhoto(FileLoader.getClosestPhotoSizeWithSize(webPage.photo.sizes, dp(32), true, null, true), webPage.photo), dp(32)+"_"+dp(32),
                    ImageLocation.getForPhoto(FileLoader.getClosestPhotoSizeWithSize(webPage.photo.sizes, dp(32), true, null, false), webPage.photo), dp(32)+"_"+dp(32), 0, messageObject);
            } else {
                final String s = textView.getText() == null ? "" : textView.getText().toString();
                final BreakIterator bi = BreakIterator.getCharacterInstance();
                bi.setText(s);
                final String firstLetter = s.isEmpty() ? "" : s.substring(bi.first(), bi.next());
                CombinedDrawable drawable = new CombinedDrawable(
                        Theme.createRoundRectDrawable(dp(6), Theme.multAlpha(textColor, .1f)),
                        new Drawable() {
                            private final Text text = new Text(firstLetter, 14, AndroidUtilities.bold());
                            @Override
                            public void draw(@NonNull Canvas canvas) {
                                text.draw(canvas, getBounds().centerX() - text.getCurrentWidth() / 2f, getBounds().centerY(), textColor, 1f);
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
                iconView.setImageDrawable(drawable);
            }
            timeView.setVisibility(View.GONE);
            insertView.setVisibility(withArrow ? View.VISIBLE : View.GONE);
            final String link = webPage != null ? webPage.url : getLink(messageObject);
            String formattedUrl = link;
            try {
                try {
                    Uri uri = Uri.parse(formattedUrl);
                    formattedUrl = Browser.replaceHostname(uri, Browser.IDN_toUnicode(uri.getHost()), null);
                } catch (Exception e) {
                    FileLog.e(e, false);
                }
                formattedUrl = URLDecoder.decode(formattedUrl.replaceAll("\\+", "%2b"), "UTF-8");
                formattedUrl = BottomSheetTabs.urlWithoutFragment(formattedUrl);
            } catch (Exception e) {
                FileLog.e(e);
            }
            subtextView.setText(formattedUrl);
            if (!TextUtils.isEmpty(query)) {
                textView.setText(AndroidUtilities.highlightText(textView.getText(), query, resourcesProvider));
                subtextView.setText(AndroidUtilities.highlightText(subtextView.getText(), query, resourcesProvider));
            }
            textView.setText(Emoji.replaceEmoji(textView.getText(), textView.getPaint().getFontMetricsInt(), false));
            subtextView.setText(Emoji.replaceEmoji(subtextView.getText(), subtextView.getPaint().getFontMetricsInt(), false));
            checkBox.setChecked(checked, false);
            textLayoutParams.rightMargin = dp(52);
            textLayout.setLayoutParams(textLayoutParams);
            setWillNotDraw(!(needDivider = divider));
        }


        public void set(BrowserHistory.Entry entry, String query, boolean divider) {
            updateColors();
            if (entry == null) return;
            final String url = entry.url;
            final WebMetadataCache.WebMetadata meta = entry.meta;
            if (meta != null && !TextUtils.isEmpty(meta.title)) {
                textView.setText(meta.title);
            } else if (meta != null && !TextUtils.isEmpty(meta.sitename)) {
                textView.setText(meta.sitename);
            } else {
                try {
                    String[] segments = Uri.parse(url).getHost().split("\\.");
                    String host = segments[segments.length - 2];
                    textView.setText(host.substring(0, 1).toUpperCase() + host.substring(1));
                } catch (Exception e) {
                    textView.setText("");
                }
            }
            if (meta != null && meta.favicon != null) {
                iconView.setImageBitmap(meta.favicon);
            } else {
                final String s = textView.getText() == null ? "" : textView.getText().toString();
                final BreakIterator bi = BreakIterator.getCharacterInstance();
                bi.setText(s);
                final String firstLetter = s.isEmpty() ? "" : s.substring(bi.first(), bi.next());
                CombinedDrawable drawable = new CombinedDrawable(
                        Theme.createRoundRectDrawable(dp(6), Theme.multAlpha(textColor, .1f)),
                        new Drawable() {
                            private final Text text = new Text(firstLetter, 14, AndroidUtilities.bold());
                            @Override
                            public void draw(@NonNull Canvas canvas) {
                                text.draw(canvas, getBounds().centerX() - text.getCurrentWidth() / 2f, getBounds().centerY(), textColor, 1f);
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
                iconView.setImageDrawable(drawable);
            }
            insertView.setVisibility(View.GONE);
            String formattedUrl = url;
            try {
                try {
                    Uri uri = Uri.parse(formattedUrl);
                    formattedUrl = Browser.replaceHostname(uri, Browser.IDN_toUnicode(uri.getHost()), null);
                } catch (Exception e) {
                    FileLog.e(e, false);
                }
                formattedUrl = URLDecoder.decode(formattedUrl.replaceAll("\\+", "%2b"), "UTF-8");
            } catch (Exception e) {
                FileLog.e(e);
            }
            subtextView.setText(formattedUrl);
            if (!TextUtils.isEmpty(query)) {
                textView.setText(AndroidUtilities.highlightText(textView.getText(), query, resourcesProvider));
                subtextView.setText(AndroidUtilities.highlightText(subtextView.getText(), query, resourcesProvider));
            }
            textView.setText(Emoji.replaceEmoji(textView.getText(), textView.getPaint().getFontMetricsInt(), false));
            subtextView.setText(Emoji.replaceEmoji(subtextView.getText(), subtextView.getPaint().getFontMetricsInt(), false));
            timeView.setText(LocaleController.getInstance().getFormatterDay().format(entry.time));
            checkBox.setChecked(false, false);
            textLayoutParams.rightMargin = dp(70);
            textLayout.setLayoutParams(textLayoutParams);
            setWillNotDraw(!(needDivider = divider));
        }

        public void setChecked(boolean checked) {
            checkBox.setChecked(checked, true);
        }

        private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean needDivider;

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (needDivider) {
                canvas.drawRect(dp(59), getHeight() - Math.max(dp(.66f), 1), getWidth(), getHeight(), dividerPaint);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(56), MeasureSpec.EXACTLY)
            );
        }

        public static class Factory extends UItem.UItemFactory<BookmarkView> {
            static { setup(new Factory()); }
            @Override
            public BookmarkView createView(Context context, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new BookmarkView(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider) {
                BookmarkView cell = (BookmarkView) view;
                if (item.object2 instanceof MessageObject) {
                    cell.set((MessageObject) (item.object2), item.accent, item.subtext == null ? null : item.subtext.toString(), item.checked, divider);
                } else if (item.object2 instanceof BrowserHistory.Entry) {
                    cell.set((BrowserHistory.Entry) (item.object2), item.subtext == null ? null : item.subtext.toString(), divider);
                }
            }

            public static UItem as(int type, String query, boolean withArrow) {
                UItem item = UItem.ofFactory(BookmarkView.Factory.class);
                item.intValue = type;
                item.accent = withArrow;
                item.subtext = query;
                return item;
            }

            public static UItem as(MessageObject msg, boolean withArrow) {
                UItem item = UItem.ofFactory(BookmarkView.Factory.class);
                item.intValue = 3;
                item.accent = withArrow;
                item.object2 = msg;
                return item;
            }

            public static UItem as(MessageObject msg, boolean withArrow, String query) {
                UItem item = UItem.ofFactory(BookmarkView.Factory.class);
                item.intValue = 3;
                item.accent = withArrow;
                item.object2 = msg;
                item.subtext = query;
                return item;
            }

            public static UItem as(BrowserHistory.Entry historyEntry, String query) {
                UItem item = UItem.ofFactory(BookmarkView.Factory.class);
                item.intValue = 3;
                item.accent = false;
                item.object2 = historyEntry;
                item.subtext = query;
                return item;
            }


            @Override
            public boolean equals(UItem a, UItem b) {
                return a.object2 == b.object2 && TextUtils.isEmpty(a.subtext) == TextUtils.isEmpty(b.subtext);
            }

            @Override
            public boolean contentsEquals(UItem a, UItem b) {
                return a.object2 == b.object2 && TextUtils.equals(a.subtext, b.subtext);
            }
        }

    }

    private static class QueryEntry {
        public final String query;
        public long lastUsage;
        public double rank;
        public QueryEntry(String query, long time) {
            this.query = query;
            this.lastUsage = time;
        }
    }

    public static ArrayList<String> getRecentSearches(Context context) {
        final SharedPreferences pref = context.getSharedPreferences("webhistory", Activity.MODE_PRIVATE);
        final ArrayList<String> queries = new ArrayList<>();
        final String json = pref.getString("queries_json", null);
        if (json != null) {
            try {
                final ArrayList<QueryEntry> entries = new ArrayList<>();
                final JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); ++i) {
                    final JSONObject obj = arr.getJSONObject(i);
                    final QueryEntry entry = new QueryEntry(
                        obj.optString("name"),
                        obj.optLong("usage", System.currentTimeMillis())
                    );
                    entry.rank = obj.optDouble("rank", 0.0);
                    entries.add(entry);
                }
                Collections.sort(entries, (a, b) -> (int) (b.rank - a.rank));
                for (QueryEntry e : entries) {
                    if (queries.size() >= MAX_RECENTS) break;
                    queries.add(e.query);
                }
            } catch (Exception e) {}
        }
        return queries;
    }

    public static void pushRecentSearch(Context context, String query) {
        final SharedPreferences pref = context.getSharedPreferences("webhistory", Activity.MODE_PRIVATE);
        final String json = pref.getString("queries_json", null);
        final ArrayList<QueryEntry> entries = new ArrayList<>();
        if (json != null) {
            try {
                final JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); ++i) {
                    final JSONObject obj = arr.getJSONObject(i);
                    final QueryEntry entry = new QueryEntry(
                        obj.optString("name"),
                        obj.optLong("usage", System.currentTimeMillis())
                    );
                    entry.rank = obj.optDouble("rank", 0.0);
                    entries.add(entry);
                }
                Collections.sort(entries, (a, b) -> (int) (b.rank - a.rank));
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        try {
            QueryEntry entry = null;
            for (int j = 0; j < entries.size(); ++j) {
                QueryEntry e = entries.get(j);
                if (TextUtils.equals(e.query, query)) {
                    entry = e;
                    break;
                }
            }
            final long now = System.currentTimeMillis();
            if (entry != null) {
                entry.rank += Math.exp((now - entry.lastUsage) / 2419200.0);
            } else {
                entry = new QueryEntry(query, now);
                entries.add(entry);
            }
            entry.lastUsage = now;

            JSONArray finalArray = new JSONArray();
            for (int i = 0; i < Math.min(entries.size(), MAX_RECENTS); ++i) {
                QueryEntry e = entries.get(i);
                JSONObject obj = new JSONObject();
                obj.put("name", e.query);
                obj.put("rank", e.rank);
                obj.put("usage", e.lastUsage);
                finalArray.put(obj);
            }
            pref.edit().putString("queries_json", finalArray.toString()).apply();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static void clearRecentSearches(Context context) {
        final SharedPreferences pref = context.getSharedPreferences("webhistory", Activity.MODE_PRIVATE);
        pref.edit().remove("queries_json").apply();
    }

    public static class BookmarksList implements NotificationCenter.NotificationCenterDelegate {

        public final ArrayList<MessageObject> links = new ArrayList<MessageObject>();

        private final int currentAccount;
        private final Runnable whenUpdated;
        private final String query;
        private int guid = ConnectionsManager.generateClassGuid();

        public BookmarksList(int currentAccount, Runnable whenUpdated) {
            this(currentAccount, null, whenUpdated);
        }

        public BookmarksList(int currentAccount, String query, Runnable whenUpdated) {
            this.currentAccount = currentAccount;
            this.query = query;
            this.whenUpdated = whenUpdated;
        }

        public boolean endReached;
        private boolean attached;
        public void attach() {
            if (attached) return;
            attached = true;
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.mediaDidLoad);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.bookmarkAdded);
            if (TextUtils.isEmpty(query)) {
                load();
            }
        }

        public void detach() {
            if (!attached) return;
            attached = false;
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.mediaDidLoad);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.bookmarkAdded);
            ConnectionsManager.getInstance(currentAccount).cancelRequestsForGuid(guid);
            loading = false;
        }

        public void delete(ArrayList<Integer> ids) {
            for (int i = 0; i < links.size(); ++i) {
                if (ids.contains(links.get(i).getId())) {
                    links.remove(i);
                    i--;
                }
            }
        }

        private boolean loading;
        public void load() {
            if (loading || endReached) return;
            loading = true;
            final long selfId = UserConfig.getInstance(currentAccount).getClientUserId();
            int min_id = Integer.MAX_VALUE;
            for (int i = 0; i < links.size(); ++i) {
                min_id = Math.min(min_id, links.get(i).getId());
            }
            MediaDataController.getInstance(currentAccount).loadMedia(selfId, links.isEmpty() ? 30 : 50, min_id == Integer.MAX_VALUE ? 0 : min_id, 0, MediaDataController.MEDIA_URL, 0, 1, guid, 0, null, query);
        }

        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (id == NotificationCenter.mediaDidLoad) {
                int guid = (Integer) args[3];
                if (guid == this.guid) {
                    loading = false;
                    final ArrayList<MessageObject> msgs = (ArrayList<MessageObject>) args[2];
                    endReached = ((Boolean) args[5]);
                    links.addAll(msgs);
                    whenUpdated.run();
                }
            } else if (id == NotificationCenter.bookmarkAdded) {
                MessageObject msg = (MessageObject) args[0];
                links.add(0, msg);
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (bookmarksList != null && opened) {
            bookmarksList.attach();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (bookmarksList != null) {
            bookmarksList.detach();
        }
    }
}
