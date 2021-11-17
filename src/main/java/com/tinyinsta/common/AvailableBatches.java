package com.tinyinsta.common;

import com.google.appengine.api.datastore.*;

import java.util.ArrayList;
import java.util.List;

public class AvailableBatches {
    private String kind;
    private Key ancestorKey;
    private List<Entity> availableBatches;
    private int number;

    public AvailableBatches(String kind, Key ancestorKey) {
        this.kind = kind;
        this.ancestorKey = ancestorKey;

        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        Query getBatchesQuery = new Query(this.kind)
                .setAncestor(this.ancestorKey)
                .setFilter(new Query.FilterPredicate("size", Query.FilterOperator.LESS_THAN, Constants.MAX_BATCH_SIZE));

        this.availableBatches =
                datastore.prepare(getBatchesQuery).asQueryResultList(FetchOptions.Builder.withDefaults());

        this.number = this.availableBatches.size();
    }

    public Entity getOneRandom() {
        Entity availableBatch;
        ArrayList<String> batch = new ArrayList<>(); // In case we get an empty batch we need to declare it

        int randomBatch = new RandomGenerator().get(0, batch.size());

        if(this.availableBatches.size() > 0) {
            availableBatch = this.availableBatches.get(randomBatch);
        } else {
            availableBatch = new Entity(this.kind, this.ancestorKey);
        }

        return availableBatch;
    }

    public int getSizeCount(){
        int count = 0;
        for (Entity batch : this.availableBatches){
            int batchSize =  new Integer(batch.getProperty("size").toString());
            count += batchSize;
        }

        return count;
    }

    public int getNumber() {
        return this.number;
    }
}
