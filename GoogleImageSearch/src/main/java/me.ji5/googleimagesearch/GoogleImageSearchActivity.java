package me.ji5.googleimagesearch;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.SearchView;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.koushikdutta.async.future.Future;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.ion.Ion;
import com.koushikdutta.ion.ProgressCallback;

import java.io.File;

/**
 * https://github.com/koush/ion/blob/master/ion-sample/src/com/koushikdutta/ion/sample/GoogleImageSearch.java
 * Modified by jongin oh.
 */
public class GoogleImageSearchActivity extends ActionBarActivity implements SearchView.OnQueryTextListener, SearchView.OnCloseListener {
    public static final String URL = "originalUrl";
    public static final String FILE_PATH = "filePath";

    private static final int NUM_OF_COLUMNS = 4;
    private imageAdapter imageAdapter;
    private Future<JsonObject> loading;
    private SearchView searchView;
    private MenuItem searchMenuItem;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Ion.getDefault(this).configure().setLogging("GoogleImageSearchActivity", BuildConfig.DEBUG ? Log.VERBOSE : Log.ASSERT);

        setContentView(R.layout.layout_search_main);

        findViewById(R.id.iv_search).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                expandSearchView();
                // TextView searchText = (TextView) searchView.findViewById(android.support.v7.appcompat.R.id.search_src_text);
            }
        });


        GridView gridView = (GridView) findViewById(R.id.results);
        gridView.setNumColumns(NUM_OF_COLUMNS);
        imageAdapter = new imageAdapter(this);
        gridView.setAdapter(imageAdapter);
        gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                String cacheRootDir = getExternalCacheDir() + File.separator;

                final String originalUrl = imageAdapter.getItem(position);

                final ProgressDialog dlg = new ProgressDialog(GoogleImageSearchActivity.this);
                dlg.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                dlg.setTitle(getString(R.string.dlg_title_download));
                dlg.show();
                Ion.with(GoogleImageSearchActivity.this)
                        .load(imageAdapter.getItem(position))
                        .progressHandler(new ProgressCallback() {
                            @Override
                            public void onProgress(long downloaded, long total) {
                                dlg.setMax((int) total);
                                dlg.setProgress((int) downloaded);
                            }
                        })
                        .write(new File(cacheRootDir + Uri.parse(imageAdapter.getItem(position)).getLastPathSegment()))
                        .setCallback(new FutureCallback<File>() {
                            @Override
                            public void onCompleted(Exception e, File file) {
                                dlg.dismiss();

                                if (file == null || !file.exists()) {
                                    Toast.makeText(GoogleImageSearchActivity.this, getString(R.string.image_download_fail), Toast.LENGTH_LONG).show();
                                    return;
                                }

                                Intent result = new Intent();
                                result.putExtra(URL, originalUrl);
                                result.putExtra(FILE_PATH, file.getAbsolutePath());
                                setResult(RESULT_OK, result);
                                finish();
                            }
                        });
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        getMenuInflater().inflate(R.menu.menu_image_search, menu);
        searchMenuItem = menu.findItem(R.id.action_search);

        searchView = (SearchView) MenuItemCompat.getActionView(searchMenuItem);
        if (searchView == null) return false;

        searchView.setIconified(false);
        ;
        searchView.setIconifiedByDefault(false);
        searchView.setOnCloseListener(this);
        searchView.setOnQueryTextListener(this);

        expandSearchView();

        return true;
    }

    private boolean search(String s) {
        if (TextUtils.isEmpty(s)) return false;

        hideSoftKeyboard(this);
        imageAdapter.clear();
        imageAdapter.setKeyword(s);
        loadMore(s);

        return true;
    }

    private void expandSearchView() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            searchMenuItem.expandActionView();
        } else {
            MenuItemCompat.expandActionView(searchMenuItem);
        }
    }

    @Override
    public boolean onQueryTextSubmit(String s) {
        return search(s);
    }

    @Override
    public boolean onQueryTextChange(String s) {
        return false;
    }

    @Override
    public boolean onClose() {
        hideSoftKeyboard(this);
        return true;
    }

    // Adapter to populate and imageview from an url contained in the array adapter
    private class imageAdapter extends ArrayAdapter<String> {
        protected String keyword = "";

        public imageAdapter(Context context) {
            super(context, 0);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            // see if we need to load more to get 40, otherwise populate the adapter
            if (position > getCount() - 4)
                loadMore(keyword);

            if (convertView == null)
                convertView = getLayoutInflater().inflate(R.layout.layout_image, null);

            // find the image view
            final ImageView iv = (ImageView) convertView.findViewById(R.id.image);

            // select the image view
            Ion.with(iv)
                    .resize(256, 256)
                    .centerCrop()
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.stat_notify_error)
                    .load(getItem(position));

            return convertView;
        }

        public void setKeyword(String s) {
            keyword = s;
        }
    }

    void loadMore(String s) {
        if (loading != null && !loading.isDone() && !loading.isCancelled() && !TextUtils.isEmpty(s))
            return;

        findViewById(R.id.empty).setVisibility(View.GONE);
        findViewById(R.id.results).setVisibility(View.GONE);
        findViewById(R.id.layout_progress).setVisibility(View.VISIBLE);
        // query googles image search api
        loading = Ion.with(GoogleImageSearchActivity.this)
                .load(String.format("https://ajax.googleapis.com/ajax/services/search/images?v=1.0&q=%s&start=%d&imgsz=medium", Uri.encode(s), imageAdapter.getCount()))
                        // get the results as json
                .asJsonObject()
                .setCallback(new FutureCallback<JsonObject>() {
                    @Override
                    public void onCompleted(Exception e, JsonObject result) {
                        try {
                            if (e != null) {
                                e.printStackTrace();
                                return;
                            }

                            // find the results and populate
                            if (result != null && result.has("responseData")) {
                                JsonArray results = result.getAsJsonObject("responseData").getAsJsonArray("results");
                                for (int i = 0; i < results.size(); i++) {
                                    imageAdapter.add(results.get(i).getAsJsonObject().get("url").getAsString());
                                }
                            }

                            findViewById(R.id.layout_progress).setVisibility(View.GONE);
                            findViewById(R.id.empty).setVisibility(imageAdapter.getCount() > 0 ? View.GONE : View.VISIBLE);
                            findViewById(R.id.results).setVisibility(imageAdapter.getCount() > 0 ? View.VISIBLE : View.GONE);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                            // toast any error we encounter (google image search has an API throttling limit that sometimes gets hit)
                            // Toast.makeText(GoogleImageSearchActivity.this, ex.toString(), Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    protected static void hideSoftKeyboard(Activity activity) {
        if (activity == null || activity.getCurrentFocus() == null) return;

        InputMethodManager inputMethodManager = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(activity.getCurrentFocus().getWindowToken(), 0);
    }
}