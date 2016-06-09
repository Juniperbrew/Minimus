package com.juniperbrew.minimus;

import com.badlogic.gdx.math.*;

import java.util.HashSet;


/**
 * Created by Juniperbrew on 5.9.2015.
 */
public class Particle {


    //FROM PROJECTILE
    public int ownerID;
    public int team;
    public int damage;
    public float knockback;
    public String onDestroy;
    public boolean explosionKnockback;
    public HashSet<Integer> entitiesHit = new HashSet<>();
    //###########

    public String name;

    public float rotation;
    float originX;
    float originY;
    Polygon bounds;


    //Sprite sprite;
    //Animation animation;
    float stateTime;
    float duration;
    long spawnTime;
    float totalDistanceTraveled;
    public boolean destroyed;
    float range;
    float velocity;
    float friction;
    public boolean bounce;
    public boolean ignoreMapCollision;
    public boolean ignoreEntityCollision;
    public boolean dontDestroyOnCollision;
    public float hitboxScaling;
    public boolean stopOnCollision;
    public boolean stopped;

    public Particle(){};

    public Particle(ProjectileDefinition def, Rectangle rect){
        this(def, rect, 0, rect.x + rect.width / 2, rect.y + rect.height / 2, 0);
    }

    public Particle(ProjectileDefinition def, Rectangle rect, float rotation, float originX, float  originY){
        this(def,rect,rotation,originX,originY,def.velocity);
    }

    public Particle(ProjectileDefinition def, Rectangle rect, float rotation, float originX, float  originY, int ownerID, int team){
        this(def,rect,rotation,originX,originY,def.velocity, ownerID, team);
    }

    public Particle(ProjectileDefinition def, Rectangle rect,  float rotation, float originX, float originY, float velocity){
        this(def,rect,rotation,originX,originY,velocity,-1,-1);
    }

    public Particle(ProjectileDefinition def, Rectangle rect, int ownerID, int team) {
        this(def,rect, 0, rect.x + rect.width / 2, rect.y + rect.height / 2, 0, ownerID, team);
    }

    public Particle(ProjectileDefinition def, Rectangle rect,  float rotation, float originX, float originY, float velocity, int ownerID, int team){
        name = def.name;
        spawnTime = System.nanoTime();
        float[] vertices = {rect.x, rect.y,
                rect.x+rect.width, rect.y,
                rect.x+rect.width, rect.y + rect.height,
                rect.x, rect.y + rect.height};
        bounds = new Polygon(vertices);

        bounds.setOrigin(originX, originY);
        bounds.setRotation(rotation);

        this.originX = originX;
        this.originY = originY;
        this.rotation = rotation;
        this.velocity = velocity;
        this.range = def.range;
        this.friction = def.friction;
        this.bounce = def.bounce;
        this.ignoreMapCollision = def.ignoreMapCollision;
        this.ignoreEntityCollision = def.ignoreEntityCollision;
        this.dontDestroyOnCollision = def.dontDestroyOnCollision;
        this.hitboxScaling = def.hitboxScaling;
        this.stopOnCollision = def.stopOnCollision;
        setDuration(def.duration);


        //FROM PROJECTILE
        this.ownerID = ownerID;
        this.team = team;
        this.damage = def.damage;
        knockback = def.knockback;
        onDestroy = def.onDestroy;
        explosionKnockback = def.explosionKnockback;
        //#############
    }

    public void setDuration(float duration){
        this.duration = duration;
    }


    public void update(float delta) {
        if(friction!=0){
            velocity -= friction*delta;
            if(velocity<0){
                velocity = 0;
            }
        }
        if(!stopped){
            float distance = velocity * delta;
            if (range>0 &&totalDistanceTraveled + distance > range) {
                moveDistance(range - totalDistanceTraveled);
                totalDistanceTraveled = range;
                destroyed = true;
            } else {
                moveDistance(distance);
                totalDistanceTraveled += distance;
            }
        }

        if(range==0&&System.nanoTime()-spawnTime > Tools.secondsToNano(duration)){
            destroyed = true;
        }
    }

    public float getLifeTime(){
        return Tools.nanoToSecondsFloat(System.nanoTime()-spawnTime);
    }

    private void moveDistance(float distance){
        float x = MathUtils.cosDeg(rotation)*distance;
        float y = MathUtils.sinDeg(rotation)*distance;
        if(!ignoreMapCollision){
            bounds.translate(x,0);
            Rectangle r = bounds.getBoundingRectangle();
            if(F.checkMapCollision(r)){
                if(bounce){
                    rotation = 180 - rotation;
                    bounds.setRotation(rotation);
                    return;
                }else if(stopOnCollision){
                    stopped = true;
                    return;
                }else if(!dontDestroyOnCollision){
                    destroyed = true;
                    return;
                }
            }
            bounds.translate(0,y);
            r = bounds.getBoundingRectangle();
            if(F.checkMapCollision(r)){
                if(bounce){
                    rotation = 0 - rotation;
                    bounds.setRotation(rotation);
                    return;
                }else if(stopOnCollision){
                    stopped = true;
                    return;
                }else if(!dontDestroyOnCollision){
                    destroyed = true;
                    return;
                }
            }
        }else{
            bounds.translate(x,y);
        }
    }

    public Polygon getBoundingPolygon(){
        Polygon b = bounds;
        if(hitboxScaling!=0){
            Vector2 center = new Vector2();
            getBoundingRectangle().getCenter(center);
            b.setOrigin(center.x,center.y);
            b.setScale(hitboxScaling,hitboxScaling);
        }
        return b;
    }

    public Rectangle getBoundingRectangle(){
        Rectangle b = bounds.getBoundingRectangle();
        if(hitboxScaling!=0){
            float scaledWidth = b.width*hitboxScaling;
            float scaledHeight = b.height*hitboxScaling;
            float xOffset = (b.width-scaledWidth)/2;
            float yOffset = (b.height-scaledHeight)/2;
            b.set(b.x + xOffset, b.y + yOffset, scaledWidth, scaledHeight);
        }
        return b;
    }
}
