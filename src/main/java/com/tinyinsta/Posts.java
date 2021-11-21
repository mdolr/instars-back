package com.tinyinsta;

import com.google.api.server.spi.auth.common.User;
import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.response.UnauthorizedException;
import com.google.appengine.api.datastore.*;
import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.*;
import com.tinyinsta.common.Constants;
import com.tinyinsta.entity.PostLikers;
import com.tinyinsta.entity.PostReceivers;
import com.tinyinsta.common.RandomGenerator;

import javax.annotation.Nullable;
import javax.inject.Named;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

import com.tinyinsta.dto.PostDTO;
import com.tinyinsta.dto.UrlDTO;

import com.google.api.server.spi.response.NotFoundException;

@Api(name = "tinyinsta", version = "v1", scopes = { Constants.EMAIL_SCOPE }, clientIds = { Constants.WEB_CLIENT_ID })
public class Posts {
    @ApiMethod(name = "posts.getOne", httpMethod = "get", path = "posts/{id}")
    public PostDTO getOne(@Named("id") String postId) throws EntityNotFoundException {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        Entity post = datastore.get(KeyFactory.createKey("Post", postId));

        return new PostDTO(post, null, 0); //TODO: Add like number and full retrieval of post
    }

    @ApiMethod(name = "posts.getAll", httpMethod = "get", path = "posts")
    public ArrayList<PostDTO> getAll(@Nullable @Named("userId") String userId) {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        Query q = new Query("Post").addSort("createdAt", Query.SortDirection.DESCENDING);

        if (userId != null) {
            q.setFilter(new Query.FilterPredicate("userId", Query.FilterOperator.EQUAL, userId));
        }

        PreparedQuery pq = datastore.prepare(q);
        List<Entity> results = pq.asList(FetchOptions.Builder.withDefaults());

        ArrayList<PostDTO> posts = new ArrayList<PostDTO>();
        
        for(Entity post : results) {
            posts.add(new PostDTO(post, null, 0)); //TODO: Add like number and full retrieval of post 
        }

        return posts;
    }
    
    @ApiMethod(name = "posts.uploadPost", httpMethod = "post", path = "posts",
            clientIds = { Constants.WEB_CLIENT_ID },
            audiences = { Constants.WEB_CLIENT_ID },
            scopes = { Constants.EMAIL_SCOPE, Constants.PROFILE_SCOPE })
    public PostDTO uploadPost(
            User reqUser,
            Map<String, Object> reqBody
    ) throws UnauthorizedException, EntityNotFoundException {
        if (reqUser == null) {
            throw new UnauthorizedException("Authorization required");
        }

        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        Entity user = datastore.get(KeyFactory.createKey("User", reqUser.getId()));

        String projectId = "tinyinsta-web";
        String bucketName = "instars-23pnm1d4";

        int randomBucket = new RandomGenerator().get(0, Constants.TIMELINE_BUCKETS - 1);

        String postId = String.valueOf(randomBucket) + String.valueOf(-1 * new Date().getTime()); //UUID.randomUUID().toString();
        String pictureId = UUID.randomUUID().toString();

        String fileName = (String) reqBody.get("fileName");
        
        String fileExtension = fileName.substring(fileName.lastIndexOf('.') + 1);
        String uploadFileName = pictureId + "." + fileExtension;

        Storage storage = StorageOptions.newBuilder().setProjectId(projectId).build()
                .getService();

        // Define Resource
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, uploadFileName)).build();

        // Generate Signed URL
        Map<String, String> extensionHeaders = new HashMap<>();
        extensionHeaders.put("Content-Type", (String) reqBody.get("fileType"));
        //extensionHeaders.put("Access-Control-Allow-Origin", "*");

        URL url = storage.signUrl(blobInfo, 15, TimeUnit.MINUTES, Storage.SignUrlOption.httpMethod(HttpMethod.PUT),
                Storage.SignUrlOption.withExtHeaders(extensionHeaders), Storage.SignUrlOption.withV4Signature());
        
        Transaction txn = datastore.beginTransaction();

        try {
            Key postKey = KeyFactory.createKey("Post", postId);

            Entity post = new Entity(postKey);
            post.setProperty("id", postId);
            post.setProperty("pictureId", pictureId);
            post.setProperty("pictureName", uploadFileName);
            post.setProperty("mediaURL", "https://storage.googleapis.com/" + bucketName + "/" + uploadFileName);// TODO: Change url to store url
            post.setProperty("authorId", user.getProperty("id").toString());
            post.setProperty("description", reqBody.get("description"));
            post.setProperty("createdAt", new Date());
            post.setProperty("published", false);
            post.setProperty("batchIndex", null);
            
            datastore.put(post);
            txn.commit();

            post.setProperty("uploadURL", url.toString()); // only return it once don't store it in the datastore
            return new PostDTO(post, user, 0);
        } finally {
            if (txn.isActive()) {
                txn.rollback();
            }
        }
    }

    @ApiMethod(name = "posts.publishPost", httpMethod = "post", path = "posts/{id}/publish",
            clientIds = { Constants.WEB_CLIENT_ID },
            audiences = { Constants.WEB_CLIENT_ID },
            scopes = { Constants.EMAIL_SCOPE, Constants.PROFILE_SCOPE })
    public PostDTO publishPost(
              User reqUser,
              @Named("id") String postId
    ) throws UnauthorizedException, EntityNotFoundException, NotFoundException {
        if (reqUser == null) {
            throw new UnauthorizedException("Authorization required");
        }
  
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
  
        Entity user = datastore.get(KeyFactory.createKey("User", reqUser.getId()));
        Entity post = datastore.get(KeyFactory.createKey("Post", postId));

        if (!post.getProperty("authorId").toString().equals(reqUser.getId().toString())) {
            throw new UnauthorizedException("Post author doesn't match user");
        }

        String projectId = "tinyinsta-web";
        String bucketName = "instars-23pnm1d4";

        String objectName = (String) post.getProperty("pictureName");

        Storage storage = StorageOptions.newBuilder().setProjectId(projectId).build()
                .getService();

        Bucket bucket = storage.get(bucketName);
        com.google.cloud.storage.Blob blob = storage.get(bucketName, objectName);

        Boolean objectExists = (blob != null && blob.exists());
        
        if(!objectExists) {
            throw new NotFoundException("Media not found");
        }

        Transaction txn = datastore.beginTransaction();

        try {
            new PostReceivers().createEntity(user, post);
            
            int nbBuckets = Constants.LIKES_MAX_BUCKETS_NUMBER;

            ArrayList<Integer> batchIndex = new ArrayList<Integer>();

            for (int i = 0; i < nbBuckets; i++) {
              new PostLikers().createEntity(post, batchIndex.size());
              batchIndex.add(0);
            }

            post.setProperty("batchIndex", batchIndex);
            post.setProperty("published", true);

            datastore.put(post);
            txn.commit();

            return new PostDTO(post, user, 0);
        } finally {
            if (txn.isActive()) {
                txn.rollback();
            } else {
                datastore.put(user);
            }
        }
    }
}
