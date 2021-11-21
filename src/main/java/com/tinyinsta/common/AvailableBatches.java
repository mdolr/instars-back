package com.tinyinsta.common;

import com.google.appengine.api.datastore.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AvailableBatches {
    private String kind;
    private Key ancestorKey;
    private List<Entity> availableBatches;
    public int fullBatches;
    public int nonFullBatches;
    public Entity entity;

    public AvailableBatches(Entity entity, String kind) {
        this.kind = kind;
        this.entity = entity;
        
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
            // Using entity.getProperty("batchIndex")
            // find all occurences of 0
            // and construct a list of keys
            // formatted as index_where_we_find_a_0 + "-" + entity.getProperty("id")
            // and then use the list to get all the batches
            ArrayList<Integer> batchIndex = (ArrayList<Integer>) entity.getProperty("batchIndex");
            List<Key> batchKeys = new ArrayList<>();
            
            for (int i = 0; i < batchIndex.size(); i++) {
                int batchState = ((Number) batchIndex.get(i)).intValue(); // Java cannot cast int to long blabla

                if (batchState == 0) {
                    String childId = String.valueOf(i) + "-" + ((String) entity.getProperty("id"));
                    
                    Key key = KeyFactory.createKey(entity.getKey(), kind, childId);  
                    batchKeys.add(key);
                    
                    this.nonFullBatches += 1;
                } 
                
                else if(batchState == 1) {
                    this.fullBatches += 1;
                }
            }
            
            Iterable<Key> iterableKeys = batchKeys;
            
            Map<Key,Entity> availableBatchesMap = datastore.get(iterableKeys);
            this.availableBatches = new ArrayList<Entity>(availableBatchesMap.values());
            
        
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
            int batchSize = new Integer(batch.getProperty("size").toString());
            count += batchSize;
        }

        return count;
    }

    public int getFullBatchesCount(){
        return this.fullBatches;
    }

    public int getNonFullBatchesCount(){
      return this.nonFullBatches;
    }
}
