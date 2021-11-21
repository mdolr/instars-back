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
    public UserDTO followById(User reqUser, @Named("targetId") String targetId) throws UnauthorizedException, EntityNotFoundException, ConflictException {

    // Make sure that the user is currently logged in
    if(reqUser == null) {
        throw new UnauthorizedException("Authorization required");
    }

    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

    // First we verify that our user exists
    Entity user = datastore.get(KeyFactory.createKey("User", reqUser.getId()));

    // Then verify that the target exists
    Entity target = datastore.get(KeyFactory.createKey("User", targetId));

    //Check if user is already following
    if(new ExistenceQuery().check("UserFollower", target.getKey(), reqUser.getId())){
        throw new ConflictException("You are already following this user");
    }

    Transaction txn = datastore.beginTransaction();

    AvailableBatches availableBatches = new AvailableBatches(target, "UserFollower");
    
    int followersCount;

    try {
        Entity availableBatch = availableBatches.getOneRandom();
        ArrayList<String> batch = (ArrayList<String>) availableBatch.getProperty("batch");

        if(batch == null) {
            batch = new ArrayList<>();
        }
        // Append the user to the batch
        batch.add(reqUser.getId());

        // Update the batch
        availableBatch.setProperty("batch", batch);
        availableBatch.setProperty("size", batch.size());
        availableBatch.setProperty("updatedAt", new Date());

        // Count all available batches size + completed batches number * batch max size
        // -1 to remove self follower from count
        followersCount = availableBatches.getSizeCount()+(availableBatches.getFullBatchesCount() * Constants.MAX_BATCH_SIZE) - 1;

        if(batch.size() >= Constants.MAX_BATCH_SIZE) {
            user.setProperty("fullBatches", new Integer(user.getProperty("fullBatches").toString()) + 1);
            datastore.put(user);
        }

        // Then put the batch back in the datastore
        datastore.put(availableBatch);
        txn.commit();
    } finally {
        if (txn.isActive()) {
            txn.rollback();
        }
    }

    target.setProperty("hasFollowed", true);

    return new UserDTO(target, true, followersCount);
  }
}
