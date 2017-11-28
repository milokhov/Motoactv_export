package com.difo.motoactv_export;

/*
 * SDSOFT Motoactv Exporter 
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

import org.scribe.builder.ServiceBuilder;
import org.scribe.model.OAuthRequest;
import org.scribe.model.Response;
import org.scribe.model.Token;
import org.scribe.model.Verb;
import org.scribe.model.Verifier;
import org.scribe.oauth.OAuthService;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;

public class GoogleDriveUpdate {
	
	final static String CALLBACK = "http://localhost/googledrive";
	private static final Token EMPTY_TOKEN = null;
	private static String TAG = "sd_motoactv_export";
	private DataUtil du = null;
	private SharedPreferences settings;
	
	public GoogleDriveUpdate (DataUtil DU,SharedPreferences settings)
	{
			this.du = DU;
			this.settings = settings;
	}
	
	public boolean UpdateAll(int NeedsUpdating)
	{
	
		Log.d(TAG,"Updating RunKeeper:" + NeedsUpdating );
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
	

	public boolean doPost(int id) {  

		boolean bGetNewToken = false;
		
		Log.d(TAG,"Google Drive Creating TCX" );
		
		String FileTemp = this.du.exportTCX(id, true);
		
		try
		{
			OAuthService s = new ServiceBuilder()
	        .provider(Google2APi.class)
			.apiKey(ApiKeys.googledrive_APIKEY)
			.apiSecret(ApiKeys.googledrive_APISECRET)
			.debug()
			.build();

			Token accessToken = this.getToken();
			
			if ( accessToken !=null)
			{
		     
				bGetNewToken = false;

				String sFile = readFile("/sdcard/" + FileTemp);
				
				// Create file and get id.
				OAuthRequest req = new OAuthRequest(Verb.POST, "https://www.googleapis.com/upload/drive/v2/files?uploadType=resumable");
				
				String payload ="{ \"title\": \"" + FileTemp.replace(".tmp",".tcx") + "\",  \"mimeType\": \"text/plain\",  \"description\": \"" + "Posted from SD Motoactv Exporter:ID-"+id  +"\"}";
				req.addPayload(payload); 
				s.signRequest(accessToken, req);
				req.addHeader("Content-Type", "application/json");
				req.addHeader("X-Upload-Content-Type", "text/plain");
				req.addHeader("X-Upload-Content-Length",""+sFile.length());
				
				Response response = req.send();
				int rcode = response.getCode();
				
				Log.d(TAG, "Code:" + rcode);

				if(rcode == 200)
				{
					// Get id
					 String sLocation = response.getHeader("Location");
					 
					 String sdocid =sLocation.substring(sLocation.indexOf("upload_id") +10);
					 Log.d(TAG,"ID:" + sdocid);
					 //String sdocid = "";
					 if(sdocid.length()>0)
					 {
					req = new OAuthRequest(Verb.POST, "https://www.googleapis.com/upload/drive/v2/files?uploadType=resumable&upload_id=" +  sdocid );
					
					req.addHeader("Content-Length", "" +payload.length());
					req.addHeader("Content-Type", "text/plain");
					
					req.addPayload(sFile);       			
					s.signRequest(accessToken, req);
					response = req.send();
					rcode = response.getCode();
					
					// 201 = good, 401 = bad
					Log.d(TAG,"Google Drive response code:" + rcode );
					switch(rcode)
					{
						case 200:
							//good
							Log.d(TAG,"Activty posted to Google Drive:" + rcode );
							break;
						case 401:
							// Need auth
							bGetNewToken = true;
							this.ClearToken();
							break;
						case 403:
							Log.d(TAG,"Bad request 403:" + response.getBody() );
							break;
						case 400:
							// Bad request
							Log.d(TAG,"Bad request 400:" + response.getBody() );
							break;
						default:
							Log.d(TAG,"Activty post received unknown respose code:" + rcode );
							Log.d(TAG, response.getBody() );
							break;
					}
				 }
				 else
				 {
					 Log.d(TAG, "GoogleDrive not valid doc id");
					 
				 }
				}
				else
				{
					return true;
				}
				

				
			}
			else
			{
				bGetNewToken = true;
			}
		}
		catch(Exception e)
		{
			Log.d(TAG,"Error Posting to Google Drive:" + e.getMessage() );
		}

		// Delete temp file
		File exportDir = new File(Environment.getExternalStorageDirectory(), "");
		File file = new File(exportDir,FileTemp );
		
		file.delete();
		
		
		
		return bGetNewToken;
	}
	
	public Token getToken()
	{
		
		String sToken = this.settings.getString("gd_access_token", "");
		String sSecret = this.settings.getString("gd_access_secret","");
		Token accessToken =null;
		
		//sToken+="tst";
		
		if(sToken !="")
			accessToken =new Token(sToken,sSecret);
		
		return accessToken;
		
	
	}
	public void ClearToken()
	{
		 SharedPreferences.Editor editor = settings.edit();
	     editor.putString("gd_access_token", "");
	     editor.putString("gd_access_secret", "");
	     editor.commit();
	
	}
	
	 public void getAuthToken(WebView wv,Context context)
	   {
		 final ProgressDialog mProgress;
		   mProgress = ProgressDialog.show(context, "Loading", "Please wait for a moment...");

		   Log.d(TAG, "Requesting Google Drive token");

	        
		   while(wv.getProgress() < 100);
	
		   try
		   {
		       final OAuthService s = new ServiceBuilder()
		       .provider( Google2APi.class)
				.debug()
				.apiKey(ApiKeys.googledrive_APIKEY)
				.apiSecret(ApiKeys.googledrive_APISECRET)
				.scope("https://www.googleapis.com/auth/drive.file")
				.callback(CALLBACK)
				.build();
						       

		       String authURL = s.getAuthorizationUrl(EMPTY_TOKEN);

		       final Token requestToken =  null;//s.getRequestToken();
		    		   
				final  WebView webview = wv;
				final Context cx = context;
		        
				webview.setVisibility(View.VISIBLE);
				webview.requestFocus(View.FOCUS_DOWN);
		
				WebSettings webSettings = webview.getSettings();
		
				webSettings.setJavaScriptEnabled(true); //set to true to enable javascript
				webSettings.setJavaScriptCanOpenWindowsAutomatically(true); //a true setting allows the window.open() js call
				webSettings.setLightTouchEnabled(true);// set to true enables touches and mouseovers
				webSettings.setSavePassword(true); //set to true save the user inputed passwords in forms
				webSettings.setSaveFormData(true);// set to true saves the user form data
				webSettings.setSupportZoom(true); //set to true to suport the zoom feature
				
		        //attach WebViewClient to intercept the callback url
		        webview.setWebViewClient(new WebViewClient(){
		        	boolean bset = false;
		        	public void onPageFinished(WebView view, String url) {
	                	Log.d(TAG,"finished");
	                    if(mProgress.isShowing()) {
	                        mProgress.dismiss();
	                    }
	                }
		        	
		        	@Override
		        	public void onPageStarted (WebView view, String url,  android.graphics.Bitmap favicon) {

		        		//check for our custom callback protocol otherwise use default behavior
		        		Log.d(TAG,url);
		        		
		        		if(! bset && url.startsWith("http://localhost/googledrive")){
		        			bset = true;
		        			
		        			//authorization complete hide webview for now.
		        			webview.setVisibility(View.GONE);
		        			String ssToken = "";
		        			String ssSecret ="";
		        			Log.d(TAG,"getting token");
		        			try
		        			{
		        				Uri uri = Uri.parse(url);
		        				String verifier = uri.getQueryParameter("code");
			        			Log.d(TAG,verifier);
			        			Verifier v = new Verifier(verifier);

			        			//save this token for practical use.
			        			Token accessToken = s.getAccessToken(requestToken, v);
			        			ssToken = accessToken.getToken();
			        			ssSecret = accessToken.getSecret();
			        			
		        			}
		        			catch(Exception e)
		        			{
		        				Log.d(TAG,"Token InValid. Error:" + e.getMessage());
		        			}
		        			
		        			
		        	        SharedPreferences.Editor editor = settings.edit();
		        	        editor.putString("gd_access_token", ssToken);
		        	        editor.putString("gd_access_secret", ssSecret);
		        	        editor.commit();
		        	        
		        	        // Close web view
		        	        ((Activity) cx).finish();
		        			//return true;
		        		}
		        		
		        		
		        		//return super.shouldOverrideUrlLoading(view, url);
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
