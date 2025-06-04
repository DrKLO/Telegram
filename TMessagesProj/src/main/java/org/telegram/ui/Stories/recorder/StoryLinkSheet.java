package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.EditTextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.CircularProgressDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LoadingSpan;
import org.telegram.ui.Components.Paint.Views.LinkPreview;
import org.telegram.ui.Components.Paint.Views.StoryLinkPreviewDialog;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;
import java.util.regex.Pattern;

public class StoryLinkSheet extends BottomSheetWithRecyclerListView implements NotificationCenter.NotificationCenterDelegate {

    private UniversalAdapter adapter;
    private EditTextCell urlEditText;
    private EditTextCell nameEditText;
    private FrameLayout buttonContainer;
    private ButtonWithCounterView button;

    private boolean ignoreUrlEdit;
    private boolean needRemoveDefPrefix;

    private Utilities.Callback<LinkPreview.WebPagePreview> whenDone;

    public StoryLinkSheet(Context context, Theme.ResourcesProvider resourcesProvider, PreviewView previewView, Utilities.Callback<LinkPreview.WebPagePreview> whenDone) {
        super(context, null, true, false, false, true, ActionBarType.SLIDING, resourcesProvider);
        this.whenDone = whenDone;

        fixNavigationBar();
        setSlidingActionBar();
        headerPaddingTop = dp(4);
        headerPaddingBottom = dp(-15);

        urlEditText = new EditTextCell(context, getString(R.string.StoryLinkURLPlaceholder), true, false, -1, resourcesProvider);
        urlEditText.whenHitEnter(this::processDone);

        String def = "https://";
        urlEditText.editText.setHandlesColor(0xFF419FE8);
        urlEditText.editText.setCursorColor(0xff54a1db);
        urlEditText.editText.setText(def);
        urlEditText.editText.setSelection(def.length());
        TextView pasteTextView = new TextView(getContext());
        pasteTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        pasteTextView.setTypeface(AndroidUtilities.bold());
        pasteTextView.setText(getString(R.string.Paste));
        pasteTextView.setPadding(dp(10), 0, dp(10), 0);
        pasteTextView.setGravity(Gravity.CENTER);
        int textColor = getThemedColor(Theme.key_windowBackgroundWhiteBlueText2);
        pasteTextView.setTextColor(textColor);
        pasteTextView.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(6), Theme.multAlpha(textColor, .12f), Theme.multAlpha(textColor, .15f)));
        ScaleStateListAnimator.apply(pasteTextView, .1f, 1.5f);
        urlEditText.addView(pasteTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 26, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 4, 24, 3));

        final Runnable checkPaste = () -> {
            ClipboardManager clipboardManager = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            final boolean show = (TextUtils.isEmpty(urlEditText.editText.getText()) || TextUtils.equals(urlEditText.editText.getText(), def) || TextUtils.isEmpty(urlEditText.editText.getText().toString())) && clipboardManager != null && clipboardManager.hasPrimaryClip();
            pasteTextView.animate()
                .alpha(show ? 1f : 0f)
                .scaleX(show ? 1f : .7f)
                .scaleY(show ? 1f : .7f)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .setDuration(300)
                .start();
        };
        pasteTextView.setOnClickListener(v -> {
            ClipboardManager clipboardManager = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            CharSequence text = null;
            try {
                text = clipboardManager.getPrimaryClip().getItemAt(0).coerceToText(getContext());
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (text != null) {
                urlEditText.editText.setText(text.toString());
                urlEditText.editText.setSelection(0, urlEditText.editText.getText().length());
            }
            checkPaste.run();
        });
        checkPaste.run();
        urlEditText.editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (ignoreUrlEdit) {
                    return;
                }
                needRemoveDefPrefix = s != null
                        && start == def.length()
                        && s.subSequence(0, start).toString().equals(def)
                        && s.length() >= start + count
                        && s.subSequence(start, start + count).toString().startsWith(def);
            }

            @Override
            public void afterTextChanged(Editable s) {
                checkPaste.run();
                if (ignoreUrlEdit) {
                    return;
                }
                if (needRemoveDefPrefix && s != null) {
                    String text = s.toString();
                    String fixedLink = text.substring(def.length());
                    ignoreUrlEdit = true;
                    urlEditText.editText.setText(fixedLink);
                    urlEditText.editText.setSelection(0, urlEditText.editText.getText().length());
                    ignoreUrlEdit = false;
                    needRemoveDefPrefix = false;
                    checkEditURL(fixedLink);
                    return;
                }
                checkEditURL(s == null ? null : s.toString());
            }
        });

        nameEditText = new EditTextCell(context, getString(R.string.StoryLinkNamePlaceholder), true, false, -1, resourcesProvider);
        nameEditText.whenHitEnter(this::processDone);

        buttonContainer = new FrameLayout(context);
        button = new ButtonWithCounterView(context, resourcesProvider);
        button.setText(getString(R.string.StoryLinkAdd), false);
        button.setOnClickListener(v -> processDone());
        button.setEnabled(containsURL(urlEditText.getText().toString()));
        buttonContainer.addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.FILL, 10, 10, 10, 10));

        topPadding = .2f;
        takeTranslationIntoAccount = true;
        smoothKeyboardAnimationEnabled = true;
        smoothKeyboardByBottom = true;
        DefaultItemAnimator itemAnimator = new DefaultItemAnimator() {
            @Override
            protected void onMoveAnimationUpdate(RecyclerView.ViewHolder holder) {
                super.onMoveAnimationUpdate(holder);
                containerView.invalidate();
            }
        };
        itemAnimator.setSupportsChangeAnimations(false);
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDurations(350);
        recyclerListView.setItemAnimator(itemAnimator);
        recyclerListView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);
        recyclerListView.setOnItemClickListener((view, position) -> {
            UItem item = adapter.getItem(position - 1);
            if (item == null) return;
            if (item.instanceOf(WebpagePreviewView.Factory.class) && webpage != null && !isPreviewEmpty(webpage)) {
                StoryLinkPreviewDialog preview = new StoryLinkPreviewDialog(context, currentAccount);
                LinkPreview.WebPagePreview settings = new LinkPreview.WebPagePreview();
                settings.url = urlEditText.editText.getText().toString();
                settings.name = nameOpen ? nameEditText.editText.getText().toString() : null;
                settings.webpage = webpage;
                settings.largePhoto = photoLarge;
                settings.captionAbove = captionAbove;
                preview.set(settings, newSettings -> {
                    if (newSettings == null) {
                        closePreview(null);
                    } else {
                        photoLarge = newSettings.largePhoto;
                        captionAbove = newSettings.captionAbove;
                    }
                });
                preview.setStoryPreviewView(previewView);
                preview.show();
            } else if (item.id == 2) {
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(nameOpen = !nameOpen);
                    adapter.update(true);
                    if (nameOpen) {
                        nameEditText.requestFocus();
                    } else {
                        urlEditText.requestFocus();
                    }
                }
            }
        });

        if (adapter != null) {
            adapter.update(false);
        }
    }

    private void processDone() {
        if (!button.isEnabled()) {
            return;
        }
        if (whenDone != null) {
            LinkPreview.WebPagePreview settings = new LinkPreview.WebPagePreview();
            settings.url = urlEditText.editText.getText().toString();
            settings.name = nameOpen ? nameEditText.editText.getText().toString() : null;
            settings.webpage = webpage;
            settings.largePhoto = photoLarge;
            settings.captionAbove = captionAbove;
            whenDone.run(settings);
            whenDone = null;
        }
        dismiss();
    }

    public boolean editing;
    public void set(LinkPreview.WebPagePreview settings) {
        ignoreUrlEdit = true;
        this.editing = true;
        if (settings != null) {
            webpage = settings.webpage;
            loading = false;

            urlEditText.setText(settings.url);
            nameEditText.setText(settings.name);
            nameOpen = !TextUtils.isEmpty(settings.name);

            captionAbove = settings.captionAbove;
            photoLarge = settings.largePhoto;
        } else {
            urlEditText.setText("");
            nameEditText.setText("");

            captionAbove = true;
            photoLarge = false;
        }
        button.setText(getString(R.string.StoryLinkEdit), false);
        if (adapter != null) {
            adapter.update(false);
        }
        button.setEnabled(containsURL(urlEditText.getText().toString()));
        ignoreUrlEdit = false;
    }

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.StoryLinkCreate);
    }

    @Override
    protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        return adapter = new UniversalAdapter(recyclerListView, getContext(), currentAccount, 0, true, this::fillItems, resourcesProvider) {
            @Override
            protected int getThemedColor(int key) {
                if (key == Theme.key_dialogBackgroundGray)
                    return 0xFF0D0D0D;
                return super.getThemedColor(key);
            }
        };
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didReceivedWebpagesInUpdates) {
            if (webpageId == 0) return;
            LongSparseArray<TLRPC.WebPage> webpages = (LongSparseArray<TLRPC.WebPage>) args[0];
            for (int i = 0; i < webpages.size(); ++i) {
                TLRPC.WebPage page = webpages.valueAt(i);
                if (page == null) continue;
                if (webpageId == page.id) {
                    webpage = isPreviewEmpty(page) ? null : page;
                    loading = false;
                    webpageId = 0;
                    if (adapter != null) {
                        adapter.update(true);
                    }
                    break;
                }
            }
        }
    }

    private long webpageId;
    private TLRPC.WebPage webpage;
    private boolean loading;
    private int reqId;
    private String lastCheckedStr;

    private void checkEditURL(String str) {
        if (str == null) return;
        if (TextUtils.equals(str, lastCheckedStr)) return;
        lastCheckedStr = str;
        final boolean containsURL = containsURL(str);
        AndroidUtilities.cancelRunOnUIThread(requestPreview);
        if (containsURL) {
            if (!loading || webpage != null) {
                loading = true;
                webpage = null;
                if (adapter != null) {
                    adapter.update(true);
                }
            }
            AndroidUtilities.runOnUIThread(requestPreview, 700);
        } else {
            if (loading || webpage != null) {
                loading = false;
                webpage = null;
                if (reqId != 0) {
                    ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true);
                    reqId = 0;
                }
                if (adapter != null) {
                    adapter.update(true);
                }
            }
        }
        button.setEnabled(containsURL);
    }

    private final Runnable requestPreview = () -> {
        TL_account.getWebPagePreview req = new TL_account.getWebPagePreview();
        req.message = urlEditText.editText.getText().toString();
        reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            TLRPC.TL_messageMediaWebPage media = null;
            if (res instanceof TL_account.webPagePreview) {
                final TL_account.webPagePreview preview = (TL_account.webPagePreview) res;
                MessagesController.getInstance(currentAccount).putUsers(preview.users, false);
                if (preview.media instanceof TLRPC.TL_messageMediaWebPage) {
                    media = (TLRPC.TL_messageMediaWebPage) preview.media;
                }
            }
            if (media != null) {
                webpage = media.webpage;
                if (isPreviewEmpty(webpage)) {
                    webpageId = webpage == null ? 0 : webpage.id;
                    webpage = null;
                } else {
                    webpageId = 0;
                }
            } else {
                webpage = null;
                webpageId = 0;
            }
            loading = webpageId != 0;
            if (adapter != null) {
                adapter.update(true);
            }
        }));
    };

    private void closePreview(View view) {
        loading = false;
        webpage = null;
        if (reqId != 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true);
            reqId = 0;
        }
        if (adapter != null) {
            adapter.update(true);
        }
    }

    private Pattern urlPattern;
    private boolean containsURL(String str) {
        if (TextUtils.isEmpty(str)) return false;
        if (urlPattern == null) {
            urlPattern = Pattern.compile("((https?)://|(www|ftp)\\.)?[a-z0-9-]+(\\.[a-z0-9-]+)+([/?]?.+)");
        }
        return urlPattern.matcher(str).find();
    }

    private boolean nameOpen;
    private boolean captionAbove;
    private boolean photoLarge;

    public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (loading || webpage != null) {
            items.add(WebpagePreviewView.Factory.item(webpage, this::closePreview));
        }
        items.add(UItem.asCustom(urlEditText));
        items.add(UItem.asShadow(1, null));
        items.add(UItem.asCheck(2, getString(R.string.StoryLinkNameHeader)).setChecked(nameOpen));
        if (nameOpen) {
            items.add(UItem.asCustom(nameEditText));
        }
        items.add(UItem.asShadow(3, null));
        items.add(UItem.asCustom(buttonContainer));
    }

    public static class WebpagePreviewView extends FrameLayout {

        private final Paint separatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final ImageView imageView;
        private final ImageView loadingView;
        private final AnimatedTextView titleView;
        private final AnimatedTextView messageView;
        private final ImageView closeView;

        private final SpannableString titleLoading;
        private final SpannableString messageLoading;

        public WebpagePreviewView(Context context) {
            super(context);
            setWillNotDraw(false);

            separatorPaint.setColor(0xFF000000);

            imageView = new ImageView(context);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            imageView.setImageResource(R.drawable.filled_link);
            imageView.setColorFilter(new PorterDuffColorFilter(0xFF1A9CFF, PorterDuff.Mode.SRC_IN));
            addView(imageView, LayoutHelper.createFrame(48, 48, Gravity.CENTER_VERTICAL | Gravity.LEFT, 9, 0, 0, 0));

            loadingView = new ImageView(context);
            loadingView.setBackground(new CircularProgressDrawable(dp(20), dp(2.4f), 0xFF1A9CFF) {
                @Override
                public int getIntrinsicHeight() {
                    return dp(26);
                }

                @Override
                public int getIntrinsicWidth() {
                    return dp(26);
                }
            });
            addView(loadingView, LayoutHelper.createFrame(48, 48, Gravity.CENTER_VERTICAL | Gravity.LEFT, 9, 0, 0, 0));

            titleView = new AnimatedTextView(context);
            titleView.setTextColor(0xFF1A9CFF);
            titleView.setTextSize(dp(14.21f));
            titleView.setTypeface(AndroidUtilities.bold());
            titleView.setEllipsizeByGradient(true);
            titleView.getDrawable().setOverrideFullWidth(AndroidUtilities.displaySize.x);
            addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 24, Gravity.FILL_HORIZONTAL | Gravity.TOP, 57, 2.33f, 48, 0));

            messageView = new AnimatedTextView(context);
            messageView.setTextColor(0xFF808080);
            messageView.setTextSize(dp(14.21f));
            messageView.setEllipsizeByGradient(true);
            messageView.getDrawable().setOverrideFullWidth(AndroidUtilities.displaySize.x);
            addView(messageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 24, Gravity.FILL_HORIZONTAL | Gravity.TOP, 57, 20.66f, 48, 0));

            int color = titleView.getTextColor();
            titleLoading = new SpannableString("x");
            LoadingSpan span = new LoadingSpan(titleView, dp(200));
            span.setScaleY(.8f);
            span.setColors(Theme.multAlpha(color, .4f), Theme.multAlpha(color, .08f));
            titleLoading.setSpan(span, 0, titleLoading.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            color = messageView.getTextColor();
            messageLoading = new SpannableString("x");
            span = new LoadingSpan(messageView, dp(140));
            span.setScaleY(.8f);
            span.setColors(Theme.multAlpha(color, .4f), Theme.multAlpha(color, .08f));
            messageLoading.setSpan(span, 0, messageLoading.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            closeView = new ImageView(context);
            closeView.setColorFilter(new PorterDuffColorFilter(0x64FFFFFF, PorterDuff.Mode.MULTIPLY));
            closeView.setImageResource(R.drawable.input_clear);
            closeView.setScaleType(ImageView.ScaleType.CENTER);
            closeView.setBackground(Theme.createSelectorDrawable(0x19ffffff, 1, AndroidUtilities.dp(18)));
            addView(closeView, LayoutHelper.createFrame(48, 48, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 4, 0));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            canvas.drawRect(0, 0, getWidth(), AndroidUtilities.getShadowHeight(), separatorPaint);
            canvas.drawRect(0, getHeight() - AndroidUtilities.getShadowHeight(), getWidth(), getHeight(), separatorPaint);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(48), MeasureSpec.EXACTLY)
            );
        }

        public void set(TLRPC.WebPage webpage, View.OnClickListener onCloseClick, boolean animated) {
            final boolean exist = webpage != null && !(webpage instanceof TLRPC.TL_webPagePending);
            if (animated) {
                imageView.animate().alpha(exist ? 1f : 0f).scaleX(exist ? 1f : .4f).scaleY(exist ? 1f : .4f).setDuration(320).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
                loadingView.animate().alpha(exist ? 0f : 1f).scaleX(exist ? .4f : 1f).scaleY(exist ? .4f : 1f).setDuration(320).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
            } else {
                imageView.setAlpha(exist ? 1f : 0f);
                imageView.setScaleX(exist ? 1f : .4f);
                imageView.setScaleY(exist ? 1f : .4f);
                loadingView.setAlpha(exist ? 0f : 1f);
                loadingView.setScaleX(exist ? .4f : 1f);
                loadingView.setScaleY(exist ? .4f : 1f);
            }
            if (exist) {
                titleView.setText(TextUtils.isEmpty(webpage.site_name) ? webpage.title : webpage.site_name, animated);
                messageView.setText(webpage.description, animated);
            } else {
                titleView.setText(titleLoading, animated);
                messageView.setText(messageLoading, animated);
            }
            closeView.setOnClickListener(onCloseClick);
        }

        public static class Factory extends UItem.UItemFactory<WebpagePreviewView> {
            static { setup(new Factory()); }
            @Override
            public WebpagePreviewView createView(Context context, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new WebpagePreviewView(context);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                ((WebpagePreviewView) view).set(
                    item.object instanceof TLRPC.WebPage ? (TLRPC.WebPage) item.object : null,
                    item.clickCallback,
                    false
                );
            }

            public static UItem item(TLRPC.WebPage webpage, View.OnClickListener onClose) {
                UItem item = UItem.ofFactory(WebpagePreviewView.Factory.class);
                item.object = webpage;
                item.clickCallback = onClose;
                return item;
            }
        }
    }

    public static boolean isPreviewEmpty(TLRPC.MessageMedia media) {
        return !(media instanceof TLRPC.TL_messageMediaWebPage) || isPreviewEmpty(media.webpage);
    }

    public static boolean isPreviewEmpty(TLRPC.WebPage webpage) {
        return (
            webpage instanceof TLRPC.TL_webPagePending ||
            TextUtils.isEmpty(webpage.title) && TextUtils.isEmpty(webpage.description)
        );
    }


    @Override
    public void show() {
        super.show();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.didReceivedWebpagesInUpdates);
        AndroidUtilities.runOnUIThread(() -> {
            if (!isShowing()) return;
            urlEditText.editText.requestFocus();
            AndroidUtilities.showKeyboard(urlEditText.editText);
        }, 150);
    }

    @Override
    public void dismiss() {
        AndroidUtilities.hideKeyboard(urlEditText.editText);
        AndroidUtilities.hideKeyboard(nameEditText.editText);
        super.dismiss();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.didReceivedWebpagesInUpdates);
    }

}
