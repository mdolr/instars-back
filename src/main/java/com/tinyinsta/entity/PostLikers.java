package com.tinyinsta.entity;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;

import java.util.Date;

public class PostLikers {
  /*
    public void createEntity(Key parent, String postId, int batchNumber) {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        
        Key key = KeyFactory.createKey("UserFollower", String.valueOf(i) + "-" + postId);

        Entity postLiker = new Entity(key);
        postLiker.setProperty("batch", null);
        postLiker.setProperty("size", 0);
        postLiker.setProperty("updatedAt", new Date());

        datastore.put(postLiker);
    }*/
}
