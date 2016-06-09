package com.juniperbrew.minimus;

import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.Rectangle;
//import com.juniperbrew.minimus.windows.ConsoleFrame;
import org.apache.commons.collections4.BidiMap;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Created by Juniperbrew on 8.8.2015.
 */
public class G {


    public static final int TIMEOUT = 0; //Default 12000
    public static final String campaignFolder = "resources"+ File.separator+ "campaign";

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
    public static HashSet<String> ammoList;
    public static Console console;
    public static BidiMap<Integer,ShopItem> shoplist;
    public static BidiMap<String,Integer> weaponNameToID;
    public static ArrayList<Rectangle> solidMapObjects;

    public static boolean debugFeatureToggle = true;

    //public static ConsoleFrame console;

}
