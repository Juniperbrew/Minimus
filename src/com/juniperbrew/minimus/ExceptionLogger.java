package com.juniperbrew.minimus;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * Created by Juniperbrew on 6.7.2015.
 */
public class ExceptionLogger implements Thread.UncaughtExceptionHandler {

    String folderName;
    boolean autoSend;
    String ip;

    public ExceptionLogger(String logFolderName){
        this(logFolderName, false, "");
    }

    public ExceptionLogger(String logFolderName, boolean autoSend, String ip){
        this.folderName = logFolderName;
        this.autoSend = autoSend;
        this.ip = ip;
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        e.printStackTrace();
        SimpleDateFormat format = new SimpleDateFormat("ddMMyyyy-HHmmss");
        String date = format.format(Calendar.getInstance().getTime());

        final File file = new File(Tools.getUserDataDirectory()+"logs"+File.separator+"errors"+File.separator+folderName+File.separator+date+".txt");
        file.getParentFile().mkdirs();
        try(PrintWriter writer = new PrintWriter(new FileWriter(file))){
            e.printStackTrace(writer);
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        //TODO System.exit(0) doesn't always work
        if(autoSend){
            ErrorLogSender sender = new ErrorLogSender(new ErrorLogSender.ErrorLogSenderListener() {
                @Override
                public void allFilesReceived() {
                    System.out.println("Sent errorlog " + file.getName() + " succesfully to server");
                    System.exit(0);
                }

                @Override
                public void transferFailed() {
                    System.out.println("Failed to send errorlog " + file.getName() + " to server");
                    System.exit(0);
                }
            });
            sender.sendErrorLogs(ip);
        }else{
            System.exit(0);
        }
    }
}
