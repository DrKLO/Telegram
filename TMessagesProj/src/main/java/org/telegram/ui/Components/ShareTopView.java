package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.collection.LongSparseArray;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.regex.Matcher;

public class ShareTopView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    public static final int MODE_NONE = 0;
    public static final int MODE_MEDIA = 1;
    public static final int MODE_LINK = 2;

    public interface OnModeChangeListener {
        void onModeChanged(int previousMode, int newMode);
    }

    private final Theme.ResourcesProvider resourcesProvider;

    public final Layout[] layouts = new Layout[2];
    private int currentMode = MODE_NONE;

    private int currentAccount;
    private long selfId;

    private ArrayList<MediaController.PhotoEntry> mediaEntries;
    private final ArrayList<Long> recipients = new ArrayList<>();

    private boolean linkSearchEnabled;
    private boolean previewEnabled = true;
    private String dismissedMessage;
    private final ArrayList<CharSequence> foundUrls = new ArrayList<>();
    private final HashMap<String, TLRPC.WebPage> linkPreviewCache = new HashMap<>();

    private TLRPC.WebPage loadedWebPage;
    private int linkRequestId;
    private int linkRequestSerial;

    private boolean showingHint;
    private CharSequence pendingHintText;
    private Runnable hintRunnable;

    private OnClickListener layoutClickListener;
    private OnModeChangeListener modeChangeListener;

    public ShareTopView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        for (int i = 0; i < layouts.length; i++) {
            layouts[i] = new Layout(context, resourcesProvider);
            addView(layouts[i], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }
        layouts[0].setVisibility(VISIBLE);
        layouts[1].setVisibility(GONE);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.didReceivedWebpagesInUpdates);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.didReceivedWebpagesInUpdates);
        cancelLinkRequest();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id != NotificationCenter.didReceivedWebpagesInUpdates) return;
        if (loadedWebPage == null || account != currentAccount) return;
        @SuppressWarnings("unchecked")
        final LongSparseArray<TLRPC.WebPage> map = (LongSparseArray<TLRPC.WebPage>) args[0];
        for (int i = 0; i < map.size(); i++) {
            final TLRPC.WebPage wp = map.valueAt(i);
            if (wp == null || wp.id != loadedWebPage.id) continue;
            if (wp instanceof TLRPC.TL_webPageEmpty) {
                final int prev = currentMode;
                loadedWebPage = null;
                cancelLinkRequest();
                if (currentMode != MODE_NONE) {
                    currentMode = MODE_NONE;
                    if (modeChangeListener != null) modeChangeListener.onModeChanged(prev, MODE_NONE);
                }
            } else if (wp instanceof TLRPC.TL_webPage) {
                loadedWebPage = wp;
                final String url = foundUrls.isEmpty() ? "" : TextUtils.join(" ", foundUrls).toString();
                if (!linkPreviewCache.containsKey(url)) linkPreviewCache.put(url, wp);
                bindLinkLoaded(current(), wp, url);
            }
            break;
        }
    }

    public Layout current() {
        return layouts[0];
    }

    public int getMode() {
        return currentMode;
    }

    public TLRPC.WebPage getLoadedWebPage() {
        return loadedWebPage;
    }

    public boolean hasLink() {
        return currentMode == MODE_LINK;
    }

    public BackupImageView getThumbView(int index) {
        if (currentMode != MODE_MEDIA) return null;
        final BackupImageView[] arr = current().images;
        if (arr == null || index < 0 || index >= arr.length) return null;
        return arr[index].getVisibility() == VISIBLE ? arr[index] : null;
    }

    public void setLayoutClickListener(OnClickListener listener) {
        this.layoutClickListener = listener;
        for (Layout l : layouts) {
            l.container.setOnClickListener(listener);
        }
    }

    public void setOnModeChangeListener(OnModeChangeListener listener) {
        this.modeChangeListener = listener;
    }

    public void setSharedMedia(int currentAccount, ArrayList<MediaController.PhotoEntry> entries) {
        rebindObserver(currentAccount);
        this.selfId = AccountInstance.getInstance(currentAccount).getUserConfig().getClientUserId();
        this.mediaEntries = entries;
        this.linkSearchEnabled = false;
        this.loadedWebPage = null;
        cancelLinkRequest();
        foundUrls.clear();

        final int prev = currentMode;
        final boolean modeChanged = currentMode != MODE_MEDIA && currentMode != MODE_NONE;
        currentMode = MODE_MEDIA;
        if (modeChanged) {
            switchLayouts(true);
        }
        bindMedia(current());
        applyHintText(current());
        if (prev != currentMode && modeChangeListener != null) {
            modeChangeListener.onModeChanged(prev, currentMode);
        }
    }

    public void setSharedLink(int currentAccount, String url) {
        prepareForLinkSearch(currentAccount);
        if (url != null && !url.isEmpty()) {
            doLinkSearch(url, true);
        }
    }

    public void setSharedText(int currentAccount, CharSequence initialText) {
        prepareForLinkSearch(currentAccount);
        if (initialText != null && initialText.length() > 0) {
            doLinkSearch(initialText, true);
        }
    }

    private void prepareForLinkSearch(int currentAccount) {
        rebindObserver(currentAccount);
        this.selfId = AccountInstance.getInstance(currentAccount).getUserConfig().getClientUserId();
        this.mediaEntries = null;
        this.linkSearchEnabled = true;
        this.previewEnabled = true;
        this.dismissedMessage = null;
        this.loadedWebPage = null;
        cancelLinkRequest();
        foundUrls.clear();
    }

    public boolean isPreviewEnabled() {
        return previewEnabled;
    }

    public void dismissWebPagePreview() {
        if (foundUrls.isEmpty()) return;
        dismissedMessage = TextUtils.join(" ", foundUrls).toString();
        previewEnabled = false;
        cancelLinkRequest();
        loadedWebPage = null;
        final int prev = currentMode;
        if (currentMode != MODE_NONE) {
            currentMode = MODE_NONE;
            if (modeChangeListener != null) modeChangeListener.onModeChanged(prev, MODE_NONE);
        }
    }

    public void onTextChanged(CharSequence text, boolean force) {
        if (!linkSearchEnabled) return;
        doLinkSearch(text, force);
    }

    private void doLinkSearch(CharSequence text, boolean force) {
        final ArrayList<CharSequence> urls = extractUrls(text);
        if (!force && sameUrls(urls)) {
            return;
        }
        foundUrls.clear();
        if (urls != null) foundUrls.addAll(urls);

        if (urls == null || urls.isEmpty()) {
            cancelLinkRequest();
            loadedWebPage = null;
            final int prev = currentMode;
            if (currentMode != MODE_NONE) {
                currentMode = MODE_NONE;
                if (modeChangeListener != null) modeChangeListener.onModeChanged(prev, MODE_NONE);
            }
            return;
        }

        final String message = TextUtils.join(" ", urls).toString();
        if (!previewEnabled) {
            if (dismissedMessage != null && dismissedMessage.equals(message)) {
                return;
            }
            previewEnabled = true;
            dismissedMessage = null;
        }
        final int prev = currentMode;
        final boolean modeChanged = currentMode != MODE_LINK && currentMode != MODE_NONE;
        if (currentMode != MODE_LINK) {
            currentMode = MODE_LINK;
        }
        if (modeChanged) {
            switchLayouts(true);
        }

        final TLRPC.WebPage cached = linkPreviewCache.get(message);
        if (cached != null) {
            loadedWebPage = cached;
            bindLinkLoaded(current(), cached, message);
        } else {
            bindLinkLoading(current(), message);
            requestLinkPreview(message);
        }
        applyHintText(current());
        if (prev != currentMode && modeChangeListener != null) {
            modeChangeListener.onModeChanged(prev, currentMode);
        }
    }

    public void setRecipients(int currentAccount, ArrayList<Long> selectedDialogs) {
        rebindObserver(currentAccount);
        this.selfId = AccountInstance.getInstance(currentAccount).getUserConfig().getClientUserId();
        recipients.clear();
        if (selectedDialogs != null) {
            recipients.addAll(selectedDialogs);
        }
        final Layout layout = current();
        if (currentMode == MODE_MEDIA) {
            layout.obj.setText(buildRecipientText(layout));
        }
    }

    private static ArrayList<CharSequence> extractUrls(CharSequence text) {
        if (text == null || text.length() == 0) return null;
        ArrayList<CharSequence> urls = null;
        try {
            final Matcher m = AndroidUtilities.WEB_URL.matcher(text);
            while (m.find()) {
                if (m.start() > 0 && text.charAt(m.start() - 1) == '@') continue;
                if (urls == null) urls = new ArrayList<>();
                urls.add(text.subSequence(m.start(), m.end()));
            }
        } catch (Exception ignored) {}
        return urls;
    }

    private boolean sameUrls(ArrayList<CharSequence> urls) {
        if (urls == null) return foundUrls.isEmpty();
        if (urls.size() != foundUrls.size()) return false;
        for (int i = 0; i < urls.size(); i++) {
            if (!TextUtils.equals(urls.get(i), foundUrls.get(i))) return false;
        }
        return true;
    }

    private void switchLayouts(boolean animated) {
        final Layout tmp = layouts[0];
        layouts[0] = layouts[1];
        layouts[1] = tmp;
        if (animated) {
            layouts[0].active = true;
            layouts[0].setVisibility(VISIBLE);
            layouts[0].setScaleX(0.8f);
            layouts[0].setScaleY(0.8f);
            layouts[0].setAlpha(0f);
            layouts[0].setTranslationY(dp(20));
            layouts[0].animate()
                .scaleX(1f).scaleY(1f).alpha(1f).translationY(0)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .setDuration(320)
                .start();
            final Layout outgoing = layouts[1];
            outgoing.active = false;
            outgoing.animate()
                .scaleX(0.8f).scaleY(0.8f).alpha(0f).translationY(-dp(20))
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .setDuration(320)
                .withEndAction(() -> outgoing.setVisibility(GONE))
                .start();
        } else {
            layouts[1].setVisibility(GONE);
            layouts[1].active = false;
            layouts[0].setVisibility(VISIBLE);
            layouts[0].setScaleX(1f);
            layouts[0].setScaleY(1f);
            layouts[0].setAlpha(1f);
            layouts[0].setTranslationY(0);
            layouts[0].active = true;
        }
    }

    private void rebindObserver(int newAccount) {
        if (currentAccount == newAccount) {
            this.currentAccount = newAccount;
            if (isAttachedToWindow()) {
                NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.didReceivedWebpagesInUpdates);
            }
            return;
        }
        if (isAttachedToWindow()) {
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.didReceivedWebpagesInUpdates);
        }
        this.currentAccount = newAccount;
        if (isAttachedToWindow()) {
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.didReceivedWebpagesInUpdates);
        }
    }

    private void cancelLinkRequest() {
        if (linkRequestId != 0) {
            AccountInstance.getInstance(currentAccount).getConnectionsManager().cancelRequest(linkRequestId, true);
            linkRequestId = 0;
        }
        linkRequestSerial++;
    }

    private void requestLinkPreview(String message) {
        cancelLinkRequest();
        if (message == null || message.isEmpty()) return;
        final TL_account.getWebPagePreview req = new TL_account.getWebPagePreview();
        req.message = message;
        final int serial = ++linkRequestSerial;
        linkRequestId = AccountInstance.getInstance(currentAccount).getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (serial != linkRequestSerial) return;
            linkRequestId = 0;
            TLRPC.WebPage webpage = null;
            if (response instanceof TL_account.webPagePreview) {
                final TL_account.webPagePreview preview = (TL_account.webPagePreview) response;
                MessagesController.getInstance(currentAccount).putUsers(preview.users, false);
                MessagesController.getInstance(currentAccount).putChats(preview.chats, false);
                if (preview.media instanceof TLRPC.TL_messageMediaWebPage) {
                    webpage = ((TLRPC.TL_messageMediaWebPage) preview.media).webpage;
                }
            }
            if (webpage instanceof TLRPC.TL_webPage) {
                if (linkPreviewCache.size() > 5) {
                    final Iterator<String> keys = linkPreviewCache.keySet().iterator();
                    while (keys.hasNext() && linkPreviewCache.size() > 5) {
                        keys.next();
                        keys.remove();
                    }
                }
                linkPreviewCache.put(message, webpage);
                loadedWebPage = webpage;
                bindLinkLoaded(current(), webpage, message);
            } else if (webpage instanceof TLRPC.TL_webPagePending) {
                loadedWebPage = webpage;
            } else if (webpage instanceof TLRPC.TL_webPageEmpty) {
                loadedWebPage = null;
                final int prev = currentMode;
                if (currentMode != MODE_NONE) {
                    currentMode = MODE_NONE;
                    if (modeChangeListener != null) modeChangeListener.onModeChanged(prev, MODE_NONE);
                }
            }
        }));
    }

    private void bindMedia(Layout layout) {
        layout.icon.setImageResource(R.drawable.filled_forward);
        layout.icon.setVisibility(VISIBLE);
        layout.linkImage.setVisibility(GONE);
        layout.imagesContainer.setVisibility(VISIBLE);
        layout.closeButton.setVisibility(GONE);
        layout.container.setClickable(true);

        final ArrayList<MediaController.PhotoEntry> entries = mediaEntries;
        if (entries == null || entries.isEmpty()) {
            layout.name.setText("");
            layout.obj.setText("");
            for (BackupImageView v : layout.images) v.setVisibility(GONE);
            return;
        }
        int videoCount = 0, photoCount = 0;
        for (MediaController.PhotoEntry e : entries) {
            if (e.isVideo) videoCount++; else photoCount++;
        }
        final int total = entries.size();
        if (total == 1) {
            layout.name.setText(LocaleController.getString(entries.get(0).isVideo ? R.string.ShareSendVideo : R.string.ShareSendPhoto));
        } else if (videoCount == 0) {
            layout.name.setText(LocaleController.formatPluralString("ShareSendPhotos", total));
        } else if (photoCount == 0) {
            layout.name.setText(LocaleController.formatPluralString("ShareSendVideos", total));
        } else {
            layout.name.setText(LocaleController.formatPluralString("ShareSendItems", total));
        }
        layout.obj.setText(buildRecipientText(layout));
        bindThumb(layout.images[0], entries.size() > 0 ? entries.get(0) : null);
        bindThumb(layout.images[1], entries.size() > 1 ? entries.get(1) : null);
        bindThumb(layout.images[2], entries.size() > 2 ? entries.get(2) : null);
    }

    private void bindLinkLoading(Layout layout, String url) {
        layout.icon.setImageResource(R.drawable.msg_link2);
        layout.icon.setVisibility(VISIBLE);
        layout.imagesContainer.setVisibility(GONE);
        layout.linkImage.setVisibility(GONE);
        layout.closeButton.setVisibility(VISIBLE);
        layout.name.setText(LocaleController.getString(R.string.GettingLinkInfo));
        layout.obj.setText(url == null ? "" : url);
        layout.container.setClickable(false);
    }

    private void bindLinkLoaded(Layout layout, TLRPC.WebPage webPage, String url) {
        layout.icon.setImageResource(R.drawable.msg_link2);
        layout.icon.setVisibility(VISIBLE);
        layout.imagesContainer.setVisibility(GONE);
        layout.closeButton.setVisibility(VISIBLE);

        CharSequence name = webPage.site_name;
        if (name == null) name = webPage.title;
        if (name == null) name = url;
        layout.name.setText(name);

        CharSequence obj = webPage.title != null && webPage.site_name != null ? webPage.title : webPage.description;
        if (obj == null) obj = webPage.display_url != null ? webPage.display_url : url;
        layout.obj.setText(obj);

        if (webPage.photo != null) {
            final TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(webPage.photo.sizes, 320);
            final TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(webPage.photo.sizes, dp(40));
            if (photoSize != null) {
                layout.linkImage.setRoundRadius(dp(4));
                layout.linkImage.setImage(ImageLocation.getForObject(photoSize, webPage.photo), "50_50", ImageLocation.getForObject(thumb, webPage.photo), "50_50_b", null, 0, 1, webPage);
                layout.linkImage.setVisibility(VISIBLE);
            } else {
                layout.linkImage.setVisibility(GONE);
            }
        } else {
            layout.linkImage.setVisibility(GONE);
        }
        layout.container.setClickable(false);
    }

    private void bindThumb(BackupImageView v, MediaController.PhotoEntry entry) {
        if (entry == null) {
            v.setVisibility(GONE);
            return;
        }
        v.setVisibility(VISIBLE);
        v.setOrientation(0, true);
        if (entry.thumbPath != null) {
            v.setImage(entry.thumbPath, null, null);
        } else if (entry.path != null) {
            if (entry.isVideo) {
                v.setImage("vthumb://" + entry.imageId + ":" + entry.path, null, null);
            } else {
                v.setOrientation(entry.orientation, entry.invert, true);
                v.setImage("thumb://" + entry.imageId + ":" + entry.path, null, null);
            }
        } else {
            v.setImageDrawable(null);
        }
    }

    private CharSequence buildRecipientText(Layout layout) {
        if (recipients.isEmpty()) return "";
        StringBuilder to = new StringBuilder();
        for (long did : recipients) {
            if (to.length() > 0) to.append(", ");
            if (did == selfId) {
                to.append(LocaleController.getString(R.string.SavedMessages));
            } else {
                to.append(recipients.size() == 1
                        ? DialogObject.getName(currentAccount, did)
                        : DialogObject.getShortName(currentAccount, did));
            }
        }
        final String namesText = LocaleController.formatString(R.string.ShareSendToChats, to.toString());
        final float available = layout.obj.getMeasuredWidth() <= 0 ? AndroidUtilities.displaySize.x - dp(140) : layout.obj.getMeasuredWidth();
        if (recipients.size() > 2 || layout.obj.getPaint().measureText(namesText) > available) {
            return LocaleController.formatPluralString("ShareSendToMany", recipients.size());
        }
        return namesText;
    }

    public void startHintRotation(CharSequence hintText) {
        stopHintRotation();
        pendingHintText = hintText;
        applyHintText(current());
        hintRunnable = () -> {
            if (currentMode != MODE_MEDIA) {
                for (Layout l : layouts) {
                    l.obj.setAlpha(1f);
                    l.obj.setScaleX(1f);
                    l.obj.setScaleY(1f);
                    l.objHint.setAlpha(0f);
                }
                showingHint = false;
                AndroidUtilities.runOnUIThread(hintRunnable, 4000);
                return;
            }
            showingHint = !showingHint;
            for (Layout l : layouts) {
                l.obj.setPivotX(0);
                l.objHint.setPivotX(0);
                if (showingHint) {
                    l.obj.animate().alpha(0f).scaleX(0.98f).scaleY(0.98f).setDuration(150).start();
                    l.objHint.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150).start();
                } else {
                    l.obj.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150).start();
                    l.objHint.animate().alpha(0f).scaleX(0.98f).scaleY(0.98f).setDuration(150).start();
                }
            }
            AndroidUtilities.runOnUIThread(hintRunnable, 4000);
        };
        AndroidUtilities.runOnUIThread(hintRunnable, 1000);
    }

    public void stopHintRotation() {
        if (hintRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(hintRunnable);
            hintRunnable = null;
        }
        showingHint = false;
        for (Layout l : layouts) {
            l.obj.setAlpha(1f);
            l.obj.setScaleX(1f);
            l.obj.setScaleY(1f);
            l.objHint.setAlpha(0f);
        }
    }

    private void applyHintText(Layout layout) {
        if (pendingHintText != null) {
            layout.objHint.setText(pendingHintText);
        }
    }

    public static String extractFirstUrl(CharSequence text) {
        if (text == null || text.length() == 0) return null;
        try {
            final Matcher m = AndroidUtilities.WEB_URL.matcher(text);
            if (m.find()) {
                return text.subSequence(m.start(), m.end()).toString();
            }
        } catch (Exception ignored) {}
        return null;
    }

    public class Layout extends FrameLayout {

        public boolean active = true;

        public final LinearLayout container;
        public final ImageView icon;
        public final FrameLayout textLayout;
        public final SimpleTextView name;
        public final SimpleTextView obj;
        public final SimpleTextView objHint;
        public final FrameLayout imagesContainer;
        public final BackupImageView[] images;
        public final BackupImageView linkImage;
        public final ImageView closeButton;

        public Layout(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);

            container = new LinearLayout(context);
            container.setOrientation(LinearLayout.HORIZONTAL);
            container.setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), 20, 20, 6, 6));
            ScaleStateListAnimator.apply(container, .02f, 1.2f);
            addView(container, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 4, 4, 4, 4));

            icon = new ImageView(context);
            icon.setScaleType(ImageView.ScaleType.CENTER);
            icon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_replyPanelIcons, resourcesProvider), PorterDuff.Mode.MULTIPLY));
            container.addView(icon, LayoutHelper.createLinear(40, 38, Gravity.TOP | Gravity.LEFT));

            imagesContainer = new FrameLayout(context);
            container.addView(imagesContainer, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.FILL_VERTICAL, 6, 0, 0, 0));

            images = new BackupImageView[3];
            for (int i = images.length - 1; i >= 0; i--) {
                images[i] = new BackupImageView(context);
                images[i].setRoundRadius(dp(6));
                images[i].setVisibility(GONE);
                imagesContainer.addView(images[i], LayoutHelper.createFrame(32 - 4 * i, 32 - 4 * i, Gravity.LEFT | Gravity.CENTER_VERTICAL, 12 * i, 0, 0, 0));
            }

            linkImage = new BackupImageView(context);
            linkImage.setRoundRadius(dp(4));
            linkImage.setVisibility(GONE);
            container.addView(linkImage, LayoutHelper.createLinear(34, 34, Gravity.LEFT | Gravity.CENTER_VERTICAL, 6, 0, 0, 0));

            textLayout = new FrameLayout(context);
            container.addView(textLayout, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f, Gravity.FILL));

            name = new SimpleTextView(context);
            name.setTextSize(14);
            name.setTypeface(AndroidUtilities.bold());
            name.setTextColor(Theme.getColor(Theme.key_chat_replyPanelName, resourcesProvider));
            textLayout.addView(name, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 18, Gravity.TOP | Gravity.LEFT, 8, 2, 8, 0));

            obj = new SimpleTextView(context);
            obj.setTextSize(14);
            obj.setTextColor(Theme.getColor(Theme.key_glass_defaultText, resourcesProvider));
            textLayout.addView(obj, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 18, Gravity.TOP | Gravity.LEFT, 8, 20, 8, 0));

            objHint = new SimpleTextView(context);
            objHint.setTextSize(14);
            objHint.setTextColor(Theme.getColor(Theme.key_glass_defaultText, resourcesProvider));
            objHint.setAlpha(0f);
            textLayout.addView(objHint, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 18, Gravity.TOP | Gravity.LEFT, 8, 20, 8, 0));

            closeButton = new ImageView(context);
            closeButton.setScaleType(ImageView.ScaleType.CENTER);
            closeButton.setImageResource(R.drawable.input_clear);
            closeButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_glass_defaultIcon, resourcesProvider), PorterDuff.Mode.MULTIPLY));
            closeButton.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), 1, dp(18)));
            closeButton.setVisibility(GONE);
            closeButton.setOnClickListener(v -> dismissWebPagePreview());
            container.addView(closeButton, LayoutHelper.createLinear(36, 36, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 4, 0));
        }
    }
}
