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

@Api(name = "tinyinsta", version = "v1", scopes = { Constants.EMAIL_SCOPE }, clientIds = { Constants.WEB_CLIENT_ID })
public class Posts {

    @ApiMethod(name = "posts.getOne", httpMethod = "get", path = "posts/{id}")
    public Entity getOne(@Named("id") String postId) throws EntityNotFoundException {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        Entity post = datastore.get(KeyFactory.createKey("Post", postId));

        return post;
    }

    @ApiMethod(name = "posts.getAll", httpMethod = "get", path = "posts")
    public List<Entity> getAll(@Nullable @Named("userId") String userId) {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        Query q = new Query("Post").addSort("createdAt", Query.SortDirection.DESCENDING);

        if (userId != null) {
            q.setFilter(new Query.FilterPredicate("userId", Query.FilterOperator.EQUAL, userId));
        }

        PreparedQuery pq = datastore.prepare(q);
        List<Entity> result = pq.asList(FetchOptions.Builder.withDefaults());

        return result;
    }

    @ApiMethod(name = "posts.requestSignedURL", httpMethod = "get", path = "signedURL")
    public URL requestSignedURL(@Named("fileName") String fileName) {
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
        return url;
    }

    @ApiMethod(name = "posts.uploadPost", httpMethod = "post", path = "posts",
            clientIds = { Constants.WEB_CLIENT_ID },
            audiences = { Constants.WEB_CLIENT_ID },
            scopes = { Constants.EMAIL_SCOPE, Constants.PROFILE_SCOPE })
    public Entity uploadPost(
            User reqUser,
            @Named("url") String url,
            @Named("title") String title,
            @Named("description") String description
    ) throws UnauthorizedException, EntityNotFoundException {
        if (reqUser == null) {
            throw new UnauthorizedException("Authorization required");
        }

        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        Entity user = datastore.get(KeyFactory.createKey("User", reqUser.getId()));

        Transaction txn = datastore.beginTransaction();

        try {
            Key postKey = KeyFactory.createKey("Post", UUID.randomUUID().toString());

            Entity post = new Entity(postKey);
            post.setProperty("url", url);// TODO: Change url to store url
            post.setProperty("userId", user.getProperty("id").toString());
            post.setProperty("title", title);
            post.setProperty("description", description);
            post.setProperty("createdAt", new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss:SSS").format(new Date()));
            post.setProperty("fullBatches", 0);

            new PostReceivers().createEntity(user, post);

            int nbBuckets = Constants.LIKES_MAX_BUCKETS_NUMBER;
            for (int i = 1; i <= nbBuckets; i++) {
                new PostLikers().createEntity(postKey);
            }

            datastore.put(post);

            txn.commit();

            return post;
        } finally {
            if (txn.isActive()) {
                txn.rollback();
            }
        }
    }
}