package com.juniperbrew.minimus.server;

import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.juniperbrew.minimus.Entity;
import com.juniperbrew.minimus.Tools;
import com.juniperbrew.minimus.client.MinimusClient;
import com.juniperbrew.minimus.server.ServerEntity;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * Created by Juniperbrew on 22/06/15.
 */
public class EntityAI {

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
    final float MIN_ATTACK_DELAY = 1;
    final float MAX_ATTACK_DELAY = 6;
    MinimusServer server;
    int aiType;
    int weapon;
    float attackDelay;
    ServerEntity target;

    public EntityAI(ServerEntity entity, int aiType, int weapon, MinimusServer server){
        this.entity = entity;
        this.server = server;
        this.aiType = aiType;
        attackDelay = MathUtils.random(MIN_ATTACK_DELAY,MAX_ATTACK_DELAY);
        this.weapon = weapon;
    }

    public void act(double velocity, double delta){
        if(aiType == MOVING){
            setRandomDestination(server.mapWidth,server.mapHeight);
            move(velocity,delta);
        }else if(aiType == MOVING_AND_SHOOTING){
            setRandomDestination(server.mapWidth,server.mapHeight);
            move(velocity,delta);
            shoot();
        }else if(aiType == FOLLOWING){
            setRandomDestination(server.mapWidth,server.mapHeight);
            lookForTarget();
            move(velocity,delta);
        }else if(aiType == FOLLOWING_AND_SHOOTING){
            setRandomDestination(server.mapWidth,server.mapHeight);
            lookForTarget();
            move(velocity,delta);
            shoot();
        }
    }

    private void lookForTarget(){
        if(target == null) {
            ArrayList<ServerEntity> potentialTargets = new ArrayList<>();
            Circle c = new Circle(entity.getCenterX(), entity.getCenterY(), server.conVars.getFloat("sv_npc_target_search_radius"));
            Iterator<ServerEntity> iter = server.entities.values().iterator();
            while (iter.hasNext()) {
                ServerEntity e = iter.next();
                if (Intersector.overlaps(c, e.getGdxBounds())&&e.getTeam()!=entity.getTeam()) {
                    potentialTargets.add(e);
                }
            }
            if(!potentialTargets.isEmpty()){
                int random = MathUtils.random(0, potentialTargets.size() - 1);
                target = potentialTargets.get(random);
            }
        }else{
            if(Tools.getSquaredDistance(entity.getCenterX(),entity.getCenterY(),target.getCenterX(),target.getCenterY()) > Math.pow(server.conVars.getFloat("sv_npc_target_search_radius"),2)){
                target = null;
            }else{
                setDestination(target.getX(),target.getY());
            }
        }
    }

    private void move(double velocity,double delta){
        if(hasDestination) {
            double distanceX = destinationX - entity.getX();
            double distanceY = destinationY - entity.getY();

            if(distanceX == 0 && distanceY == 0){
                hasDestination = false;
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

            double newX = entity.getX()+deltaX;
            double newY = entity.getY()+deltaY;
            entity.moveTo((float)newX,(float)newY);
        }
    }

    private void shoot(){
        if(System.nanoTime()-lastAttackDone < Tools.secondsToNano(attackDelay)){
            return;
        }
        lastAttackDone = System.nanoTime();
        attackDelay = MathUtils.random(MIN_ATTACK_DELAY,MAX_ATTACK_DELAY);
        server.createAttack(entity, weapon);
    }

    private void setDestination(float x, float y){
        hasDestination = true;
        destinationX = x;
        destinationY = y;
        setRotation();
    }

    private void setRotation(){
        float entityOrigoX = entity.getX()+ entity.width/2;
        float entityOrigoY = entity.getY()+ entity.height/2;
        float destinationOrigoX = destinationX+entity.width/2;
        float destinationOrigoY = destinationY+entity.height/2;
        float deltaX = destinationOrigoX - entityOrigoX;
        float deltaY = destinationOrigoY - entityOrigoY;
        int degrees = (int) (MathUtils.radiansToDegrees*MathUtils.atan2(deltaY,deltaX));
        entity.setRotation(degrees);
    }

    private void setRandomDestination(int mapWidth,int mapHeight){

        if(hasDestination||target != null){
            return;
        }
        if(server.posChangedEntities.size()>=server.conVars.getInt("sv_max_moving_entities")){
            return;
        }

        double minX = Math.max(entity.getX()-MAX_RANGE,0);
        double maxX = Math.min(entity.getX()+MAX_RANGE,mapWidth-entity.width);
        double minY = Math.max(entity.getY()-MAX_RANGE,0);
        double maxY = Math.min(entity.getY()+MAX_RANGE,mapHeight-entity.height);
        destinationX = MathUtils.random((float)minX,(float)maxX);
        destinationY = MathUtils.random((float)minY,(float)maxY);
        setDestination(destinationX,destinationY);
    }
}
