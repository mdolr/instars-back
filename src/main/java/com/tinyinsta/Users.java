package com.tinyinsta;

import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.appengine.api.datastore.*;

import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.*;

import javax.annotation.Nullable;
import javax.inject.Named;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.rmi.UnexpectedException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.google.api.server.spi.auth.common.User;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;

import com.google.api.server.spi.response.BadRequestException;
import com.google.api.server.spi.response.UnauthorizedException;
import com.google.api.server.spi.response.ForbiddenException;
import com.google.api.server.spi.response.NotFoundException;
import com.google.api.server.spi.response.ConflictException;
import com.google.api.server.spi.response.InternalServerErrorException;
import com.google.api.server.spi.response.ServiceUnavailableException;


@Api(
    name = "tinyinsta",
    version = "v1",
    scopes = {Constants.EMAIL_SCOPE},
    clientIds = {Constants.WEB_CLIENT_ID}
)
public class Users {
  @ApiMethod(name = "me",
              httpMethod = "get",
              path = "users/me",
              clientIds={"284772421623-8klntslq83finkqcgee2d3bi08rj7kt0.apps.googleusercontent.com"},
              audiences={"284772421623-8klntslq83finkqcgee2d3bi08rj7kt0.apps.googleusercontent.com"})
  public User getSelf(User user) throws UnauthorizedException {
    // If the user is not logged in : throw an error or redirect to the login page
    if (user == null) {
      throw new UnauthorizedException("Authorization required");
    }
  
    return user;
  }
}