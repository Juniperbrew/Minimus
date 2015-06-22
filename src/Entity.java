import java.awt.geom.Rectangle2D;

/**
 * Created by Juniperbrew on 23.1.2015.
 */
public class Entity {
    public int id;
    public float x;
    public float y;
    public int width;
    public int height;
    public int health;
    public int maxHealth;
    public Enums.Heading heading;

    public Entity(){
        this(-1,-1,-1);
    }

    public Entity(Entity e){
        id = e.id;
        x = e.x;
        y = e.y;
        width = e.width;
        height = e.height;
        health = e.health;
        maxHealth = e.maxHealth;
        heading = e.heading;
    }

    public Entity(int id, float x, float y, int width, int height, int health, int maxHealth, Enums.Heading heading){
        this.id = id;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.health = health;
        this.maxHealth = maxHealth;
        this.heading = heading;
    }

    public Entity(int id, float x, float y) {
        this.id = id;
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
