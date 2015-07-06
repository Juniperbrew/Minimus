package com.juniperbrew.minimus;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * Created by Juniperbrew on 6.7.2015.
 */
public class ExceptionLogger implements Thread.UncaughtExceptionHandler {

    String folderName;

    public ExceptionLogger(String logFolderName){
        this.folderName = logFolderName;
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        e.printStackTrace();
        SimpleDateFormat format = new SimpleDateFormat("ddMMyyyy-HHmmss");
        String date = format.format(Calendar.getInstance().getTime());

        File file = new File(Tools.getUserDataDirectory()+"logs\\errors\\"+folderName+"\\"+date+".txt");
        file.getParentFile().mkdirs();
        try(PrintWriter writer = new PrintWriter(new FileWriter(file))){
            e.printStackTrace(writer);
        } catch (IOException e1) {
            e1.printStackTrace();
        }
    }
}
