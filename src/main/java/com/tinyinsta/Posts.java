package com.tinyinsta;

import com.google.api.server.spi.auth.common.User;
import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.response.ConflictException;
import com.google.api.server.spi.response.NotFoundException;
import com.google.api.server.spi.response.UnauthorizedException;
import com.google.appengine.api.datastore.*;
import com.google.cloud.storage.*;
import com.tinyinsta.common.AvailableBatches;
import com.tinyinsta.common.Constants;
import com.tinyinsta.common.ExistenceQuery;
import com.tinyinsta.common.RandomGenerator;
import com.tinyinsta.dto.PostDTO;
import com.tinyinsta.entity.PostLikers;
import com.tinyinsta.entity.PostReceivers;

import javax.inject.Named;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Api(name = "tinyinsta", version = "v1", scopes = { Constants.EMAIL_SCOPE }, clientIds = { Constants.WEB_CLIENT_ID })
public class Posts {
    @ApiMethod(name = "posts.getAll", httpMethod = "get", path = "posts/{authorId}")
    public ArrayList<PostDTO> getAll(User reqUser, @Named("authorId") String authorId) throws EntityNotFoundException, ConflictException {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        //Check if user exists
        datastore.get(KeyFactory.createKey("User", reqUser.getId()));

        Query q = new Query("Post");
        q.setFilter(new Query.FilterPredicate("authorId", Query.FilterOperator.EQUAL, authorId));

        PreparedQuery pq = datastore.prepare(q);
        List<Entity> results = pq.asList(FetchOptions.Builder.withDefaults());

        ArrayList<PostDTO> posts = new ArrayList<>();
        
        // Recover likes and user info
        // Use posts.keySet() transform to array list and then sort the array list or smth
        for(Entity post : results){
            AvailableBatches availableBatches= new AvailableBatches(post, "PostLiker");
            // Count all available batches size + completed batches number * batch max size
            int likesCount = availableBatches.getSizeCount()+(availableBatches.getFullBatchesCount() * Constants.MAX_BATCH_SIZE);
            post.setProperty("likes", likesCount);

            Boolean hasLiked = new ExistenceQuery().check("PostLiker", post.getKey(), reqUser.getId());
            post.setProperty("hasLiked", hasLiked);

            Entity author = datastore.get(KeyFactory.createKey("User", post.getProperty("authorId").toString()));
            posts.add(new PostDTO(post, author, likesCount));
        }

        Collections.sort(posts, new Comparator<PostDTO>() {
            public int compare(PostDTO a, PostDTO b) {
                return b.createdAt.compareTo(a.createdAt);
            }});

        return posts;
    }
    
