package com.tinyinsta;

import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.response.NotFoundException;
import com.google.appengine.api.users.User;

import java.util.ArrayList;

import javax.inject.Named;

import com.google.api.server.spi.response.BadRequestException;
import com.google.api.server.spi.response.UnauthorizedException;
import com.google.api.server.spi.response.ForbiddenException;
import com.google.api.server.spi.response.NotFoundException;
import com.google.api.server.spi.response.ConflictException;
import com.google.api.server.spi.response.InternalServerErrorException;
import com.google.api.server.spi.response.ServiceUnavailableException;
// Classe temporaire : 
// A Mettre dans "User.java"
@Api(
    name = "tinyinsta",
    version = "v1",
    scopes = {Constants.EMAIL_SCOPE},
    clientIds = {Constants.WEB_CLIENT_ID, Constants.ANDROID_CLIENT_ID, Constants.IOS_CLIENT_ID},
    audiences = {Constants.ANDROID_AUDIENCE}
)


public class Followers {
    
    @ApiMethod(name ="getFollowers", httpMethod = "get", path = "follow/{userId}")
    public List<Entity> getFollowers(
        @Named("userId") String userId,
        @Nullable @Named("softLimit") String softLimit
    ) throws BadRequestException
    {
        if (userId == null){
            throw new BadRequestException("userId is unset");
        }
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        // No transaction necessary
        Query q = new Query("User").setProjection("userId","followers");

        if(softLimit != null){ // Pas de limite et curseur ? 
            softLimit = 30;
        }
        int limit = min(sofLimit, 30);

        q.setFilter(new Query.FilterPredicate("userId", Query.FilterOperator.EQUAL, userId));
        q.setFilter(new Query.setLimit(limit));
        q.setFilter(new Query.setProjection("followers"))

        PreparedQuery pq = datastore.prepare(q);
        List<Entity> result = pq.asList(FetchOptions.Builder.withDefaults());

        return result;

    }

    @ApiMethod(name ="getFollowings", httpMethod = "get", path = "follow/{userId}")
    public List<Entity> getFollowers(
        @Named("userId") String userId,
        @Nullable @Named("softLimit") String softLimit
    ) throws BadRequestException
    {
        if (userId == null){
            throw new BadRequestException("userId is unset");
        }
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        // No transaction necessary
        Query q = new Query("User").setProjection("userId","followings");

        if(softLimit != null){
            softLimit = 30;
        }
        int limit = min(sofLimit, 30);

        q.setFilter(new Query.FilterPredicate("userId", Query.FilterOperator.EQUAL, userId));
        q.setFilter(new Query.setLimit(limit));
        q.setFilter(new Query.setProjection("followings"))
        
        PreparedQuery pq = datastore.prepare(q);
        List<Entity> result = pq.asList(FetchOptions.Builder.withDefaults());

        return result;

    }
    @ApiMethod(name ="addFollowings", httpMethod = "post", path = "follow/{userId}")
    public List<Entity> getFollowers(
        @Named("userId") String userId,
        @Named("newFollow") String newFollow
    ) throws BadRequestException
    {
        if (userId == null or newFollow){
            throw new BadRequestException("A necessary parameter is unset");
        }
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        Transaction txn = datastore.beginTransaction();
        try{
            txn.commit();
        }
        catch{
            
        }
        finally{
            if (txn.isActive()) {
                txn.rollback();
            }
        }
        

        return result;

    }
    @ApiMethod(name ="getFollowings", httpMethod = "post", path = "followers/{userId}")
    public List<Entity> getFollowers(
        @Named("userId") String userId,
        @Named("newFollow") String newFollow
    ) throws BadRequestException
    {
        if (userId == null){
            throw new BadRequestException("A necessary parameter is unset");
        }
        Transaction txn = datastore.beginTransaction();
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        // No transaction necessary
        try{
            txn.commit();
        }
        catch{

        }
        finally{
            if (txn.isActive()) {
                txn.rollback();
            }
        }
        return result;

    }
    
}
