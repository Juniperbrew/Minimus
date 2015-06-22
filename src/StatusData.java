import com.esotericsoftware.kryonet.Connection;

import java.net.InetSocketAddress;
import java.util.Locale;

/**
 * Created by Juniperbrew on 20.6.2015.
 */
public class StatusData {

    int fps;
    int entityCount;
    Connection connection;
    long connectionTime;
    long disconnectTime = 0;
    int logIntervalSeconds;
    int currentTick;
    int entitySize;

    int currentInputRequest;
    int inputQueue;

    float serverTime;
    float clientTime;

    long bytesSent;
    int packetsSent;
    private int bytesSentIntervalCounter;
    int bytesSentInterval;
    private int packetsSentIntervalCounter;
    int packetsSentInterval;
    int lastSentPacketSize;

    long bytesReceived;
    int packetsReceived;
    private int bytesReceivedIntervalCounter;
    int bytesReceivedInterval;
    private int packetsReceivedIntervalCounter;
    int packetsReceivedInterval;
    int lastReceivedPacketSize;

    public StatusData(Connection connection, long connectionTime, int logIntervalSeconds){
        this.connection = connection;
        this.connectionTime = connectionTime;
        this.logIntervalSeconds = logIntervalSeconds;
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
    public void setEntityCount(int entityCount){
        this.entityCount = entityCount;
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
        return packetsSentInterval/logIntervalSeconds;
    }
    public int getBytesSentPerSecond(){
        return bytesSentInterval/logIntervalSeconds;
    }
    public int getAverageSentPacketSize() {
        if (packetsSentInterval == 0) {
            return 0;
        } else {
            return bytesSentInterval / packetsSentInterval;
        }
    }
    public int getPacketsReceivedPerSecond(){
        return packetsReceivedInterval/logIntervalSeconds;
    }
    public int getBytesReceivedPerSecond(){
        return bytesReceivedInterval/logIntervalSeconds;
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
        lastSentPacketSize = bytes;
    }

    public void addBytesReceived(int bytes){
        bytesReceived += bytes;
        bytesReceivedIntervalCounter += bytes;
        packetsReceived++;
        packetsReceivedIntervalCounter++;
        lastReceivedPacketSize = bytes;
    }
}