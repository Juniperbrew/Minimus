package com.juniperbrew.minimus;

/**
 * Created by Juniperbrew on 20.9.2015.
 */
public class HealthPack extends Powerup{
    public int value;

    public HealthPack() {
    }

    public HealthPack(float x, float y, float width, float height, int value){
        super(x,y,width,height);
        this.value = value;
    }
}
