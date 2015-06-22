import com.badlogic.gdx.math.MathUtils;

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

    public void move(float velocity,float delta){
        if(hasDestination) {
            float distanceX = destinationX - entity.getX();
            float distanceY = destinationY - entity.getY();

            float distanceMoved = velocity * delta;
            float fullDistance = (float) Math.sqrt(Math.pow(distanceX, 2) + Math.pow(distanceY, 2));

            float deltaX = (distanceX * distanceMoved) / fullDistance;
            float deltaY = (distanceY * distanceMoved) / fullDistance;

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

            float newX = entity.getX()+deltaX;
            float newY = entity.getY()+deltaY;
            entity.moveTo(newX,newY);
        }
    }

    public void setDestination(float x, float y){
        hasDestination = true;
        destinationX = x;
        destinationY = y;
    }

    public void setRandomDestination(int mapWidth,int mapHeight){

        float minX = Math.max(entity.getX()-MAX_RANGE,0);
        float maxX = Math.min(entity.getX()+MAX_RANGE,mapWidth-entity.width);
        float minY = Math.max(entity.getY()-MAX_RANGE,0);
        float maxY = Math.min(entity.getY()+MAX_RANGE,mapHeight-entity.height);
        destinationX = MathUtils.random(minX,maxX);
        destinationY = MathUtils.random(minY,maxY);
        setDestination(destinationX,destinationY);
    }
}
