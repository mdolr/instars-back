package com.tinyinsta.common;

import com.google.appengine.api.datastore.*;

import java.util.ArrayList;

public class AvailableBatch {
    private int size = 0;

    public Entity getRandom(String kind, Key ancestorKey) {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        Query getBatchesQuery = new Query(kind)
                .setAncestor(ancestorKey)
                .setFilter(new Query.FilterPredicate("size", Query.FilterOperator.LESS_THAN, Constants.MAX_BATCH_SIZE));

        QueryResultList<Entity> availableBatches =
                datastore.prepare(getBatchesQuery).asQueryResultList(FetchOptions.Builder.withLimit(Constants.LIKES_MAX_BUCKETS_NUMBER));

        Entity availableBatch = null;
        ArrayList<String> batch = new ArrayList<String>(); // In case we get an empty batch we need to declare it

        int randomBatch = new RandomGenerator().get(0, batch.size());

        if(availableBatches.size() > 0) {
            availableBatch = availableBatches.get(randomBatch);
        } else {
            availableBatch = new Entity(kind, ancestorKey);
        }

        this.size = availableBatches.size();

        return availableBatch;
    }

    public int getSize() {
        return this.size;
    }
}
