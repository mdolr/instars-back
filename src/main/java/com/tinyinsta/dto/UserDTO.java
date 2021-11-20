package com.tinyinsta.res;

import java.util.Date;
import java.util.HashMap;
import java.util.*;
import com.google.appengine.api.datastore.*;

public class UserDTO {
  public String id;
  public Date createdAt;
  public Date updatedAt;
  public String name;
  public String handle;
  public String email;
  public String pictureURL;

  public UserDTO(Entity user, Boolean hideSensitiveData) { 
    this.id = (String) user.getProperty("id");
    this.createdAt = (Date) user.getProperty("createdAt");
    this.updatedAt = (Date) user.getProperty("updatedAt");
    this.name = (String) user.getProperty("name");
    this.handle = (String) user.getProperty("handle");
    this.email = (String) user.getProperty("email");
    this.pictureURL = (String) user.getProperty("pictureURL");

    if (hideSensitiveData) {
      this.email = null;
    }
  }
}