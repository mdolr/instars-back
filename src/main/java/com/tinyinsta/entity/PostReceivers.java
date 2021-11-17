package com.tinyinsta.entity;


import com.google.appengine.api.datastore.*;

import java.util.List;

public class PostReceivers {

    public void createEntity(Entity user, Key postKey) {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        Query q = new Query("UserFollower").setAncestor(user.getKey());

        PreparedQuery pq = datastore.prepare(q);
        List<Entity> followers = pq.asList(FetchOptions.Builder.withDefaults());

        for(Entity follower : followers) {
            Entity receivers = new Entity("PostReceiver", postKey);
            receivers.setProperty("batch", follower.getProperty("batch"));
            datastore.put(receivers);
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
