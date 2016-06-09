package com.juniperbrew.minimus.server;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.juniperbrew.minimus.F;
import com.juniperbrew.minimus.G;
import com.juniperbrew.minimus.Particle;
import com.juniperbrew.minimus.ProjectileDefinition;

import java.io.File;
import java.util.HashMap;

/**
 * Created by Juniperbrew on 28/04/16.
 */
public class ServerGUI implements ApplicationListener, InputProcessor {

    GameServer server;

    TextureRegion cachedMap;
    String mapName;
    TiledMap map;
    OrthogonalTiledMapRenderer mapRenderer;
    private OrthographicCamera camera;
    private OrthographicCamera hudCamera;
    SpriteBatch batch;
    ShapeRenderer shapeRenderer;
    PolygonSpriteBatch polyBatch;

    TextureAtlas atlas;
    HashMap<String,TextureRegion> textures = new HashMap<>();
    HashMap<String,Animation> animations = new HashMap<>();
    final float animationFrameTime = 0.15f;
    Animation deathAnimation;

    public ServerGUI(){
        server = new GameServer();
    }

    @Override
    public void create() {

        Gdx.input.setInputProcessor(this);
        batch = new SpriteBatch();
        polyBatch = new PolygonSpriteBatch();
        shapeRenderer = new ShapeRenderer();
        camera = new OrthographicCamera(Gdx.graphics.getWidth(),Gdx.graphics.getHeight());
        hudCamera = new OrthographicCamera();
        loadTextures(server.game.campaignName);
    }

    @Override
    public void resize(int w, int h) {
        camera.viewportHeight = h;
        camera.viewportWidth = w;

        hudCamera.viewportWidth = w;
        hudCamera.viewportHeight = h;
        hudCamera.position.set(w/2,h / 2,0);
        hudCamera.update();
    }

    @Override
    public void render() {
        if(server.game.mapName!=mapName){
            loadMap(server.game.mapName);
        }
        camera.update();
        shapeRenderer.setProjectionMatrix(camera.view);
        shapeRenderer.updateMatrices();
        Gdx.gl.glClearColor(0,0,0,1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);


        /*if(mapRenderer!=null){
            if(cachedMap==null){
                mapRenderer.setView(camera);
                mapRenderer.render();
                cachedMap = ScreenUtils.getFrameBufferTexture();
            }else{
                batch.setProjectionMatrix(hudCamera.combined);
                batch.begin();
                batch.draw(cachedMap,0,0);
                batch.end();
                batch.setProjectionMatrix(camera.combined);
            }
        }*/
        if(mapRenderer!=null){
            camera.update();
            mapRenderer.setView(camera);
            mapRenderer.render();
        }

        //System.out.println(server.world.playerList.size());
        //System.out.println(server.world.entities.size());
        shapeRenderer.setProjectionMatrix(camera.combined);
        //Render dudes
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for(ServerEntity e : server.game.entities.values()) {
            if(server.game.playerList.contains(e.getID())){
                shapeRenderer.setColor(0,0,1,1);
            }else{
                shapeRenderer.setColor(1,1,1,1);
            }
            shapeRenderer.rect(e.getX(), e.getY(), e.getWidth() / 2, e.getHeight() / 2, e.getWidth(), e.getHeight(), 1, 1, e.getRotation());
            int healthWidth = (int) (e.getWidth()*e.getHealthPercent());
            shapeRenderer.setColor(1, 0, 0, 1); //red
            shapeRenderer.rect(e.getX(),e.getY(),e.getWidth()/2,e.getHeight()/2,healthWidth,e.getHeight(),1,1,e.getRotation());
        }
        shapeRenderer.end();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        Rectangle spawn = server.game.playerSpawn;
        shapeRenderer.setColor(0, 1, 0, 1);
        shapeRenderer.rect(spawn.x, spawn.y, spawn.width, spawn.height);
        shapeRenderer.setColor(1, 0, 1, 1);
        for(Rectangle r: G.solidMapObjects){
            shapeRenderer.rect(r.x,r.y,r.width,r.height);
        }
        shapeRenderer.end();

        /*shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

        shapeRenderer.setColor(1, 0, 1, 1);
        for(Particle p : server.world.projectiles){
            shapeRenderer.polygon(p.getBoundingPolygon().getTransformedVertices());
        }
        shapeRenderer.end();
*/
        /*batch.begin();
        for(Particle p : server.world.projectiles){
            batch.draw(textures.get("blank"),)
        }
        batch.end();*/

        batch.begin();
        for(Particle p : server.game.particles){
            F.renderPolygon(batch,getParticleTexture(p),p.getBoundingPolygon());
        }
        batch.end();

        if(Gdx.input.isKeyPressed(Input.Keys.UP)) camera.translate(0,10);
        if(Gdx.input.isKeyPressed(Input.Keys.DOWN)) camera.translate(0,-10);
        if(Gdx.input.isKeyPressed(Input.Keys.LEFT)) camera.translate(-10,0);
        if(Gdx.input.isKeyPressed(Input.Keys.RIGHT)) camera.translate(10,0);

    }

