package com.tinyinsta;

import com.google.api.server.spi.auth.common.User;
import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.response.ConflictException;
import com.google.api.server.spi.response.UnauthorizedException;
import com.google.appengine.api.datastore.*;
import com.tinyinsta.common.AvailableBatch;
import com.tinyinsta.common.Constants;
import com.tinyinsta.entity.PostLikers;

import javax.inject.Named;
import java.util.ArrayList;
import java.util.Date;

@Api(name = "tinyinsta", version = "v1", scopes = { Constants.EMAIL_SCOPE }, clientIds = { Constants.WEB_CLIENT_ID })
public class Likes {
    @ApiMethod(name = "likes.updateLikes", httpMethod = "put", path = "posts/{id}/likes",
            clientIds = { Constants.WEB_CLIENT_ID },
            audiences = { Constants.WEB_CLIENT_ID },
            scopes = { Constants.EMAIL_SCOPE, Constants.PROFILE_SCOPE })
    public Entity updateLikes(User reqUser, @Named("id") String postId) throws EntityNotFoundException, UnauthorizedException, ConflictException {

        if(reqUser == null) {
            throw new UnauthorizedException("Authorization required");
        }

        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        //Verify user existence
        Entity user = datastore.get(KeyFactory.createKey("User", reqUser.getId()));

        Entity post = datastore.get(KeyFactory.createKey("Post", postId));

        /*if(new ExistenceQuery().check("PostLiker", post.getKey(), reqUser.getId())){
            throw new ConflictException("You've already liked this post");
        }*/

        Transaction txn = datastore.beginTransaction();

        AvailableBatch availableBatchObject = new AvailableBatch();

        try {
            Entity availableBatch = availableBatchObject.getRandom("PostLiker", post.getKey());
            ArrayList<String> batch = (ArrayList<String>) availableBatch.getProperty("batch");

            if(batch == null) {
                batch = new ArrayList<String>();
            }
            // Append the user to the batch
            batch.add(reqUser.getId());

            // Update the batch
            availableBatch.setProperty("batch", batch);
            availableBatch.setProperty("size", batch.size());
            availableBatch.setProperty("updatedAt", new Date());

            datastore.put(availableBatch);

            txn.commit();
        } finally {
            if (txn.isActive()) {
                txn.rollback();
            }
        }

        for (int i = 0; i < Constants.LIKES_MAX_BUCKETS_NUMBER-availableBatchObject.getSize(); i++) {
            new PostLikers().createEntity(post.getKey());
        }
        return null;
    }
}
