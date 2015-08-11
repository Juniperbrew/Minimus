package com.juniperbrew.minimus;

import com.badlogic.gdx.math.Polygon;
import com.badlogic.gdx.math.Rectangle;

import java.awt.geom.Rectangle2D;

/**
 * Created by Juniperbrew on 23.1.2015.
 */
public class NetworkEntity {
    public int id;
    private float x;
    private float y;
    public float width;
    public float height;
    private int health;
    public int maxHealth;
    private int rotation;
    public int slot1Weapon = 0;
    public int slot2Weapon = 1;
    private int team;
    public String image;

    public NetworkEntity(){
        this(-1,-1,-1,-1);
    }

    public NetworkEntity(NetworkEntity e){
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
        image = e.image;
    }

    public NetworkEntity(int id, float x, float y, float width, float height, int health, int maxHealth, int team, String image){
        this.id = id;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.health = health;
        this.maxHealth = maxHealth;
        this.team = team;
        this.image = image;
    }

    public NetworkEntity(int id, float x, float y, int team) {
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

/*
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
    }*/

    public void setRotation(int degrees){
        this.rotation = degrees;
    }

    public int getRotation(){
        return rotation;
    }

    /*
    public void setHeading(Enums.Heading heading){
        switch(heading){
            case EAST: rotation = 0; break;
            case SOUTH: rotation = -90; break;
            case WEST: rotation = 180; break;
            case NORTH: rotation = 90; break;
        }
    }*/

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

    protected void move(double deltaX, double deltaY){
        x += deltaX;
        y += deltaY;
    }

    public void moveTo(double x, double y){
        this.x = (float) x;
        this.y = (float) y;
    }

    public void setHealth(int health){
        if(health > maxHealth){
            this.health = maxHealth;
        }else{
            this.health = health;
        }
    }

    public int getTeam() {
        return team;
    }

    public void setTeam(int team) {
        this.team = team;
    }

    public Rectangle getGdxBounds(){
        return new Rectangle(x,y,width,height);
    }

    public Rectangle2D.Float getJavaBounds(){
        return new Rectangle2D.Float(x,y,width,height);
    }

    public Polygon getPolygonBounds(){
        float[] vertices = new float[8];
        vertices[0] = x;
        vertices[1] = y;
        vertices[2] = x+width;
        vertices[3] = y;
        vertices[4] = x+width;
        vertices[5] = y+height;
        vertices[6] = x;
        vertices[7] = y+height;
        return new Polygon(vertices);
    }

    public String toString(){
        return "ID:"+id+" X:"+x+" Y:"+y+" Width:"+width+" Height:"+height+" Health:"+health+"/"+maxHealth+" Image:"+image;
    }
}
