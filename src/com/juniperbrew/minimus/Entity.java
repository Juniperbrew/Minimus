package com.juniperbrew.minimus;

import com.badlogic.gdx.math.Polygon;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

import java.awt.geom.Rectangle2D;

/**
 * Created by Juniperbrew on 7.8.2015.
 */
public class Entity {

    protected NetworkEntity networkEntity;
    Vector2 movement = new Vector2();

    public Entity() {
        networkEntity = new NetworkEntity();
    }

    public Entity(int id, float x, float y, int team) {
        networkEntity = new NetworkEntity(id, x, y, team);
    }

    public Entity(int id, float x, float y, int team, int health) {
        networkEntity = new NetworkEntity(id, x, y, team, health);
    }

    public Entity(int id, float x, float y, float width, float height, int maxHealth, int team, String image){
        networkEntity = new NetworkEntity(id, x, y, width, height, maxHealth, maxHealth, team, image);
    }

    public Entity(NetworkEntity e) {
        networkEntity = new NetworkEntity(e);
    }

    public void addMovement(Vector2 movement) {
        this.movement.add(movement);
    }

    public float getX() {
        return networkEntity.x;
    }

    public float getY() {
        return networkEntity.y;
    }

    public float getWidth() {
        return networkEntity.width;
    }

    public float getHeight() {
        return networkEntity.height;
    }

    public void applyMovement() {

        if (getX() + getWidth() + movement.x > G.mapWidth) {
            movement.x = G.mapWidth - getX() - getWidth();
        }
        if (getX() + movement.x < 0) {
            movement.x = 0 - getX();
        }
        if (getY() + getHeight() + movement.y > G.mapHeight) {
            movement.y = G.mapHeight - getY() - getHeight();
        }
        if (getY() + movement.y < 0) {
            movement.y = 0 - getY();
        }

        Rectangle bounds = getGdxBounds();

        //less precise collision correction
        bounds.setX(bounds.getX() + movement.x);
        if (SharedMethods.checkMapCollision(bounds)) {
            bounds.setX(bounds.getX() - movement.x);
            movement.x = 0;
            //TODO reduce movement so entity hits the wall
        }
        bounds.setY(bounds.getY() + movement.y);
        if (SharedMethods.checkMapCollision(bounds)) {
            bounds.setY(bounds.getY() - movement.y);
            movement.y = 0;
            //TODO reduce movement so entity hits the wall
        }
/*
        //TODO compare performance to the less precise collision correction
        //FIXME entities that start on tile borders will tunnel through walls, probably only N and W tileborder
        bounds.setX(bounds.getX() + movement.x);
        float correctionX = SharedMethods.checkMapAxisCollision(bounds, true);
        if(movement.x>0){
            correctionX *= -1;
        }
        bounds.setX(bounds.getX() + correctionX);
        movement.x += correctionX;

        bounds.setY(bounds.getY() + movement.y);
        float correctionY = SharedMethods.checkMapAxisCollision(bounds,false);
        if(movement.y>0){
            correctionY *= -1;
        }
        bounds.setY(bounds.getY() + correctionY);
        movement.y += correctionY;
*/

        move(movement.x, movement.y);
        movement.setZero();
    }

    public void move(double deltaX, double deltaY) {
        networkEntity.x += deltaX;
        networkEntity.y += deltaY;
    }

    public Polygon getPolygonBounds() {
        float[] vertices = new float[8];
        vertices[0] = networkEntity.x;
        vertices[1] = networkEntity.y;
        vertices[2] = networkEntity.x + networkEntity.width;
        vertices[3] = networkEntity.y;
        vertices[4] = networkEntity.x + networkEntity.width;
        vertices[5] = networkEntity.y + networkEntity.height;
        vertices[6] = networkEntity.x;
        vertices[7] = networkEntity.y + networkEntity.height;
        return new Polygon(vertices);
    }

    public Rectangle getGdxBounds() {
        return new Rectangle(networkEntity.x, networkEntity.y, networkEntity.width, networkEntity.height);
    }

    public Rectangle2D.Float getJavaBounds() {
        return new Rectangle2D.Float(networkEntity.x, networkEntity.y, networkEntity.width, networkEntity.height);
    }

    public float getRotation() {
        return networkEntity.rotation;
    }

    public void setRotation(float degrees) {
        networkEntity.rotation = degrees;
    }

    public float getCenterX() {
        return networkEntity.x + networkEntity.width / 2;
    }

    public float getCenterY() {
        return networkEntity.y + networkEntity.height / 2;
    }

    public int getHealth() {
        return networkEntity.health;
    }

    public void setHealth(int health) {
        if (health > networkEntity.maxHealth) {
            networkEntity.health = networkEntity.maxHealth;
        } else {
            networkEntity.health = health;
        }
    }

    public int getMaxHealth() {
        return networkEntity.maxHealth;
    }

    public void setMaxHealth(int health){
        networkEntity.maxHealth = health;
    }


    public void moveTo(double x, double y) {
        networkEntity.x = (float) x;
        networkEntity.y = (float) y;
    }


    public int getTeam() {
        return networkEntity.team;
    }

    public void setTeam(int team) {
        networkEntity.team = team;
    }

    public NetworkEntity getNetworkEntity() {
        return networkEntity;
    }

    public void setNetworkedState(NetworkEntity e) {
        this.networkEntity = e;
    }

    public float getHealthPercent() {
        return 1 - ((float) networkEntity.health / networkEntity.maxHealth);
    }

    public int getID() {
        return networkEntity.id;
    }

    public void setSlot1Weapon(int weaponID){
        if(weaponID> G.primaryWeaponCount){
            return;
        }
        networkEntity.slot1Weapon = weaponID;
    }

    public void setSlot2Weapon(int secondarySlot){
        int weaponID = G.primaryWeaponCount+secondarySlot;
        if(weaponID> G.weaponList.size()){
            return;
        }
        networkEntity.slot2Weapon = weaponID;
    }

    public int getSlot1Weapon(){
        return networkEntity.slot1Weapon;
    }
    public int getSlot2Weapon(){
        return networkEntity.slot2Weapon;
    }

    public String getImage(){
        return networkEntity.image;
    }

    @Override
    public String toString() {
        return "Entity{" +
                "networkEntity=" + networkEntity +
                ", movement=" + movement +
                '}';
    }
}
