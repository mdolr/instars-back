package com.tinyinsta.dto;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

import java.util.*;
import com.google.appengine.api.datastore.*;

public class PaginationDTO {
  public String previous;
  public String next;
  public List<PostDTO> items;

  public PaginationDTO(List<PostDTO> posts, String previous, String next) { 
      this.items = posts;
      this.previous = previous;
      this.next = next;
  }
}