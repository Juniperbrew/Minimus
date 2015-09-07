package com.juniperbrew.minimus.server;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

/**
 * Created by Juniperbrew on 26.8.2015.
 */
public class Knockback {

    private Vector2 frictionAcceleration;
    int id;
    Vector2 velocity;
    float duration;
    Vector2 startVelocity;
    int updateCounter;

    private final float mass = 100;
    private final float friction = 20f;
    private final float g = 9.81f;

    boolean expired;

    public Knockback(int id, Vector2 velocity){
        this.velocity = velocity;
        startVelocity = velocity.cpy();
        Vector2 frictionForce = new Vector2(-1*friction*mass*g,0);
        frictionForce.setAngle(velocity.angle()-180);
        frictionAcceleration = frictionForce.cpy().scl(1/mass);
        this.id = id;
    }

    public Vector2 getMovement(float delta){

        velocity.add(frictionAcceleration.cpy().scl(delta));
        Vector2 movement = velocity.cpy().scl(delta);
        duration += delta;
        updateCounter++;

        if(velocity.hasSameDirection(frictionAcceleration)){
            expired = true;
        }
        return movement;
    }

    public boolean isExpired(){
        return expired;
    }
}
