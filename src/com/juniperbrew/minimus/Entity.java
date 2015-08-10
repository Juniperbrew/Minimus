package com.juniperbrew.minimus;

import com.juniperbrew.minimus.NetworkEntity;

/**
 * Created by Juniperbrew on 7.8.2015.
 */
public class Entity extends NetworkEntity {
    public double deltaX;
    public double deltaY;

    public Entity(int id, float x, float y, int team) {
        super(id,x,y,team);
    }

    public Entity(NetworkEntity e) {
        super(e);
    }

    public void moveTo(double x, double y){
        deltaX = 0;
        deltaY = 0;
        super.moveTo(x,y);
    }
}
