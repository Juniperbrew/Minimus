package com.juniperbrew.minimus.server;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.juniperbrew.minimus.ConVars;
import com.juniperbrew.minimus.G;
import com.juniperbrew.minimus.F;
import com.juniperbrew.minimus.Tools;

/**
 * Created by Juniperbrew on 22/06/15.
 */
public class EntityAI {

    Game game;

    public static final int MOVING = 0;
    public static final int MOVING_AND_SHOOTING = 1;
    public static final int FOLLOWING = 2;
    public static final int FOLLOWING_AND_SHOOTING = 3;

    private static final int MAX_RANGE = 400; //Pixels
    //FIXME Test how well 1 second target update delay would work in practice
    private static final float TARGET_UPDATE_DELAY = 0.1f;
    private static final float FOV = 160;

    public boolean hasDestination;
    NpcServerEntity entity;
    long lastAttackDone;
    int aiType;
    int weapon;
    Vector2 destination = new Vector2();
    float destinationTimer;
    float destinationTimeLimit;
    boolean targetUpdated;
    float targetSearchTimer;

    private static final float TURNRATE = 180; //Deg/s
    private float targetRotation;

    public EntityAI(NpcServerEntity entity, int aiType, int weapon, Game game){
        this.entity = entity;
        this.game = game;
        this.aiType = aiType;
        this.weapon = weapon;
        lastAttackDone = System.nanoTime();
    }

    public void act(double delta){
        entity.updateCooldowns(delta);
        targetSearchTimer -= delta;
        if(aiType == MOVING){
            setRandomDestination(G.mapWidth, G.mapHeight);
            move(delta);
        }else if(aiType == MOVING_AND_SHOOTING){
            setRandomDestination(G.mapWidth, G.mapHeight);
            move(delta);
            shoot(delta);
        }else if(aiType == FOLLOWING){
            setRandomDestination(G.mapWidth, G.mapHeight);
            lookForTarget();
            move(delta);
        }else if(aiType == FOLLOWING_AND_SHOOTING){
            setRandomDestination(G.mapWidth, G.mapHeight);
            lookForTarget();
            move(delta);
            shoot(delta);
        }
    }

    private void lookForTarget(){
        if(targetSearchTimer>0){
            return;
        }
        targetSearchTimer = TARGET_UPDATE_DELAY;
        float closestDistance = Float.POSITIVE_INFINITY;
        ServerEntity closestTarget = null;


        for(ServerEntity target : game.entities.values()){
            if (target.invulnerable ||  target.getTeam() == entity.getTeam()) {
                continue;
            }
            float targetAngle = Tools.getAngle(entity.getCenterX(), entity.getCenterY(), target.getCenterX(), target.getCenterY());
            float anglediff = Tools.getAngleDiff(targetAngle,entity.getRotation());

            if(anglediff>FOV/2||anglediff<-FOV/2){
                continue;
            }
            float distance = Tools.getSquaredDistance(target.getCenterX(),target.getCenterY(),entity.getCenterX(),entity.getCenterY());
            if(distance < closestDistance){
                if (!F.isTileCollisionOnLine(entity.getCenterX(), entity.getCenterY(), target.getCenterX(), target.getCenterY())) {
                    closestDistance = distance;
                    closestTarget = target;
                }
            }
        }
        if(closestTarget!=null && Math.sqrt(closestDistance) < entity.vision){
            setTarget(closestTarget.getCenterX(), closestTarget.getCenterY());
            targetUpdated = true;
        }

    }

    public void setTarget(float x, float y){
        setDestination(x,y);
    }

    private void move(double delta){
        destinationTimer += delta;
        destinationTimeLimit -= delta;

        if(targetRotation!=entity.getRotation()){
            double turn = TURNRATE*delta;
            float anglediff = Tools.getAngleDiff(entity.getRotation(),targetRotation);

            if(Math.abs(anglediff) < turn){
                float deltaUsage = Math.abs(anglediff)/TURNRATE;
                entity.setRotation(targetRotation);
                delta-=deltaUsage;
            }else{
                if(anglediff<=0){
                   entity.setRotation((float) (entity.getRotation()+turn));
                }else{
                    entity.setRotation((float) (entity.getRotation()-turn));
                }
                return;
            }
        }

        if(hasDestination) {
            double distanceX = destination.x - entity.getCenterX();
            double distanceY = destination.y - entity.getCenterY();

            if(destinationTimeLimit<0 || (Math.abs(distanceX) < 1 && Math.abs(distanceY) < 1)){
                hasDestination = false;
                return;
            }

            double distanceMoved = entity.velocity * delta;
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

    private void shoot(double delta){
        if(targetUpdated){
            game.attack(entity, weapon, (short) (delta*1000));
            targetUpdated = false;
        }
    }

    private void setDestination(float x, float y){
        destinationTimer = 0;
        destinationTimeLimit = ConVars.getFloat("sv_npc_destination_time_limit");
        hasDestination = true;
        destination.x = x;
        destination.y = y;
        setRotation();
    }

    private void setRotation(){
        float deltaX = destination.x - entity.getCenterX();
        float deltaY = destination.y - entity.getCenterY();
        int degrees = (int) (MathUtils.radiansToDegrees*MathUtils.atan2(deltaY,deltaX));
        targetRotation = degrees;
    }

    private void setRandomDestination(int mapWidth,int mapHeight){

        if(hasDestination){
            return;
        }
        if(game.posChangedEntities.size()>=ConVars.getInt("sv_max_moving_entities")){
            return;
        }

        double minX = Math.max(entity.getCenterX()-MAX_RANGE,entity.getWidth()/2);
        double maxX = Math.min(entity.getCenterX()+MAX_RANGE,mapWidth-(entity.getWidth()/2));
        double minY = Math.max(entity.getCenterY()-MAX_RANGE,entity.getHeight()/2);
        double maxY = Math.min(entity.getCenterY()+MAX_RANGE,mapHeight-(entity.getHeight()/2));
        destination.x = MathUtils.random((float)minX,(float)maxX);
        destination.y = MathUtils.random((float)minY,(float)maxY);

        //TODO clean this up
        int margin = 5;
        int offsetX = 0;
        int offsetY = 0;
        float destinationXTileCheck = destination.x;
        float destinationYTileCheck = destination.y;
        if(destination.x-entity.getCenterX()>=0){
            offsetX -= (entity.getWidth()/2) + margin;
            destinationXTileCheck += (entity.getWidth()/2);
        }else{
            offsetX += G.tileWidth + (entity.getWidth()/2) + margin;
            destinationXTileCheck -= (entity.getWidth()/2);
        }
        if(destination.y-entity.getCenterY()>=0){
            offsetY -= (entity.getHeight()/2) + margin;
            destinationYTileCheck += (entity.getHeight()/2);
        }else{
            offsetY += G.tileHeight +(entity.getHeight()/2)+ margin;
            destinationYTileCheck -= (entity.getHeight()/2);
        }
        Vector2 tile = F.raytrace(entity.getCenterX(), entity.getCenterY(), destinationXTileCheck, destinationYTileCheck);
        if(tile!=null) {
            setDestination(tile.x* G.tileWidth+offsetX,tile.y* G.tileHeight+offsetY);
        }else{
            setDestination(destination.x, destination.y);
        }
    }
}
