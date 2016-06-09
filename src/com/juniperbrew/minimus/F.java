package com.juniperbrew.minimus;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.math.*;
import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.awt.geom.Line2D;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by Juniperbrew on 24.6.2015.
 */
public class F {

    public static ArrayList<Line2D.Float> createHitscan(Weapon weapon, float centerX, float centerY, float deg){
        ProjectileDefinition projectileDefinition = weapon.projectile;
        final ArrayList<Line2D.Float> hitscans = new ArrayList<>();
        //int startDistance = ConVars.getInt("sv_npc_default_size") / 2;
        int length = projectileDefinition.length;

        deg -= weapon.spread / 2f;
        for (int i = 0; i < weapon.projectileCount; i++) {
            float sina = MathUtils.sinDeg(deg);
            float cosa = MathUtils.cosDeg(deg);

            float startX = centerX;//+cosa*startDistance;
            float startY = centerY;//+sina*startDistance;
            float targetX = startX + cosa*length;
            float targetY = startY + sina*length;
            Line2D.Float line = new Line2D.Float(startX,startY,targetX,targetY);
            hitscans.add(line);

            if (weapon.projectileCount > 1) {
                deg += weapon.spread / (weapon.projectileCount - 1);
            }
        }

        return hitscans;
    }

    public static Particle createTracer(ProjectileDefinition def, Line2D.Float line){
        int rotation = (int)Tools.getAngle(line);
        int length = (int) Tools.getLength(line);
        Rectangle bounds = new Rectangle(line.x1,line.y1,length,def.width);
        return new Particle(def,bounds,rotation,line.x1,line.y1);
    }

    public static Particle createStationaryParticle(ProjectileDefinition def, float x, float y) {
        Rectangle bounds = new Rectangle(x-def.width/2, y-def.length/2, def.length, def.width);
        return new Particle(def,bounds);
    }

    public static Particle createRotatedParticle(ProjectileDefinition def, float x, float y, int rotation) {
        Rectangle bounds = new Rectangle(x-def.width/2, y-def.length/2, def.length, def.width);
        return new Particle(def,bounds,rotation,x,y,0);
    }

    public static Particle createMovingParticle(ProjectileDefinition def, float x, float y, float rotation, float velocity) {
        Rectangle bounds = new Rectangle(x-def.width/2, y-def.length/2, def.length, def.width);
        return new Particle(def,bounds,rotation,x,y, velocity);
    }

    public static Particle createStationaryProjectile(ProjectileDefinition def, float x, float y, int entityId, int team) {
        Rectangle bounds = new Rectangle(x-def.width/2, y-def.length/2, def.length, def.width);
        return new Particle(def, bounds, entityId, team);
    }

    public static ArrayList<RenderedParticle> makeRenderedParticles(ArrayList<Particle> particles, ProjectileDefinition def){
        ArrayList<RenderedParticle> renderedParticles = new ArrayList<>();
        for(Particle p : particles){
            renderedParticles.add(new RenderedParticle(p, def));
        }
        return renderedParticles;
    }

    public static ArrayList<Particle> createProjectiles(Weapon weapon, float centerX, float centerY, float deg, int entityId, int team, HashMap<String,Float> projectileModifiers) {
        System.out.println("Creating projectiles: "+weapon.name);
        ProjectileDefinition def = weapon.projectile;
        final ArrayList<Particle> projectiles = new ArrayList<>();
        int length = def.length;
        int width = def.width;
        int startDistanceX = ConVars.getInt("sv_npc_default_size") / 2;

        deg -= weapon.spread / 2f;
        for (int i = 0; i < weapon.projectileCount; i++) {

            Rectangle bounds = new Rectangle(centerX + startDistanceX, centerY - width / 2, length, width);

            Particle p = new Particle(def, bounds, deg, centerX, centerY, entityId, team);

            if(projectileModifiers!=null){
                if(projectileModifiers.containsKey("velocity")){
                    p.velocity = projectileModifiers.get("velocity");
                }
                if(projectileModifiers.containsKey("duration")){
                    p.duration = projectileModifiers.get("duration");
                }
            }

            projectiles.add(p);

            if (weapon.projectileCount > 1) {
                deg += weapon.spread / (weapon.projectileCount - 1);
            }
        }
        System.out.println("Created "+projectiles.size()+" projectiles.");
        return projectiles;
    }

    public static void applyInput(Entity e, Network.UserInput input) {
        float deltaX = 0;
        float deltaY = 0;
        float velocity = ConVars.getFloat("sv_player_velocity");

        if(input.buttons.contains(Enums.Buttons.LCTRL)){
            velocity *= 3;
        }

        setRotation(e, input);

        float delta = input.msec / 1000f;

        float distance = velocity * delta;
        float direction = e.getRotation();

        if (input.buttons.contains(Enums.Buttons.W)) {
            deltaX = MathUtils.cosDeg(direction) * distance;
            deltaY = MathUtils.sinDeg(direction) * distance;
        }
        if (input.buttons.contains(Enums.Buttons.S)) {
            deltaX += MathUtils.cosDeg(direction - 180) * distance;
            deltaY += MathUtils.sinDeg(direction - 180) * distance;
        }
        if (input.buttons.contains(Enums.Buttons.A)) {
            deltaX += MathUtils.cosDeg(direction + 90) * distance;
            deltaY += MathUtils.sinDeg(direction + 90) * distance;
        }
        if (input.buttons.contains(Enums.Buttons.D)) {
            deltaX += MathUtils.cosDeg(direction - 90) * distance;
            deltaY += MathUtils.sinDeg(direction - 90) * distance;
        }

        e.addMovement(new Vector2(deltaX,deltaY));
        e.applyMovement();
/*

        if (ConVars.getBool("sv_check_map_collisions")) {
            if (e.getX() + e.getWidth() + deltaX > GlobalVars.mapWidth) {
                deltaX = GlobalVars.mapWidth - e.getX() - e.getWidth();
            }
            if (e.getX() + deltaX < 0) {
                deltaX = 0 - e.getX();
            }
            if (e.getY() + e.getHeight() + deltaY > GlobalVars.mapHeight) {
                deltaY = GlobalVars.mapHeight - e.getY() - e.getHeight();
            }
            if (e.getY() + deltaY < 0) {
                deltaY = 0 - e.getY();
            }
        }
        Rectangle bounds = e.getGdxBounds();
        bounds.setX(bounds.getX() + deltaX);
        if (SharedMethods.checkMapCollision(bounds)) {
            bounds.setX(bounds.getX() - deltaX);
            deltaX = 0;
        }
        bounds.setY(bounds.getY() + deltaY);
        if (SharedMethods.checkMapCollision(bounds)) {
            deltaY = 0;
        }
        e.move(deltaX, deltaY);
        */
    }


