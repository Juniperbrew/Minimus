package com.juniperbrew.minimus.windows;

import com.esotericsoftware.kryonet.Connection;
import com.juniperbrew.minimus.Tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.util.Calendar;
import java.util.Locale;

/**
 * Created by Juniperbrew on 20.6.2015.
 */
public class StatusData {

    public int fps;
    private int entityCount;
    public Connection connection;
    public long connectionTime;
    public long disconnectTime = 0;
    public int logIntervalSeconds;
    public int currentTick;
    public int entitySize;

    public int currentInputRequest;
    public int inputQueue;

    private float serverTime;
    private float clientTime;

    public long bytesSent;
    public int packetsSent;
    private int bytesSentIntervalCounter;
    public int bytesSentInterval;
    private int packetsSentIntervalCounter;
    public int packetsSentInterval;
    private int lastSentPacketSize;

    public long bytesReceived;
    public int packetsReceived;
    private int bytesReceivedIntervalCounter;
    public int bytesReceivedInterval;
    private int packetsReceivedIntervalCounter;
    public int packetsReceivedInterval;
    private int lastReceivedPacketSize;

    public int lastPingID;
    public long lastPingSendTime;
    public int homemadeReturnTripTime;

    private int playerCount;
    private int maxPlayerCount;
    private int maxEntityCount = -1;
    private int minEntityCount = -1;

    private int maxPacketsReceivedPerSecond;
    private int maxPacketsSentPerSecond;
    private int maxBytesReceivedPerSecond;
    private int maxBytesSentPerSecond;
    private int maxReceivedPacketSize;

    public int getEntityCount() {
        return entityCount;
    }

    private int maxSentPacketSize;

    AverageData averageData = new AverageData();

    public StatusData(Connection connection, long connectionTime, int logIntervalSeconds){
        this.connection = connection;
        this.connectionTime = connectionTime;
        this.logIntervalSeconds = logIntervalSeconds;
    }

    public int getLastSentPacketSize() {
        return lastSentPacketSize;
    }

    public int getLastReceivedPacketSize() {
        return lastReceivedPacketSize;
    }

    public void setLastSentPacketSize(int packetSize){
        lastSentPacketSize = packetSize;
        if(packetSize>maxSentPacketSize){
            maxSentPacketSize = packetSize;
        }
    }
    public void setLastReceivedPacketSize(int packetSize){
        lastReceivedPacketSize = packetSize;
        if(packetSize>maxReceivedPacketSize){
            maxReceivedPacketSize = packetSize;
        }
    }

    public void addPlayer(){
        playerCount++;
        if(playerCount>maxPlayerCount){
            maxPlayerCount = playerCount;
        }
    }
    public void removePlayer(){
        playerCount--;
    }

    public void setEntityCount(int entityCount){
        if(maxEntityCount == -1 && minEntityCount == -1){
            maxEntityCount = entityCount;
            minEntityCount = entityCount;
        }
        this.entityCount = entityCount;
        if(entityCount>maxEntityCount){
            maxEntityCount = entityCount;
        }
        if(entityCount<minEntityCount){
            minEntityCount = entityCount;
        }
    }

    public String getConnectionTime(){
        String timestamp;
        if(disconnectTime>0){
            timestamp = Tools.secondsToTimestamp(Tools.nanoToSeconds(disconnectTime - connectionTime));
        }else{
            timestamp = Tools.secondsToTimestamp(Tools.nanoToSeconds(System.nanoTime() - connectionTime));
        }
        return timestamp;
    }

    public String getServerTime(){
        return Tools.secondsToMilliTimestamp(serverTime);
    }

    public String getClientTime(){
        return Tools.secondsToMilliTimestamp(clientTime);
    }

    public void setServerTime(float serverTime){
        this.serverTime = serverTime;
    }
    public void setClientTime(float clientTime){
        this.clientTime = clientTime;
    }
    public void setFps(int fps){
        this.fps = fps;
    }

