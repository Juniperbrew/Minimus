package com.juniperbrew.minimus;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;

/**
 * Created by Juniperbrew on 28.6.2015.
 */
public class Projectile extends AttackVisual{

    public boolean destroyed;
    public int ownerID;
    public int team;
    public int damage;

    float range;
    float velocity;

    float totalDistanceTraveled;

    public Projectile(Rectangle2D.Float bounds, int rotation, float originX, float originY, Color color, float range, float velocity, int ownerID, int team, int damage) {
        super(bounds, rotation, originX, originY, color);
        this.range = range;
        this.velocity = velocity;
        this.ownerID = ownerID;
        this.team = team;
        this.damage = damage;
    }

    public void move(float delta){
        float distance = velocity * delta;
        if(totalDistanceTraveled+distance>range){
            bounds.x += range-totalDistanceTraveled;
            totalDistanceTraveled = range;
            destroyed = true;
        }else{
            bounds.x += distance;
            totalDistanceTraveled += distance;
        }
    }

}
