package com.difo.motoactv_export;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

// Due to android incompatibility with apache httpclient use the repackaged version.
// https://code.google.com/p/httpclientandroidlib/
import ch.boye.httpclientandroidlib.*;
import ch.boye.httpclientandroidlib.client.HttpClient;
import ch.boye.httpclientandroidlib.client.methods.HttpPost;
import ch.boye.httpclientandroidlib.entity.ContentType;
import ch.boye.httpclientandroidlib.entity.mime.HttpMultipartMode;
import ch.boye.httpclientandroidlib.entity.mime.MultipartEntityBuilder;
import ch.boye.httpclientandroidlib.entity.mime.content.FileBody;
import ch.boye.httpclientandroidlib.entity.mime.content.StringBody;
import ch.boye.httpclientandroidlib.impl.client.HttpClientBuilder;
import ch.boye.httpclientandroidlib.util.EntityUtils;

import org.scribe.builder.ServiceBuilder;
import org.scribe.extractors.JsonTokenExtractor;
import org.scribe.model.OAuthRequest;
import org.scribe.model.Response;
import org.scribe.model.Token;
import org.scribe.model.Verb;
import org.scribe.model.Verifier;
import org.scribe.oauth.OAuthService;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;

public class StravaUpdate {
    final static String CALLBACK = "http://localhost";
    private static String TAG = "sd_motoactv_export";
    private DataUtil du = null;
    private SharedPreferences settings;

    public StravaUpdate (DataUtil DU,SharedPreferences settings)
    {
        this.du = DU;
        this.settings = settings;
    }

    public boolean UpdateAll(int NeedsUpdating)
    {
        Log.d(TAG,"Updating Strava:" + NeedsUpdating );
        Cursor curUpdate = null;

        curUpdate = this.du.getNeedsUpdate(NeedsUpdating) ;
        while(curUpdate.moveToNext())
        {
            int Workout_id = curUpdate.getInt(curUpdate.getColumnIndex("_id"));
            Log.d(TAG,"Needs:" + Workout_id );

            //Update shared pref to current id
            SharedPreferences.Editor editor = this.settings.edit();
            editor.putInt("needs_updating", Workout_id);
            editor.commit();

            if( doPost(Workout_id))
            {
                // Needs new token
                Log.d(TAG,"Bad Post, needs Auth:" + NeedsUpdating );
                return false;
            }
        }
        curUpdate.close();

        SharedPreferences.Editor editor = this.settings.edit();
        editor.putInt("needs_updating", 0);
        editor.commit();

        return true;
    }


    private static String readFile(String path) throws IOException {
        FileInputStream stream = new FileInputStream(new File(path));
        try {
            FileChannel fc = stream.getChannel();
            MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
              /* Instead of using default, pass in a decoder. */
            return Charset.defaultCharset().decode(bb).toString();
        }
        finally {
            stream.close();
        }
    }