    public void disconnected(){
        disconnectTime = System.nanoTime();
    }
    public int getPing(){
        return connection.getReturnTripTime();
    }
    public String getUdpHostName(){
        return connection.getRemoteAddressUDP().getHostName();
    }
    public String getUdpAddress() {
        InetSocketAddress address = connection.getRemoteAddressUDP();
        return address.getAddress().getHostAddress() + ":" + address.getPort();
    }
    public int getPacketsSentPerSecond(){
        int packetsSentPerSecond = packetsSentInterval/logIntervalSeconds;
        averageData.packetsSentPerSecond += packetsSentPerSecond;
        averageData.packetsSentPerSecondCounter++;
        if(packetsSentPerSecond>maxPacketsSentPerSecond){
            maxPacketsSentPerSecond = packetsSentPerSecond;
        }
        return packetsSentPerSecond;
    }
    public int getBytesSentPerSecond(){
        int bytesSentPerSecond = bytesSentInterval/logIntervalSeconds;
        if(bytesSentPerSecond>maxBytesSentPerSecond){
            maxBytesSentPerSecond = bytesSentPerSecond;
        }
        averageData.bytesSentPerSecond += bytesSentPerSecond;
        averageData.bytesSentPerSecondCounter++;
        return bytesSentPerSecond;
    }
    public int getAverageSentPacketSize() {
        if (packetsSentInterval == 0) {
            return 0;
        } else {
            return bytesSentInterval / packetsSentInterval;
        }
    }
    public int getPacketsReceivedPerSecond(){
        int packetsReceivedPerSecond = packetsReceivedInterval/logIntervalSeconds;
        averageData.packetsReceivedPerSecond += packetsReceivedPerSecond;
        averageData.packetsReceivedPerSecondCounter++;
        if(packetsReceivedPerSecond>maxPacketsReceivedPerSecond){
            maxPacketsReceivedPerSecond = packetsReceivedPerSecond;
        }
        return packetsReceivedPerSecond;
    }
    public int getBytesReceivedPerSecond(){
        int bytesReceivedPerSecond = bytesReceivedInterval/logIntervalSeconds;
        if(bytesReceivedPerSecond>maxBytesReceivedPerSecond){
            maxBytesReceivedPerSecond = bytesReceivedPerSecond;
        }
        averageData.bytesReceivedPerSecond += bytesReceivedPerSecond;
        averageData.bytesReceivedPerSecondCounter++;
        return bytesReceivedPerSecond;
    }
    public int getAverageReceivedPacketSize(){
        if(packetsReceivedInterval==0){
            return 0;
        }else{
            return bytesReceivedInterval/packetsReceivedInterval;
        }
    }

    public String getUpload(){
        return String.format(Locale.ENGLISH,"%.3f kB/s",(getBytesSentPerSecond()/1000f));
    }
    public String getDownload(){
        return String.format(Locale.ENGLISH,"%.3f kB/s",(getBytesReceivedPerSecond()/1000f));
    }

    public void intervalElapsed(){
        bytesSentInterval = bytesSentIntervalCounter;
        bytesReceivedInterval = bytesReceivedIntervalCounter;
        packetsSentInterval = packetsSentIntervalCounter;
        packetsReceivedInterval = packetsReceivedIntervalCounter;

        bytesSentIntervalCounter = 0;
        bytesReceivedIntervalCounter = 0;
        packetsSentIntervalCounter = 0;
        packetsReceivedIntervalCounter = 0;
    }

    public void addBytesSent(int bytes){
        bytesSent += bytes;
        bytesSentIntervalCounter += bytes;
        packetsSent++;
        packetsSentIntervalCounter++;
        setLastSentPacketSize(bytes);
    }

    public void addBytesReceived(int bytes){
        bytesReceived += bytes;
        bytesReceivedIntervalCounter += bytes;
        packetsReceived++;
        packetsReceivedIntervalCounter++;
        setLastReceivedPacketSize(bytes);
    }

