package com.juniperbrew.minimus;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
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
}
