package com.tinyinsta;

import com.google.api.server.spi.auth.common.User;
import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.response.NotFoundException;
import com.google.api.server.spi.response.UnauthorizedException;
import com.google.appengine.api.datastore.*;

import javax.inject.Named;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Api(name = "tinyinsta", version = "v1", scopes = { Constants.EMAIL_SCOPE }, clientIds = { Constants.WEB_CLIENT_ID })
public class Users {
    @ApiMethod(name = "users.getSelf", httpMethod = "get", path = "users/me",
            clientIds = { Constants.WEB_CLIENT_ID },
            audiences = { Constants.WEB_CLIENT_ID },
            scopes = { Constants.EMAIL_SCOPE, Constants.PROFILE_SCOPE })
    public Entity getSelf(User reqUser) throws UnauthorizedException, EntityNotFoundException {
        // If the user is not logged in : throw an error or redirect to the login page
        if (reqUser == null) {
            throw new UnauthorizedException("Authorization required");
        }

        // Query the datastore to get the user by its ID
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        // If the user is already in the datastore
        try {
            // Get entity by key id
            Entity user = datastore.get(KeyFactory.createKey("User", reqUser.getId()));
            return user;
        }

        // If the user isn't already in the datastore
        catch (EntityNotFoundException e) {
            // Create an user entity with the following properties:
            // - id : the user id
            // - email : the user email
            // - name : the user name
            // - handle : the user account handle (@...)
            // - picture : the user picture
            // - createdAt : the date of creation
            // - updatedAt : the date of last update
            // - followers : An UserFollowers entity which contains multiple lists of
            // followers
            // - following : An UserFollowings entity which contains multiple lists of
            // followings

            // Create the user entity
            Entity newUser = new Entity("User", reqUser.getId());

            // Set the user properties
            newUser.setProperty("id", reqUser.getId());
            newUser.setProperty("email", reqUser.getEmail());
            newUser.setProperty("handle", reqUser.getEmail().split("@")[0]);
            newUser.setProperty("name", reqUser.getEmail().split("@")[0]);
            newUser.setProperty("pictureURL", "https://thispersondoesnotexist.com/"); // set link to a default picture
            newUser.setProperty("createdAt", new Date());
            newUser.setProperty("updatedAt", new Date());

            // Create the UserFollowers entity

            // A for loop that loops 3 times
            for (int i = 0; i < 3; i++) {
                // Create the UserFollowers entity
                Entity userFollowers = new Entity("UserFollower", newUser.getKey());

                // The user's first follower is itself so it can see its own posts in its timeline
                if(i == 0) {
                    ArrayList<String> list = new ArrayList<String>();
                    list.add(reqUser.getId());

                    userFollowers.setProperty("batch", list);
                    userFollowers.setProperty("size", 1);
                } else {
                    userFollowers.setProperty("batch", new ArrayList<String>());
                    userFollowers.setProperty("size", 0);
                }

                userFollowers.setProperty("updatedAt", new Date());

                // Put the UserFollowers entity in the datastore
                datastore.put(userFollowers);
            }

            // Create the UserFollowings entity
            Entity newUserFollowings = new Entity("UserFollowing", newUser.getKey());
            newUserFollowings.setProperty("batch", new ArrayList<String>());

            // Put the entities in the datastore
            datastore.put(newUser);
            datastore.put(newUserFollowings);

            // Return the user
            return newUser;
        }
    }

    @ApiMethod(name = "users.updateSelf", httpMethod = "put", path = "users/me",
            clientIds = { Constants.WEB_CLIENT_ID },
            audiences = { Constants.WEB_CLIENT_ID },
            scopes = { Constants.EMAIL_SCOPE, Constants.PROFILE_SCOPE })
    public Entity updateSelf(User reqUser, Map<String, Object> reqBody)
            throws UnauthorizedException, EntityNotFoundException {
        // If the user is not logged in : throw an error or redirect to the login page
        if (reqUser == null) {
            throw new UnauthorizedException("Authorization required");
        }

        // Query the datastore to get the user by its ID
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        Entity user = datastore.get(KeyFactory.createKey("User", reqUser.getId()));

        user.setProperty("updatedAt", new Date()); // Update the last updated date

        // Only allow update of certains variables
        String[] mutableVariables = { "name", "handle", "email", "pictureURL" };

        for (Map.Entry<String, Object> entry : reqBody.entrySet()) {
            // TODO: Make sure the key matches one of the mutable variables
            // String key = entry.getKey();

            // if(Array.stream(mutableVariables).anyMatch(key::equals)) {
            user.setProperty(entry.getKey(), entry.getValue()); // Update the property
            // }
        }

        // Update each parameter
        datastore.put(user);

        return user;
    }

    // A route to get an user by its handle
    @ApiMethod(name = "users.getUserByHandle", httpMethod = "get", path = "users/handle/{handle}")
    public Entity getUserByHandle(@Named("handle") String handle) throws NotFoundException, EntityNotFoundException {
        // Query the datastore to get the user by its handle
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        Query query = new Query("User").setFilter(new Query.FilterPredicate("handle", Query.FilterOperator.EQUAL, handle));
        Entity user = datastore.prepare(query).asSingleEntity();

        if (user == null) {
            throw new NotFoundException("User not found");
        }

        // Remove sensitive data before returning the user (only in the response do not update the datastore)
        user.removeProperty("email");

        return user;
    }

    // A route to get timeline
    @ApiMethod(name = "users.getTimeline", httpMethod = "get", path = "users/me/timeline",
            clientIds = { Constants.WEB_CLIENT_ID },
            audiences = { Constants.WEB_CLIENT_ID },
            scopes = { Constants.EMAIL_SCOPE, Constants.PROFILE_SCOPE })
    public List<Entity> getTimeline(User reqUser) throws UnauthorizedException, EntityNotFoundException {
        // If the user is not logged in : throw an error or redirect to the login page
        if (reqUser == null) {
            throw new UnauthorizedException("Authorization required");
        }
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        // Query query = new Query("Post").setFilter(new Query.FilterPredicate("owner",
        // Query.FilterOperator.EQUAL, "UserFollowings")).addSort("createdAt",
        // Query.SortDirection.DESCENDING)
        Entity userFollowings = datastore.get(KeyFactory.createKey("UserFollowings", reqUser.getId()));
        ArrayList<String> followings = (ArrayList<String>) userFollowings.getProperty("followingsBatch-1");
        Query query = new Query("Post")
                .setFilter(new Query.FilterPredicate("owner", Query.FilterOperator.EQUAL, followings))
                .addSort("createdAt", Query.SortDirection.DESCENDING);
        QueryResultList<Entity> result = datastore.prepare(query).asQueryResultList(FetchOptions.Builder.withLimit(10));

        return result;
    }

}
