package com.juniperbrew.minimus;

import com.badlogic.gdx.math.*;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;

/**
 * Created by Juniperbrew on 20.6.2015.
 */
public class Tools {

    private static SimpleDateFormat timeStamp = new SimpleDateFormat("HH:mm:ss");
    private static SimpleDateFormat milliTimeStamp = new SimpleDateFormat("HH:mm:ss.SSS");

    static{
        timeStamp.setTimeZone(TimeZone.getTimeZone("UTC"));
        milliTimeStamp.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public static long secondsToNano(double seconds){
        return (long)(seconds*1000000000l);
    }

    public static int nanoToSeconds(long nano){
        return (int)(nano/1000000000l);
    }

    public static String secondsToTimestamp(int seconds){
        //TODO Breaks after 24 hours?
        int millis = seconds*1000;
        return timeStamp.format(new Date(millis));
    }
    public static String secondsToMilliTimestamp(float seconds){
        //TODO Breaks after 24 hours?
        int millis = (int) (seconds*1000);
        return milliTimeStamp.format(new Date(millis));
    }

    public static int secondsToMilli(double seconds){
        return (int) (seconds*1000);
    }

    public static double clamp(double value, double minValue,double maxValue){
        if(value < minValue){
            value = minValue;
        }
        if(value > maxValue){
            value = maxValue;
        }
        return value;
    }

    public static String getUserDataDirectory() {
        return System.getProperty("user.home") + File.separator + ".minimus" + File.separator;
    }

    public static float getSquaredDistance(float x1, float y1, float x2, float y2){
        return (float) (Math.pow((x1-x2),2)+Math.pow((y1-y2),2));
    }

    public static float getLength(Line2D.Float l){
        return (float) Math.sqrt(Math.pow((l.x1-l.x2),2)+Math.pow((l.y1-l.y2),2));
    }

    public static float getAngle(Line2D.Float line){
        return (MathUtils.atan2(line.y2 - line.y1, line.x2 - line.x1)*MathUtils.radiansToDegrees);
    }

    public static float getAngle(float x1, float y1, float x2, float y2){
        return (MathUtils.atan2(y2 - y1, x2 - x1)*MathUtils.radiansToDegrees);
    }

    public static float getDistance ( float x1, float y1, float x2, float y2){
        return (float) Math.sqrt(Math.pow((x1-x2),2)+Math.pow((y1-y2),2));
    }

    public static void addToMap(Map<Integer,Integer> map, int key, int increment){
        int count = map.containsKey(key) ? map.get(key) : 0;
        map.put(key, count + increment);
    }

    public static Polygon getRotatedRectangle(Rectangle rectangle, int rotation, float originX, float originY){
        float[] vertices = new float[8];
        Point2D.Float p1 = rotatePoint(rectangle.getX(),rectangle.getY(),originX,originY,rotation);
        Point2D.Float p2 = rotatePoint(rectangle.getX()+rectangle.getWidth(),rectangle.getY(),originX,originY,rotation);
        Point2D.Float p3 = rotatePoint(rectangle.getX()+rectangle.getWidth(),rectangle.getY()+rectangle.getHeight(),originX,originY,rotation);
        Point2D.Float p4 = rotatePoint(rectangle.getX(),rectangle.getY()+rectangle.getHeight(),originX,originY,rotation);
        vertices[0] = p1.x;
        vertices[1] = p1.y;
        vertices[2] = p2.x;
        vertices[3] = p2.y;
        vertices[4] = p3.x;
        vertices[5] = p3.y;
        vertices[6] = p4.x;
        vertices[7] = p4.y;
        Polygon polygon = new Polygon(vertices);
        polygon.translate(originX,originY);
        return new Polygon(vertices);
    }

    public static Vector2 getVelocityVector(float velocity, float angle){
        Vector2 v = new Vector2();
        v.x = MathUtils.cosDeg(angle)*velocity;
        v.y = MathUtils.sinDeg(angle)*velocity;
        return v;
    }

    /*public static boolean intersects(Polygon polygon, Rectangle rectangle){
        Vector2 v1 = new Vector2(rectangle.x,rectangle.y);
        Vector2 v2 = new Vector2(rectangle.x,rectangle.y+rectangle.height);
        Vector2 v3 = new Vector2(rectangle.x+rectangle.width,rectangle.y+rectangle.height);
        Vector2 v4 = new Vector2(rectangle.x+rectangle.width,rectangle.y);

        if(Intersector.intersectLinePolygon(v1,v2,polygon)||Intersector.intersectLinePolygon(v2,v3,polygon)||
                Intersector.intersectLinePolygon(v3,v4,polygon)||Intersector.intersectLinePolygon(v4,v1,polygon)){
            return true;
        }else{
            return false;
        }
    }*/

    public static Point2D.Float rotatePoint(float x, float y, float originX, float originY,int rotation){
        float x1 = (x-originX) * MathUtils.cosDeg(rotation) - (y-originY) * MathUtils.sinDeg(rotation) + originX;
        float y1 = (y-originY) * MathUtils.cosDeg(rotation) + (x-originX) * MathUtils.sinDeg(rotation) + originY;
        return new Point2D.Float(x1,y1);
    }


    public static void main(String[] args) {
        System.out.println(rotatePoint(5, 0, 1, 0, 360));
    }
}
