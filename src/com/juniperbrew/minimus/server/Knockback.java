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

    private final float mass = 100;
    private final float friction = 8f;
    private final float g = 9.81f;

    boolean expired;

    public Knockback(int id, Vector2 velocity){
        this.velocity = velocity;
        Vector2 frictionForce = new Vector2(-1*friction*mass*g,0);
        frictionForce.setAngle(velocity.angle()-180);
        frictionAcceleration = frictionForce.cpy().scl(1/mass);
        this.id = id;
    }

    public Vector2 getMovement(float delta){

        float startAngle = velocity.angle();
        velocity.add(frictionAcceleration.cpy().scl(delta));
        float endAngle = velocity.angle();
        Vector2 movement = velocity.cpy().scl(delta);
        duration += delta;
/*
        System.out.println("####"+id+"####");
        System.out.println("Delta:"+delta);
        System.out.println("a:"+frictionAcceleration);
        System.out.println("Movement:"+movement);
        System.out.println("Start angle:"+startAngle);
        System.out.println("End angle:"+endAngle);*/
        if(startAngle!=endAngle){
            expired = true;
            System.out.println("Knockback duration:"+duration);
        }
        return movement;

        //Vector2 baseMovement = velocity.cpy().scl(delta);
        // Vector2 frictionMovement = frictionAcceleration.cpy().scl(0.5f * (float) Math.pow(delta, 2));
        /*System.out.println("Base movement:"+baseMovement);
        System.out.println("Friction movement:"+frictionMovement);

        if(frictionMovement.len()>baseMovement.len()){
            expired = true;
            return new Vector2(0,0);
        }else{
            return baseMovement.add(frictionMovement);
        }*/
    }

    public boolean isExpired(){
        return expired;
    }
}
