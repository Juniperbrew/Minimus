package com.juniperbrew.minimus;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.*;

import java.awt.geom.Line2D;
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

    public static int nanoToMilli(long nano){
        return (int) (nano/1000000);
    }

    public static float nanoToMilliFloat(long nano){
        return nano/1000000f;
    }

    public static long secondsToNano(double seconds){
        return (long)(seconds*1000000000l);
    }

    public static int nanoToSeconds(long nano){
        return (int)(nano/1000000000l);
    }

    public static float nanoToSecondsFloat(long nano){
        return nano/1000000000f;
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
        Vector2 p1 = rotatePoint(rectangle.getX(), rectangle.getY(), originX, originY, rotation);
        Vector2 p2 = rotatePoint(rectangle.getX()+rectangle.getWidth(),rectangle.getY(),originX,originY,rotation);
        Vector2 p3 = rotatePoint(rectangle.getX()+rectangle.getWidth(),rectangle.getY()+rectangle.getHeight(),originX,originY,rotation);
        Vector2 p4 = rotatePoint(rectangle.getX(),rectangle.getY()+rectangle.getHeight(),originX,originY,rotation);
        vertices[0] = p1.x;
        vertices[1] = p1.y;
        vertices[2] = p2.x;
        vertices[3] = p2.y;
        vertices[4] = p3.x;
        vertices[5] = p3.y;
        vertices[6] = p4.x;
        vertices[7] = p4.y;
        Polygon polygon = new Polygon(vertices);
        polygon.translate(originX, originY);
        return new Polygon(vertices);
    }

    public static Vector2 getVelocityVector(float velocity, float angle){
        Vector2 v = new Vector2();
        v.x = MathUtils.cosDeg(angle)*velocity;
        v.y = MathUtils.sinDeg(angle)*velocity;
        return v;
    }


    public static Polygon getBoundingPolygon(Sprite sprite){
        float[] vertices = sprite.getVertices();
        float[] newVertices = new float[8];
        newVertices[0] = vertices[Batch.X1];
        newVertices[1] = vertices[Batch.Y1];
        newVertices[2] = vertices[Batch.X2];
        newVertices[3] = vertices[Batch.Y2];
        newVertices[4] = vertices[Batch.X3];
        newVertices[5] = vertices[Batch.Y3];
        newVertices[6] = vertices[Batch.X4];
        newVertices[7] = vertices[Batch.Y4];
        return new Polygon(newVertices);
    }

    public static Vector2 rotatePoint(float x, float y, float originX, float originY,float rotation){
        float x1 = (x-originX) * MathUtils.cosDeg(rotation) - (y-originY) * MathUtils.sinDeg(rotation) + originX;
        float y1 = (y-originY) * MathUtils.cosDeg(rotation) + (x-originX) * MathUtils.sinDeg(rotation) + originY;
        return new Vector2(x1,y1);
    }

    public static String printCamera(OrthographicCamera camera){
        return "Position = "+camera.position+
                ", width = "+camera.viewportWidth+
                ", height = "+camera.viewportHeight+
                ", zoom = "+camera.zoom;
    }

    public static float getAngleDiff(float angle1, float angle2){
        float anglediff = (angle1 - angle2 + 180) % 360 - 180;
        if(anglediff<=-180) anglediff +=360;
        return anglediff;
    }

    public static Vector2 screenToWorldCoordinates(OrthographicCamera camera, float x, float y){
        Vector2 worldCoord = new Vector2();
        worldCoord.x = camera.position.x-(camera.viewportWidth/2)+x;
        worldCoord.y = camera.position.y+(camera.viewportHeight/2)-y;
        return worldCoord;
    }

    public static String wrapText(String text, int lineWidth){
        return null;
    }
}
