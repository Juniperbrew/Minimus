package com.juniperbrew.minimus.server;

import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.juniperbrew.minimus.ConVars;
import com.juniperbrew.minimus.GlobalVars;
import com.juniperbrew.minimus.SharedMethods;
import com.juniperbrew.minimus.Tools;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * Created by Juniperbrew on 22/06/15.
 */
public class EntityAI {

    World world;

    public static final int MOVING = 0;
    public static final int MOVING_AND_SHOOTING = 1;
    public static final int FOLLOWING = 2;
    public static final int FOLLOWING_AND_SHOOTING = 3;

    private static final int MAX_RANGE = 400; //Pixels

    public float destinationX;
    public float destinationY;
    public boolean hasDestination;
    ServerEntity entity;
    long lastAttackDone;
    int aiType;
    int weapon;
    Vector2 targetLocation;
    float destinationTimer;
    float destinationTimeLimit;
    boolean targetUpdated;

    public EntityAI(ServerEntity entity, int aiType, int weapon, World world){
        this.entity = entity;
        this.world = world;
        this.aiType = aiType;
        this.weapon = weapon;
        lastAttackDone = System.nanoTime();
    }

    public void act(double velocity, double delta){
        if(aiType == MOVING){
            setRandomDestination(GlobalVars.mapWidth, GlobalVars.mapHeight);
            move(velocity,delta);
        }else if(aiType == MOVING_AND_SHOOTING){
            setRandomDestination(GlobalVars.mapWidth, GlobalVars.mapHeight);
            move(velocity,delta);
            shoot();
        }else if(aiType == FOLLOWING){
            setRandomDestination(GlobalVars.mapWidth, GlobalVars.mapHeight);
            lookForTarget();
            move(velocity,delta);
        }else if(aiType == FOLLOWING_AND_SHOOTING){
            setRandomDestination(GlobalVars.mapWidth, GlobalVars.mapHeight);
            lookForTarget();
            move(velocity,delta);
            shoot();
        }
        entity.updateCooldowns(delta);
    }

    private void lookForTarget(){
        float closestDistance = Float.POSITIVE_INFINITY;
        ServerEntity closestTarget = null;
        for(ServerEntity target : world.entities.values()){
            if (target.invulnerable ||  target.getTeam() == entity.getTeam()) {
                continue;
            }
            float distance = Tools.getSquaredDistance(target.getCenterX(),target.getCenterY(),entity.getCenterX(),entity.getCenterY());
            if(distance < closestDistance){
                if (!SharedMethods.isTileCollisionOnLine(entity.getCenterX(), entity.getCenterY(), target.getCenterX(), target.getCenterY())) {
                    closestDistance = distance;
                    closestTarget = target;
                }
            }
        }
        if(closestTarget!=null && Math.sqrt(closestDistance) < ConVars.getFloat("sv_npc_target_search_radius")){
            targetLocation = new Vector2(closestTarget.getCenterX(), closestTarget.getCenterY());
            targetUpdated = true;
        }

        if(targetLocation!=null){
            setDestination(targetLocation.x,targetLocation.y);
        }
    }

    private void move(double velocity,double delta){
        destinationTimer += delta;
        destinationTimeLimit -= delta;
        if(hasDestination) {
            double distanceX = destinationX - entity.getCenterX();
            double distanceY = destinationY - entity.getCenterY();

            if(destinationTimeLimit<0 || (Math.abs(distanceX) < 1 && Math.abs(distanceY) < 1)){
                hasDestination = false;
                targetLocation = null;
                return;
            }

            double distanceMoved = velocity * delta;
            double fullDistance = (float) Math.sqrt(Math.pow(distanceX, 2) + Math.pow(distanceY, 2));

            double deltaX = (distanceX * distanceMoved) / fullDistance;
            double deltaY = (distanceY * distanceMoved) / fullDistance;

            if (deltaX > 0) {
                deltaX = Tools.clamp(deltaX, 0, distanceX);
            } else {
                deltaX = Tools.clamp(deltaX, distanceX, 0);
            }
            if (deltaY > 0) {
                deltaY = Tools.clamp(deltaY, 0, distanceY);
            } else {
                deltaY = Tools.clamp(deltaY, distanceY, 0);
            }

            entity.addMovement(new Vector2((float) deltaX, (float) deltaY));
        }
    }

    private void shoot(){
        if(targetUpdated){
            world.attackWithEntity(entity, weapon);
            targetUpdated = false;
        }
    }

    private void setDestination(float x, float y){
        destinationTimer = 0;
        destinationTimeLimit = ConVars.getFloat("sv_npc_destination_time_limit");
        hasDestination = true;
        destinationX = x;
        destinationY = y;
        setRotation();
    }

    private void setRotation(){
        float deltaX = destinationX - entity.getCenterX();
        float deltaY = destinationY - entity.getCenterY();
        int degrees = (int) (MathUtils.radiansToDegrees*MathUtils.atan2(deltaY,deltaX));
        entity.setRotation(degrees);
    }

    private void setRandomDestination(int mapWidth,int mapHeight){

        if(hasDestination||targetLocation!=null){
            return;
        }
        if(world.posChangedEntities.size()>=ConVars.getInt("sv_max_moving_entities")){
            return;
        }

        double minX = Math.max(entity.getCenterX()-MAX_RANGE,entity.getWidth()/2);
        double maxX = Math.min(entity.getCenterX()+MAX_RANGE,mapWidth-(entity.getWidth()/2));
        double minY = Math.max(entity.getCenterY()-MAX_RANGE,entity.getHeight()/2);
        double maxY = Math.min(entity.getCenterY()+MAX_RANGE,mapHeight-(entity.getHeight()/2));
        destinationX = MathUtils.random((float)minX,(float)maxX);
        destinationY = MathUtils.random((float)minY,(float)maxY);

        //TODO clean this up
        int margin = 5;
        int offsetX = 0;
        int offsetY = 0;
        float destinationXTileCheck = destinationX;
        float destinationYTileCheck = destinationY;
        Vector2 tile = null;
        if(destinationX-entity.getCenterX()>=0){
            offsetX -= (entity.getWidth()/2) + margin;
            destinationXTileCheck += (entity.getWidth()/2);
        }else{
            offsetX += GlobalVars.tileWidth + (entity.getWidth()/2) + margin;
            destinationXTileCheck -= (entity.getWidth()/2);
        }
        if(destinationY-entity.getCenterY()>=0){
            offsetY -= (entity.getHeight()/2) + margin;
            destinationYTileCheck += (entity.getHeight()/2);
        }else{
            offsetY += GlobalVars.tileHeight +(entity.getHeight()/2)+ margin;
            destinationYTileCheck -= (entity.getHeight()/2);
        }
        tile = SharedMethods.raytrace(entity.getCenterX(), entity.getCenterY(), destinationXTileCheck, destinationYTileCheck);
        if(tile!=null) {
            setDestination(tile.x*GlobalVars.tileWidth+offsetX,tile.y*GlobalVars.tileHeight+offsetY);
        }else{
            setDestination(destinationX, destinationY);
        }
    }
}
