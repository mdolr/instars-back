package com.tinyinsta.common;

public class RandomGenerator {
    public int get(int min, int max) {
        int range = max - min + 1;
        return (int) Math.floor(Math.random()*range)+min;
    }
}