    @ApiMethod(name = "posts.uploadPost", httpMethod = "post", path = "posts",
            clientIds = { Constants.WEB_CLIENT_ID },
            audiences = { Constants.WEB_CLIENT_ID },
            scopes = { Constants.EMAIL_SCOPE, Constants.PROFILE_SCOPE })
    public PostDTO uploadPost(
            User reqUser,
            Map<String, Object> reqBody
    ) throws UnauthorizedException, EntityNotFoundException {
        if (reqUser == null) {
            throw new UnauthorizedException("Authorization required");
        }

        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        Entity user = datastore.get(KeyFactory.createKey("User", reqUser.getId()));

        String projectId = "tinyinsta-web";
        String bucketName = "instars-23pnm1d4";

        int randomBucket = new RandomGenerator().get(0, Constants.TIMELINE_BUCKETS - 1);

        long maxLong = Long.MAX_VALUE;

        String postId = String.valueOf(randomBucket) + "-" + String.valueOf(maxLong - new Date().getTime()); //UUID.randomUUID().toString();
        String pictureId = UUID.randomUUID().toString();

        String fileName = (String) reqBody.get("fileName");
        String fileType = (String) reqBody.get("fileType");
        
        String fileExtension = fileType.substring(fileType.lastIndexOf("/") + 1); //fileName.substring(fileName.lastIndexOf('.') + 1);
        String uploadFileName = pictureId + "." + fileExtension;

        Storage storage = StorageOptions.newBuilder().setProjectId(projectId).build()
                .getService();

        // Define Resource
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, uploadFileName)).build();

        // Generate Signed URL
        Map<String, String> extensionHeaders = new HashMap<>();
        extensionHeaders.put("Content-Type", (String) reqBody.get("fileType"));
        //extensionHeaders.put("Access-Control-Allow-Origin", "*");

        URL url = storage.signUrl(blobInfo, 15, TimeUnit.MINUTES, Storage.SignUrlOption.httpMethod(HttpMethod.PUT),
                Storage.SignUrlOption.withExtHeaders(extensionHeaders), Storage.SignUrlOption.withV4Signature());
        
        Transaction txn = datastore.beginTransaction();

        try {
            Key postKey = KeyFactory.createKey("Post", postId);

            Entity post = new Entity(postKey);
            post.setProperty("id", postId);
            post.setProperty("pictureId", pictureId);
            post.setProperty("pictureName", uploadFileName);
            post.setProperty("mediaURL", "https://storage.googleapis.com/" + bucketName + "/" + uploadFileName);// TODO: Change url to store url
            post.setProperty("authorId", user.getProperty("id").toString());
            post.setProperty("description", reqBody.get("description"));
            post.setProperty("createdAt", new Date());
            post.setProperty("published", false);
            post.setProperty("batchIndex", null);
            
            datastore.put(post);
            txn.commit();

            post.setProperty("uploadURL", url.toString()); // only return it once don't store it in the datastore
            return new PostDTO(post, user, 0);
        } finally {
            if (txn.isActive()) {
                txn.rollback();
            }
        }
    }

    @ApiMethod(name = "posts.publishPost", httpMethod = "post", path = "posts/{id}/publish",
            clientIds = { Constants.WEB_CLIENT_ID },
            audiences = { Constants.WEB_CLIENT_ID },
            scopes = { Constants.EMAIL_SCOPE, Constants.PROFILE_SCOPE })
    public PostDTO publishPost(
              User reqUser,
              @Named("id") String postId
    ) throws UnauthorizedException, EntityNotFoundException, NotFoundException {
        if (reqUser == null) {
            throw new UnauthorizedException("Authorization required");
        }
  
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
  
        Entity user = datastore.get(KeyFactory.createKey("User", reqUser.getId()));
        Entity post = datastore.get(KeyFactory.createKey("Post", postId));

        if (!post.getProperty("authorId").toString().equals(reqUser.getId().toString())) {
            throw new UnauthorizedException("Post author doesn't match user");
        }

        String projectId = "tinyinsta-web";
        String bucketName = "instars-23pnm1d4";

        String objectName = (String) post.getProperty("pictureName");

        Storage storage = StorageOptions.newBuilder().setProjectId(projectId).build()
                .getService();

        Bucket bucket = storage.get(bucketName);
        com.google.cloud.storage.Blob blob = storage.get(bucketName, objectName);

        Boolean objectExists = (blob != null && blob.exists());
        
        if(!objectExists) {
            throw new NotFoundException("Media not found");
        }

        //Transaction txn = datastore.beginTransaction();

        try {
            new PostReceivers().createEntity(user, post);
            
            int nbBuckets = Constants.MAX_BUCKETS_NUMBER;

            ArrayList<Integer> batchIndex = new ArrayList<Integer>();

            for (int i = 0; i < nbBuckets; i++) {
                new PostLikers().createEntity(post, batchIndex.size());
                batchIndex.add(0);
            }

            post.setProperty("batchIndex", batchIndex);
            post.setProperty("published", true);

            //txn.commit();
        } finally {
            //if (txn.isActive()) {
                //txn.rollback();
            //} else {
                datastore.put(post);
            //}
        }

        return new PostDTO(post, user, 0);
    }
}
