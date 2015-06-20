import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

/**
 * Created by Juniperbrew on 20.6.2015.
 */
public class ServerStatusFrame extends JFrame {

    ArrayList<DataPanel> dataPanels = new ArrayList<DataPanel>();
    JComboBox dropDownMenu = new JComboBox();
    DataPanel activeDataPanel;
    TotalDataPanel totalDataPanel;
    JPanel dataContentPanel;

    public ServerStatusFrame(StatusData totalDataUsage){

        super("Server stats");
        setResizable(false);
        dataContentPanel = new JPanel();

        totalDataPanel = new TotalDataPanel(totalDataUsage);
        dataPanels.add(totalDataPanel);
        dropDownMenu.addItem("Total");
        dataContentPanel.add(totalDataPanel);
        activeDataPanel = totalDataPanel;

        setLayout(new MigLayout("wrap"));

        dropDownMenu.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.out.println("Selected index:"+dropDownMenu.getSelectedIndex());
                dataContentPanel.removeAll();
                activeDataPanel = dataPanels.get(dropDownMenu.getSelectedIndex());
                dataContentPanel.add(activeDataPanel);
                activeDataPanel.update();
                repaint();
            }
        });

        add(dropDownMenu,"growx,pushx");
        add(dataContentPanel);
        pack();
        update();
    }

    public void addConnection(String name, StatusData dataUsage){
        dataPanels.add(new ConnectionDataPanel(name,dataUsage));
        dropDownMenu.addItem(name);
    }

    public void update(){
        activeDataPanel.update();
    }

    abstract class DataPanel extends JPanel{

        JLabel nameLabel = new JLabel();
        JLabel connectionTimeLabel = new JLabel();
        JLabel up = new JLabel();
        JLabel down = new JLabel();

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

        abstract public void update();
    }

    class ConnectionDataPanel extends DataPanel{
        JLabel hostName = new JLabel();
        JLabel ipLabel = new JLabel();
        JLabel pingLabel = new JLabel();
        JLabel inputQueue = new JLabel();

        public ConnectionDataPanel(String name, StatusData dataUsage){

            data = dataUsage;
            nameLabel = new JLabel(name);
            setLayout(new MigLayout("wrap"));
            add(nameLabel);
            add(hostName);
            add(ipLabel);
            add(connectionTimeLabel);
            add(inputQueue);
            add(pingLabel);
            add(down);
            add(up);

            add(new JSeparator(),"growx,pushx");

            add(packetsSent);
            add(bytesSent);
            add(packetsPerSecondSent);
            add(bytesPerSecondSent);
            add(averageSentPacketSize);
            add(lastSentPacketSize);

            add(new JSeparator(),"growx,pushx");

            add(packetsReceived);
            add(bytesReceived);
            add(packetsPerSecondReceived);
            add(bytesPerSecondReceived);
            add(averageReceivedPacketSize);
            add(lastReceivedPacketSize);

            setVisible(true);
            update();
        }

        @Override
        public void update() {
            javax.swing.SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    String connectionTime;
                    hostName.setText("Hostname:"+data.getUdpHostName());
                    inputQueue.setText("Input queue:"+data.inputQueue);
                    ipLabel.setText("IP:"+data.getUdpAddress());
                    up.setText("Up:"+data.getUpload());
                    down.setText("Down:"+data.getDownload());
                    if(data.disconnectTime > 0){
                        pingLabel.setText("Ping: DISCONNECTED");
                    }else{
                        pingLabel.setText("Ping:"+data.getPing());
                    }
                    connectionTimeLabel.setText("Connection time:" + data.getConnectionTime());

                    packetsSent.setText("Packets sent:" + data.packetsSent);
                    bytesSent.setText("Bytes sent:" + data.bytesSent);
                    packetsPerSecondSent.setText("Current packets/s sent:" + data.getPacketsSentPerSecond());
                    bytesPerSecondSent.setText("Current bytes/s sent:" + data.getBytesSentPerSecond());
                    averageSentPacketSize.setText("Average sent packet size:" + data.getAverageSentPacketSize());
                    lastSentPacketSize.setText("Last sent packet size:"+data.lastSentPacketSize);

                    packetsReceived.setText("Packets received:" + data.packetsReceived);
                    bytesReceived.setText("Bytes received:" + data.bytesReceived);
                    packetsPerSecondReceived.setText("Current packets/s received:"+ data.getPacketsReceivedPerSecond());
                    bytesPerSecondReceived.setText("Current bytes/s received:"+ data.getBytesReceivedPerSecond());
                    averageReceivedPacketSize.setText("Average received packet size:" + data.getAverageReceivedPacketSize());
                    lastReceivedPacketSize.setText("Last received packet size:" + data.lastReceivedPacketSize);
                    pack();
                }
            });
        }
    }

    class TotalDataPanel extends DataPanel{

        /*
                statusData.fps = Gdx.graphics.getFramesPerSecond();
        statusData.entityCount = entities.size();
        statusData.currentTick = currentTick;
        statusData.serverTime = (System.nanoTime() - serverStartTime) / 1000000000f;
         */

        JLabel fps = new JLabel();
        JLabel entityCount = new JLabel();
        JLabel serverTime = new JLabel();
        JLabel currentTick = new JLabel();

        public TotalDataPanel(StatusData dataUsage){
            data = dataUsage;
            nameLabel = new JLabel("Total");
            setLayout(new MigLayout("wrap"));
            add(nameLabel);
            add(fps);
            add(entityCount);
            add(serverTime);
            add(currentTick);
            add(connectionTimeLabel);
            add(down);
            add(up);

            add(new JSeparator(),"growx,pushx");

            add(packetsSent);
            add(bytesSent);
            add(packetsPerSecondSent);
            add(bytesPerSecondSent);
            add(averageSentPacketSize);
            add(lastSentPacketSize);

            add(new JSeparator(),"growx,pushx");

            add(packetsReceived);
            add(bytesReceived);
            add(packetsPerSecondReceived);
            add(bytesPerSecondReceived);
            add(averageReceivedPacketSize);
            add(lastReceivedPacketSize);

            setVisible(true);
            update();
        }

        @Override
        public void update() {
            javax.swing.SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {

                    fps.setText("Fps: "+data.fps);
                    entityCount.setText("Entities: "+data.entityCount);
                    serverTime.setText("Server time: "+data.getServerTime());
                    currentTick.setText("Tick: "+data.currentTick);

                    String connectionTime;
                    connectionTime = Tool.secondsToTimestamp(Tool.nanoToSeconds(System.nanoTime()-data.connectionTime));
                    connectionTimeLabel.setText("Server uptime: " + connectionTime);
                    up.setText("Up: "+data.getUpload());
                    down.setText("Down: "+data.getDownload());

                    packetsSent.setText("Packets sent: " + data.packetsSent);
                    bytesSent.setText("Bytes sent: " + data.bytesSent);
                    packetsPerSecondSent.setText("Current packets/s sent: " + data.getPacketsSentPerSecond());
                    bytesPerSecondSent.setText("Current bytes/s sent: " + data.getBytesSentPerSecond());
                    averageSentPacketSize.setText("Average sent packet size: " + data.getAverageSentPacketSize());
                    lastSentPacketSize.setText("Last sent packet size: "+data.lastSentPacketSize);

                    packetsReceived.setText("Packets received: " + data.packetsReceived);
                    bytesReceived.setText("Bytes received: " + data.bytesReceived);
                    packetsPerSecondReceived.setText("Current packets/s received: "+ data.getPacketsReceivedPerSecond());
                    bytesPerSecondReceived.setText("Current bytes/s received: "+ data.getBytesReceivedPerSecond());
                    averageReceivedPacketSize.setText("Average received packet size: " + data.getAverageReceivedPacketSize());
                    lastReceivedPacketSize.setText("Last received packet size: " + data.lastReceivedPacketSize);
                    pack();
                }
            });
        }
    }
}
