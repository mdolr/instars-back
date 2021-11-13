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
import java.util.ArrayList;

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
              audiences={"284772421623-8klntslq83finkqcgee2d3bi08rj7kt0.apps.googleusercontent.com"},
              scopes={"https://www.googleapis.com/auth/userinfo.email", "https://www.googleapis.com/auth/userinfo.profile"})
  public Entity getSelf(User reqUser) throws UnauthorizedException, EntityNotFoundException {
    // If the user is not logged in : throw an error or redirect to the login page
    if (reqUser == null) {
      throw new UnauthorizedException("Authorization required");
    }

    // Query the datastore to get the user by its ID
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

    // If the user is already in the datastore
    try {
      // Get entity by key id
      Entity user = datastore.get(KeyFactory.createKey("User", reqUser.getId()));
      return user;
    }

    // If the user isn't already in the datastore
    catch (EntityNotFoundException e) {
      // Create an user entity with the following properties:
      // - id : the user id
      // - email : the user email
      // - name : the user name
      // - handle : the user account handle (@...)
      // - picture : the user picture
      // - createdAt : the date of creation
      // - updatedAt : the date of last update
      // - followers : An UserFollowers entity which contains multiple lists of followers
      // - following : An UserFollowings entity which contains multiple lists of followings
      
      // Create the user entity
      Entity newUser = new Entity("User", reqUser.getId());

      // Set the user properties
      newUser.setProperty("email", reqUser.getEmail());
      newUser.setProperty("handle", reqUser.getEmail().split("@")[0]);
      newUser.setProperty("name", reqUser.getEmail().split("@")[0]);
      newUser.setProperty("picture", "https://thispersondoesnotexist.com/"); // set link to a default picture
      newUser.setProperty("createdAt", new Date());
      newUser.setProperty("updatedAt", new Date());

      // Create the UserFollowers entity
      Entity newUserFollowers = new Entity("UserFollowers", reqUser.getId());
      newUserFollowers.setProperty("followersBatch-1", new ArrayList<String>());
      newUserFollowers.setProperty("followersBatch-2", new ArrayList<String>());
      newUserFollowers.setProperty("followersBatch-3", new ArrayList<String>());

      // Create the UserFollowings entity
      Entity newUserFollowings = new Entity("UserFollowings", reqUser.getId());
      newUserFollowings.setProperty("followingsBatch-1", new ArrayList<String>());

      // Put the entities in the datastore
      datastore.put(newUser);
      datastore.put(newUserFollowers);
      datastore.put(newUserFollowings);

      // Return the user
      return newUser;
    }
  }
}