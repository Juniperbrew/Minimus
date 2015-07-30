package com.juniperbrew.minimus;

/**
 * Created by Juniperbrew on 30.7.2015.
 */

public class Powerup {

    final static public int HEALTH = 1;
    final static public int AMMO = 2;
    final static public int weapon = 3;

    public int type;
    public int value;
    public int x;
    public int y;

    public Powerup() {

    }

    public Powerup(int x, int y, int type, int value) {
        this.x = x;
        this.y = y;
        this.type = type;
        this.value = value;
    }
}
