package com.tinyinsta.dto;

import java.util.Date;
import java.util.HashMap;

import java.util.*;
import com.google.appengine.api.datastore.*;

public class PostDTO {
  public String id;
  public Date createdAt;
  public String title;
  public String description;
  public String mediaURL;
  public String authorId;
  public long likes;
  public String uploadURL;
  public Boolean published;
  public UserDTO author;

  public PostDTO(Entity post, Entity user, long likes) { 
    this.id = (String) post.getProperty("id");
    this.createdAt = (Date) post.getProperty("createdAt");
    this.title = (String) post.getProperty("title");
    this.description = (String) post.getProperty("description");
    this.mediaURL = (String) post.getProperty("mediaURL");
    this.authorId = (String) post.getProperty("authorId");
    this.likes = likes;
    this.published = (Boolean) post.getProperty("published");
    this.uploadURL = (String) post.getProperty("uploadURL");
    this.author = new UserDTO(user, true, 0);
  }
}