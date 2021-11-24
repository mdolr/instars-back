package com.tinyinsta;

import com.google.api.server.spi.auth.common.User;
import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.response.ConflictException;
import com.google.api.server.spi.response.UnauthorizedException;
import com.google.appengine.api.datastore.*;
import com.tinyinsta.common.AvailableBatches;
import com.tinyinsta.common.Constants;
import com.tinyinsta.common.ExistenceQuery;

import javax.inject.Named;
import java.util.ArrayList;
import java.util.Date;

import com.tinyinsta.dto.UserDTO;

@Api(name = "tinyinsta", version = "v1", scopes = { Constants.EMAIL_SCOPE }, clientIds = { Constants.WEB_CLIENT_ID })
public class Follows {
    @ApiMethod(name = "follows.followById", httpMethod = "post", path = "follow/{targetId}",
          clientIds = { Constants.WEB_CLIENT_ID },
          audiences = { Constants.WEB_CLIENT_ID },
          scopes = { Constants.EMAIL_SCOPE, Constants.PROFILE_SCOPE })
    public UserDTO followById(User reqUser, @Named("targetId") String targetId, @Named("fakeUser") String fakeUserId) throws UnauthorizedException, EntityNotFoundException, ConflictException {

    // Make sure that the user is currently logged in
    String userId;
        
    if(reqUser == null) {
        if(fakeUserId == null) {
            throw new UnauthorizedException("Authorization required");
        } else {
            userId = fakeUserId;
        }
    } else {
      userId = reqUser.getId();
    }

    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

    // First we verify that our user exists
    Entity user = datastore.get(KeyFactory.createKey("User", userId));

    // Then verify that the target exists
    Entity target = datastore.get(KeyFactory.createKey("User", targetId));

    //Check if user is already following
    if(new ExistenceQuery().check("UserFollower", target.getKey(), userId)){
        throw new ConflictException("You are already following this user");
    }

    Transaction txn = datastore.beginTransaction();

    AvailableBatches availableBatches = new AvailableBatches(target, "UserFollower");
    
    int followersCount;

    try {
        Entity availableBatch = availableBatches.getOneRandom();
        ArrayList<String> batch = (ArrayList<String>) availableBatch.getProperty("batch");

        // Retrieve the i part of the i + '-' + parentId
        String batchId = (String) availableBatch.getProperty("id");
        int batchNumber = Integer.valueOf(batchId.split("-")[0]);
        
        
        if(batch == null) {
            batch = new ArrayList<>();
        }
        
        // Append the user to the batch
        batch.add(userId);

        // Update the batch
        availableBatch.setProperty("batch", batch);
        availableBatch.setProperty("size", batch.size());
        availableBatch.setProperty("updatedAt", new Date());

        // Count all available batches size + completed batches number * batch max size
        // -1 to remove self follower from count
        followersCount = availableBatches.getSizeCount()+(availableBatches.getFullBatchesCount() * Constants.MAX_BATCH_SIZE) - 1;

        // Update the batch index when a batch is completely filled
        if(batch.size() >= Constants.MAX_BATCH_SIZE) {
            ArrayList<Integer> batchIndex = (ArrayList<Integer>) target.getProperty("batchIndex");
            batchIndex.set(batchNumber, 1);

            user.setProperty("batchIndex", batchIndex);
            datastore.put(target);
        }

        // Then put the batch back in the datastore
        datastore.put(availableBatch);
        txn.commit();
    } finally {
        if (txn.isActive()) {
            txn.rollback();
        }
    }

    int newBucketsCount = Constants.LIKES_MAX_BUCKETS_NUMBER - availableBatches.getNonFullBatchesCount();
        
    if(newBucketsCount > 0) {
      ArrayList<Integer> batchIndex = (ArrayList<Integer>) target.getProperty("batchIndex");

      for (int i = 0; i < newBucketsCount; i++) {
          String userFollowersId = String.valueOf(batchIndex.size()) + "-" + targetId;
          Key key = KeyFactory.createKey(target.getKey(), "UserFollower", userFollowersId);

          // Create the UserFollowers entity
          Entity userFollowers = new Entity(key);
          userFollowers.setProperty("id", userFollowersId);
          userFollowers.setProperty("batch", null);
          userFollowers.setProperty("size", 0);
          userFollowers.setProperty("parentId", targetId);
          userFollowers.setProperty("updatedAt", new Date());

          datastore.put(userFollowers);
          batchIndex.add(0);
      }

      target.setProperty("batchIndex", batchIndex);
      datastore.put(target);
    }

    target.setProperty("hasFollowed", true);

    return new UserDTO(target, true, followersCount);
  }
}
