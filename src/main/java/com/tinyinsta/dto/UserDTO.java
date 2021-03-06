package com.tinyinsta.dto;

import com.google.appengine.api.datastore.Entity;

import java.util.Date;

public class UserDTO {
  public String id;
  public Date createdAt;
  public Date updatedAt;
  public String name;
  public String handle;
  public String email;
  public String pictureURL;
  public long followers;
  public Boolean hasFollowed;

  public UserDTO(Entity user, Boolean hideSensitiveData, long followers) { 
    this.id = (String) user.getProperty("id");
    this.createdAt = (Date) user.getProperty("createdAt");
    this.updatedAt = (Date) user.getProperty("updatedAt");
    this.name = (String) user.getProperty("name");
    this.handle = (String) user.getProperty("handle");
    this.email = (String) user.getProperty("email");
    this.pictureURL = (String) user.getProperty("pictureURL");
    this.hasFollowed = (Boolean) user.getProperty("hasFollowed");
    this.followers = followers < 0 ? 0 : followers;

    if (hideSensitiveData) {
      this.email = null;
    }
  }
}