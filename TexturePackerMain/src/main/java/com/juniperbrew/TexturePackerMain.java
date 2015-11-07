package com.juniperbrew;

import com.badlogic.gdx.tools.texturepacker.TexturePacker;

import java.io.File;
import java.net.URISyntaxException;
import java.util.Scanner;

/**
 * Created by Juniperbrew on 7.11.2015.
 */
public class TexturePackerMain {

    public static void main(String[] args) {
        try {
            File file = new File(TexturePackerMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            String path = file.getParentFile().toString();
            System.out.println(path);
            TexturePacker.process(path+File.separator+"raw", path, "sprites");
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }
}