    public static boolean checkMapCollision(Rectangle bounds) {

            for(Rectangle o : G.solidMapObjects){
                if(bounds.overlaps(o)){
                    return true;
                }
            }

        int minX = (int) Math.floor(bounds.x / G.tileWidth);
        int maxX = (int) Math.floor((bounds.x + bounds.width) / G.tileWidth);
        int minY = (int) Math.floor(bounds.y / G.tileHeight);
        int maxY = (int) Math.floor((bounds.y + bounds.height) / G.tileHeight);


        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                if (isTileSolid(x, y)) return true;
            }
        }
        return false;
    }

    public static float checkMapAxisCollision(Rectangle bounds, boolean xAxis) {

        int minX = (int) Math.floor(bounds.x / G.tileWidth);
        int maxX = (int) Math.floor((bounds.x + bounds.width) / G.tileWidth);
        int minY = (int) Math.floor(bounds.y / G.tileHeight);
        int maxY = (int) Math.floor((bounds.y + bounds.height) / G.tileHeight);

        float correction = 0;
        float tempCorrection;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                if (isTileSolid(x, y)){
                    Rectangle tile = getTileBounds(x,y);
                    if(xAxis){
                        tempCorrection = Math.min(tile.x + tile.width, bounds.x + bounds.width) - Math.max(tile.x, bounds.x);
                    }else{
                        tempCorrection = Math.min(tile.y + tile.height, bounds.y + bounds.height) - Math.max(tile.y, bounds.y);
                    }
                    if( tempCorrection>correction){
                        correction =  tempCorrection;
                    }
                }
            }
        }
        if(correction>0){
            correction += 0.1f;
            if(xAxis){
                System.out.println("X axis correction:"+correction);
            }else{
                System.out.println("Y axis correction:"+correction);
            }
        }

        return correction;
    }

    private static Rectangle getTileBounds(int x, int y){
        return new Rectangle(x* G.tileWidth,y* G.tileHeight, G.tileWidth, G.tileHeight);
    }

    public static boolean isTileSolid(int x, int y) {
        if (x < 0 || x >= G.mapWidthTiles || y < 0 || y >= G.mapHeightTiles) {
            return false;
        }
        if (G.collisionMap[x][y]) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean isTileCollisionOnLine(float screenX0, float screenY0, float screenX1, float screenY1) {
        if (raytrace(screenX0, screenY0, screenX1, screenY1) != null) {
            return true;
        } else {
            return false;
        }
    }

    public static Vector2 getLineIntersectionWithRectangle(Line2D.Float line, Rectangle bounds){
        Vector2 intersection = new Vector2();
        Vector2 l1 = new Vector2(line.x1,line.y1);
        Vector2 l2 = new Vector2(line.x2,line.y2);
        Vector2 sw = new Vector2(bounds.x,bounds.y);
        Vector2 se = new Vector2(bounds.x+bounds.width,bounds.y);
        Vector2 ne = new Vector2(bounds.x+bounds.width,bounds.y+bounds.height);
        Vector2 nw = new Vector2(bounds.x,bounds.y+bounds.height);


        if(l1.y<=sw.y&&Intersector.intersectSegments(l1, l2, sw, se, intersection))return intersection;
        if(l1.x>=se.x&&Intersector.intersectSegments(l1, l2, se, ne, intersection))return intersection;
        if(l1.y>ne.y&&Intersector.intersectSegments(l1,l2,ne,nw,intersection))return intersection;
        if(l1.x<nw.x&&Intersector.intersectSegments(l1, l2, nw, sw, intersection))return intersection;

        return null;
    }

    public static Vector2 findLineIntersectionPointWithTile(float screenX0, float screenY0, float screenX1, float screenY1){
        Vector2 tile = raytrace(screenX0, screenY0, screenX1, screenY1);
        if(tile==null){
            return null;
        }
        Rectangle bounds = new Rectangle(tile.x* G.tileWidth,tile.y* G.tileHeight, G.tileWidth, G.tileHeight);
        Line2D.Float line = new Line2D.Float(screenX0,screenY0,screenX1,screenY1);
        return getLineIntersectionWithRectangle(line,bounds);
    }


    public static Vector2 raytraceInt(float screenX0, float screenY0, float screenX1, float screenY1) {
        int x0 = (int) (screenX0 / G.tileWidth);
        int y0 = (int) (screenY0 / G.tileHeight);
        int x1 = (int) (screenX1 / G.tileWidth);
        int y1 = (int) (screenY1 / G.tileHeight);

        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int x = x0;
        int y = y0;
        int n = 1 + dx + dy;
        int x_inc = (x1 > x0) ? 1 : -1;
        int y_inc = (y1 > y0) ? 1 : -1;
        int error = dx - dy;
        dx *= 2;
        dy *= 2;


        for (; n > 0; --n) {
            if (isTileSolid(x, y)) {
                return new Vector2(x, y);
            }
            if (error > 0) {
                x += x_inc;
                error -= dy;
            } else {
                y += y_inc;
                error += dx;
            }
        }
        return null;
    }

    public static void debugDrawRaytrace(ShapeRenderer renderer, float screenX0, float screenY0, float screenX1, float screenY1){

        renderer.begin(ShapeRenderer.ShapeType.Line);
        renderer.setColor(0,1,0,1);
        double x0 = screenX0 / G.tileWidth;
        double y0 = screenY0 / G.tileHeight;
        double x1 = screenX1 / G.tileWidth;
        double y1 = screenY1 / G.tileHeight;

        double dx = Math.abs(x1 - x0);
        double dy = Math.abs(y1 - y0);

        int x = (int)(Math.floor(x0));
        int y = (int)(Math.floor(y0));

        int n = 1;
        int x_inc, y_inc;
        double error;

        if (dx == 0)
        {
            x_inc = 0;
            error = Double.POSITIVE_INFINITY;
        }
        else if (x1 > x0)
        {
            x_inc = 1;
            n += (int)(Math.floor(x1)) - x;
            error = (Math.floor(x0) + 1 - x0) * dy;
        }
        else
        {
            x_inc = -1;
            n += x - (int)(Math.floor(x1));
            error = (x0 - Math.floor(x0)) * dy;
        }

        if (dy == 0)
        {
            y_inc = 0;
            error -= Double.POSITIVE_INFINITY;
        }
        else if (y1 > y0)
        {
            y_inc = 1;
            n += (int)(Math.floor(y1)) - y;
            error -= (Math.floor(y0) + 1 - y0) * dx;
        }
        else
        {
            y_inc = -1;
            n += y - (int)(Math.floor(y1));
            error -= (y0 - Math.floor(y0)) * dx;
        }

        for (; n > 0; --n)
        {
            renderer.rect(x* G.tileWidth,y* G.tileHeight, G.tileWidth, G.tileHeight);

            if (error > 0)
            {
                y += y_inc;
                error -= dx;
            }
            else
            {
                x += x_inc;
                error += dy;
            }
        }
        renderer.end();
    }

    public static Vector2 debugRaytrace(float screenX0, float screenY0, float screenX1, float screenY1)
    {

        System.out.println("sX0:"+screenX0);
        System.out.println("sY0:"+screenY0);
        System.out.println("sX1:"+screenX1);
        System.out.println("sY1:"+screenY1);
        double x0 = screenX0 / G.tileWidth;
        double y0 = screenY0 / G.tileHeight;
        double x1 = screenX1 / G.tileWidth;
        double y1 = screenY1 / G.tileHeight;
        System.out.println("X0:"+x0);
        System.out.println("Y0:"+y0);
        System.out.println("X1:"+x1);
        System.out.println("Y1:"+y1);

        double dx = Math.abs(x1 - x0);
        double dy = Math.abs(y1 - y0);

        System.out.println("dx:"+dx);
        System.out.println("dy:"+dy);

        int x = (int)(Math.floor(x0));
        int y = (int)(Math.floor(y0));

        int n = 1;
        int x_inc, y_inc;
        double error;

        if (dx == 0)
        {
            x_inc = 0;
            error = Double.POSITIVE_INFINITY;
        }
        else if (x1 > x0)
        {
            x_inc = 1;
            n += (int)(Math.floor(x1)) - x;
            error = (Math.floor(x0) + 1 - x0) * dy;
        }
        else
        {
            x_inc = -1;
            n += x - (int)(Math.floor(x1));
            error = (x0 - Math.floor(x0)) * dy;
        }
        System.out.println("error(afterX):"+error);

        if (dy == 0)
        {
            y_inc = 0;
            error -= Double.POSITIVE_INFINITY;
        }
        else if (y1 > y0)
        {
            y_inc = 1;
            n += (int)(Math.floor(y1)) - y;
            error -= (Math.floor(y0) + 1 - y0) * dx;
        }
        else
        {
            y_inc = -1;
            n += y - (int)(Math.floor(y1));
            error -= (y0 - Math.floor(y0)) * dx;
        }

        System.out.println("x_inc:"+x_inc);
        System.out.println("x_inc:"+y_inc);

        for (; n > 0; --n)
        {
            System.out.println("n:"+n);
            System.out.println("x:"+x);
            System.out.println("y:"+y);
            System.out.println("error:"+error);
            if (isTileSolid(x, y)) {
                System.out.println("COLLISION");
                return new Vector2(x, y);
            }

            if (error > 0)
            {
                y += y_inc;
                error -= dx;
            }
            else
            {
                x += x_inc;
                error += dy;
            }
        }
        System.out.println("NO COLLISION");
        return null;
    }

    public static Vector2 raytrace(float screenX0, float screenY0, float screenX1, float screenY1)
    {

        double x0 = screenX0 / G.tileWidth;
        double y0 = screenY0 / G.tileHeight;
        double x1 = screenX1 / G.tileWidth;
        double y1 = screenY1 / G.tileHeight;

        double dx = Math.abs(x1 - x0);
        double dy = Math.abs(y1 - y0);

        int x = (int)(Math.floor(x0));
        int y = (int)(Math.floor(y0));

        int n = 1;
        int x_inc, y_inc;
        double error;

        if (dx == 0)
        {
            x_inc = 0;
            error = Double.POSITIVE_INFINITY;
        }
        else if (x1 > x0)
        {
            x_inc = 1;
            n += (int)(Math.floor(x1)) - x;
            error = (Math.floor(x0) + 1 - x0) * dy;
        }
        else
        {
            x_inc = -1;
            n += x - (int)(Math.floor(x1));
            error = (x0 - Math.floor(x0)) * dy;
        }

        if (dy == 0)
        {
            y_inc = 0;
            error -= Double.POSITIVE_INFINITY;
        }
        else if (y1 > y0)
        {
            y_inc = 1;
            n += (int)(Math.floor(y1)) - y;
            error -= (Math.floor(y0) + 1 - y0) * dx;
        }
        else
        {
            y_inc = -1;
            n += y - (int)(Math.floor(y1));
            error -= (y0 - Math.floor(y0)) * dx;
        }

        for (; n > 0; --n)
        {
            if (isTileSolid(x, y)) {
                return new Vector2(x, y);
            }

            if (error > 0)
            {
                y += y_inc;
                error -= dx;
            }
            else
            {
                x += x_inc;
                error += dy;
            }
        }
        return null;
    }

    public static boolean[][] createCollisionMap(TiledMap map, int mapWidth, int mapHeight) {
        boolean[][] collisions = new boolean[mapWidth][mapHeight];
        for (TiledMapTileLayer layer : map.getLayers().getByType(TiledMapTileLayer.class)) {
            for (int x = 0; x < layer.getWidth(); x++) {
                for (int y = 0; y < layer.getHeight(); y++) {
                    TiledMapTileLayer.Cell cell = layer.getCell(x, y);
                    if (cell != null) {
                        if (cell.getTile().getProperties().containsKey("solid")) {
                            collisions[x][y] = true;
                        }
                    }
                }
            }
        }
        return collisions;
    }

    public static void printCollisionMap(){
        for(int y = 0; y < G.mapHeightTiles; y++){
            for (int x = 0; x < G.mapWidthTiles; x++) {
                if(G.collisionMap[x][G.mapHeightTiles-y-1]){
                    System.out.print("#");
                }else{
                    System.out.print("0");
                }
            }
            System.out.println();
        }
    }

    public static void setRotation(Entity e, Network.UserInput input) {
        float mouseX = input.mouseX;
        float mouseY = input.mouseY;
        float playerX = e.getX() + e.getWidth() / 2;
        float playerY = e.getY() + e.getHeight() / 2;
        float deltaX = mouseX - playerX;
        float deltaY = mouseY - playerY;
        int degrees = (int) (MathUtils.radiansToDegrees * MathUtils.atan2(deltaY, deltaX));
        e.setRotation(degrees);
    }

    public static void renderAttackBoundingBox(ShapeRenderer renderer, ConcurrentLinkedQueue<? extends Particle> projectiles) {
        renderer.begin(ShapeRenderer.ShapeType.Line);
        for (Particle projectile : projectiles) {
            Rectangle rect = projectile.getBoundingRectangle();
            renderer.rect(rect.x, rect.y, rect.width, rect.height);
        }
        renderer.end();
    }

    public static void renderAttackPolygon(ShapeRenderer renderer, ConcurrentLinkedQueue<? extends Particle> projectiles) {
        renderer.begin(ShapeRenderer.ShapeType.Line);
        for (Particle projectile : projectiles) {
            renderer.polygon(projectile.getBoundingPolygon().getTransformedVertices());
        }
        renderer.end();
    }

    public static void renderParticles(SpriteBatch batch, ConcurrentLinkedQueue<RenderedParticle> particles) {
        batch.begin();
        for (RenderedParticle particle : particles) {
            F.renderPolygon(batch, particle.texture, particle.bounds);
        }
        batch.end();
    }

    public static float getMapScale(TiledMap map) {
        String scale = map.getProperties().get("mapScale", String.class);
        if (scale != null) {
            return Float.parseFloat(scale);
        } else {
            return 1;
        }
    }

    public static void renderPolygon(SpriteBatch batch, TextureRegion texture, Polygon bounds) {

        float color = Color.WHITE.toFloatBits();
        float[] vertices = new float[4 * 5];
        float[] vBounds = bounds.getTransformedVertices();
        int i = 0;

        vertices[i++] = vBounds[0];
        vertices[i++] = vBounds[1];
        vertices[i++] = color;
        vertices[i++] = texture.getU();
        vertices[i++] = texture.getV();

        vertices[i++] = vBounds[2];
        vertices[i++] = vBounds[3];
        vertices[i++] = color;
        vertices[i++] = texture.getU();
        vertices[i++] = texture.getV2();

        vertices[i++] = vBounds[4];
        vertices[i++] = vBounds[5];
        vertices[i++] = color;
        vertices[i++] = texture.getU2();
        vertices[i++] = texture.getV2();

        vertices[i++] = vBounds[6];
        vertices[i++] = vBounds[7];
        vertices[i++] = color;
        vertices[i++] = texture.getU2();
        vertices[i++] = texture.getV();

        batch.draw(texture.getTexture(), vertices, 0, 4 * 5);
    }

    //TODO inefficient lookup of weapon by name
    public static int getWeaponID(HashMap<Integer,Weapon> weaponList, String name){
        for(int weaponID:weaponList.keySet()){
            Weapon w = weaponList.get(weaponID);
            if(w.name.equals(name)){
                return weaponID;
            }
        }
        return -1;
    }

    public static ArrayList<Integer> getEntitiesWithinRange(Collection<? extends Entity> entities, Entity origin, float range){
        double squaredDistance = Math.pow(range, 2);
        ArrayList<Integer> entityIDs = new ArrayList<>();
        for(Entity e:entities){
            float distance = Tools.getSquaredDistance(origin.getCenterX(), origin.getCenterY(), e.getCenterX(), e.getCenterY());
            if(distance<squaredDistance){
                if(origin.getID() != e.getID()){
                    entityIDs.add(e.getID());
                }
            }
        }
        return entityIDs;
    }

    public static void drawLog(String title, String unit, Queue<Float> log, ShapeRenderer shapeRenderer,SpriteBatch batch, BitmapFont font, float x, float y, float width, float height, float yScale, float targetY, float badY){
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(1, 1, 0, 1);
        shapeRenderer.rect(x, y, width, height);
        shapeRenderer.setColor(0, 1, 0, 1);
        shapeRenderer.line(x, y + (targetY * yScale), x + width, y + (targetY * yScale));
        shapeRenderer.setColor(1, 0, 0, 1);
        shapeRenderer.line(x, y + (badY * yScale), x + width, y + (badY * yScale));
        shapeRenderer.setColor(1, 1, 0, 1);
        float xScale = width/log.size();
        Iterator<Float> iter = log.iterator();
        int i = 0;
        float value = 0;
        float sum = 0;
        if(iter.hasNext()){
            value = iter.next();
            sum += value;
        }
        while(iter.hasNext()){
            float oldValue = value;
            value = iter.next();
            sum += value;
            shapeRenderer.line(x+(i*xScale),y+(oldValue*yScale),x+((i+1)*xScale),y+(value*yScale));
            i++;
        }
        shapeRenderer.end();
        batch.begin();
        font.setColor(Color.GREEN);
        font.draw(batch, String.format("%.2f", targetY), x + width + 5, y + (targetY * yScale));
        font.setColor(Color.YELLOW);
        font.draw(batch, String.format("Cur: %.2f %s", value, unit), x, y - 5);
        font.draw(batch, String.format("Avg: %.2f %s", (sum / log.size()), unit), x, y - 20);
        font.draw(batch, title, x, y + height + 15);
        font.setColor(Color.RED);
        font.draw(batch, String.format("%.2f", badY), x + width + 5, y + (badY * yScale));
        batch.end();
    }

    public static HashMap<String,EnemyDefinition> readEnemyList(HashMap<Integer,Weapon> weaponList, String campaignName){
        File file = new File(G.campaignFolder+File.separator+campaignName+File.separator+"enemylist.txt");
        G.console.log("\nLoading enemies from file:" + file);
        HashMap<String,EnemyDefinition> enemies = new HashMap<>();
        EnemyDefinition enemyDefinition = null;
        String enemyName = null;

        try(BufferedReader reader = new BufferedReader(new FileReader(file))) {
            for (String line; (line = reader.readLine()) != null; ) {
                if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == ' ') {
                    continue;
                }
                if (line.charAt(0) == '{') {
                    enemyDefinition = new EnemyDefinition();
                    enemyDefinition.weapon = -1;
                    continue;
                }
                if (line.charAt(0) == '}') {
                    enemies.put(enemyName, enemyDefinition);
                    enemyName = null;
                    continue;
                }
                String[] splits = line.split("=");
                if(splits[0].equals("name")){
                    enemyName = splits[1];
                }
                if(splits[0].equals("weapon")){
                    enemyDefinition.weapon = F.getWeaponID(weaponList, splits[1]);
                }
                if(splits[0].equals("image")){
                    enemyDefinition.image = splits[1];
                }
                if(splits[0].equals("health")){
                    enemyDefinition.health = Integer.parseInt(splits[1]);
                }
                if(splits[0].equals("velocity")){
                    enemyDefinition.velocity = Float.parseFloat(splits[1]);
                }
                if(splits[0].equals("vision")){
                    enemyDefinition.vision = Float.parseFloat(splits[1]);
                }
                if(splits[0].equals("bounty")){
                    enemyDefinition.bounty = Integer.parseInt(splits[1]);
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        for(EnemyDefinition e: enemies.values()){
            G.console.log(e.toString());
        }

        return enemies;
    }

    public static HashMap<String,ProjectileDefinition> readSeperatedProjectileList(String campaignName){
        File projectileFolder = new File(G.campaignFolder+File.separator+campaignName+File.separator+"weapons"+File.separator+"projectiles");
        ArrayList<File> files = new ArrayList<>();
        G.console.log("\nLoading projectiles from folder:" + projectileFolder);
        for (File fileEntry : projectileFolder.listFiles()) {
            if (!fileEntry.isDirectory()&&!fileEntry.isHidden()) {
                if(!fileEntry.getName().equals("!PROPERTIES.txt")){
                    files.add(fileEntry);
                }
            }
        }
        HashMap<String,ProjectileDefinition> projectiles = new HashMap<>();
        for(File file : files){
            ProjectileDefinition projectileDefinition = new ProjectileDefinition();
            String projectileName = null;
            try(BufferedReader reader = new BufferedReader(new FileReader(file))) {
                for (String line; (line = reader.readLine()) != null; ) {
                    if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == ' ') {
                        continue;
                    }
                    String[] splits = line.split("=");
                    if(splits[0].equals("name")){
                        projectileName = splits[1];
                        projectileDefinition.name = splits[1];
                    }
                    if(splits[0].equals("type")){
                        if(splits[1].equals("hitscan")){
                            projectileDefinition.type = ProjectileDefinition.HITSCAN;
                        }else if(splits[1].equals("projectile")){
                            projectileDefinition.type = ProjectileDefinition.PROJECTILE;
                        }else if(splits[1].equals("particle")){
                            projectileDefinition.type = ProjectileDefinition.PARTICLE;
                        }else if(splits[1].equals("tracer")){
                            projectileDefinition.type = ProjectileDefinition.TRACER;
                        }
                    }
                    if(splits[0].equals("damage")){
                        projectileDefinition.damage = Integer.parseInt(splits[1]);
                    }
                    if(splits[0].equals("range")){
                        projectileDefinition.range = Integer.parseInt(splits[1]);
                    }
                    if(splits[0].equals("velocity")){
                        projectileDefinition.velocity = Integer.parseInt(splits[1]);
                    }
                    if(splits[0].equals("duration")){
                        projectileDefinition.duration = Float.parseFloat(splits[1]);
                    }
                    if(splits[0].equals("color")){
                        String strip = splits[1].substring(1,splits[1].length()-1);
                        String[] rgb = strip.split(",");
                        projectileDefinition.color = new Color(Integer.parseInt(rgb[0])/255f,Integer.parseInt(rgb[1])/255f,Integer.parseInt(rgb[2])/255f,1f);
                    }
                    if(splits[0].equals("width")){
                        projectileDefinition.width = Integer.parseInt(splits[1]);
                    }
                    if(splits[0].equals("length")){
                        projectileDefinition.length = Integer.parseInt(splits[1]);
                    }
                    if(splits[0].equals("image")){
                        projectileDefinition.image = splits[1];
                    }
                    if(splits[0].equals("animation")){
                        projectileDefinition.animation = splits[1];
                    }
                    if(splits[0].equals("frameDuration")){
                        projectileDefinition.frameDuration = Float.parseFloat(splits[1]);
                    }
                    if(splits[0].equals("onDestroy")){
                        projectileDefinition.onDestroy = splits[1];
                    }
                    if(splits[0].equals("knockback")){
                        projectileDefinition.knockback = Float.parseFloat(splits[1]);
                    }
                    if(splits[0].equals("ignoreMapCollision")){
                        projectileDefinition.ignoreMapCollision = true;
                    }
                    if (splits[0].equals("explosionKnockback")) {
                        projectileDefinition.explosionKnockback = true;
                    }
                    if (splits[0].equals("dontDestroyOnCollision")) {
                        projectileDefinition.dontDestroyOnCollision = true;
                    }
                    if (splits[0].equals("sound")) {
                        projectileDefinition.sound = splits[1];
                    }
                    if (splits[0].equals("ignoreEntityCollision")) {
                        projectileDefinition.ignoreEntityCollision = true;
                    }
                    if (splits[0].equals("networked")) {
                        projectileDefinition.networked = true;
                    }
                    if (splits[0].equals("friction")) {
                        projectileDefinition.friction = Float.parseFloat(splits[1]);
                    }
                    if(splits[0].equals("tracer")){
                        projectileDefinition.tracer = splits[1];
                    }
                    if(splits[0].equals("looping")){
                        projectileDefinition.looping = true;
                    }
                    if(splits[0].equals("bounce")){
                        projectileDefinition.bounce = true;
                    }
                    if (splits[0].equals("hitboxScaling")) {
                        projectileDefinition.hitboxScaling = Float.parseFloat(splits[1]);
                    }
                    if(splits[0].equals("stopOnCollision")){
                        projectileDefinition.stopOnCollision = true;
                    }
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            projectiles.put(projectileName,projectileDefinition);
        }

        for(ProjectileDefinition def: projectiles.values()){
            G.console.log(def.toString());
        }

        return projectiles;
    }

    public static HashMap<String,Weapon> readSeperatedWeaponList(HashMap<String,ProjectileDefinition> projectileList, String campaignName){
        File weaponsFolder = new File(G.campaignFolder+File.separator+campaignName+File.separator+"weapons");
        ArrayList<File> files = new ArrayList<>();
        G.console.log("\nLoading weapons from folder:" + weaponsFolder);
        for (File fileEntry : weaponsFolder.listFiles()) {
            if (!fileEntry.isDirectory()&&!fileEntry.isHidden()) {
                if(!fileEntry.getName().equals("!PROPERTIES.txt")){
                    files.add(fileEntry);
                }
            }
        }
        HashMap<String,Weapon> weapons = new HashMap<>();
        for(File file : files){
            Weapon weapon = new Weapon();
            String name = null;
            try(BufferedReader reader = new BufferedReader(new FileReader(file))) {
                for (String line; (line = reader.readLine()) != null; ) {
                    if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == ' ') {
                        continue;
                    }
                    if(line.charAt(0) == '{'){
                        weapon = new Weapon();
                        continue;
                    }
                    if(line.charAt(0) == '}'){
                        weapons.put(name,weapon);
                        continue;
                    }
                    String[] splits = line.split("=");
                    if(splits[0].equals("name")){
                        weapon.name = splits[1];
                        name = splits[1];
                    }
                    if(splits[0].equals("spread")){
                        weapon.spread = Integer.parseInt(splits[1]);
                    }
                    if(splits[0].equals("projectileCount")){
                        weapon.projectileCount = Integer.parseInt(splits[1]);
                    }
                    if(splits[0].equals("chargeDuration")){
                        weapon.chargeDuration = Float.parseFloat(splits[1]);
                    }
                    if(splits[0].equals("minChargeVelocity")){
                        weapon.minChargeVelocity = Float.parseFloat(splits[1]);
                    }
                    if(splits[0].equals("maxChargeVelocity")){
                        weapon.maxChargeVelocity = Float.parseFloat(splits[1]);
                    }
                    if(splits[0].equals("sound")){
                        weapon.sound = splits[1];
                    }
                    if(splits[0].equals("projectile")){
                        weapon.projectile = projectileList.get(splits[1]);
                        if(weapon.projectile==null){
                            System.out.println(weapon.name+": PROJECTILE NULL");
                        }
                    }
                    if(splits[0].equals("cooldown")){
                        weapon.cooldown = Float.parseFloat(splits[1]);
                    }
                    if(splits[0].equals("image")){
                        weapon.image = splits[1];
                    }
                    if(splits[0].equals("ammo")){
                        weapon.ammo = splits[1];
                    }
                    if(splits[0].equals("sprite")){
                        weapon.sprite = splits[1];
                    }
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            weapons.put(name,weapon);
        }

        for(Weapon w: weapons.values()){
            G.console.log(w.toString());
        }

        return weapons;
    }

    public static HashMap<String,ProjectileDefinition> readProjectileList(){
        File file = new File(Tools.getUserDataDirectory()+ File.separator+"projectilelist.txt");
        if(!file.exists()){
            file = new File("resources"+File.separator+"defaultprojectilelist.txt");
        }
        G.console.log("\nLoading projectiles from file:"+file);
        HashMap<String,ProjectileDefinition> projectiles = new HashMap<>();
        ProjectileDefinition projectileDefinition = null;
        String projectileName = null;

        try(BufferedReader reader = new BufferedReader(new FileReader(file))) {
            for (String line; (line = reader.readLine()) != null; ) {
                if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == ' ') {
                    continue;
                }
                if (line.charAt(0) == '{') {
                    projectileDefinition = new ProjectileDefinition();
                    continue;
                }
                if (line.charAt(0) == '}') {
                    projectiles.put(projectileName, projectileDefinition);
                    projectileName=null;
                    continue;
                }
                String[] splits = line.split("=");
                if(splits[0].equals("name")){
                    projectileName = splits[1];
                    projectileDefinition.name = splits[1];
                }
                if(splits[0].equals("type")){
                    if(splits[1].equals("hitscan")){
                        projectileDefinition.type = ProjectileDefinition.HITSCAN;
                    }else if(splits[1].equals("projectile")){
                        projectileDefinition.type = ProjectileDefinition.PROJECTILE;
                    }else if(splits[1].equals("particle")){
                        projectileDefinition.type = ProjectileDefinition.PARTICLE;
                    }else if(splits[1].equals("tracer")){
                        projectileDefinition.type = ProjectileDefinition.TRACER;
                    }
                }
                if(splits[0].equals("damage")){
                    projectileDefinition.damage = Integer.parseInt(splits[1]);
                }
                if(splits[0].equals("range")){
                    projectileDefinition.range = Integer.parseInt(splits[1]);
                }
                if(splits[0].equals("velocity")){
                    projectileDefinition.velocity = Integer.parseInt(splits[1]);
                }
                if(splits[0].equals("duration")){
                    projectileDefinition.duration = Float.parseFloat(splits[1]);
                }
                if(splits[0].equals("color")){
                    String strip = splits[1].substring(1,splits[1].length()-1);
                    String[] rgb = strip.split(",");
                    projectileDefinition.color = new Color(Integer.parseInt(rgb[0])/255f,Integer.parseInt(rgb[1])/255f,Integer.parseInt(rgb[2])/255f,1f);
                }
                if(splits[0].equals("width")){
                    projectileDefinition.width = Integer.parseInt(splits[1]);
                }
                if(splits[0].equals("length")){
                    projectileDefinition.length = Integer.parseInt(splits[1]);
                }
                if(splits[0].equals("image")){
                    projectileDefinition.image = splits[1];
                }
                if(splits[0].equals("animation")){
                    projectileDefinition.animation = splits[1];
                }
                if(splits[0].equals("frameDuration")){
                    projectileDefinition.frameDuration = Float.parseFloat(splits[1]);
                }
                if(splits[0].equals("onDestroy")){
                    projectileDefinition.onDestroy = splits[1];
                }
                if(splits[0].equals("knockback")){
                    projectileDefinition.knockback = Float.parseFloat(splits[1]);
                }
                if(splits[0].equals("ignoreMapCollision")){
                    projectileDefinition.ignoreMapCollision = true;
                }
                if (splits[0].equals("explosionKnockback")) {
                    projectileDefinition.explosionKnockback = true;
                }
                if (splits[0].equals("dontDestroyOnCollision")) {
                    projectileDefinition.dontDestroyOnCollision = true;
                }
                if (splits[0].equals("sound")) {
                    projectileDefinition.sound = splits[1];
                }
                if (splits[0].equals("ignoreEntityCollision")) {
                    projectileDefinition.ignoreEntityCollision = true;
                }
                if (splits[0].equals("networked")) {
                    projectileDefinition.networked = true;
                }
                if (splits[0].equals("friction")) {
                    projectileDefinition.friction = Float.parseFloat(splits[1]);
                }
                if(splits[0].equals("tracer")){
                    projectileDefinition.tracer = splits[1];
                }
                if(splits[0].equals("looping")){
                    projectileDefinition.looping = true;
                }
                if(splits[0].equals("bounce")){
                    projectileDefinition.bounce = true;
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        for(ProjectileDefinition def: projectiles.values()){
            G.console.log(def.toString());
        }

        return projectiles;
    }

    public static HashMap<String,Weapon> readWeaponList(HashMap<String,ProjectileDefinition> projectileList){
        File file = new File(Tools.getUserDataDirectory()+ File.separator+"weaponlist.txt");
        if(!file.exists()){
            file = new File("resources"+File.separator+"defaultweaponlist.txt");
        }
        G.console.log("\nLoading weapons from file:" + file);
        HashMap<String,Weapon> weapons = new HashMap<>();
        Weapon weapon = null;
        String name = null;

        try(BufferedReader reader = new BufferedReader(new FileReader(file))) {
            for (String line; (line = reader.readLine()) != null; ) {
                if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == ' ') {
                    continue;
                }
                if(line.charAt(0) == '{'){
                    weapon = new Weapon();
                    continue;
                }
                if(line.charAt(0) == '}'){
                    weapons.put(name,weapon);
                    continue;
                }
                String[] splits = line.split("=");
                if(splits[0].equals("name")){
                    weapon.name = splits[1];
                    name = splits[1];
                }
                if(splits[0].equals("spread")){
                    weapon.spread = Integer.parseInt(splits[1]);
                }
                if(splits[0].equals("projectileCount")){
                    weapon.projectileCount = Integer.parseInt(splits[1]);
                }
                if(splits[0].equals("chargeDuration")){
                    weapon.chargeDuration = Float.parseFloat(splits[1]);
                }
                if(splits[0].equals("minChargeVelocity")){
                    weapon.minChargeVelocity = Float.parseFloat(splits[1]);
                }
                if(splits[0].equals("maxChargeVelocity")){
                    weapon.maxChargeVelocity = Float.parseFloat(splits[1]);
                }
                if(splits[0].equals("sound")){
                    weapon.sound = splits[1];
                }
                if(splits[0].equals("projectile")){
                    weapon.projectile = projectileList.get(splits[1]);
                }
                if(splits[0].equals("cooldown")){
                    weapon.cooldown = Float.parseFloat(splits[1]);
                }
                if(splits[0].equals("image")){
                    weapon.image = splits[1];
                }
                if(splits[0].equals("ammo")){
                    weapon.ammo = splits[1];
                }
                if(splits[0].equals("sprite")){
                    weapon.sprite = splits[1];
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        for(Weapon w: weapons.values()){
            G.console.log(w.toString());
        }

        return weapons;
    }

    public static BidiMap<String,Integer> createWeaponNameToIDMapping(HashMap<Integer,Weapon> weaponList){
        BidiMap<String,Integer> nameToID = new DualHashBidiMap<>();

        for(int weaponID:weaponList.keySet()){
            nameToID.put(weaponList.get(weaponID).name, weaponID);
        }
        return nameToID;
    }

    public static BidiMap<Integer,ShopItem> readShopList(String campaignName){
        File file = new File(G.campaignFolder+File.separator+campaignName+File.separator+"shoplist.txt");
        G.console.log("\nLoading shoplist from file:" + file);
        BidiMap<Integer,ShopItem> shoplist = new DualHashBidiMap<>();
        int id = 0;

        try(BufferedReader reader = new BufferedReader(new FileReader(file))) {
            for (String line; (line = reader.readLine()) != null; ) {
                if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == ' ') {
                    continue;
                }
                String[] splits = line.split(":");
                shoplist.put(id,new ShopItem(splits[0],splits[1],Integer.parseInt(splits[2])));
                id++;
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        for(int i: shoplist.keySet()){
            G.console.log(i + ":" + shoplist.get(i));
        }

        return shoplist;
    }

    public static HashSet<String> getAmmoList(HashMap<Integer,Weapon> weaponList){
        HashSet<String> ammoList = new HashSet<>();
        for(Weapon w : weaponList.values()){
            if(w.ammo!=null){
                ammoList.add(w.ammo);
            }
        }
        return ammoList;
    }

    public static Rectangle getMapShop(ArrayList<RectangleMapObject> mapObjects){
        for(RectangleMapObject o : mapObjects){
            if(o.getProperties().containsKey("type") && o.getProperties().get("type",String.class).equals("shop")){
                return o.getRectangle();
            }
        }
        return null;
    }

    public static Rectangle getMapExit(ArrayList<RectangleMapObject> mapObjects){
        for(RectangleMapObject o : mapObjects){
            if(o.getProperties().containsKey("type") && o.getProperties().get("type",String.class).equals("exit")){
                return o.getRectangle();
            }
        }
        return null;
    }

    public static ArrayList<RectangleMapObject> getMessageObjects(ArrayList<RectangleMapObject> mapObjects){
        ArrayList<RectangleMapObject> messageObjects = new ArrayList<>();
        for(RectangleMapObject o : mapObjects){
            if(o.getProperties().containsKey("type") && o.getProperties().get("type",String.class).equals("message")){
                messageObjects.add(o);
            }
        }
        return messageObjects;
    }

    public static ArrayList<Rectangle> getSolidMapObjects(ArrayList<RectangleMapObject> mapObjects){
        ArrayList<Rectangle> solidObjects = new ArrayList<>();
        for(RectangleMapObject o : mapObjects){
            if(o.getProperties().containsKey("type")) {
                String type = o.getProperties().get("type", String.class);
                if(type.equals("message")||type.equals("dialogue")||type.equals("shop")){
                    solidObjects.add(o.getRectangle());
                }
            }
        }
        return solidObjects;
    }

    public static ArrayList<RectangleMapObject> getMapObjects(TiledMap map){
        ArrayList<RectangleMapObject> mapObjects = new ArrayList<>();
        for(MapLayer layer :map.getLayers().getByType(MapLayer.class)){
            for(MapObject o : layer.getObjects()){
                if(o instanceof RectangleMapObject){
                    RectangleMapObject rectObject = (RectangleMapObject) o;
                    Rectangle bounds = rectObject.getRectangle();
                    rectObject.getRectangle().set(bounds.x,bounds.y,bounds.width,bounds.height);
                    mapObjects.add(rectObject);
                }
            }
        }
        return mapObjects;
    }

    public static ArrayList<RectangleMapObject> getInteractableMapObjects(ArrayList<RectangleMapObject> mapObjects){
        ArrayList<RectangleMapObject> interactableMapObjects = new ArrayList<>();
        for(RectangleMapObject o : mapObjects){
            if(o.getProperties().containsKey("type")) {
                String type = o.getProperties().get("type", String.class);
                if(type.equals("message")||type.equals("dialogue")||type.equals("shop")){
                    interactableMapObjects.add(o);
                }
            }
        }
        return interactableMapObjects;
    }

    public static ArrayList<RectangleMapObject> getDrawableMapObjects(ArrayList<RectangleMapObject> mapObjects){
        ArrayList<RectangleMapObject> drawableMapObjects = new ArrayList<>();
        for(RectangleMapObject o : mapObjects){
            if(o.getProperties().containsKey("type")) {
                String type = o.getProperties().get("type", String.class);
                if(type.equals("message")||type.equals("dialogue")||type.equals("shop")){
                    drawableMapObjects.add(o);
                }
            }
        }
        return drawableMapObjects;
    }

    public static Element getConversation(String filepath, String dialogueID){
        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = null;
        try {
            builder = builderFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        }

        try {
            Document document = builder.parse(new FileInputStream(filepath));
            Element rootElement = document.getDocumentElement();
            NodeList nodes = rootElement.getChildNodes();
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if (node instanceof Element) {
                    System.out.println(node);
                    Element element = (Element) node;
                    if (element.getAttribute("id").equals(dialogueID)) {
                        System.out.println("FOUND dialogue:" + element.getAttribute("id"));
                        return element;
                    }
                }
            }
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String getDialogueName(Element dialogue){
        NodeList nodes = dialogue.getChildNodes();

        for(int i=0; i<nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element) {
                Element element = (Element) node;
                if (element.getTagName().equals("name")) {
                    return element.getFirstChild().getNodeValue();
                }
            }
        }
        return null;
    }

    public static String getDialoguePortrait(Element dialogue){
        NodeList nodes = dialogue.getChildNodes();

        for(int i=0; i<nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element) {
                Element element = (Element) node;
                if (element.getTagName().equals("portrait")) {
                    if(element.getFirstChild()!=null){
                        return element.getFirstChild().getNodeValue();
                    }else{
                        return null;
                    }
                }
            }
        }
        return null;
    }

    public static Tree<String> getDialogueNodes(Element dialogue){

            NodeList nodes = dialogue.getChildNodes();
            String startText = null;
            Element portrait;
            Element options = null;


            for(int i=0; i<nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if(node instanceof Element){
                    System.out.println(node);
                    Element element = (Element) node;
                    if(element.getTagName().equals("text")){
                        startText = element.getTextContent();
                    }
                    if(element.getTagName().equals("option_container")){
                        options = element;
                    }
                    if(element.getTagName().equals("portrait")){
                        portrait = element;
                    }

                }
            }

        Tree<String> dialogTree = new Tree<>("root");
        dialogTree.addToNode(dialogTree.getRoot(),"text:"+startText);
        System.out.println("Start text:" + startText);
        parseNode(options, "", dialogTree.getRoot(), dialogTree);
        printTreeNode(dialogTree.getRoot(),"");

        return dialogTree;
    }

    public static void printTreeNode(Tree.Node node, String indent){
        System.out.println(indent+node.getValue());
        List<Tree.Node> children = node.getChildren();
        for(Tree.Node childNode : children){
            printTreeNode(childNode,indent+" ");
        }
    }

    public static void parseNode(Element node, String indent, Tree.Node dialogNode, Tree dialogTree) {
        System.out.println(indent + node);
        if(node.getTagName().equals("option")){
            String choiceText = node.getAttributes().getNamedItem("choice_text").getNodeValue();
            dialogNode = dialogTree.addToNode(dialogNode,"choice:"+choiceText);
        }else if(node.getTagName().equals("text")){
            String text = node.getFirstChild().getNodeValue();
            System.out.println("Adding text to node: " + text);
            dialogTree.addToNode(dialogNode,"text:"+text);
        }else if(node.getTagName().equals("goto")){
            String goTo = node.getFirstChild().getNodeValue();
            System.out.println("Adding goto to node: " + goTo);
            dialogTree.addToNode(dialogNode, "goto:"+goTo);
        }
        NodeList nodeList = node.getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node currentNode = nodeList.item(i);
            if(currentNode instanceof Element){
                Element element = (Element) currentNode;
                //calls this method for all the children which is Element
                parseNode(element, indent + " ", dialogNode ,dialogTree);
            }
        }
    }



    public static Rectangle getSpawnZone(ArrayList<RectangleMapObject> mapObjects){
        for(RectangleMapObject o : mapObjects){
            if(o.getProperties().containsKey("type") && o.getProperties().get("type",String.class).equals("spawn")){
                G.console.log("Spawn at:" + o.getRectangle());
                return o.getRectangle();
            }
        }
        return null;
    }

    public static ArrayList<Rectangle> getEnemySpawnZones(ArrayList<RectangleMapObject> mapObjects){
        ArrayList<Rectangle> zones = new ArrayList<>();
        for(RectangleMapObject o : mapObjects){
            if(o.getProperties().containsKey("type") && o.getProperties().get("type",String.class).equals("enemySpawn")){
                zones.add(o.getRectangle());
            }
        }
        return zones;
    }
}
