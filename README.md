# Instars

_A scalable API Backend built on Google Datastore and Google App Engine_

[Project frontend available here (separate repo)](https://github.com/mdolr/instars-front)

Demo available here:

- [Front](https://front-dot-tinyinsta-web.oa.r.appspot.com/)
- [Direct API access](https://tinyinsta-web.oa.r.appspot.com/)
- [API Endpoints portal](https://endpointsportal.tinyinsta-web.cloud.goog/docs/tinyinsta-web.appspot.com/1/introduction)

# Installation

## Setup

### Prerequisites

| Software                 | version     |
| ------------------------ | ----------- |
| Java                     | 11.0        |
| Maven                    | 3.8         |
| Google Cloud             | SDK 363.0.0 |
| app-engine-java          | 1.9.91      |
| app-engine-python        | 1.9.96      |
| bq                       | 2.0.71      |
| cloud-datastore-emulator | 2.1.0       |
| core                     | 2021.10.29  |
| gsutil                   | 5.4         |

### Service account

Add the following permissions to a custom service account you created + your App Engine service account:

- Service Account Token Creator
- App Engine Admin
- Storage Admin
- Storage Object Admin
- Storage Object Creator
- Storage Object Viewer
- Storage Transfer Viewer

Once the account has been created, create a keypair and download it as a JSON file placed at the root of this folder

### OAuth2

Configurer l'écran de consentement OAuth, ajouter son e-mail en tant qu'utilisateur test.

Créer une paire de credentials OAuth 2.0 avec les caractéristiques suivantes pour le champ URIs:

- https://localhost
- http://localhost
- https://localhost:3000
- http://localhost:3000
- https://front-dot-{{YOUR_PROJECT_ID}}.oa.r.appspot.com
- http://front-dot-{{YOUR_PROJECT_ID}}.oa.r.appspot.com

Ajouter le Client ID dans les fichiers suivants:

- `openapi.yaml` dans la propriété `x-google-audience`
- `src/main/java/com/tinyinsta/common/Constants.java` dans la propriété `WEB_CLIENT_ID`

### Cloud storage Bucket CORS Configuration

```bash
printf '[
  {
    "maxAgeSeconds": 60,
    "method": [
      "GET",
      "HEAD",
      "DELETE",
      "POST",
      "PUT"
    ],
    "origin": [
      "http://localhost:3000",
      "http://localhost",
      "https://localhost:3000",
      "https://localhost",
      "localhost",
      "localhost:3000",
      "http://front-dot-{{YOUR_PROJECT_ID}}.oa.r.appspot.com",
      "https://front-dot-{{YOUR_PROJECT_ID}}.oa.r.appspot.com"
    ],
    "responseHeader": [
      "Content-Type",
      "Access-Control-Allow-Origin",
      "x-goog-resumable"
    ]
  }
]' > cors.json

gsutil cors set cors.json gs://your_bucket_id
```

### Download

```bash
git clone mdolr:instars-back
```

## Run locally

### Set environment credentials

```bash
# Windows:
set GOOGLE_APPLICATION_CREDENTIALS=/ABSOLUTE/PATH/TO/name_of_your_service_account_priv_key.json

# Mac / Linux
export GOOGLE_APPLICATION_CREDENTIALS=/ABSOLUTE/PATH/TO/name_of_your_service_account_priv_key.json
```

### Start the development server

```bash
mvn clean appengine:run # starts the server on port 8080
```

- Web server is accessible at [localhost:8080](http://localhost:8080)

- API Explorer is situated at [localhost:8080/\_ah/api/explorer](http://localhost:8080/_ah/api/explorer)

- Datastore emulator is situated at [localhost:8080/\_ah/admin](http://localhost:8080/_ah/admin)

## Deploy on Google App Engine

### Build and deploy the build to Google App Engine

```bash
mvn clean appengine:deploy
```

### Generate the OpenAPI file to generate the Google Endpoints portal

```bash
mvn endpoints-framework:openApiDocs
gcloud endpoints services deploy target/openapi-docs/openapi.json
```

# Our approach to scaling an instagram clone with Google App Engine & Google Datastore

## Objectives

- Requests should be responsive (less than 500ms accounting network latency)
- Queries complexity should be proportional to the size of the results (only query what you need / no scans)
- The app should scale and support contention and concurrent requests as much as possible

## Kinds

Most of the properties on the kinds are self-explanatory. The only one that might be a bit complex and requires some explanation is "batchIndex".

We're using the batchIndex to optimize our calculations of likes and followers, this index lets us keep track of which batch of UserFollower or PostLiker is full or not. Based on this and knowing the size of our batches we can easily determine the number of followers / likes of the respective entities without having to iterate over every batch.

We can also quickly get a random batch which is not full to add new likes / new followers.

We can create new batches dynamically based on contention while always keep a minimum of a given number of "available batches" (=non-full batches).

### Post

![Kind Post](https://media.discordapp.net/attachments/903607396054741062/914830587452325938/unknown.png?width=598&height=669)

### PostLiker

![Kind PostLiker](https://media.discordapp.net/attachments/903607396054741062/914830836040351754/unknown.png)

### PostReceiver

![Kind PostReceiver](https://media.discordapp.net/attachments/903607396054741062/914831910948204544/unknown.png)

### PostReceiver

![Kind PostReceiver](https://media.discordapp.net/attachments/903607396054741062/914831910948204544/unknown.png)

### User

![Kind User](https://media.discordapp.net/attachments/903607396054741062/914830512865042483/unknown.png?width=493&height=670)

### UserFollower

![Kind UserFollower](https://media.discordapp.net/attachments/903607396054741062/914836052978577428/unknown.png)

## Keys definition

### User keys

The most natural choice for user keys was to use the Google Account User ID which is given to us by Google App Engine when an Authorization token is included in the headers

### Post keys

Because of the datastore limitations we can't use a sort method on our queries so we need to rely on the datastore sorting entities by ascending keys by default.

Another problem is that we need to deal with contention, so we need to split posts in different buckets.

We achieved that by formatting the post's key as `{{BUCKET_NUMBER}}-{{SUBSTRACTED_TIMESTAMP}}` where :

- BUCKET_NUMBER is a random integer between 0 and the number of buckets we want
- SUBSTRACTED_TIMESTAMP is the maximum long supported in Java minus the current timestamp

By choosing such a format for our keys the datastore auto-sorts the posts in a descending order which is handy to build our timeline

### Post receivers

The key is built of an ancestor key set to the parent Post, and the default random Google key for the post receiver entity

### Post likers & User Followers

The keys for both of these entities are built in the same way which is `{{BUCKET_NUMBER}}-{{(USER or POST)_ID}}`

By default we create BUCKET_COUNT (e.g 5) entities whenever a post or user is created in the Datastore. And then as the different batches fill we create more buckets in order to always keep at least non-filled BUCKETS_COUNT batches.

## Cloud storage upload

Because Google App Engine has a timeout on requests, and the upload can take a long time depending on the user's connection, we have decided to shift the upload process directly to Google Cloud Storage without processing the image on our backend

We have made use of [Google recommendations](https://cloud.google.com/blog/products/storage-data-transfer/uploading-images-directly-to-cloud-storage-by-using-signed-url) on setting up uploads via signed URL. The upload follows the following flow:

![Upload flow](https://cdn.discordapp.com/attachments/458197576676671488/914133817135071252/upload_flow_diagram.png)
_Diagram generated with [diagram.codes](https://www.diagram.codes/)_

# Load-testing results

## Likes per seconds on a single post

The goal here was to measure the throughput our system could handle on a single post. The likes per second load-testing was conducted using [Siege](https://github.com/JoeDog/siege) with the following settings:

- Time duration: 30 seconds
- Concurrent users: 200 users

We have conducted the tests with varying batch sizes, the "Hit rate" describes the percentage of requests that did not receive a 5XX HTTP status code from the server.
| MAX_BATCH_SIZE | Average likes per second | Hit rate |
|----------------|--------------------------|-----|
| 39000 | ~45 likes/s | 100% |
| 50 | 80-95 likes/s | 98% |
| 25 | ~45 likes/s | 96% |

The hit rate is under 100% with smaller batch sizes as the batch size limit is reached more often which leads to queries updating 2 entity groups at once happening more often.

We consider it a relatively small and acceptable error rate but it means there are errors nonetheless.

## Timeline with varying pagination size

We tried to see how our timeline generation system scaled with an increasing pagination size, of course the sizes tested here would never be used in production as it makes no sense to load that much posts at once from an user experience standpoint.

### Results on local server

![post a picture performance](https://media.discordapp.net/attachments/893492288016244816/914812451080527882/Average_time_in_ms_to_load_n_posts_in_one_request_without_pagination_local.png)

### Results on the deployed appengine

![post a picture performance](https://media.discordapp.net/attachments/893492288016244816/914814201296453652/Average_time_in_ms_to_load_n_posts_in_one_request_without_pagination_deployed.png)

As expected our app doesn't scale with an increasing pagination size, this is not surprising given the architectural choices that we made

In production we will probably want to use a pagination size ranging from 5 to 10 posts depending on how fast the average user scrolls

## Post a picture varying followers count

- Tests were ran locally and on the deployed appengine.
- The number of followers varried between (10, 100 and 500 followers)
- Results are based on an average of 30 requets.

### Results on local server

![post a picture performance](https://media.discordapp.net/attachments/893492288016244816/913911296880160808/Post_a_picture_performance_per_number_of_Followers_local.png)

- The average total time fluctuates around 330ms
- The results aren't affected by the increase of the number of Post_a_picture_performance_per_number_of_Followers_local

### Results on the deployed appengine

![post a picture performance](https://media.discordapp.net/attachments/893492288016244816/913912606975209502/Post_a_picture_performance_per_number_of_Followers_Deployed.png)

- The average total time fluctuates around 500ms
- The "Post" query has become significantly more time consuming

## Possible improvements

- Don't compute the number of likes and followers when a new likes / follower is added and only increment by 1 on the client side, the exact number of likes and followers will be returned once the user reloads the page
- Raise the number of minimum available buckets and have a smarter way to create a lot of buckets in case of contention

# Contributeurs

|                                                    |                |
| -------------------------------------------------- | -------------- |
| [@mdolr](https://github.com/mdolr)                 | Maxime DOLORES |
| [@jjbes](https://github.com/jjbes)                 | Julien AUBERT  |
| [@NabilOulbaz](https://github.com/NabilOulbaz)     | Nabil OULBAZ   |
| [@RobinPourtaud](https://github.com/RobinPourtaud) | Robin POURTAUD |