    private TextureRegion getParticleTexture(Particle p){
        ProjectileDefinition def = server.game.projectileList.get(p.name);
        if(textures.containsKey(def.image)){
            return textures.get(def.image);
        }else if(animations.containsKey(def.animation)){
            return animations.get(def.animation).getKeyFrame(p.getLifeTime());
        }else{
            return textures.get("blank");
        }
    }

    private void loadTextures(String campaignName){
        String atlasPath = G.campaignFolder+File.separator+campaignName+File.separator+"images"+File.separator+"sprites.atlas";
        G.console.log("Loading texture atlas from "+atlasPath);
        atlas =  new TextureAtlas(atlasPath);
        G.atlas = atlas;
        G.console.log("Loading textures");
        Array<TextureAtlas.AtlasRegion> regions = atlas.getRegions();

        for(TextureAtlas.AtlasRegion region: regions){
            if(textures.containsKey(region.name)||animations.containsKey(region.name)){
                continue;
            }
            Array<TextureAtlas.AtlasRegion> regionArray = atlas.findRegions(region.name);
            if(regionArray.size>1){
                Animation animation = new Animation(animationFrameTime,regionArray, Animation.PlayMode.LOOP);
                animations.put(region.name,animation);
                G.console.log("Loaded animation:" + region.name);
            }else{
                textures.put(region.name,regionArray.first());
                G.console.log("Loaded texture:" + region.name);
            }
        }
        deathAnimation = new Animation(animationFrameTime,atlas.findRegions("death"), Animation.PlayMode.NORMAL);
    }

    private void loadMap(String mapName){
        this.mapName = mapName;
        String mapPath = G.campaignFolder+File.separator+server.game.campaignName+File.separator+"maps"+File.separator+mapName+File.separator+mapName+".tmx";
        map = new TmxMapLoader().load(mapPath);
        mapRenderer = new OrthogonalTiledMapRenderer(map,batch);
    }

    private void loadImages() {
        G.console.log("\nLoading texture atlas");
        //atlas =  new TextureAtlas(G.campaignFolder+ File.separator+campaignName+File.separator+"images"+File.separator+"sprites.atlas");
        G.atlas = atlas;
    }

    @Override
    public void pause() {

    }

    @Override
    public void resume() {

    }

    @Override
    public void dispose() {

    }





    /*public void showConsoleWindow(){
        //TODO
        //We need to delay showing the window or else
        //the window steals the keyUP event on mac resulting
        //in InputProcessor getting KeyTyped events indefinately
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                consoleFrame.setVisible(true);
            }
        }).start();
    }*/






    @Override
    public boolean keyDown(int keycode) {
        return false;
    }

    @Override
    public boolean keyUp(int keycode) {
        return false;
    }

    @Override
    public boolean keyTyped(char character) {
        return false;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        return false;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        return false;
    }

    @Override
    public boolean scrolled(int amount) {
        return false;
    }
}
