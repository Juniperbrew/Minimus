package com.juniperbrew.minimus;

import com.badlogic.gdx.math.Rectangle;

/**
 * Created by Juniperbrew on 30.7.2015.
 */

public class Powerup {

    final static public int HEALTH = 1;
    final static public int AMMO = 2;
    final static public int WEAPON = 3;

    public int type;
    public int typeModifier;
    public int value;
    /*public float x;
    public float y;
    public float width;
    public float height;*/
    public Rectangle bounds;

    public Powerup() {

    }

    public Powerup(float x, float y, int type, int value) {
        this(x,y,25,25,type,1,value);
    }

    public Powerup(float x, float y, float width, float height, int type, int typeModifier, int value) {
        bounds = new Rectangle(x,y,width,height);
        /*this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;*/
        this.type = type;
        this.value = value;
        this.typeModifier = typeModifier;
    }
}
