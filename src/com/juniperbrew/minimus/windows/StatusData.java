package com.juniperbrew.minimus.windows;

import com.esotericsoftware.kryonet.Connection;
import com.juniperbrew.minimus.Tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.text.Format;
import java.text.SimpleDateFormat;
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
    private int fakeReturnTripTime;

    private int playerCount;
    private int maxPlayerCount;
    private int maxEntityCount;

    private int maxPacketsReceivedPerSecond;
    private int maxPacketsSentPerSecond;
    private int maxBytesReceivedPerSecond;
    private int maxBytesSentPerSecond;
    private int maxReceivedPacketSize;

    private int maxPing;
    private int maxFakePing;

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

    public StatusData(long connectionTime, int logIntervalSeconds){
        this.connectionTime = connectionTime;
        this.logIntervalSeconds = logIntervalSeconds;
    }

    public void setFakeReturnTripTime(int rtt){
        fakeReturnTripTime = rtt;
        averageData.fakePing += rtt;
        averageData.fakePingCounter++;
        if(rtt>maxFakePing){
            maxFakePing = rtt;
        }
    }


    public void updatePing(){
        connection.updateReturnTripTime();
        int rtt = connection.getReturnTripTime();
        averageData.ping += rtt;
        averageData.pingCounter++;
        if(rtt > maxPing){
            maxPing = rtt;
        }
    }

    public int getFakePing(){
        return fakeReturnTripTime;
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
        this.entityCount = entityCount;
        if(entityCount>maxEntityCount){
            maxEntityCount = entityCount;
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
        if(address!=null){
            return address.getAddress().getHostAddress() + ":" + address.getPort();
        }else{
            return null;
        }
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

    public void writeLog(boolean server){
        String logname;
        if(server){
            logname = "serverLog";
        }else{
            logname = "clientLog";
        }
        File file = new File(Tools.getUserDataDirectory()+"logs"+File.separator+logname+".csv");
        file.getParentFile().mkdirs();

        StringBuilder bTitle = new StringBuilder();
        bTitle.append("Date;");
        bTitle.append("ConnectionTime;");
        if(!server){
            bTitle.append("IP;");
            bTitle.append("MaxPing;");
            bTitle.append("MaxFakePing;");
            bTitle.append("AveragePing;");
            bTitle.append("AverageFakePing;");
        }
        bTitle.append("MaxEntityCount;");
        if(server){
            bTitle.append("MaxPlayerCount;");
        }
        bTitle.append("MaxBytesSentPerSecond;");
        bTitle.append("MaxBytesReceivedPerSecond;");
        bTitle.append("AverageBytesSentPerSecond;");
        bTitle.append("AverageBytesReceivedPerSecond;");
        bTitle.append("BytesSent;");
        bTitle.append("BytesReceived;");
        bTitle.append("MaxPacketsSentPerSecond;");
        bTitle.append("MaxPacketsReceivedPerSecond;");
        bTitle.append("AveragePacketsSentPerSecond;");
        bTitle.append("AveragePacketsReceivedPerSecond;");
        bTitle.append("PacketsSent;");
        bTitle.append("PacketsReceived;");
        bTitle.append("MaxSentPacketSize;");
        bTitle.append("MaxReceivedPacketSize;");
        String title = bTitle.toString();

        StringBuilder bData = new StringBuilder();
        SimpleDateFormat format = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
        String date = format.format(Calendar.getInstance().getTime());
        bData.append(date+";");
        bData.append(getConnectionTime()+";");
        if(!server){
            bData.append(getUdpAddress()+";");
            bData.append(maxPing+";");
            bData.append(maxFakePing+";");
            bData.append(averageData.getAveragePing()+";");
            bData.append(averageData.getAverageFakePing()+";");
        }
        bData.append(maxEntityCount+";");
        if(server){
            bData.append(maxPlayerCount+";");
        }
        bData.append(maxBytesSentPerSecond+";");
        bData.append(maxBytesReceivedPerSecond+";");
        bData.append(averageData.getAverageBytesSentPerSecond()+";");
        bData.append(averageData.getAverageBytesReceivedPerSecond()+";");
        bData.append(bytesSent+";");
        bData.append(bytesReceived+";");
        bData.append(maxPacketsSentPerSecond+";");
        bData.append(maxPacketsReceivedPerSecond+";");
        bData.append(averageData.getAveragePacketsSentPerSecond()+";");
        bData.append(averageData.getAveragePacketsReceivedPerSecond()+";");
        bData.append(packetsSent+";");
        bData.append(packetsReceived+";");
        bData.append(maxSentPacketSize+";");
        bData.append(maxReceivedPacketSize+";");
        String data = bData.toString();

        boolean writeTitle = false;
        if(!file.exists()){
            writeTitle = true;
        }else{
            try(BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String firstLine = reader.readLine();
                if (!firstLine.equals(title)) {
                    writeTitle = true;
                }
            }catch (IOException e) {
                e.printStackTrace();
            }
        }

        if(writeTitle){
            try(PrintWriter writer = new PrintWriter(new FileWriter(file,true))){
                writer.println(title);
            } catch (FileNotFoundException e){
                System.out.println(e);
                System.out.println("Could not write to logfile probably because it is in use elsewhere.");
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

        int ping;
        int pingCounter;

        int fakePing;
        int fakePingCounter;

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
        public int getAveragePing(){
            return ping/pingCounter;
        }
        public int getAverageFakePing(){
            return fakePing/fakePingCounter;
        }
    }
}