package com.mygdx.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

/**
 * Created by Digilogue on 27/10/2016.
 */
public class GameScreen extends ScreenAdapter {

    private static final float WORLD_WIDTH = 960;
    private static final float WORLD_HEIGHT = 544;
    private static final float UNITS_PER_METER = 32F;
    private static final float UNIT_WIDTH = WORLD_WIDTH / UNITS_PER_METER;
    private static final float UNIT_HEIGHT = WORLD_HEIGHT / UNITS_PER_METER;
    private static final float MAX_STRENGTH = 15;
    private static final float MAX_DISTANCE = 100;
    private static final float UPPER_ANGLE = 3 * MathUtils.PI / 2f;
    private static final float LOWER_ANGLE = MathUtils.PI / 2f;


    private World world;
    private Box2DDebugRenderer debugRenderer;
    private Body body;
    private ShapeRenderer shapeRenderer;
    private Viewport viewport;
    private OrthographicCamera camera;
    private SpriteBatch batch;
    private final NuttyGame nuttyGame;
    private TiledMap tiledMap;
    private OrthogonalTiledMapRenderer orthogonalTiledMapRenderer;
    private OrthographicCamera box2dCam;
    private Array<Body> toRemove = new Array<Body>();

    private final Vector2 anchor = new Vector2(convertMetersToUnits(3), convertMetersToUnits(6));
    private final Vector2 firingPosition = anchor.cpy();
    private float distance;
    private float angle;

    public GameScreen(NuttyGame nuttyGame) {
        this.nuttyGame = nuttyGame;
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        viewport.update(width, height);
    }

