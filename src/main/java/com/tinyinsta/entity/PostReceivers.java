package com.tinyinsta.entity;


import com.google.appengine.api.datastore.*;

import java.util.Date;
import java.util.List;

public class PostReceivers {

    public void createEntity(Entity user, Entity post) {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        Query q = new Query("UserFollower").setAncestor(user.getKey());

        PreparedQuery pq = datastore.prepare(q);
        List<Entity> followers = pq.asList(FetchOptions.Builder.withDefaults());

        for(Entity follower : followers) {
            Entity receivers = new Entity("PostReceiver", post.getKey());
            if(follower.getProperty("batch") != null) {
                receivers.setProperty("createdAt", post.getProperty("createdAt"));
                receivers.setProperty("batch", follower.getProperty("batch"));
                receivers.setProperty("parentId", post.getProperty("postId"));
                datastore.put(receivers);
            }
        }
    }

    public List<Entity> getPostReceiver(String id) {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        //Récupérer les entités "PostReceiver" dont le parent est un id de "Post"
        Query q = new Query("PostReceiver").setAncestor(KeyFactory.createKey("Post", id));

        PreparedQuery pq = datastore.prepare(q);
        List<Entity> result = pq.asList(FetchOptions.Builder.withDefaults());

        return result;
    }
}
