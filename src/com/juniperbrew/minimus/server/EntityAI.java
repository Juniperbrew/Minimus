package com.juniperbrew.minimus.server;

import com.badlogic.gdx.math.MathUtils;
import com.juniperbrew.minimus.Tools;
import com.juniperbrew.minimus.server.ServerEntity;

/**
 * Created by Juniperbrew on 22/06/15.
 */
public class EntityAI {

    private static final int MAX_RANGE = 400; //Pixels

    public float destinationX;
    public float destinationY;
    public boolean hasDestination;
    ServerEntity entity;

    public EntityAI(ServerEntity entity){
        this.entity = entity;
    }

    public void move(double velocity,double delta){
        if(hasDestination) {
            double distanceX = destinationX - entity.getX();
            double distanceY = destinationY - entity.getY();

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

            if (deltaX == distanceX && deltaY == distanceY) {
                hasDestination = false;
            }

            double newX = entity.getX()+deltaX;
            double newY = entity.getY()+deltaY;
            entity.moveTo((float)newX,(float)newY);
        }
    }

    public void setDestination(float x, float y){
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

    public void setRandomDestination(int mapWidth,int mapHeight){

        double minX = Math.max(entity.getX()-MAX_RANGE,0);
        double maxX = Math.min(entity.getX()+MAX_RANGE,mapWidth-entity.width);
        double minY = Math.max(entity.getY()-MAX_RANGE,0);
        double maxY = Math.min(entity.getY()+MAX_RANGE,mapHeight-entity.height);
        destinationX = MathUtils.random((float)minX,(float)maxX);
        destinationY = MathUtils.random((float)minY,(float)maxY);
        setDestination(destinationX,destinationY);
    }
}
