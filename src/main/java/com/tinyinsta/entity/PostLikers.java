package com.tinyinsta.entity;

import com.google.appengine.api.datastore.*;
import java.util.*;

public class PostLikers {
    public void createEntity(Entity post, int batchNumber) {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        String postId = (String) post.getProperty("id");
    
        String batchId = String.valueOf(String.valueOf(batchNumber) + "-" + postId);
        Key key = KeyFactory.createKey("PostLiker", batchId);
        
        Entity postLiker = new Entity(key);
        
        postLiker.setProperty("id", batchId);
        postLiker.setProperty("batch", null);
        postLiker.setProperty("size", 0);
        postLiker.setProperty("updatedAt", new Date());
        
        datastore.put(postLiker);
    }
}
