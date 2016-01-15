package com.sdsoft.motoactv_export;

import org.scribe.extractors.AccessTokenExtractor;
import org.scribe.extractors.JsonTokenExtractor;
import org.scribe.builder.api.DefaultApi20;
import org.scribe.model.*;
import org.scribe.utils.OAuthEncoder;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class StravaApi extends DefaultApi20
{
    public static final String STRAVA_AUTH_URL = "https://www.strava.com/oauth/authorize?client_id=%s&response_type=code&scope=write&redirect_uri=%s";

    @Override
    public Verb getAccessTokenVerb() {
        return Verb.GET;
    }

    @Override
    public AccessTokenExtractor getAccessTokenExtractor() {
        return new JsonTokenExtractor();
    }

    @Override
    public String getAccessTokenEndpoint() {
        return "https://www.strava.com/oauth/token";
    }

    @Override
    public String getAuthorizationUrl(OAuthConfig oAuthConfig) {
        return String.format(STRAVA_AUTH_URL, oAuthConfig.getApiKey(), OAuthEncoder.encode(oAuthConfig.getCallback()));
    }

}