    public static String getToken(String code) {
        HttpClient httpClient = HttpClientBuilder.create().build();
        HttpPost httpPost = new HttpPost(
                "https://www.strava.com/oauth/token");
        httpPost.setHeader("enctype", "multipart/form-data");

        HttpEntity entity = MultipartEntityBuilder.create()
                .setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
                .addPart("client_id", new StringBody(ApiKeys.strava_APIKEY,ContentType.TEXT_PLAIN))
                .addPart("client_secret", new StringBody(ApiKeys.strava_APISECRET,ContentType.TEXT_PLAIN))
                .addPart("code", new StringBody(code,ContentType.TEXT_PLAIN))
                .build();
        httpPost.setEntity(entity);

        HttpResponse response;
        try {
            response = httpClient.execute(httpPost);
            HttpEntity respEntity = response.getEntity();
            int retCode = response.getStatusLine().getStatusCode();

            if (respEntity != null) {
                // EntityUtils to get the response content
                String content = EntityUtils.toString(respEntity);
                System.out.println(content);

                JsonTokenExtractor e = new JsonTokenExtractor();
                Token t = e.extract(content);

                return t.getToken();
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }


    public static int uploadActivity(String bearer, String fileName) {
        HttpClient httpClient = HttpClientBuilder.create().build();
        HttpPost httpPost = new HttpPost(
                "https://www.strava.com/api/v3/uploads");
        httpPost.addHeader("Authorization", "Bearer " + bearer);
        httpPost.setHeader("enctype", "multipart/form-data");


        File f = new File("/sdcard/" + fileName);
        FileBody fb = new FileBody(f);

        String tcx = "tcx";
        StringBody sb = new StringBody("tcx", ContentType.TEXT_PLAIN);

        HttpEntity entity = MultipartEntityBuilder.create()
                .setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
                .addPart("file", fb)
                .addPart("data_type", sb)
                .build();

        httpPost.setEntity(entity);
        int retCode=0;

        HttpResponse response;
        try {
            response = httpClient.execute(httpPost);
            HttpEntity respEntity = response.getEntity();
            retCode = response.getStatusLine().getStatusCode();

// 401 unauthorised
// 400 response.getStatusLine().getReasonPhrase() "Bad Request" - something wrong with the data
// 201 uploaded. probably

            if (respEntity != null) {
                // EntityUtils to get the response content
                String content = EntityUtils.toString(respEntity);
                System.out.println(content);
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return retCode;
    }

    public boolean doPost(int id) {
        boolean bGetNewToken = false;

        Log.d(TAG,"Strava Creating TCX" );
        String FileTemp = this.du.exportTCX(id, true);
//        String FileTemp = "workout.tcx";

        try
        {
            OAuthService s = new ServiceBuilder()
                    .provider(StravaApi.class)
                    .debug()
                    .apiKey(ApiKeys.strava_APIKEY)
                    .apiSecret(ApiKeys.strava_APISECRET)
                    .callback(CALLBACK)
                    .build();

            Token accessToken = this.getToken();

            if ( accessToken != null)
            {
                bGetNewToken = false;

                int rcode = uploadActivity(accessToken.getToken(), FileTemp);

                switch(rcode) {
                    case 200:
                    case 201:
                        //good
                        Log.d(TAG, "Activty posted to strava:" + rcode);
                        break;
                    case 401:
                        // Need auth
                        bGetNewToken = true;
                        this.ClearToken();
                        break;
                    case 403:
//                        Log.d(TAG, "Bad request 403:" + response.getBody());
                        break;
                    case 400:
                        // Bad request
//                        Log.d(TAG, "Bad request 400:" + response.getBody());
                        break;
                    default:
                        Log.d(TAG, "Activty post received unknown respose code:" + rcode);
//                        Log.d(TAG, response.getBody());
                        break;
                }
                }
            else
            {
                bGetNewToken = true;
            }
        }
        catch(Exception e)
        {
            Log.d(TAG,"Error Posting to Strava:" + e.getMessage() );
        }

        // Delete temp file
        File exportDir = new File(Environment.getExternalStorageDirectory(), "");
        File file = new File(exportDir,FileTemp );
        file.delete();

        return bGetNewToken;
    }


    public Token getToken()
    {
        String sToken = this.settings.getString("db_access_token", "");
        String sSecret = this.settings.getString("db_access_secret","");
        Token accessToken =null;

        if(sToken !="")
            accessToken =new Token(sToken,sSecret);

        return accessToken;
    }
    public void ClearToken()
    {
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("db_access_token", "");
        editor.putString("db_access_secret", "");
        editor.commit();
    }

    public void getAuthToken(WebView wv, Context context)
    {
        final ProgressDialog mProgress;
        mProgress = ProgressDialog.show(context, "Loading", "Please wait for a moment...");

        Log.d(TAG, "Requesting Strave token");

        try
        {
            final OAuthService s = new ServiceBuilder()
                    .provider(StravaApi.class)
                    .debug()
                    .apiKey(ApiKeys.strava_APIKEY)
                    .apiSecret(ApiKeys.strava_APISECRET)
                    .callback(CALLBACK)
                    .build();

            final String authURL = s.getAuthorizationUrl(null);

            //final Token requestToken =  s.getRequestToken();
            final Context cx = context;

            final  WebView webview = wv;

            webview.setVisibility(View.VISIBLE);
            webview.requestFocus(View.FOCUS_DOWN);
            wv.bringToFront();

            WebSettings webSettings = webview.getSettings();

            webSettings.setJavaScriptEnabled(true); //set to true to enable javascript
            webSettings.setJavaScriptCanOpenWindowsAutomatically(true); //a true setting allows the window.open() js call
            webSettings.setLightTouchEnabled(true);// set to true enables touches and mouseovers
            webSettings.setSavePassword(true); //set to true save the user inputed passwords in forms
            webSettings.setSaveFormData(true);// set to true saves the user form data
            webSettings.setSupportZoom(true); //set to true to suport the zoom feature

            //attach WebViewClient to intercept the callback url
            webview.setWebViewClient(new WebViewClient() {

                //Using post so we don't get the should override. do after load instead.
                public void onPageFinished(WebView view, String url) {
                    Log.d(TAG, "finished");
                    if (mProgress.isShowing()) {
                        mProgress.dismiss();
                    }

                    Uri uri = Uri.parse(url);
                    //check for our custom callback protocol otherwise use default behavior
                    if (uri.getHost().equals("localhost")) {
                        //authorization complete hide webview for now.
                        webview.setVisibility(View.GONE);
                        String ssToken = "";
                        String ssSecret = "";
                        Log.d(TAG, "getting token");
                        try {
                            String code = uri.getQueryParameter("code");
                            String secret = getToken(code);

                            ssToken = secret;

                            Log.d(TAG, "Token: " + ssToken);
                            Log.d(TAG, "Secret: " + ssToken);

                        } catch (Exception e) {
                            Log.d(TAG, "Token InValid. Error:" + e.getMessage());
                        }

                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString("db_access_token", ssToken);
                        editor.putString("db_access_secret", ssSecret);
                        editor.commit();

                        // Close web view
                        ((Activity) cx).finish();
                    }
                }
            });

            //send user to authorization page if needed
            webview.loadUrl(authURL);
        }
        catch (Exception e)
        {
            Log.d(TAG,"Failed Retreiving Token. Error:" + e.getMessage());
        }
    }
}
