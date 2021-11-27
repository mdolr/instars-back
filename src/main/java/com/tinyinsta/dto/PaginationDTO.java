package com.tinyinsta.dto;

import java.util.List;

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