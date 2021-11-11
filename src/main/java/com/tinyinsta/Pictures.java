package com.tinyinsta;

import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.appengine.api.datastore.*;

import com.google.cloud.storage.*;

import javax.annotation.Nullable;
import javax.inject.Named;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@Api(
    name = "tinyinsta",
    version = "v1",
    scopes = {Constants.EMAIL_SCOPE},
    clientIds = {Constants.WEB_CLIENT_ID}
)
public class Pictures {

    @ApiMethod(name = "pictures", httpMethod = "get", path = "pictures/{id}")
    public Entity getIndividual(
        @Named("id") String id
    ) throws EntityNotFoundException {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        Entity e=new Entity("Picture",id);

        Entity e1=datastore.get(e.getKey());

        return e1;
    }

    @ApiMethod(name = "pictures", httpMethod = "get", path = "pictures")
    public List<Entity> getAll(
        @Nullable @Named("owner") String owner
    ) {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        Query q = new Query("Picture");

        if(owner!=null) {
            q.setFilter(new Query.FilterPredicate("owner", Query.FilterOperator.EQUAL, owner));
        }

        PreparedQuery pq = datastore.prepare(q);
        List<Entity> result = pq.asList(FetchOptions.Builder.withDefaults());

        return result;
    }

    @ApiMethod(name = "pictures", httpMethod = "post", path = "pictures")
    public Entity uploadPicture(
        @Named("url") String url,
        @Named("owner") String owner,
        @Named("title") String title
    ) {
        String projectId = "tinyinsta-web";
        String bucketName = "pictures_test789";
        String objectName = "picture"+owner+title;

        Storage storage = StorageOptions.newBuilder().setProjectId(projectId).build().getService();
        BlobId blobId = BlobId.of(bucketName, objectName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
        try {
            storage.create(blobInfo, Files.readAllBytes(Paths.get(url)));//TODO: L'url ne fonctionne pas
        } catch (IOException e) {
            e.printStackTrace();
        }

        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        Transaction txn = datastore.beginTransaction();
        try {
            Date date = new Date();
            String date_formatted = new SimpleDateFormat("dd-MM-yyyy_HH:mm:ss").format(date);

            String id = date_formatted + "_" + owner;

            Entity e = new Entity("Picture", id);
            e.setProperty("url", url);//TODO: Change url to store url
            e.setProperty("owner", owner);
            e.setProperty("title", title);
            e.setProperty("date", date_formatted);
            e.setProperty("likes", 0);

            datastore.put(e);

            txn.commit();

            return e;
        } finally {
            if (txn.isActive()) {
                txn.rollback();
            }
        }
    }

    @ApiMethod(name = "pictures", httpMethod = "put", path = "pictures/{id}/like")
    public Entity likePicture(
        @Named("id") String id
    ) throws EntityNotFoundException {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        Transaction txn = datastore.beginTransaction();
        try {
            Entity e=new Entity("Picture",id);

            Entity e1=datastore.get(e.getKey());
            long likes = (long) e1.getProperty("likes");
            e1.setProperty("likes", likes+1);
            datastore.put(e1);

            txn.commit();

            return e1;
        } finally {
            if (txn.isActive()) {
                txn.rollback();
            }
        }
    }
}