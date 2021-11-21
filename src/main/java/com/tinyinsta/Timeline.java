package com.tinyinsta;

import com.google.api.server.spi.auth.common.User;
import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.Named;
import com.google.api.server.spi.config.Nullable;
import com.google.api.server.spi.response.UnauthorizedException;
import com.google.appengine.api.datastore.*;
import com.tinyinsta.common.AvailableBatches;
import com.tinyinsta.common.Constants;
import com.tinyinsta.dto.PostDTO;
import com.tinyinsta.dto.PaginationDTO;
import com.tinyinsta.common.ExistenceQuery;
import com.google.api.server.spi.response.ConflictException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.*;


@Api(name = "tinyinsta", version = "v1", scopes = { Constants.EMAIL_SCOPE }, clientIds = { Constants.WEB_CLIENT_ID })
public class Timeline {
    // A route to get timeline
    @ApiMethod(name = "users.getTimeline", httpMethod = "get", path = "timeline",
            clientIds = { Constants.WEB_CLIENT_ID },
            audiences = { Constants.WEB_CLIENT_ID },
            scopes = { Constants.EMAIL_SCOPE, Constants.PROFILE_SCOPE })
    public PaginationDTO getTimeline(User reqUser, @Nullable @Named("after") String after, @Nullable @Named("before") String before) throws UnauthorizedException, EntityNotFoundException, ConflictException {
        // If the user is not logged in : throw an error or redirect to the login page
        if (reqUser == null) {
            throw new UnauthorizedException("Authorization required");
        }

        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        // Verify that the user exists
        // otherwise we receive a non existing entity when retrieving the timeline
        // if the loggedUser doesn't already exist in the databse !!!
        Entity user = datastore.get(KeyFactory.createKey("User", reqUser.getId()));

        FetchOptions fetchOptions = FetchOptions.Builder.withLimit(Constants.PAGINATION_SIZE);

        if (after != null) {
            fetchOptions.startCursor(Cursor.fromWebSafeString(after));
        }

        Query query = new Query("PostReceiver").addSort("createdAt", Query.SortDirection.DESCENDING);
        query.setFilter(new Query.FilterPredicate("batch", Query.FilterOperator.EQUAL, reqUser.getId()));

        QueryResultList<Entity> postReceivers = datastore.prepare(query).asQueryResultList(fetchOptions);
        
        String nextCursor = postReceivers.getCursor().toWebSafeString();
        String previousCursor = nextCursor;

        List<Key> postKeys = new ArrayList<>();

        for(Entity postReceiver : postReceivers){
            postKeys.add(postReceiver.getParent());
        }

        Iterable<Key> postKeysIterable = postKeys;

        Map<Key,Entity> results = datastore.get(postKeysIterable);

        List<PostDTO> posts = new ArrayList<>();

        // Recover likes and user info
        // Use posts.keySet() transform to array list and then sort the array list or smth
        for(Map.Entry<Key, Entity> entry : results.entrySet()){
            Entity post = entry.getValue();

            AvailableBatches availableBatches= new AvailableBatches(post, "PostLiker");
            // Count all available batches size + completed batches number * batch max size
            int likesCount = availableBatches.getSizeCount()+(availableBatches.getFullBatchesCount() * Constants.MAX_BATCH_SIZE);
            post.setProperty("likes", likesCount);

            Boolean hasLiked = (Boolean) new ExistenceQuery().check("PostLiker", post.getKey(), reqUser.getId());
            post.setProperty("hasLiked", hasLiked);

            Entity author = datastore.get(KeyFactory.createKey("User", post.getProperty("authorId").toString()));
            posts.add(new PostDTO(post, author, likesCount));
        }

        Collections.sort(posts, new Comparator<PostDTO>() {
            public int compare(PostDTO a, PostDTO b) {
              return b.createdAt.compareTo(a.createdAt);
            }});

        return new PaginationDTO(posts, previousCursor, nextCursor);
    }
}
