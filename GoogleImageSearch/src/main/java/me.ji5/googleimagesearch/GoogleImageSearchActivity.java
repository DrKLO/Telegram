package me.ji5.googleimagesearch;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.koushikdutta.async.future.Future;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.ion.Ion;

import java.io.File;

/**
 * https://github.com/koush/ion/blob/master/ion-sample/src/com/koushikdutta/ion/sample/GoogleImageSearch.java
 * Created by koush on 6/4/13.
 */
public class GoogleImageSearchActivity extends ActionBarActivity {
    public static final String URL = "originalUrl";
    public static final String FILE_PATH = "filePath";

    private static final int NUM_OF_COLUMNS = 4;
    private imageAdapter imageAdapter;
    private Future<JsonObject> loading;
    private EditText searchText;
    private GridView gridView;

    // Adapter to populate and imageview from an url contained in the array adapter
    private class imageAdapter extends ArrayAdapter<String> {
        public imageAdapter(Context context) {
            super(context, 0);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            // see if we need to load more to get 40, otherwise populate the adapter
            if (position > getCount() - 4)
                loadMore();

            if (convertView == null)
                convertView = getLayoutInflater().inflate(R.layout.google_image, null);

            // find the image view
            final ImageView iv = (ImageView) convertView.findViewById(R.id.image);

            // select the image view
            Ion.with(iv)
                    .resize(256, 256)
                    .centerCrop()
                    .placeholder(R.drawable.placeholder)
                    .error(R.drawable.error)
                    .load(getItem(position));

            return convertView;
        }
    }

    void loadMore() {
        if (loading != null && !loading.isDone() && !loading.isCancelled())
            return;

        // query googles image search api
        loading = Ion.with(GoogleImageSearchActivity.this)
                .load(String.format("https://ajax.googleapis.com/ajax/services/search/images?v=1.0&q=%s&start=%d&imgsz=medium", Uri.encode(searchText.getText().toString()), imageAdapter.getCount()))
                        // get the results as json
                .asJsonObject()
                .setCallback(new FutureCallback<JsonObject>() {
                    @Override
                    public void onCompleted(Exception e, JsonObject result) {
                        try {
                            if (e != null)
                                throw e;
                            // find the results and populate

                            if (result != null && result.has("responseData")) {
                                JsonArray results = result.getAsJsonObject("responseData").getAsJsonArray("results");
                                for (int i = 0; i < results.size(); i++) {
                                    imageAdapter.add(results.get(i).getAsJsonObject().get("url").getAsString());
                                }
                            }
                        } catch (Exception ex) {
                            ex.printStackTrace();
                            // toast any error we encounter (google image search has an API throttling limit that sometimes gets hit)
                            // Toast.makeText(GoogleImageSearchActivity.this, ex.toString(), Toast.LENGTH_LONG).show();
                        }

                        searchText.setEnabled(true);
                        findViewById(R.id.search).setEnabled(true);
                    }
                });
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Ion.getDefault(this).configure().setLogging("GoogleImageSearchActivity", BuildConfig.DEBUG ? Log.VERBOSE : Log.ASSERT);

        setContentView(R.layout.google_image_search);

        final Button search = (Button) findViewById(R.id.search);
        searchText = (EditText) findViewById(R.id.search_text);
        search.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                search();
            }
        });

        gridView = (GridView) findViewById(R.id.results);
        gridView.setNumColumns(NUM_OF_COLUMNS);
        imageAdapter = new imageAdapter(this);
        gridView.setAdapter(imageAdapter);
        gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                String cacheRootDir = getExternalCacheDir() + File.separator;

                final String originalUrl = imageAdapter.getItem(position);

                ProgressDialog progressDialog = new ProgressDialog(GoogleImageSearchActivity.this);
                Ion.with(GoogleImageSearchActivity.this)
                        .load(imageAdapter.getItem(position))
                        .progressDialog(progressDialog)
                        .write(new File(cacheRootDir + Uri.parse(imageAdapter.getItem(position)).getLastPathSegment()))
                        .setCallback(new FutureCallback<File>() {
                            @Override
                            public void onCompleted(Exception e, File file) {
                                if (file == null || !file.exists()) {
                                    setResult(RESULT_CANCELED);
                                    finish();
                                    return;
                                }

                                Intent result = new Intent();
                                result.putExtra(URL, originalUrl);
                                result.putExtra(FILE_PATH, file.getAbsolutePath());
                                setResult(RESULT_OK, result);
                                finish();
                                return;
                            }
                        });
            }
        });

        search();
    }

    private void search() {
        if (TextUtils.isEmpty(searchText.getText().toString())) return;

        searchText.setEnabled(false);
        findViewById(R.id.search).setEnabled(false);

        imageAdapter.clear();
        loadMore();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(searchText.getWindowToken(), 0);
    }
}