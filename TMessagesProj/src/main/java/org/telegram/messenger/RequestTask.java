package org.telegram.messenger;

import android.os.AsyncTask;

import com.aniways.service.utils.AniwaysServiceUtils;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.HttpConnectionParams;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by danielkalman on 5/27/14.
 */
class RequestTask extends AsyncTask<String, String, String> {

    @Override
    protected String doInBackground(String... params) {
        HttpClient httpClient = new DefaultHttpClient();
        HttpResponse response;
        String responseString = null;
        try {

            ConnManagerParams.setTimeout(httpClient.getParams(), AniwaysServiceUtils.CONNECTION_MANAGER_TIMEOUT);
            HttpConnectionParams.setSoTimeout(httpClient.getParams(), AniwaysServiceUtils.READ_TIMEOUT);
            HttpConnectionParams.setConnectionTimeout(httpClient.getParams(), AniwaysServiceUtils.CONNECTION_TIMEOUT);
            System.setProperty("http.keepAlive", "false");

            HttpPost httppost = new HttpPost(params[0]);

            List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(2);
            nameValuePairs.add(new BasicNameValuePair("notification-userId", params[1]));
            nameValuePairs.add(new BasicNameValuePair("notification-message", params[2]));

            httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs, "UTF-8"));

            // Execute HTTP Post Request
            response = httpClient.execute(httppost);
            StatusLine statusLine = response.getStatusLine();
            if(statusLine.getStatusCode() == HttpStatus.SC_OK){
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                response.getEntity().writeTo(out);
                out.close();
                responseString = out.toString();
            } else{
                //Closes the connection.
                response.getEntity().getContent().close();
                throw new IOException(statusLine.getReasonPhrase());
            }
        } catch (ClientProtocolException e) {
            //TODO Handle problems..
        } catch (IOException e) {
            //TODO Handle problems..
        }
        return responseString;
    }

}
