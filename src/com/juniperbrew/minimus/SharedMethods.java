package com.juniperbrew.minimus;

import com.badlogic.gdx.math.MathUtils;
import com.juniperbrew.minimus.server.ServerEntity;

import java.awt.geom.Line2D;
import java.awt.image.ConvolveOp;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by Juniperbrew on 24.6.2015.
 */
public class SharedMethods {

    ConVars conVars;
    int mapWidth;
    int mapHeight;
    Timer timer;

    public SharedMethods(ConVars conVars, int mapWidth, int mapHeight){
        this.conVars = conVars;
        this.mapWidth = mapWidth;
        this.mapHeight = mapHeight;
        timer = new Timer();
    }

    public Line2D.Float createAttackVisual(Entity e, final ArrayList<Line2D.Float> attackVisuals){

        float originX = e.getX()+e.width/2;
        float originY = e.getY()+e.height/2;

        int laserLength = 200;
        int laserStartDistanceX = e.width/2;
        int laserStartDistanceY = e.height/2;

        float sina = MathUtils.sinDeg(e.getRotation());
        float cosa = MathUtils.cosDeg(e.getRotation());

        originX += cosa*laserStartDistanceX;
        originY += sina*laserStartDistanceY;
        float targetX = originX + cosa*laserLength;
        float targetY = originY + sina*laserLength;

        final Line2D.Float hitScan = new Line2D.Float(originX,originY,targetX,targetY);
        attackVisuals.add(hitScan);
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                attackVisuals.remove(hitScan);
            }
        };
        timer.schedule(task,Tools.secondsToMilli(conVars.get("sv_attack_visual_timer")));
        return hitScan;
    }

    public void applyCompassInput(Entity e, Network.UserInput input){
        float deltaX = 0;
        float deltaY = 0;
        float velocity = (float)conVars.get("sv_velocity");

        setCompassHeading(e, input.buttons);

        if(input.buttons.contains(Enums.Buttons.UP)){
            deltaY = velocity *input.msec;
        }
        if(input.buttons.contains(Enums.Buttons.DOWN)){
            deltaY = -1* velocity *input.msec;
        }
        if(input.buttons.contains(Enums.Buttons.LEFT)){
            deltaX = -1* velocity *input.msec;
        }
        if(input.buttons.contains(Enums.Buttons.RIGHT)){
            deltaX = velocity *input.msec;
        }

        if(conVars.getBool("sv_check_map_collisions")) {
            if (e.getX() + e.width + deltaX > mapWidth) {
                deltaX = mapWidth - e.getX() - e.width;
            }
            if (e.getX() + deltaX < 0) {
                deltaX = 0 - e.getX();
            }
            if (e.getY() + e.height + deltaY > mapHeight) {
                deltaY = mapHeight - e.getY() - e.height;
            }
            if (e.getY() + deltaY < 0) {
                deltaY = 0 - e.getY();
            }
        }

        float newX = e.getX()+deltaX;
        float newY = e.getY()+deltaY;
        e.moveTo(newX,newY);
    }

    public void applyInput(Entity e, Network.UserInput input){
        float deltaX = 0;
        float deltaY = 0;
        float velocity = (float)conVars.get("sv_velocity");

        setRotation(e, input);

        float distance = velocity * input.msec;
        int direction = e.getRotation();

        if(input.buttons.contains(Enums.Buttons.W)){
            deltaX = MathUtils.cosDeg(direction)*distance;
            deltaY = MathUtils.sinDeg(direction)*distance;
        }
        if(input.buttons.contains(Enums.Buttons.S)){
            deltaX += MathUtils.cosDeg(direction-180)*distance;
            deltaY += MathUtils.sinDeg(direction-180)*distance;
        }
        if(input.buttons.contains(Enums.Buttons.A)){
            deltaX += MathUtils.cosDeg(direction+90)*distance;
            deltaY += MathUtils.sinDeg(direction+90)*distance;
        }
        if(input.buttons.contains(Enums.Buttons.D)){
            deltaX += MathUtils.cosDeg(direction-90)*distance;
            deltaY += MathUtils.sinDeg(direction-90)*distance;
        }


        if(conVars.getBool("sv_check_map_collisions")) {
            if (e.getX() + e.width + deltaX > mapWidth) {
                deltaX = mapWidth - e.getX() - e.width;
            }
            if (e.getX() + deltaX < 0) {
                deltaX = 0 - e.getX();
            }
            if (e.getY() + e.height + deltaY > mapHeight) {
                deltaY = mapHeight - e.getY() - e.height;
            }
            if (e.getY() + deltaY < 0) {
                deltaY = 0 - e.getY();
            }
        }

        float newX = e.getX()+deltaX;
        float newY = e.getY()+deltaY;
        e.moveTo(newX,newY);
    }

    public void setRotation(Entity e, Network.UserInput input){
        float mouseX = input.mouseX;
        float mouseY = input.mouseY;
        float playerX = e.getX() + e.width/2;
        float playerY = e.getY() + e.height/2;
        float deltaX = mouseX - playerX;
        float deltaY = mouseY - playerY;
        int degrees = (int) (MathUtils.radiansToDegrees*MathUtils.atan2(deltaY,deltaX));
        e.setRotation(degrees);
    }

    public void setCompassHeading(Entity e, EnumSet<Enums.Buttons> buttons){

        if(buttons.contains(Enums.Buttons.UP)){
            if(buttons.contains(Enums.Buttons.LEFT)&&e.getHeading()== Enums.Heading.WEST){
                e.setHeading(Enums.Heading.WEST);
            }else if(buttons.contains(Enums.Buttons.RIGHT)&&e.getHeading()== Enums.Heading.EAST){
                e.setHeading(Enums.Heading.EAST);
            }else{
                e.setHeading(Enums.Heading.NORTH);
            }
        }

        if(buttons.contains(Enums.Buttons.DOWN)){
            if(buttons.contains(Enums.Buttons.LEFT)&&e.getHeading()== Enums.Heading.WEST){
                e.setHeading(Enums.Heading.WEST);
            }else if(buttons.contains(Enums.Buttons.RIGHT)&&e.getHeading()== Enums.Heading.EAST){
                e.setHeading(Enums.Heading.EAST);
            }else{
                e.setHeading(Enums.Heading.SOUTH);
            }
        }

        if(buttons.contains(Enums.Buttons.LEFT)){
            if(buttons.contains(Enums.Buttons.UP)&&e.getHeading()== Enums.Heading.NORTH){
                e.setHeading(Enums.Heading.NORTH);
            }else if(buttons.contains(Enums.Buttons.DOWN)&&e.getHeading()== Enums.Heading.SOUTH){
                e.setHeading(Enums.Heading.SOUTH);
            }else{
                e.setHeading(Enums.Heading.WEST);
            }
        }

        if(buttons.contains(Enums.Buttons.RIGHT)){
            if(buttons.contains(Enums.Buttons.UP)&&e.getHeading()== Enums.Heading.NORTH){
                e.setHeading(Enums.Heading.NORTH);
            }else if(buttons.contains(Enums.Buttons.DOWN)&&e.getHeading()== Enums.Heading.SOUTH){
                e.setHeading(Enums.Heading.SOUTH);
            }else{
                e.setHeading(Enums.Heading.EAST);
            }
        }
    }
}
