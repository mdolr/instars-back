package com.tinyinsta.dto;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

import java.util.*;
import com.google.appengine.api.datastore.*;

public class TimelineDTO {
  public String previous;
  public String next;
  public List<PostDTO> posts;

  public TimelineDTO(List<PostDTO> posts, String previous, String next) { 
      this.posts = posts;
      this.previous = previous;
      this.next = next;
  }
}