package com.juniperbrew.minimus;

import com.badlogic.gdx.math.MathUtils;

import java.awt.geom.Line2D;
import java.util.ArrayList;

/**
 * Created by Juniperbrew on 28.6.2015.
 */
public class Projectile {

    float range;
    float totalDistanceTraveled;
    float velocity;
    int angle; //deg
    Line2D.Float path;
    public boolean destroyed;
    public int ownerID;

    public Projectile(float startX, float startY, float range, float velocity, int angle, int ownerID) {
        float targetX = startX+MathUtils.cosDeg(angle)*range;
        float targetY = startY+MathUtils.sinDeg(angle)*range;
        path = new Line2D.Float(startX,startY,targetX,targetY);
        this.range = range;
        this.velocity = velocity;
        this.angle = angle;
        this.ownerID = ownerID;
    }

    public Line2D.Float move(float delta){
        float startX = getX();
        float startY = getY();
        float distanceTraveled = velocity * delta;
        totalDistanceTraveled += distanceTraveled;

        if(totalDistanceTraveled>range){
            totalDistanceTraveled = range;
            destroyed = true;
        }

        return new Line2D.Float(startX,startY,getX(),getY());
    }

    public float getX(){
        return path.x1+MathUtils.cosDeg(angle)*totalDistanceTraveled;
    }
    public float getY(){
        return path.y1+MathUtils.sinDeg(angle)*totalDistanceTraveled;
    }
}
