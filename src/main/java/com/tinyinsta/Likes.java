package com.tinyinsta;


import com.google.appengine.api.datastore.*;

public class Likes {

    public void createEntity(String name, Key keyPosts) {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        Entity likes = new Entity("Like", name, keyPosts);
        likes.setProperty("count", 0);
        likes.setProperty("likers", 0);
        datastore.put(likes);
    }
}
