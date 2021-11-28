package com.tinyinsta.common;

import com.google.appengine.api.datastore.*;
import com.google.appengine.api.datastore.Query.*;

public class ExistenceQuery {
    public boolean check(String kind, Key ancestorKey, String searchedValue) {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        Query existenceQuery;
     
        Filter parentFilter = new FilterPredicate("parentId", FilterOperator.EQUAL, ancestorKey.getName());
        Filter batchFilter = new FilterPredicate("batch", FilterOperator.EQUAL, searchedValue);
        CompositeFilter filter = CompositeFilterOperator.and(parentFilter, batchFilter);

        existenceQuery = new Query(kind)
                .setFilter(filter)
                .setKeysOnly(); 
        
        QueryResultList<Entity> existenceResults = datastore.prepare(existenceQuery).asQueryResultList(FetchOptions.Builder.withLimit(1));
        
        // If we find a result then it means that the user is already following the target
        return existenceResults.size() > 0;
    }
}
