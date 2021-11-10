package com.tinyinsta;

import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.appengine.api.datastore.*;

import javax.annotation.Nullable;
import javax.inject.Named;

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
    public Entity post(
        @Named("image") String image,
        @Named("owner") String owner,
        @Named("title") String title
    ) {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        Transaction txn = datastore.beginTransaction();
        try {
            Date date = new Date();
            String date_formatted = new SimpleDateFormat("dd-MM-yyyy_HH:mm:ss").format(date);

            String id = date_formatted + "_" + owner;

            Entity e = new Entity("Picture", id);
            e.setProperty("image", image);
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