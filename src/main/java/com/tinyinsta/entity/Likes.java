package com.tinyinsta.entity;


import com.google.appengine.api.datastore.*;

import java.util.List;

public class Likes {

    public void createEntity(String name, Key parent) {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        Entity likes = new Entity("Like", name, parent);
        likes.setProperty("count", 0);
        datastore.put(likes);
    }

    public List<Entity> getLikes(String id) {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        //Récupérer les entités "Like" dont le parent est un id de "Post"
        Query q = new Query("Like").setAncestor(KeyFactory.createKey("Post", id));

        PreparedQuery pq = datastore.prepare(q);
        List<Entity> result = pq.asList(FetchOptions.Builder.withDefaults());
        //TODO: Renvoyer un total plutôt qu'une liste d'entités?
        return result;
    }
}
