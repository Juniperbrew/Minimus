package com.juniperbrew.minimus.server;

import com.badlogic.gdx.math.Rectangle;
import com.juniperbrew.minimus.G;
import tiled.core.*;

import java.util.ArrayList;

/**
 * Created by Juniperbrew on 04/06/16.
 */
public class MapFunctions {

    public static float getMapScale(Map map) {
        String scale = map.getProperties().getProperty("mapScale");
        if (scale != null) {
            return Float.parseFloat(scale);
        } else {
            return 1;
        }
    }

    public static ArrayList<MapObject> getMapObjects(Map map){
        float s = getMapScale(map);
        ArrayList<MapObject> mapObjects = new ArrayList<>();
        map.getLayers().stream().filter(e -> e instanceof ObjectGroup).forEach(layer ->
            ((ObjectGroup)layer).getObjects().forEachRemaining(o -> {
                o.setY(G.mapHeight - o.getY() - o.getHeight()); //flipY
                mapObjects.add(o);
            }));
        return mapObjects;
    }

    public static ArrayList<Rectangle> getSolidMapObjects(ArrayList<MapObject> mapObjects){
        ArrayList<Rectangle> solidObjects = new ArrayList<>();
        for(MapObject o : mapObjects){
                String type = o.getType();
                if(type.equals("message")||type.equals("dialogue")||type.equals("shop")){
                    if(o.getShape() instanceof java.awt.geom.Rectangle2D){
                        java.awt.geom.Rectangle2D.Double r = o.getBounds();
                        solidObjects.add(new Rectangle((float)r.x, (float)r.y, (float)r.width, (float)r.height));
                    }
                }
        }
        return solidObjects;
    }

    public static boolean[][] createCollisionMap(Map map) {
        boolean[][] collisions = new boolean[G.mapWidthTiles][G.mapHeightTiles];
        for(MapLayer layer : map.getLayers()){
            if(layer instanceof TileLayer){
                TileLayer tileLayer =  (TileLayer)layer;
                for (int col = 0; col < tileLayer.getWidth(); col++) {
                    for (int row = 0; row < tileLayer.getHeight(); row++) {
                        Tile tile = tileLayer.getTileAt(col,tileLayer.getHeight()-row-1); //flipY
                        if(tile != null && tile.getProperties().containsKey("solid")){
                            collisions[col][row] = true;
                        }
                    }
                }
            }
        }
        return collisions;
    }

    public static Rectangle getSpawnZone(ArrayList<MapObject> mapObjects){
        for(MapObject o : mapObjects){
            if(o.getType().equals("spawn")){
                java.awt.geom.Rectangle2D.Double r = (java.awt.geom.Rectangle2D.Double) o.getShape();
                G.console.log("Spawn at:" + o.getBounds());
                return new Rectangle((float) r.x, (float) r.y, (float) r.width, (float) r.height);
            }
        }
        return null;
    }

    public static ArrayList<Rectangle> getEnemySpawnZones(ArrayList<MapObject> mapObjects){
        ArrayList<Rectangle> zones = new ArrayList<>();
        for(MapObject o : mapObjects){
            if(o.getProperties().containsKey("type") && o.getProperties().get("type").equals("enemySpawn")){
                java.awt.geom.Rectangle2D.Double r = o.getBounds();
                zones.add(new Rectangle((float)r.x, (float)r.y, (float)r.width, (float)r.height));
            }
        }
        return zones;
    }

    public static Rectangle getMapExit(ArrayList<MapObject> mapObjects){
        for(MapObject o : mapObjects){
            if(o.getProperties().containsKey("type") && o.getProperties().get("type").equals("exit")){
                java.awt.geom.Rectangle2D.Double r = o.getBounds();
                return new Rectangle((float)r.x, (float)r.y, (float)r.width, (float)r.height);
            }
        }
        return null;
    }
}
