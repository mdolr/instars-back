package com.tinyinsta.common;

/**
 * Contains the client IDs and scopes for allowed clients consuming the helloworld API.
 */
public class Constants {
  public static final String WEB_CLIENT_ID = "284772421623-8klntslq83finkqcgee2d3bi08rj7kt0.apps.googleusercontent.com";
  public static final int LIKES_MAX_BUCKETS_NUMBER = 5;
  public static final int MAX_BATCH_SIZE = 39_000;

  public static final String EMAIL_SCOPE = "https://www.googleapis.com/auth/userinfo.email";
  public static final String PROFILE_SCOPE = "https://www.googleapis.com/auth/userinfo.profile";

}
