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
import com.tinyinsta.entity.PostLikers;

import javax.inject.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.Date;

import com.tinyinsta.dto.PostDTO;

@Api(name = "tinyinsta", version = "v1", scopes = { Constants.EMAIL_SCOPE }, clientIds = { Constants.WEB_CLIENT_ID })
public class Likes {
   @ApiMethod(name = "likes.updateLikes", httpMethod = "post", path = "posts/{id}/likes",
            clientIds = { Constants.WEB_CLIENT_ID },
            audiences = { Constants.WEB_CLIENT_ID },
            scopes = { Constants.EMAIL_SCOPE, Constants.PROFILE_SCOPE })
    public PostDTO updateLikes(User reqUser, @Named("id") String postId, @Named("fakeUser") String fakeUserId) throws EntityNotFoundException, UnauthorizedException, ConflictException {
        
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

        //Verify user existence
        Entity user = datastore.get(KeyFactory.createKey("User", userId));

        Entity post = datastore.get(KeyFactory.createKey("Post", postId));

        if(new ExistenceQuery().check("PostLiker", post.getKey(), userId)){
          throw new ConflictException("You've already liked this post");
        }

        AvailableBatches availableBatches = new AvailableBatches(post, "PostLiker");

        TransactionOptions options = TransactionOptions.Builder.withXG(true);
        Transaction txn = datastore.beginTransaction(options);

        int likesCount;

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
            datastore.put(availableBatch);

            // Count all available batches size + completed batches number * batch max size
            likesCount = availableBatches.getSizeCount()+(availableBatches.getFullBatchesCount() * Constants.MAX_BATCH_SIZE);

            // Update the batch index when a batch is completely filled
            if(batch.size() >= Constants.MAX_BATCH_SIZE) {
              ArrayList<Integer> batchIndex = (ArrayList<Integer>) post.getProperty("batchIndex");
              batchIndex.set(batchNumber, 1);

              post.setProperty("batchIndex", batchIndex);           
              datastore.put(post);
            }

            txn.commit();
        } finally {
            if (txn.isActive()) {
                txn.rollback();
            }// else {
                //datastore.put(post);
            //}
        }
        
        int newBucketsCount = Constants.LIKES_MAX_BUCKETS_NUMBER - availableBatches.getNonFullBatchesCount();
        
        if(newBucketsCount > 0) {
          ArrayList<Integer> batchIndex = (ArrayList<Integer>) post.getProperty("batchIndex");

          for (int i = 0; i < newBucketsCount; i++) {
              new PostLikers().createEntity(post, batchIndex.size());
              batchIndex.add(0);
          }

          post.setProperty("batchIndex", batchIndex);
          datastore.put(post);
        }

        post.setProperty("hasLiked", true);
        
        return new PostDTO(post, null, likesCount);
    }
}