    @Override
    public void show() {
        super.show();
        world = new World(new Vector2(0, -10F), true);
        debugRenderer = new Box2DDebugRenderer();
        body = createBody();
        body.setTransform(100, 120, 0);
        camera = new OrthographicCamera();
        viewport = new FitViewport(WORLD_WIDTH, WORLD_HEIGHT, camera);
        viewport.apply(true);
        shapeRenderer = new ShapeRenderer();

        batch = new SpriteBatch();
        tiledMap = nuttyGame.getAssetManager().get("nuttybirds.tmx");
        orthogonalTiledMapRenderer = new OrthogonalTiledMapRenderer(tiledMap, batch);
        orthogonalTiledMapRenderer.setView(camera);

        TiledObjectBodyBuilder.buildBuildingBodies(tiledMap, world);
        box2dCam = new OrthographicCamera(UNIT_WIDTH, UNIT_HEIGHT);
        TiledObjectBodyBuilder.buildFloorBodies(tiledMap, world);
        TiledObjectBodyBuilder.buildBirdBodies(tiledMap, world);

        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean touchDragged(int screenX, int screenY, int pointer) {
                calculateAngleAndDistanceForBullet(screenX, screenY);
                return true;
            }

            @Override
            public boolean touchUp(int screenX, int screenY, int pointer, int button) {
                createBullet();
                firingPosition.set(anchor.cpy());
                return true;
            }
        });

        world.setContactListener(new NuttyContactListener());
    }

    private Body createBody() {
        BodyDef def = new BodyDef();
        def.type = BodyDef.BodyType.DynamicBody;
        Body box = world.createBody(def);
        PolygonShape poly = new PolygonShape();
        poly.setAsBox(60 / UNITS_PER_METER, 60 / UNITS_PER_METER);
        box.createFixture(poly, 1);
        poly.dispose();
        return box;
    }

    @Override
    public void render(float delta) {
        super.render(delta);
        update(delta);
        clearScreen();
        // draw();
        drawDebug();
    }

    private void drawDebug() {
        debugRenderer.render(world, box2dCam.combined);
        shapeRenderer.setProjectionMatrix(camera.projection);
        shapeRenderer.setTransformMatrix(camera.view);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.rect(anchor.x - 5, anchor.y - 5, 10, 10);
        shapeRenderer.rect(firingPosition.x - 5, firingPosition.y - 5, 10, 10);
        shapeRenderer.line(anchor.x, anchor.y, firingPosition.x, firingPosition.y);
        shapeRenderer.end();
    }

    private void draw() {
        batch.setProjectionMatrix(camera.projection);
        batch.setTransformMatrix(camera.view);
        orthogonalTiledMapRenderer.render();
//
//        batch.begin();
//
//        batch.end();
    }

    private void clearScreen() {
        Gdx.gl.glClearColor(Color.BLACK.r, Color.BLACK.g, Color.BLACK.b, Color.BLACK.a);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    }

    private void update(float delta) {
        clearDeadBodies();
        world.step(delta, 6, 2);
        box2dCam.position.set(UNIT_WIDTH / 2, UNIT_HEIGHT / 2, 0);
        box2dCam.update();
    }

    private void createBullet() {
        CircleShape circleShape = new CircleShape();
        circleShape.setRadius(0.5f);
        circleShape.setPosition(new Vector2(convertUnitsToMeters(firingPosition.x), convertUnitsToMeters
                (firingPosition.y)));
        BodyDef bd = new BodyDef();
        bd.type = BodyDef.BodyType.DynamicBody;
        Body bullet = world.createBody(bd);
        bullet.createFixture(circleShape, 1);
        circleShape.dispose();
        float velX = Math.abs( (MAX_STRENGTH * -MathUtils.cos(angle) * (distance / 100f)));
        float velY = Math.abs( (MAX_STRENGTH * -MathUtils.sin(angle) * (distance / 100f)));
        bullet.setLinearVelocity(velX, velY);
    }

    private void clearDeadBodies() {
        for (Body body : toRemove) {
            world.destroyBody(body);
        }
        toRemove.clear();
    }

    private float convertUnitsToMeters(float pixels) {
        return pixels / UNITS_PER_METER;
    }

    private float convertMetersToUnits(float meters) {
        return meters * UNITS_PER_METER;
    }

    private float angleBetweenTwoPoints() {
        float angle = MathUtils.atan2(anchor.y - firingPosition.y, anchor.x - firingPosition.x);
        angle %= 2 * MathUtils.PI;
        if (angle < 0) angle += 2 * MathUtils.PI2;
        return angle;
    }

    private float distanceBetweenTwoPoints() {
        return (float) Math.sqrt(((anchor.x - firingPosition.x) * (anchor.x - firingPosition.x)) + ((anchor.y -
                firingPosition.y) * (anchor.y - firingPosition.y)));
    }

    private void calculateAngleAndDistanceForBullet(int screenX, int screenY) {
        firingPosition.set(screenX, screenY);
        viewport.unproject(firingPosition);
        distance = distanceBetweenTwoPoints();
        angle = angleBetweenTwoPoints();
        if (distance > MAX_DISTANCE) {
            distance = MAX_DISTANCE;
        }
        if (angle > LOWER_ANGLE) {
            if (angle > UPPER_ANGLE) {
                angle = 0;
            } else {
                angle = LOWER_ANGLE;
            }
        }
        firingPosition.set(anchor.x + (distance * -MathUtils.cos(angle)), anchor.y + (distance * -MathUtils.sin(angle)));
    }

    private class NuttyContactListener implements ContactListener {

        @Override
        public void beginContact(Contact contact) {
            if (contact.isTouching()) {
                Fixture attacker = contact.getFixtureA();
                Fixture defender = contact.getFixtureB();
                WorldManifold worldManifold = contact.getWorldManifold();
                if ("enemy".equals(defender.getUserData())) {
                    Vector2 vel1 = attacker.getBody().getLinearVelocityFromWorldPoint(worldManifold.getPoints()[0]);
                    Vector2 vel2 = defender.getBody().getLinearVelocityFromWorldPoint(worldManifold.getPoints()[0]);
                    Vector2 impactVelocity = vel1.sub(vel2);
                    if (Math.abs(impactVelocity.x) > 1 || Math.abs(impactVelocity.y) > 1) {
                        toRemove.add(defender.getBody());
                    }
                }
            }
        }

        @Override
        public void endContact(Contact contact) {

        }

        @Override
        public void preSolve(Contact contact, Manifold oldManifold) {

        }

        @Override
        public void postSolve(Contact contact, ContactImpulse impulse) {

        }
    }
}
