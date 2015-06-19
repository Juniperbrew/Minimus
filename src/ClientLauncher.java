import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;

/**
 * Created by Juniperbrew on 23.1.2015.
 */
public class ClientLauncher {
    public static void main(String[] args) {
        LwjglApplicationConfiguration cfg = new LwjglApplicationConfiguration();
        cfg.vSyncEnabled = false; //vsync wastes cpu cycles for some reason
        //cfg.foregroundFPS = 0;
        //cfg.backgroundFPS = 0;
        new LwjglApplication(new MinimusClient(args[0]) ,cfg);
    }
}
