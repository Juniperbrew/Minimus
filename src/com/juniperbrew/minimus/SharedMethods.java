package com.juniperbrew.minimus;

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

        float originX = 0;
        float originY = 0;

        switch(e.getHeading()){
            case NORTH: originX = e.getX()+e.width/2; originY = e.getY()+e.height; break;
            case SOUTH: originX = e.getX()+e.width/2; originY = e.getY(); break;
            case WEST: originX = e.getX(); originY = e.getY()+e.height/2; break;
            case EAST: originX = e.getX()+e.width; originY = e.getY()+e.height/2; break;
        }

        float targetX = e.getX()+e.width/2;
        float targetY = e.getY()+e.height/2;
        switch (e.getHeading()){
            case NORTH: targetY += 200; break;
            case SOUTH: targetY -= 200; break;
            case EAST: targetX += 200; break;
            case WEST: targetX -= 200; break;
        }

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

    public void applyInput(Entity e, Network.UserInput input){
        float deltaX = 0;
        float deltaY = 0;
        if(input.buttons.contains(Enums.Buttons.UP)){
            deltaY = (float)conVars.get("sv_velocity") *input.msec;
        }
        if(input.buttons.contains(Enums.Buttons.DOWN)){
            deltaY = -1* (float)conVars.get("sv_velocity") *input.msec;
        }
        if(input.buttons.contains(Enums.Buttons.LEFT)){
            deltaX = -1* (float)conVars.get("sv_velocity") *input.msec;
        }
        if(input.buttons.contains(Enums.Buttons.RIGHT)){
            deltaX = (float)conVars.get("sv_velocity") *input.msec;
        }

        setHeading(e, input.buttons);

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

    public void setHeading(Entity e, EnumSet<Enums.Buttons> buttons){

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
