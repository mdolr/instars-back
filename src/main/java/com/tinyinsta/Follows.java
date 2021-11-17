package com.tinyinsta;

import com.google.api.server.spi.auth.common.User;
import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.response.ConflictException;
import com.google.api.server.spi.response.UnauthorizedException;
import com.google.appengine.api.datastore.*;
import com.tinyinsta.common.Constants;

import javax.inject.Named;
import java.util.ArrayList;
import java.util.Date;

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
        .setAncestor(target.getKey())
        .setFilter(new Query.FilterPredicate("batch", Query.FilterOperator.EQUAL, reqUser.getId()));

    // We set a limit of 1 because we won't need more than 1 result
    QueryResultList<Entity> existenceResults = datastore.prepare(existenceQuery).asQueryResultList(FetchOptions.Builder.withLimit(1));

    // If we find a result then it means that the user is already following the target
    if(existenceResults.size() > 0) {
        throw new ConflictException("You are already following this user");
    }

    Transaction txn = datastore.beginTransaction();
    try {
        // Get the batches of followers who are not full
        // to append our current user
        Query getBatchesQuery = new Query("UserFollower")
            .setAncestor(KeyFactory.createKey("User", targetId))
            .setFilter(new Query.FilterPredicate("size", Query.FilterOperator.LESS_THAN, 39_000));
            //.addSort("updatedAt", Query.SortDirection.ASCENDING);
            // TODO: Find a way to rotate batches

        // We set a limit of 1 because we won't need more than 1 resul
        QueryResultList<Entity> availableBatches = datastore.prepare(getBatchesQuery).asQueryResultList(FetchOptions.Builder.withLimit(1));

        // TODO: Verify that there is at least 1 result if not create it (it means we have all our batches full)
        // This gives us an available batch which we append our user to

        Entity availableBatch = null;
        ArrayList<String> batch = new ArrayList<String>(); // In case we get an empty batch we need to declare it

        if(availableBatches.size() > 0) {
            availableBatch = availableBatches.get(0); // Get the first item from our list
            batch = (ArrayList<String>) availableBatch.getProperty("batch");

            if(batch == null) {
                batch = new ArrayList<String>();
            }
        } else {
            availableBatch = new Entity("UserFollower", user.getKey());
        }

        // Append the user to the batch
        batch.add(reqUser.getId());

        // Update the batch
        availableBatch.setProperty("batch", batch);
        availableBatch.setProperty("size", batch.size());
        availableBatch.setProperty("updatedAt", new Date());

        // Then put the batch back in the datastore
        datastore.put(availableBatch);
        txn.commit();
    } finally {
        if (txn.isActive()) {
            txn.rollback();
        }
    }
    return null;
    }
}
