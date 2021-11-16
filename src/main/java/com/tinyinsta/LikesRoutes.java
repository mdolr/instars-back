package com.tinyinsta;

import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.appengine.api.datastore.*;

import javax.inject.Named;
import java.util.List;

@Api(name = "tinyinsta", version = "v1", scopes = { Constants.EMAIL_SCOPE }, clientIds = { Constants.WEB_CLIENT_ID })
public class LikesRoutes {

    @ApiMethod(name = "likes.getLikes", httpMethod = "get", path = "posts/{id}/likes")
    public List<Entity> getLikes(@Named("id") String id) {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        //Récupérer les entités "Like" dont le parent est un id de "Post"
        Query q = new Query("Like").setAncestor(KeyFactory.createKey("Post", id));

        PreparedQuery pq = datastore.prepare(q);
        List<Entity> result = pq.asList(FetchOptions.Builder.withDefaults());
        //TODO: Renvoyer un total plutôt qu'une liste d'entités?
        return result;
    }

    @ApiMethod(name = "likes.updateLikes", httpMethod = "put", path = "posts/{id}/likes")
    public Entity updateLikes(@Named("id") String id) throws EntityNotFoundException {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        Transaction txn = datastore.beginTransaction();

        int max = Constants.LIKES_MAX_BUCKETS_NUMBER;
        int min = 1;
        int range = max - min + 1;
        int random = (int) Math.floor(Math.random()*range)+min;

        try {
            //Récupérer les entités "Like" dont le parent est un id de "Post"
            Key postKey = KeyFactory.createKey("Post", id);
            Entity likes = datastore.get(KeyFactory.createKey(postKey, "Like", String.valueOf(random)));

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