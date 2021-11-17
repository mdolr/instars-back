package com.tinyinsta;

import com.google.api.server.spi.auth.common.User;
import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.response.UnauthorizedException;
import com.google.appengine.api.datastore.*;
import com.tinyinsta.common.AvailableBatches;
import com.tinyinsta.common.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Api(name = "tinyinsta", version = "v1", scopes = { Constants.EMAIL_SCOPE }, clientIds = { Constants.WEB_CLIENT_ID })
public class Timeline {
    // A route to get timeline
    @ApiMethod(name = "users.getTimeline", httpMethod = "get", path = "timeline",
            clientIds = { Constants.WEB_CLIENT_ID },
            audiences = { Constants.WEB_CLIENT_ID },
            scopes = { Constants.EMAIL_SCOPE, Constants.PROFILE_SCOPE })
    public Map<Key, Entity> getTimeline(User reqUser) throws UnauthorizedException, EntityNotFoundException {
        // If the user is not logged in : throw an error or redirect to the login page
        if (reqUser == null) {
            throw new UnauthorizedException("Authorization required");
        }
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        Query query = new Query("PostReceiver").addSort("createdAt", Query.SortDirection.DESCENDING);
        query.setFilter(new Query.FilterPredicate("batch", Query.FilterOperator.EQUAL, reqUser.getId()));
        List<Entity> postReceivers = datastore.prepare(query).asList(FetchOptions.Builder.withLimit(20));

        List<Key> postKeys = new ArrayList<>();
        for(Entity postReceiver : postReceivers){
            postKeys.add(postReceiver.getParent());
        }
        Iterable<Key> postKeysIterable = postKeys;

        Map<Key,Entity> posts = datastore.get(postKeysIterable);

        //Recover likes and user info
        for(Map.Entry<Key, Entity> entry : posts.entrySet()){
            Entity post = entry.getValue();

            AvailableBatches availableBatches= new AvailableBatches("PostLiker", post.getKey());
            // Count all available batches size + completed batches number * batch max size
            int likesCount = availableBatches.getSizeCount()+(new Integer(post.getProperty("fullBatches").toString())*Constants.MAX_BATCH_SIZE);
            post.setProperty("likes", likesCount);

            Entity user = datastore.get(KeyFactory.createKey("User", post.getProperty("user").toString()));

            post.setProperty("userName", user.getProperty("name"));
            post.setProperty("userPicture", user.getProperty("pictureURL"));
        }
        //TODO : Sort posts
        return posts;
    }
}
