package com.tinyinsta;

import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.response.UnauthorizedException;
import com.google.appengine.api.datastore.*;

import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.*;

import javax.annotation.Nullable;
import javax.inject.Named;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.google.api.server.spi.auth.common.User;

@Api(name = "tinyinsta", version = "v1", scopes = { Constants.EMAIL_SCOPE }, clientIds = { Constants.WEB_CLIENT_ID })
public class Posts {

    @ApiMethod(name = "posts.getOne", httpMethod = "get", path = "posts/{id}")
    public Entity getOne(@Named("id") String id) throws EntityNotFoundException {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        Entity post = datastore.get(KeyFactory.createKey("Post", id));

        return post;
    }

    @ApiMethod(name = "posts.getAll", httpMethod = "get", path = "posts")
    public List<Entity> getAll(@Nullable @Named("owner") String owner) {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        Query q = new Query("Post");

        if (owner != null) {
            q.setFilter(new Query.FilterPredicate("owner", Query.FilterOperator.EQUAL, owner));
        }

        PreparedQuery pq = datastore.prepare(q);
        List<Entity> result = pq.asList(FetchOptions.Builder.withDefaults());

        return result;
    }

    @ApiMethod(name = "posts.requestSignedURL", httpMethod = "get", path = "signedURL")
    public URL requestSignedURL(@Named("fileName") String fileName) {
        String projectId = "tinyinsta-web";
        String bucketName = "posts_test789";

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
        extensionHeaders.put("Content-Type", "application/octet-stream");

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
            Date date = new Date();
            String date_timestamp = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(date);
            String ownerId = (String) user.getProperty("id");
            Key postKey = KeyFactory.createKey("Post", ownerId + "_" + date_timestamp);

            Entity e = new Entity(postKey);
            e.setProperty("url", url);// TODO: Change url to store url
            e.setProperty("user", ownerId);
            e.setProperty("title", title);
            e.setProperty("description", description);
            e.setProperty("createdAt", date_timestamp);

            int nbBuckets = Constants.LIKES_MAX_BUCKETS_NUMBER;
            for (int i = 1; i <= nbBuckets; i++) {
                new Likes().createEntity(String.valueOf(i), postKey);
            }

            datastore.put(e);

            txn.commit();

            return e;
        } finally {
            if (txn.isActive()) {
                txn.rollback();
            }
        }
    }
}