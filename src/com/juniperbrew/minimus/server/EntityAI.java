package com.juniperbrew.minimus.server;

import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.juniperbrew.minimus.ConVars;
import com.juniperbrew.minimus.Tools;

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
    World world;
    int aiType;
    int weapon;
    float attackDelay;
    int targetID = -1;

    public EntityAI(ServerEntity entity, int aiType, int weapon, World world){
        this.entity = entity;
        this.world = world;
        this.aiType = aiType;
        attackDelay = MathUtils.random(MIN_ATTACK_DELAY,MAX_ATTACK_DELAY);
        this.weapon = weapon;
        lastAttackDone = System.nanoTime();
    }

    public void act(double velocity, double delta){
        if(aiType == MOVING){
            setRandomDestination(world.mapWidth, world.mapHeight);
            move(velocity,delta);
        }else if(aiType == MOVING_AND_SHOOTING){
            setRandomDestination(world.mapWidth, world.mapHeight);
            move(velocity,delta);
            shoot();
        }else if(aiType == FOLLOWING){
            setRandomDestination(world.mapWidth, world.mapHeight);
            lookForTarget();
            move(velocity,delta);
        }else if(aiType == FOLLOWING_AND_SHOOTING){
            setRandomDestination(world.mapWidth, world.mapHeight);
            lookForTarget();
            move(velocity,delta);
            shoot();
        }
    }

    private void lookForTarget(){
        if(targetID == -1) {
            ArrayList<ServerEntity> potentialTargets = new ArrayList<>();
            Circle c = new Circle(entity.getCenterX(), entity.getCenterY(), ConVars.getFloat("sv_npc_target_search_radius"));
            Iterator<ServerEntity> iter = world.entities.values().iterator();
            while (iter.hasNext()) {
                ServerEntity e = iter.next();
                if(e.invulnerable){
                    continue;
                }
                if (Intersector.overlaps(c, e.getGdxBounds())&&e.getTeam()!=entity.getTeam()) {
                    potentialTargets.add(e);
                }
            }
            if(!potentialTargets.isEmpty()){
                int random = MathUtils.random(0, potentialTargets.size() - 1);
                targetID = potentialTargets.get(random).id;
            }
        }else{
            ServerEntity target = world.entities.get(targetID);
            if(target==null){
                targetID = -1;
                return;
            }
            if(Tools.getSquaredDistance(entity.getCenterX(),entity.getCenterY(),target.getCenterX(),target.getCenterY()) > Math.pow(ConVars.getFloat("sv_npc_target_search_radius"),2)){
                targetID = -1;
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
        world.createAttack(entity.id, weapon);
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

        if(hasDestination||targetID != -1){
            return;
        }
        if(world.posChangedEntities.size()>=ConVars.getInt("sv_max_moving_entities")){
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
