package com.juniperbrew.minimus;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

/**
 * Created by Juniperbrew on 7.8.2015.
 */
public class Entity extends NetworkEntity {

    Vector2 movement = new Vector2();

    public Entity(int id, float x, float y, int team) {
        super(id,x,y,team);
    }

    public Entity(NetworkEntity e) {
        super(e);
    }

    public void moveTo(double x, double y){
        super.moveTo(x,y);
    }

    public void addMovement(Vector2 movement){
        this.movement.add(movement);
    }

    public void applyMovement(){

        Rectangle bounds = getGdxBounds();
        bounds.setX(bounds.getX()+ movement.x);
        if(SharedMethods.checkMapCollision(bounds)){
            bounds.setX(bounds.getX()- movement.x);
            movement.x=0;
            //TODO reduce movement so entity hits the wall
        }
        bounds.setY(bounds.getY() + movement.y);
        if(SharedMethods.checkMapCollision(bounds)){
            bounds.setY(bounds.getY() - movement.y);
            movement.y=0;
            //TODO reduce movement so entity hits the wall
        }

        move(movement.x, movement.y);
        movement.setZero();
    }
}
