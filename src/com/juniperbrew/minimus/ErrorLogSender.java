package com.juniperbrew.minimus;

import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.util.TcpIdleSender;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by Juniperbrew on 7.7.2015.
 */
public class ErrorLogSender {

    final static String errorFolderPath = Tools.getUserDataDirectory()+File.separator+"logs"+File.separator+"errors"+File.separator+"client"+File.separator;
    ErrorLogSenderListener listener;
    String dateStamp;
    int writeBuffer = 8192; //Default 8192
    int objectBuffer = 8192; //Default 2048

    ConcurrentLinkedQueue<Network.SendFile> sendQueue = new ConcurrentLinkedQueue<>();

    public ErrorLogSender(ErrorLogSenderListener listener){
        this.listener = listener;
        SimpleDateFormat format = new SimpleDateFormat("ddMMyyyy-HHmmss");
        dateStamp = format.format(Calendar.getInstance().getTime());
    }

    public void sendErrorLogs(String ip){
        final ArrayList<String> sentFiles = new ArrayList<>();
        final Client client = new Client(writeBuffer,objectBuffer);
        Network.register(client);
        client.addListener(new Listener() {

            public void received(Connection connection, Object object) {
                if (object instanceof Network.FileReceived) {
                    Network.FileReceived fileReceived = (Network.FileReceived) object;
                    String fileName = fileReceived.fileName;
                    System.out.println(fileName + " has been received.");
                    sentFiles.remove(fileName);
                    File sentFolder = new File(errorFolderPath+"sent"+File.separator);
                    sentFolder.mkdirs();
                    Path source = Paths.get(errorFolderPath + fileName);
                    Path target = Paths.get(errorFolderPath+"sent"+File.separator+fileName);
                    System.out.println("Moving "+source+" to "+target);
                    try {
                        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    if(getErrorLogs().isEmpty()){
                        listener.allFilesReceived();
                    }
                }
            }
        });
        client.start();
        try {
            client.connect(5000, ip, Network.portTCP, Network.portUDP);
        } catch (IOException e) {
            e.printStackTrace();
        }

        for(File file: getErrorLogs()){
            System.out.println("Adding file to send queue: "+file);
            if(queueFile(file)){
                sentFiles.add(file.getName());
            }
        }

        TcpIdleSender tcpIdleSender = new TcpIdleSender() {
            @Override
            protected Object next() {
                return sendQueue.poll();
            }
        };
        client.addListener(tcpIdleSender);

        new Thread(new Runnable() {
            @Override
            public void run() {

                //Wait untill all files sent
                while(!sendQueue.isEmpty()){
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                long startTime = System.nanoTime();
                //After all files sent timeout after 5 sec if files haven't been received
                int timeout = 5;
                while(!sentFiles.isEmpty()){
                    if(Tools.nanoToSeconds(System.nanoTime()-startTime) > timeout){
                        System.out.println("Sending error logs was not successful");
                        System.out.println("Filetransfer timed out ("+timeout+" seconds)");
                        listener.transferFailed();
                        break;
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                client.close();
            }
        }).start();
    }

    public static ArrayList<File> getErrorLogs(){
        ArrayList<File> files = new ArrayList<>();
        File errorFolder = new File(errorFolderPath);
        if(errorFolder.exists()){
            for (final File fileEntry : errorFolder.listFiles()) {
                if (!fileEntry.isDirectory()&&!fileEntry.isHidden()) {
                    files.add(fileEntry);
                }
            }
        }
        return files;
    }

    private boolean queueFile(File file){

        if(file.length()>writeBuffer){
            System.out.println("File "+file+" is too large to send "+file.length()+" bytes.");
            return false;
        }
        byte[] b = new byte[(int) file.length()];
        try (FileInputStream fileInputStream = new FileInputStream(file);){
            fileInputStream.read(b);
        } catch (FileNotFoundException e) {
            System.out.println("File Not Found.");
            e.printStackTrace();
            return false;
        }
        catch (IOException e1) {
            System.out.println("Error Reading The File.");
            e1.printStackTrace();
            return false;
        }

        Network.SendFile sendFile = new Network.SendFile();
        sendFile.fileName = file.getName();
        sendFile.data = b;
        sendFile.dateStamp = dateStamp;
        sendQueue.add(sendFile);
        return true;
    }

    public interface ErrorLogSenderListener{
        public void allFilesReceived();
        public void transferFailed();
    }
}
