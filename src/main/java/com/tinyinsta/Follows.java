package com.tinyinsta;

import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.appengine.api.datastore.*;
import com.google.appengine.api.datastore.Query.SortDirection;
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

@Api(name = "tinyinsta", version = "v1", scopes = { Constants.EMAIL_SCOPE }, clientIds = { Constants.WEB_CLIENT_ID })
public class Follows {
  @ApiMethod(name = "follows.followById", httpMethod = "post", path = "follow/{targetId}",
          clientIds = { Constants.WEB_CLIENT_ID },
          audiences = { Constants.WEB_CLIENT_ID },
          scopes = { Constants.EMAIL_SCOPE, Constants.PROFILE_SCOPE })
  public Entity followById(User reqUser, @Named("targetId") String targetId) throws UnauthorizedException, EntityNotFoundException, ConflictException {
    // Make sure that the user is currently logged in
    if(reqUser == null) {
      throw new UnauthorizedException("Authorization required");
    }

    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    
    // First we verify that our user exists
    Entity user = datastore.get(KeyFactory.createKey("User", reqUser.getId()));

    // Then verify that the target exists
    Entity target = datastore.get(KeyFactory.createKey("User", targetId));

    // Check that the target doesn't belong to one of the children "UserFollower" of the user
    Query existenceQuery = new Query("UserFollower")
        .setAncestor(KeyFactory.createKey("User", targetId))
        .setFilter(new Query.FilterPredicate("batch", Query.FilterOperator.EQUAL, reqUser.getId()));
            
    // We set a limit of 1 because we won't need more than 1 result
    QueryResultList<Entity> existenceResults = datastore.prepare(existenceQuery).asQueryResultList(FetchOptions.Builder.withLimit(1));
    
    // If we find a result then it means that the user is already following the target
    if(existenceResults.size() > 0) {
      throw new ConflictException("You are already following this user");
    }

    // Get the batches of followers who are not full
    // to append our current user
    Query getBatchesQuery = new Query("UserFollower")
        .setAncestor(KeyFactory.createKey("User", targetId))
        .setFilter(new Query.FilterPredicate("size", Query.FilterOperator.LESS_THAN, 39_000));

    // We set a limit of 1 because we won't need more than 1 result
    // TODO: Get random batch among those that have under 39k size
    QueryResultList<Entity> availableBatches = datastore.prepare(getBatchesQuery).asQueryResultList(FetchOptions.Builder.withLimit(1));
    
    // TODO: Verify that there is at least 1 result if not create it (it means we have all our batches full)
    // This gives us an available batch which we append our user to
    Entity availableBatch = availableBatches.get(0); // Get the first item from our list
    
    // Append reqUser.getId() to the availableBatch
    ArrayList<String> batch = (ArrayList<String>) availableBatch.getProperty("batch");
    
    // In case we get an empty batch we need to declare it
    if(batch == null) {
      batch = new ArrayList<String>();
    }

    // Append the user to the batch
    batch.add(reqUser.getId());

    // Update the batch
    availableBatch.setProperty("batch", batch);
    availableBatch.setProperty("size", batch.size());
    
    // Then put the batch back in the datastore
    datastore.put(availableBatch);
    return null;
  }
}