    public void writeLog(String logname){
        File file = new File("logs\\"+logname+".csv");
        file.getParentFile().mkdirs();

        StringBuilder bTitle = new StringBuilder();
        bTitle.append("Date;");
        bTitle.append("ConnectionTime;");
        bTitle.append("MaxEntityCount;");
        bTitle.append("MinEntityCount;");
        bTitle.append("MaxPlayerCount;");
        bTitle.append("MaxPacketsSentPerSecond;");
        bTitle.append("MaxPacketsReceivedPerSecond;");
        bTitle.append("AveragePacketsSentPerSecond;");
        bTitle.append("AveragePacketsReceivedPerSecond;");
        bTitle.append("MaxBytesSentPerSecond;");
        bTitle.append("MaxBytesReceivedPerSecond;");
        bTitle.append("AverageBytesSentPerSecond;");
        bTitle.append("AverageBytesReceivedPerSecond;");
        bTitle.append("MaxReceivedPacketSize;");
        bTitle.append("MaxSentPacketSize;");
        bTitle.append("PacketsReceived;");
        bTitle.append("PacketsSent;");
        bTitle.append("BytesReceived;");
        bTitle.append("BytesSent;");
        String title = bTitle.toString();

        StringBuilder bData = new StringBuilder();
        bData.append(Calendar.getInstance().getTime()+";");
        bData.append(getConnectionTime()+";");
        bData.append(maxEntityCount+";");
        bData.append(minEntityCount+";");
        bData.append(maxPlayerCount+";");
        bData.append(maxPacketsSentPerSecond+";");
        bData.append(maxPacketsReceivedPerSecond+";");
        bData.append(averageData.getAveragePacketsSentPerSecond()+";");
        bData.append(averageData.getAveragePacketsReceivedPerSecond()+";");
        bData.append(maxBytesSentPerSecond+";");
        bData.append(maxBytesReceivedPerSecond+";");
        bData.append(averageData.getAverageBytesSentPerSecond()+";");
        bData.append(averageData.getAverageBytesReceivedPerSecond()+";");
        bData.append(maxReceivedPacketSize+";");
        bData.append(maxSentPacketSize+";");
        bData.append(packetsReceived+";");
        bData.append(packetsSent+";");
        bData.append(bytesReceived+";");
        bData.append(bytesSent+";");
        String data = bData.toString();

        boolean writeTitle = false;
        if(!file.exists()){
            writeTitle = true;
        }else{
            try(BufferedReader reader = new BufferedReader(new FileReader(file))){
                String firstLine = reader.readLine();
                System.out.println(firstLine);
                if(!firstLine.equals(title)){
                    writeTitle = true;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if(writeTitle){
            try(PrintWriter writer = new PrintWriter(new FileWriter(file,true))){
                writer.println(title);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try(PrintWriter writer = new PrintWriter(new FileWriter(file,true))){
            writer.println(data);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class AverageData{
        int packetsReceivedPerSecond;
        int packetsReceivedPerSecondCounter;
        int packetsSentPerSecond;
        int packetsSentPerSecondCounter;

        int bytesReceivedPerSecond;
        int bytesReceivedPerSecondCounter;
        int bytesSentPerSecond;
        int bytesSentPerSecondCounter;

        public int getAveragePacketsReceivedPerSecond(){
            return packetsReceivedPerSecond/packetsReceivedPerSecondCounter;
        }
        public int getAveragePacketsSentPerSecond(){
            return packetsSentPerSecond/packetsSentPerSecondCounter;
        }
        public int getAverageBytesReceivedPerSecond(){
            return bytesReceivedPerSecond/bytesReceivedPerSecondCounter;
        }
        public int getAverageBytesSentPerSecond(){
            return bytesSentPerSecond/bytesSentPerSecondCounter;
        }
    }
}