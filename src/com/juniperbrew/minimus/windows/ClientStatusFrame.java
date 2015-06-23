package com.juniperbrew.minimus.windows;

import net.miginfocom.swing.MigLayout;

import javax.swing.*;

/**
 * Created by Juniperbrew on 20.6.2015.
 */
public class ClientStatusFrame extends JFrame {

    JLabel runtimeLabel = new JLabel();
    JLabel fps = new JLabel();
    JLabel entityCount = new JLabel();
    JLabel serverTime = new JLabel();
    JLabel clientTime = new JLabel();
    JLabel currentInputRequest = new JLabel();
    JLabel inputQueue = new JLabel();
    JLabel download = new JLabel();
    JLabel upload = new JLabel();

    JLabel packetsSent = new JLabel();
    JLabel bytesSent = new JLabel();
    JLabel packetsPerSecondSent = new JLabel();
    JLabel bytesPerSecondSent = new JLabel();
    JLabel averageSentPacketSize = new JLabel();
    JLabel lastSentPacketSize = new JLabel();

    JLabel packetsReceived = new JLabel();
    JLabel bytesReceived = new JLabel();
    JLabel packetsPerSecondReceived = new JLabel();
    JLabel bytesPerSecondReceived = new JLabel();
    JLabel averageReceivedPacketSize = new JLabel();
    JLabel lastReceivedPacketSize = new JLabel();

    StatusData data;

    public ClientStatusFrame(StatusData dataUsage){

        /*

        statusData.setFps(Gdx.graphics.getFramesPerSecond());
        statusData.setEntityCount(stateSnapshot.size());
        statusData.setServerTime(lastServerTime);
        statusData.setClientTime(lastServerTime+((System.nanoTime()- lastAuthoritativeStateReceived)/1000000000f));
        statusData.currentInputRequest = currentInputRequest;
        statusData.inputQueue = inputQueue.size();
         */
        super("Stats");
        setResizable(false);
        data = dataUsage;

        setLayout(new MigLayout("wrap"));
        add(runtimeLabel);
        add(download);
        add(upload);
        add(fps);
        add(entityCount);
        add(serverTime);
        add(clientTime);
        add(currentInputRequest);
        add(inputQueue);
        add(new JSeparator(),"pushx, growx");

        add(packetsSent);
        add(bytesSent);
        add(packetsPerSecondSent);
        add(bytesPerSecondSent);
        add(averageSentPacketSize);
        add(lastSentPacketSize);

        add(new JSeparator(),"pushx, growx");

        add(packetsReceived);
        add(bytesReceived);
        add(packetsPerSecondReceived);
        add(bytesPerSecondReceived);
        add(averageReceivedPacketSize);
        add(lastReceivedPacketSize);

        update();
    }

    public void update(){
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                runtimeLabel.setText("Connection time: "+data.getConnectionTime());
                download.setText("Down: "+data.getDownload());
                upload.setText("Up: "+data.getUpload());
                fps.setText("FPS: "+data.fps);
                entityCount.setText("Entities: "+data.entityCount);
                serverTime.setText("Server time: "+data.getServerTime());
                clientTime.setText("Client time: "+data.getClientTime());
                currentInputRequest.setText("Current input request: "+data.currentInputRequest);
                inputQueue.setText("Input queue: "+data.inputQueue);

                packetsSent.setText("Packets sent: " + data.packetsSent);
                bytesSent.setText("Bytes sent: " + data.bytesSent);
                packetsPerSecondSent.setText("Current  packets/s sent: " + data.getPacketsSentPerSecond());
                bytesPerSecondSent.setText("Current bytes/s sent: " + data.getBytesSentPerSecond());
                averageSentPacketSize.setText("Average sent packet size: " + data.getAverageSentPacketSize());
                lastSentPacketSize.setText("Last sent packet size: "+ data.lastSentPacketSize);

                packetsReceived.setText("Packets received: " + data.packetsReceived);
                bytesReceived.setText("Bytes received: " + data.bytesReceived);
                packetsPerSecondReceived.setText("Current packets/s received: " + data.getPacketsReceivedPerSecond());
                bytesPerSecondReceived.setText("Current bytes/s received: " + data.getBytesReceivedPerSecond());
                averageReceivedPacketSize.setText("Average received packet size: " + data.getAverageReceivedPacketSize());
                lastReceivedPacketSize.setText("Last received packet size: " + data.lastReceivedPacketSize);
                pack();
            }
        });
    }
}
