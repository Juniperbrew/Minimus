package com.juniperbrew.minimus;

import com.badlogic.gdx.math.Rectangle;

/**
 * Created by Juniperbrew on 30.7.2015.
 */

public abstract class Powerup {

    public Rectangle bounds;

    public Powerup() {
    }

    public Powerup(float x, float y, float width, float height) {
        bounds = new Rectangle(x,y,width,height);
    }
}
