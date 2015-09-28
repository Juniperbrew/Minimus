package com.juniperbrew.minimus;

import com.badlogic.gdx.math.Rectangle;

/**
 * Created by Juniperbrew on 28.9.2015.
 */
public class MapObject {

    public static final int SHOPKEEPER = 1;
    public static final int MESSAGE = 2;
    public static final int MAP_EXIT = 3;

    Rectangle bounds;
    String image;
    int type;

    public MapObject(Rectangle bounds, String image, int type) {
        this.bounds = bounds;
        this.image = image;
        this.type = type;
    }
}
