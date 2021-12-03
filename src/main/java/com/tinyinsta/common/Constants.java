package com.tinyinsta.common;

import java.util.Map;

/**
 * Contains the client IDs and scopes for allowed clients consuming the helloworld API.
 */
public class Constants {
  public static final String WEB_CLIENT_ID =  "284772421623-8klntslq83finkqcgee2d3bi08rj7kt0.apps.googleusercontent.com";
  public static final String CLOUD_STORAGE_PROJECT_ID =  "tinyinsta-web";
  public static final String CLOUD_STORAGE_BUCKET_NAME =  "instars-23pnm1d4";

  public static final int MAX_BUCKETS_NUMBER = 5;
  public static final int MAX_BATCH_SIZE = 50; //39_000;
  public static final int PAGINATION_SIZE = 5;
  public static final int TIMELINE_BUCKETS = 5; // from 0 to 4 so 5 buckets

  public static final String EMAIL_SCOPE = "https://www.googleapis.com/auth/userinfo.email";
  public static final String PROFILE_SCOPE = "https://www.googleapis.com/auth/userinfo.profile";
}
