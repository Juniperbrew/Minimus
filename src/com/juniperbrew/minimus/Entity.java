package com.juniperbrew.minimus;

import com.badlogic.gdx.math.Rectangle;

import java.awt.geom.Rectangle2D;

/**
 * Created by Juniperbrew on 23.1.2015.
 */
public class Entity {
    public int id;
    private float x;
    private float y;
    public int width;
    public int height;
    private int health;
    public int maxHealth;
    private int rotation;
    public int slot1Weapon = 0;
    public int slot2Weapon = 1;
    public int team;

    public Entity(){
        this(-1,-1,-1,-1);
    }

    public Entity(Entity e){
        id = e.id;
        x = e.x;
        y = e.y;
        width = e.width;
        height = e.height;
        health = e.health;
        maxHealth = e.maxHealth;
        rotation = e.rotation;
        slot1Weapon = e.slot1Weapon;
        slot2Weapon = e.slot2Weapon;
        team = e.team;
    }

    public Entity(int id, float x, float y, int width, int height, int health, int maxHealth, Enums.Heading heading, int team){
        this.id = id;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.health = health;
        this.maxHealth = maxHealth;
        this.team = team;
    }

    public Entity(int id, float x, float y, int team) {
        this.id = id;
        this.x = x;
        this.y = y;
        width = 50;
        height = 50;
        health = 100;
        maxHealth = 100;
        rotation = -90;
        this.team = team;
    }

    public Enums.Heading getHeading(){
        if(rotation>=-45 || rotation <45){
            return Enums.Heading.EAST;
        }else if(rotation>=45 && rotation<135){
            return Enums.Heading.NORTH;
        }else if(rotation>=135 || rotation<-135){
            return Enums.Heading.WEST;
        }else if(rotation>=-135 && rotation<-45){
            return Enums.Heading.SOUTH;
        }
        return null;
    }
    public void setRotation(int degrees){
        this.rotation = degrees;
    }

    public int getRotation(){
        return rotation;
    }

    public void setHeading(Enums.Heading heading){
        switch(heading){
            case EAST: rotation = 0; break;
            case SOUTH: rotation = -90; break;
            case WEST: rotation = 180; break;
            case NORTH: rotation = 90; break;
        }
    }
    public float getX(){
        return x;
    }
    public float getY(){
        return y;
    }

    public float getCenterX(){
        return x+width/2;
    }
    public float getCenterY(){
        return y+height/2;
    }
    public int getHealth(){
        return health;
    }

    public void moveTo(float newX, float newY){
        x = newX;
        y = newY;
    }

    public void setHealth(int health){
        this.health = health;
    }

    public Rectangle getGdxBounds(){
        return new Rectangle(x,y,width,height);
    }

    public Rectangle2D.Float getJavaBounds(){
        return new Rectangle2D.Float(x,y,width,height);
    }
}
