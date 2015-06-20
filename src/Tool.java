import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * Created by Juniperbrew on 20.6.2015.
 */
public class Tool {

    private static SimpleDateFormat timeStamp = new SimpleDateFormat("HH:mm:ss");
    private static SimpleDateFormat milliTimeStamp = new SimpleDateFormat("HH:mm:ss.SSS");

    static{
        timeStamp.setTimeZone(TimeZone.getTimeZone("UTC"));
        milliTimeStamp.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public static long secondsToNano(int seconds){
        return seconds*1000000000l;
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
}
