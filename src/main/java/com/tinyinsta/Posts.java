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

import javax.annotation.Nullable;
import javax.inject.Named;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

import com.tinyinsta.res.PostDTO;
import com.tinyinsta.res.UrlDTO;

@Api(name = "tinyinsta", version = "v1", scopes = { Constants.EMAIL_SCOPE }, clientIds = { Constants.WEB_CLIENT_ID })
public class Posts {

    @ApiMethod(name = "posts.getOne", httpMethod = "get", path = "posts/{id}")
    public PostDTO getOne(@Named("id") String postId) throws EntityNotFoundException {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        Entity post = datastore.get(KeyFactory.createKey("Post", postId));

        return new PostDTO(post, 0); //TODO: Add like number and full retrieval of post
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
            posts.add(new PostDTO(post, 0)); //TODO: Add like number and full retrieval of post 
        }

        return posts;
    }

    @ApiMethod(name = "posts.requestSignedURL", httpMethod = "get", path = "signedURL")
    public UrlDTO requestSignedURL(@Named("fileName") String fileName) {
        String projectId = "tinyinsta-web";
        String bucketName = "signed-urls-upload";

        Credentials credentials = null;
        try {
            credentials = GoogleCredentials.fromStream(new FileInputStream("src/cred.json"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        Storage storage = StorageOptions.newBuilder().setProjectId(projectId).setCredentials(credentials).build()
                .getService();

        // Define Resource
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, fileName)).build();

        // Generate Signed URL
        Map<String, String> extensionHeaders = new HashMap<>();
        extensionHeaders.put("Content-Type", "image/png");
        //extensionHeaders.put("Access-Control-Allow-Origin", "*");

        URL url = storage.signUrl(blobInfo, 15, TimeUnit.MINUTES, Storage.SignUrlOption.httpMethod(HttpMethod.PUT),
                Storage.SignUrlOption.withExtHeaders(extensionHeaders), Storage.SignUrlOption.withV4Signature());
        
        return new UrlDTO(url.toString());
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
        String bucketName = "signed-urls-upload";

        String postId = UUID.randomUUID().toString();
        String pictureId = UUID.randomUUID().toString();

        String fileName = (String) reqBody.get("fileName");
        
        String fileExtension = fileName.substring(fileName.lastIndexOf('.') + 1);
        String uploadFileName = pictureId + "." + fileExtension;

        Credentials credentials = null;

        try {
            credentials = GoogleCredentials.fromStream(new FileInputStream("src/cred.json"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        Storage storage = StorageOptions.newBuilder().setProjectId(projectId).setCredentials(credentials).build()
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
            post.setProperty("mediaURL", "https://storage.googleapis.com/" + bucketName + "/" + uploadFileName);// TODO: Change url to store url
            post.setProperty("authorId", user.getProperty("id").toString());
            post.setProperty("description", reqBody.get("description"));
            post.setProperty("createdAt", new Date());
            post.setProperty("fullBatches", 0);
            post.setProperty("published", false);

            new PostReceivers().createEntity(user, post);

            int nbBuckets = Constants.LIKES_MAX_BUCKETS_NUMBER;
            for (int i = 1; i <= nbBuckets; i++) {
                new PostLikers().createEntity(postKey);
            }
           
            datastore.put(post);

            txn.commit();

            // To only return it once in this request and never return it in the future
            post.setProperty("uploadURL", url.toString());
            return new PostDTO(post, 0);
        } finally {
            if (txn.isActive()) {
                txn.rollback();
            }
        }
    }
}