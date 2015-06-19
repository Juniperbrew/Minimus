import java.awt.geom.Rectangle2D;

/**
 * Created by Juniperbrew on 23.1.2015.
 */
public class Entity {
    public float x;
    public float y;
    public int width;
    public int height;
    public short health;
    public short maxHealth;
    public Enums.Heading heading;

    public Entity(){
        this(0,0);
    }

    public Entity(Entity e){
        x = e.x;
        y = e.y;
        width = e.width;
        height = e.height;
        health = e.health;
        maxHealth = e.maxHealth;
        heading = e.heading;
    }

    public Entity(float x, float y) {
        this.x = x;
        this.y = y;
        width = 50;
        height = 50;
        health = 100;
        maxHealth = 100;
        heading = Enums.Heading.SOUTH;
    }

    public Rectangle2D.Float getBounds(){
        return new Rectangle2D.Float(x,y,width,height);
    }
}
