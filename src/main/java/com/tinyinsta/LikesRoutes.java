package com.tinyinsta;

import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.appengine.api.datastore.*;

import javax.inject.Named;
import java.util.List;

@Api(name = "tinyinsta", version = "v1", scopes = { Constants.EMAIL_SCOPE }, clientIds = { Constants.WEB_CLIENT_ID })
public class LikesRoutes {

    @ApiMethod(name = "getLikes", httpMethod = "get", path = "posts/{id}/likes")
    public List<Entity> getLikes(@Named("id") String id) {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        Query q = new Query("Like").setAncestor(KeyFactory.createKey("Post", id));

        PreparedQuery pq = datastore.prepare(q);
        List<Entity> result = pq.asList(FetchOptions.Builder.withDefaults());

        return result;
    }

    @ApiMethod(name = "updatelike", httpMethod = "put", path = "posts/{id}/likes")
    public Entity updatelike(@Named("id") String id) throws EntityNotFoundException {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        Transaction txn = datastore.beginTransaction();

        int max = Constants.LIKES_MAX_BUCKETS_NUMBER;
        int min = 1;
        int range = max - min + 1;
        int random = (int) Math.floor(Math.random()*range)+min;

        try {
            Key keyPosts = KeyFactory.createKey("Post", id);

            Entity likes = datastore.get(KeyFactory.createKey(keyPosts, "Like", String.valueOf(random)));
            long count = (long) likes.getProperty("count");
            likes.setProperty("count", count + 1);
            datastore.put(likes);

            txn.commit();

            return likes;
        } finally {
            if (txn.isActive()) {
                txn.rollback();
            }
        }
    }
}
