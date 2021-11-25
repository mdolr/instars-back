package com.tinyinsta.common;

import com.google.appengine.api.datastore.*;

public class ExistenceQuery {
    public boolean check(String kind, Key ancestorKey, String searchedValue) {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        Query existenceQuery = new Query(kind)
                .setAncestor(ancestorKey)
                .setFilter(new Query.FilterPredicate("batch", Query.FilterOperator.EQUAL, searchedValue));

        QueryResultList<Entity> existenceResults = datastore.prepare(existenceQuery).asQueryResultList(FetchOptions.Builder.withLimit(1));

        // If we find a result then it means that the user is already following the target
        return existenceResults.size() > 0;
    }
}
