package com.juniperbrew.minimus;

import com.badlogic.gdx.graphics.g2d.TextureAtlas;

import java.io.File;
import java.util.HashMap;

/**
 * Created by Juniperbrew on 8.8.2015.
 */
public class GlobalVars {


    public static final int TIMEOUT = 0; //Default 12000
    public static final String mapFolder = "resources"+ File.separator+ "maps";

    public static int tileWidth;
    public static int tileHeight;
    public static int mapWidthTiles;
    public static int mapHeightTiles;
    public static int mapWidth;
    public static int mapHeight;
    public static float mapScale;
    public static boolean[][] collisionMap;
    public static TextureAtlas atlas;
    public static int primaryWeaponCount;
    public static HashMap<Integer,Weapon> weaponList;
}
