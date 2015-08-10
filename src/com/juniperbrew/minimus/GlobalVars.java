package com.juniperbrew.minimus;

import java.io.File;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Created by Juniperbrew on 8.8.2015.
 */
public class GlobalVars {


    public static final String mapFolder = "resources"+ File.separator+ "maps";

    public static int tileWidth;
    public static int tileHeight;
    public static int mapWidthTiles;
    public static int mapHeightTiles;
    public static int mapWidth;
    public static int mapHeight;
    public static boolean[][] collisionMap;
}
