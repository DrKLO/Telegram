package org.telegram.ui.Components;

import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.BaseFragment;

public class URLSpanCopyToClipboard extends URLSpanNoUnderline {
    private BaseFragment fragment;

    public URLSpanCopyToClipboard(String url, BaseFragment fragment) {
        super(url);
        this.fragment = fragment;
    }

    public URLSpanCopyToClipboard(String url, boolean forceNoUnderline, BaseFragment fragment) {
        super(url, forceNoUnderline);
        this.fragment = fragment;
    }

    public URLSpanCopyToClipboard(String url, TextStyleSpan.TextStyleRun run, BaseFragment fragment) {
        super(url, run);
        this.fragment = fragment;
    }

    @Override
    public void onClick(View widget) {
        String url = getURL();
        AndroidUtilities.addToClipboard(url);
        BulletinFactory.of(fragment).createCopyLinkBulletin().show();
    }
}
