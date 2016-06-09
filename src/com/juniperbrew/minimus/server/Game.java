package com.juniperbrew.minimus.server;

import com.juniperbrew.minimus.G;
import com.juniperbrew.minimus.Tools;
import tiled.core.Map;
import tiled.io.TMXMapReader;

import java.io.File;
import java.io.IOException;

/**
 * Created by Juniperbrew on 18/05/16.
 */
public class Game {

    String campaignName = "Hohto";

    private final long TIMESTEP = Tools.secondsToNano(1/60f);
    private int work = 10000;

    public Game(){
        loadMap("e2m1");
        gameLoop();
    }

    private void gameLoop(){

        boolean logging = false;
        long startTime = System.nanoTime();
        long simulationTime = 0;

        long ackumulator = 0;

        int frameCounter = 0;
        long lastFpsLog = System.nanoTime();
        int fps = 0;
        long delta = 0;
        long loopTime = 0;
        long sleep = 0;
        long frameTime = 0;

        java.util.Scanner console = new java.util.Scanner(System.in);

        long lastTick = System.nanoTime();
        while(true){
            if(System.nanoTime()-lastFpsLog > 1000000000l){
                lastFpsLog = System.nanoTime();
                fps = frameCounter;
                frameCounter = 0;
                if(logging){
                    System.out.println("FPS: "+fps);
                    System.out.println("Last delta: "+ Tools.nanoToMilli(delta));
                    System.out.println("Last looptime: "+Tools.nanoToMilli(loopTime));
                    System.out.println("Last frametime: "+Tools.nanoToMilli(frameTime));
                    System.out.println("Last sleeptime: "+Tools.nanoToMilli(sleep));
                    System.out.println("Loop+sleep-timestep: "+Tools.nanoToMilli(loopTime+sleep-TIMESTEP));
                    System.out.println("Runtime: "+ Tools.nanoToSecondsFloat(System.nanoTime()-startTime));
                    System.out.println("Simulation time: "+ Tools.nanoToSecondsFloat(simulationTime));
                    System.out.println();
                }
            }
            delta = System.nanoTime() - lastTick;
            lastTick = System.nanoTime();

            try {
                if(System.in.available() > 0){
                    String input = console.nextLine();
                    if(input.equals("1")){
                        logging = true;
                        System.out.println("Logging enabled");
                    }
                    if(input.equals("0")){
                        logging = false;
                        System.out.println("Logging disabled");
                    }
                    String[] args = input.split(" ");
                    if(args[0].equals("w")){
                        work = Integer.parseInt(args[1]);
                        if(work > 4000000){
                            work = 4000000;
                        }
                        System.out.println("Looping "+work+" iterations");
                    }
                    //System.out.println("Read: "+ console.nextLine());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            if(Tools.nanoToSecondsFloat(delta)>0.25){
                delta=Tools.secondsToNano(0.25f);
                System.out.println("TOO MUCH WORK THROTTHLING SIMULATION");
            }
            int frames = 0;
            ackumulator += delta;
            while (ackumulator>=TIMESTEP){
                ackumulator-=TIMESTEP;

                long frameStart = System.nanoTime();
                doLogic(TIMESTEP);
                frameTime = System.nanoTime()-frameStart;
                simulationTime += TIMESTEP;

                frames++;
                frameCounter++;
            }
            /*if(frames > 1){
                System.out.println("Frames: "+frames);
            }*/
            loopTime = System.nanoTime()-lastTick;
            try {
                sleep = TIMESTEP-loopTime;
                if(sleep<0){
                    sleep = 0;
                    //System.out.println("Sleep negative");
                }
                Thread.sleep(Tools.nanoToMilli(sleep));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }

    private void doLogic(float delta){

        java.util.Random rand = new java.util.Random();

        int[][] test = new int[2000][2000];
        for (int i = 0; i < work; i++) {
            test[i%2000][i/2000] = rand.nextInt();
        }
    }

    private void loadMap(String mapName){
        TMXMapReader mapReader = new TMXMapReader();
        Map newMap = null;
        System.out.println("Loading map: " + mapName);
        String fileName = G.campaignFolder+ File.separator+campaignName+File.separator+"maps"+File.separator+mapName+File.separator+mapName+".tmx";
        try {
            newMap = mapReader.readMap(fileName);
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println(newMap.getLayerCount());
        //mapObjects = SharedMethods.getScaledMapobjects(map);
        //G.solidMapObjects = SharedMethods.getSolidMapObjects(mapObjects);
    }

    public static void main(String[] args) {
        new Game();
    }
}
