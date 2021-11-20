package com.tinyinsta.dto;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

import java.util.*;
import com.google.appengine.api.datastore.*;

public class TimelineDTO {
  public String before;
  public String after;
  public List<PostDTO> posts;

  public TimelineDTO(List<PostDTO> posts, String before, String after) { 
      this.posts = posts;
      this.before = before;
      this.after = after;
  }
}