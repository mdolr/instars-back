package com.tinyinsta.common;

import com.google.appengine.api.datastore.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AvailableBatches {
    private final String kind;
    private Key ancestorKey;
    private List<Entity> availableBatches;
    public int fullBatches;
    public int nonFullBatches;
    private List<Key> availableBatchesKeys;
    public Entity entity;

    public AvailableBatches(Entity entity, String kind) {
        this.kind = kind;
        this.entity = entity;
        this.availableBatchesKeys = new ArrayList<Key>();
        
        // Using entity.getProperty("batchIndex")
        // find all occurences of 0
        // and construct a list of keys
        // formatted as index_where_we_find_a_0 + "-" + entity.getProperty("id")
        // and then use the list to get all the batches
        ArrayList<Integer> batchIndex = (ArrayList<Integer>) entity.getProperty("batchIndex");
        List<Key> availableBatchesKeys = new ArrayList<>();
        
        for (int i = 0; i < batchIndex.size(); i++) {
            int batchState = ((Number) batchIndex.get(i)).intValue(); // Java cannot cast int to long blabla
          
            if (batchState == 0) {
                String childId = i + "-" + entity.getProperty("id");
                Key key = KeyFactory.createKey(kind, childId);  
                
                this.availableBatchesKeys.add(key);
                this.nonFullBatches += 1;
            } 
            
            else if(batchState == 1) {
                this.fullBatches += 1;
            }
        }
    }

    private void LoadAvailableBatches() {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        Iterable<Key> iterableKeys = this.availableBatchesKeys;
              
        Map<Key,Entity> availableBatchesMap = datastore.get(iterableKeys);
        this.availableBatches = new ArrayList<>(availableBatchesMap.values());
    }

    public Entity getOneRandom() throws EntityNotFoundException {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        Entity availableBatch;

        if(this.availableBatchesKeys.size() > 0) {
            int randomBatch = new RandomGenerator().get(0, this.availableBatchesKeys.size() - 1);
            availableBatch = datastore.get(this.availableBatchesKeys.get(randomBatch));
        } else {
            availableBatch = new Entity(this.kind, this.ancestorKey);
        }

        return availableBatch;
    }

    public int getSizeCount(){
        int count = 0;
        this.LoadAvailableBatches();

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
