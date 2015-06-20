import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * Created by Juniperbrew on 20.6.2015.
 */
public class Tool {

    private static SimpleDateFormat df = new SimpleDateFormat("HH:mm:ss");

    static{
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
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
        return df.format(new Date(millis));
    }
}
