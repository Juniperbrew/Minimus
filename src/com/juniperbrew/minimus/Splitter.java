package com.juniperbrew.minimus;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Created by Juniperbrew on 19.9.2015.
 */
public class Splitter {
    public static void main(String[] args) {
        splitList("projectiles", "resources"+File.separator+"defaultprojectilelist.txt");
        splitList("weapons", "resources"+File.separator+"defaultweaponlist.txt");
    }

    private static void splitList(String listName, String path){
        File file = new File(path);

        String fileName = null;
        StringBuilder b = new StringBuilder();

        try(BufferedReader reader = new BufferedReader(new FileReader(file))) {
            for (String line; (line = reader.readLine()) != null; ) {
                if(line.isEmpty()){
                    b.append(line+System.getProperty("line.separator"));
                    continue;
                }
                if (line.charAt(0) == '{') {
                    b.setLength(0);
                    continue;
                }
                if (line.charAt(0) == '}') {
                    writeFile(listName,fileName,b.toString());
                    fileName = null;
                    continue;
                }
                b.append(line+System.getProperty("line.separator"));
                String[] splits = line.split("=");
                if(splits[0].equals("name")){
                    fileName = splits[1].replace(" ","_");
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void writeFile(String folder, String fileName, String content){
        File file = new File("splitter"+File.separator+folder+File.separator+fileName+".txt");
        file.getParentFile().mkdirs();
        System.out.println(content);
        System.out.println("Writing to file "+file);
        try(PrintWriter writer = new PrintWriter(file)) {
            writer.print(content);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println();
    }
}
