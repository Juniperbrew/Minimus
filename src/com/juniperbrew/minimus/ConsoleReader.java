package com.juniperbrew.minimus;

import com.juniperbrew.minimus.windows.ConsoleFrame;

import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.logging.ConsoleHandler;

/**
 * Created by Juniperbrew on 10.8.2015.
 */
public class ConsoleReader {


    public ConsoleReader(final Console console){
        new Thread(new Runnable() {
            @Override
            public void run() {
                while(true){
                    try {
                        Scanner scanInput = new Scanner(System.in);
                        console.giveCommand(scanInput.nextLine());
                    }catch(NoSuchElementException e){
                        //...
                    }
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        },"System.in to console").start();

    }

}
