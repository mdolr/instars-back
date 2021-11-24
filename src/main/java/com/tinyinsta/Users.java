package com.tinyinsta;

import com.google.api.server.spi.auth.common.User;
import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.response.NotFoundException;
import com.google.api.server.spi.response.UnauthorizedException;
import com.google.api.server.spi.response.ConflictException;
import com.google.appengine.api.datastore.*;
import com.tinyinsta.common.AvailableBatches;
import com.tinyinsta.common.Constants;
import com.tinyinsta.common.ExistenceQuery;

import javax.servlet.http.HttpServletRequest;
import javax.inject.Named;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.tinyinsta.dto.UserDTO;
import org.apache.commons.codec.binary.Base64;
import org.json.*;

@Api(name = "tinyinsta", version = "v1", scopes = { Constants.EMAIL_SCOPE }, clientIds = { Constants.WEB_CLIENT_ID })
public class Users {
    @ApiMethod(name = "users.getSelf", httpMethod = "get", path = "users/me",
            clientIds = { Constants.WEB_CLIENT_ID },
            audiences = { Constants.WEB_CLIENT_ID },
            scopes = { Constants.EMAIL_SCOPE, Constants.PROFILE_SCOPE })
    public UserDTO getSelf(User reqUser, HttpServletRequest request) throws UnauthorizedException, EntityNotFoundException {
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
            return new UserDTO(user, false, 0);
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
            // - fullBatches : number of followers batches full
            // - followers : An UserFollowers entity which contains multiple lists of followers

            // Decode Authorization JWT
            String token = (String) request.getHeader("Authorization");
            token = token.substring(token.indexOf(" ") + 1);
            
            String[] chunks = token.split("\\.");
            
            String stringHeader = new String(Base64.decodeBase64(chunks[0]));
            String stringPayload = new String(Base64.decodeBase64(chunks[1]));

            JSONObject header = new JSONObject(stringHeader);  
            JSONObject payload = new JSONObject(stringPayload);

            Entity newUser = new Entity("User", reqUser.getId());

            // Set the user properties
            newUser.setProperty("id", reqUser.getId());
            newUser.setProperty("email", reqUser.getEmail());
            newUser.setProperty("handle", reqUser.getEmail().split("@")[0]);
            newUser.setProperty("name", payload.getString("name"));
            newUser.setProperty("pictureURL", payload.getString("picture")); 
            newUser.setProperty("createdAt", new Date());
            newUser.setProperty("updatedAt", new Date());

            ArrayList<Integer> batchIndex = new ArrayList<Integer>();

            // Create the UserFollowers entity

            // A for loop that loops 3 times
            for (int i = 0; i < 5; i++) {
                // Key  
                String userFollowersId = String.valueOf(i) + "-" + reqUser.getId().toString();
                Key key = KeyFactory.createKey(newUser.getKey(),"UserFollower", userFollowersId);

                // Create the UserFollowers entity
                Entity userFollowers = new Entity(key);
                userFollowers.setProperty("id", userFollowersId);

                batchIndex.add(0);

                // The user's first follower is itself so it can see its own posts in its timeline
                if(i == 0) {
                    List<String> list = new ArrayList<>();
                    list.add(reqUser.getId());

                    userFollowers.setProperty("batch", list);
                    userFollowers.setProperty("size", 1);
                } else {
                    userFollowers.setProperty("batch", new ArrayList<String>());
                    userFollowers.setProperty("size", 0);
                }

                userFollowers.setProperty("parentId", reqUser.getId());
                userFollowers.setProperty("updatedAt", new Date());

                // Put the UserFollowers entity in the datastore
                datastore.put(userFollowers);
            }

            newUser.setProperty("batchIndex", batchIndex);

            // Put the entities in the datastore
            datastore.put(newUser);

            return new UserDTO(newUser, false, 0);
        }
    }

    @ApiMethod(name = "users.getRandom", httpMethod = "get", path = "users/explore",
               clientIds = { Constants.WEB_CLIENT_ID },
               audiences = { Constants.WEB_CLIENT_ID },
               scopes = { Constants.EMAIL_SCOPE, Constants.PROFILE_SCOPE })
    public List<UserDTO> getRandom(User reqUser) throws UnauthorizedException, EntityNotFoundException, ConflictException {

      if(reqUser == null) {
            throw new UnauthorizedException("Authorization required");
        }

        // Query the datastore to get the users
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        
        // Get the first User's created date
        Query firstUserQuery = new Query("User")
            .addSort("createdAt", Query.SortDirection.ASCENDING);

        // Get the last User's created date
        Query lastUserQuery = new Query("User")
            .addSort("createdAt", Query.SortDirection.DESCENDING);

        // Get the results
        PreparedQuery preparedFirstUser = datastore.prepare(firstUserQuery);
        PreparedQuery preparedLastUser = datastore.prepare(lastUserQuery);

        // Get the results
        List<Entity> first = preparedFirstUser.asList(FetchOptions.Builder.withLimit(1));
        List<Entity> last = preparedLastUser.asList(FetchOptions.Builder.withLimit(1));

        Long timestamp =  1635760373519L;
        Date firstDate = new Date(timestamp);
        Date lastDate = new Date();

        if (first.size()>0) {
            firstDate = (Date) first.get(0).getProperty("createdAt");
        }
        if (last.size()>0){
            lastDate = (Date) last.get(0).getProperty("createdAt");
        }

        // Get a random date between oldest User's created date and newest user's created date.
        Date randomDate = new Date((long) (Math.random() * (lastDate.getTime() - firstDate.getTime())));

        // Verify that the user exists
        Entity user = datastore.get(KeyFactory.createKey("User", reqUser.getId()));

        // Then query 5 users before the random date
        Query queryAfter = new Query("User")
            .addSort("createdAt", Query.SortDirection.DESCENDING)
            .setFilter(new Query.FilterPredicate("createdAt", Query.FilterOperator.LESS_THAN, randomDate));

        // And then query 5 other users after the random date
        Query queryBefore = new Query("User")
            .addSort("createdAt", Query.SortDirection.ASCENDING)
            .setFilter(new Query.FilterPredicate("createdAt", Query.FilterOperator.GREATER_THAN, randomDate));

        // Get the results
        PreparedQuery preparedAfter = datastore.prepare(queryAfter);
        PreparedQuery preparedBefore = datastore.prepare(queryBefore);

        // Get the results
        List<Entity> after = preparedAfter.asList(FetchOptions.Builder.withLimit(5));
        List<Entity> before = preparedBefore.asList(FetchOptions.Builder.withLimit(5));

        // Create a list of UserDTO
        List<UserDTO> users = new ArrayList<>();

        
        for (Entity resultUser : after) {
            AvailableBatches availableBatches = new AvailableBatches(resultUser, "UserFollower");
            int followersCount = availableBatches.getSizeCount()+(availableBatches.getFullBatchesCount() * Constants.MAX_BATCH_SIZE) - 1;
            resultUser.setProperty("followers", followersCount);

            Boolean hasFollowed = (Boolean) new ExistenceQuery().check("UserFollower", resultUser.getKey(), reqUser.getId());
            resultUser.setProperty("hasFollowed", hasFollowed);

            users.add(new UserDTO(resultUser, true, followersCount));
        }

        
        for (Entity resultUser : before) {
            AvailableBatches availableBatches = new AvailableBatches(resultUser, "UserFollower");
            int followersCount = availableBatches.getSizeCount()+(availableBatches.getFullBatchesCount() * Constants.MAX_BATCH_SIZE) - 1;
            resultUser.setProperty("followers", followersCount);

            Boolean hasFollowed = (Boolean) new ExistenceQuery().check("UserFollower", resultUser.getKey(), reqUser.getId());
            resultUser.setProperty("hasFollowed", hasFollowed);

            users.add(new UserDTO(resultUser, true, followersCount));
        }

        // Return the list
        return users;
    }



    @ApiMethod(name = "users.updateSelf", httpMethod = "put", path = "users/me",
            clientIds = { Constants.WEB_CLIENT_ID },
            audiences = { Constants.WEB_CLIENT_ID },
            scopes = { Constants.EMAIL_SCOPE, Constants.PROFILE_SCOPE })
    public UserDTO updateSelf(User reqUser, Map<String, Object> reqBody)
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
        ArrayList<String> mutableVariables = new ArrayList<String>();
        mutableVariables.add("name");
        mutableVariables.add("handle");
        mutableVariables.add("email");
        mutableVariables.add("pictureURL");

        for (Map.Entry<String, Object> entry : reqBody.entrySet()) {
            String key = entry.getKey();

            if(mutableVariables.contains(key)) {
                user.setProperty(entry.getKey(), entry.getValue()); // Update the property
            }
        }

        // Update each parameter
        datastore.put(user);

        return new UserDTO(user, false, 0);
    }

    // A route to get an user by its handle
    @ApiMethod(name = "users.getUserByHandle", httpMethod = "get", path = "users/handle/{handle}")
    public UserDTO getUserByHandle(@Named("handle") String handle) throws NotFoundException, EntityNotFoundException {
        // Query the datastore to get the user by its handle
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        Query query = new Query("User").setFilter(new Query.FilterPredicate("handle", Query.FilterOperator.EQUAL, handle));
        Entity user = datastore.prepare(query).asSingleEntity();

        if (user == null) {
            throw new NotFoundException("User not found");
        }

        // Remove sensitive data before returning the user (only in the response do not update the datastore)
        user.removeProperty("email");

        return new UserDTO(user, true, 0);
    }

    @ApiMethod(name = "users.createFake", httpMethod = "post", path = "users/fake",
        clientIds = { Constants.WEB_CLIENT_ID },
        audiences = { Constants.WEB_CLIENT_ID },
        scopes = { Constants.EMAIL_SCOPE, Constants.PROFILE_SCOPE })
    public UserDTO createFakeUser(User reqUser, HttpServletRequest request, Map<String, Object> reqBody) throws UnauthorizedException, EntityNotFoundException {

        // Query the datastore to get the user by its ID
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        // If the user is already in the datastore
        try {
            // Get entity by key id
            Entity user = datastore.get(KeyFactory.createKey("User", (String) reqBody.get("id")));
            return new UserDTO(user, false, 0);
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
            // - fullBatches : number of followers batches full
            // - followers : An UserFollowers entity which contains multiple lists of followers
 
            Entity newUser = new Entity("User", (String) reqBody.get("id"));

            // Set the user properties
            newUser.setProperty("id", (String) reqBody.get("id"));
            newUser.setProperty("email", (String) reqBody.get("email"));
            newUser.setProperty("handle", ((String) reqBody.get("email")).split("@")[0]);
            newUser.setProperty("name", (String) reqBody.get("name"));
            newUser.setProperty("pictureURL", (String) reqBody.get("pictureURL")); 
            newUser.setProperty("createdAt", new Date());
            newUser.setProperty("updatedAt", new Date());

            ArrayList<Integer> batchIndex = new ArrayList<Integer>();

            // Create the UserFollowers entity

            // A for loop that loops 3 times
            for (int i = 0; i < 5; i++) {
                // Key  
                String userFollowersId = String.valueOf(i) + "-" + ((String) reqBody.get("id"));
                Key key = KeyFactory.createKey(newUser.getKey(),"UserFollower", userFollowersId);

                // Create the UserFollowers entity
                Entity userFollowers = new Entity(key);
                userFollowers.setProperty("id", userFollowersId);

                batchIndex.add(0);

                // The user's first follower is itself so it can see its own posts in its timeline
                if(i == 0) {
                    List<String> list = new ArrayList<>();
                    list.add((String) reqBody.get("id"));

                    userFollowers.setProperty("batch", list);
                    userFollowers.setProperty("size", 1);
                } else {
                    userFollowers.setProperty("batch", new ArrayList<String>());
                    userFollowers.setProperty("size", 0);
                }

                userFollowers.setProperty("parentId", (String) reqBody.get("id"));
                userFollowers.setProperty("updatedAt", new Date());

                // Put the UserFollowers entity in the datastore
                datastore.put(userFollowers);
            }

            newUser.setProperty("batchIndex", batchIndex);

            // Put the entities in the datastore
            datastore.put(newUser);

            return new UserDTO(newUser, false, 0);
        }
    }
}
