package com.tinyinsta.entity;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;

import java.util.Date;

public class PostLikers {
    public void createEntity(Key parent) {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        Entity postLiker = new Entity("PostLiker", parent);
        postLiker.setProperty("batch", null);
        postLiker.setProperty("size", 0);
        postLiker.setProperty("updatedAt", new Date());

        datastore.put(postLiker);
    }
}
