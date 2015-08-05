package com.juniperbrew.minimus;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;


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

    public Projectile(Rectangle bounds, int rotation, float originX, float originY, Color color, float range, float velocity, int ownerID, int team, int damage) {
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
            moveDistance(range-totalDistanceTraveled);
            totalDistanceTraveled = range;
            destroyed = true;
        }else{
            moveDistance(distance);
            totalDistanceTraveled += distance;
        }
    }

    public void moveDistance(float distance){
        float x = MathUtils.cosDeg(rotation)*distance;
        float y = MathUtils.sinDeg(rotation)*distance;
        bounds.translate(x,y);
    }

}
