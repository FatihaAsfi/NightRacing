import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.layout.StackPane;
import javafx.scene.media.AudioClip;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class Main extends Application {

    // ============================================================
    // GAME WINDOW / INTERNAL RESOLUTION
    // ============================================================

    // The game is designed internally at 480 x 800.
    // The Canvas is scaled automatically when the window is fullscreen.
    private static final double W = 480;
    private static final double H = 800;

    // ============================================================
    // ROAD SETTINGS
    // ============================================================

    private static final double ROAD_X = 60;
    private static final double ROAD_W = 360;
    private static final int LANES = 4;
    private static final double LANE_W = ROAD_W / LANES;

    // ============================================================
    // PLAYER CAR SETTINGS
    // ============================================================

    private static final double CAR_W = 44;
    private static final double CAR_H = 80;

    // Player's vertical position.
    private static final double PLAYER_Y = H - 170;

    // Starting and maximum driving speeds.
    private static final double START_SPEED = 320;
    private static final double MAX_SPEED = 950;

    // How quickly the player accelerates.
    private static final double ACCELERATION = 11;

    // Steering speed.
    private static final double STEERING = 400;

    // ============================================================
    // GAME STATE
    // ============================================================

    private enum GameState {
        MENU,
        PLAYING,
        PAUSED,
        GAME_OVER
    }

    private GameState state = GameState.MENU;

    // ============================================================
    // JAVA FX OBJECTS
    // ============================================================

    private Stage stage;
    private Canvas canvas;
    private GraphicsContext gc;

    // ============================================================
    // GAME OBJECT LISTS
    // ============================================================

    private final List<Enemy> enemies = new ArrayList<>();
    private final List<Coin> coins = new ArrayList<>();
    private final List<Tree> trees = new ArrayList<>();
    private final List<Particle> particles = new ArrayList<>();

    // Random number generator for enemy / object spawning.
    private final Random random = new Random();

    // ============================================================
    // PLAYER VARIABLES
    // ============================================================

    private double playerX = W / 2 - CAR_W / 2;

    private double speed = START_SPEED;

    private int score = 0;
    private int coinsCollected = 0;

    // Distance used to determine when to spawn objects.
    private double enemySpawnTimer = 0;
    private double coinSpawnTimer = 0;
    private double treeSpawnTimer = 0;

    // ============================================================
    // ROAD ANIMATION
    // ============================================================

    private double roadOffset = 0;

    // ============================================================
    // KEYBOARD INPUT
    // ============================================================

    private boolean leftPressed = false;
    private boolean rightPressed = false;

    // ============================================================
    // GAME TIMER
    // ============================================================

    private AnimationTimer gameLoop;

    private long lastTime = 0;

    // ============================================================
    // HIGH SCORE
    // ============================================================

    private int highScore = 0;

    private final Path highScoreFile =
            Path.of(System.getProperty("user.home"), ".night-racer-highscore");

    // ============================================================
    // SOUND EFFECTS
    // ============================================================

    private AudioClip coinSound;
    private AudioClip crashSound;
    private AudioClip startSound;
    private AudioClip pauseSound;

    // ============================================================
    // APPLICATION START
    // ============================================================

    @Override
    public void start(Stage stage) {

        this.stage = stage;

        // Create the Canvas where the entire game is drawn.
        canvas = new Canvas(W, H);

        // Get the drawing context.
        gc = canvas.getGraphicsContext2D();

        // StackPane allows the Canvas to sit in the center
        // while the window itself can become fullscreen.
        StackPane root = new StackPane(canvas);

        // Black background fills the areas outside the game
        // when the monitor has a different aspect ratio.
        root.setStyle("-fx-background-color: black;");

        // Create the JavaFX scene.
        Scene scene = new Scene(
                root,
                W,
                H,
                Color.BLACK
        );

        // Load saved high score.
        loadHighScore();

        // Load sound effects from src/main/resources/sounds.
        loadSounds();

        // ========================================================
        // KEYBOARD INPUT
        // ========================================================

        scene.setOnKeyPressed(event -> {

            KeyCode key = event.getCode();

            // Steering.
            if (key == KeyCode.LEFT || key == KeyCode.A) {
                leftPressed = true;
            }

            if (key == KeyCode.RIGHT || key == KeyCode.D) {
                rightPressed = true;
            }

            // Start / retry the game.
            if (key == KeyCode.ENTER || key == KeyCode.SPACE) {

                if (state == GameState.MENU ||
                        state == GameState.GAME_OVER) {

                    startGame();
                }
            }

            // Pause / resume.
            if (key == KeyCode.P) {

                if (state == GameState.PLAYING) {

                    state = GameState.PAUSED;
                    playSound(pauseSound);

                } else if (state == GameState.PAUSED) {

                    state = GameState.PLAYING;
                    playSound(pauseSound);

                    // Reset timer so the game does not jump forward
                    // after being paused.
                    lastTime = System.nanoTime();
                }
            }

            // ESC:
            // If fullscreen, leave fullscreen.
            // Otherwise close the game.
            if (key == KeyCode.ESCAPE) {

                if (stage.isFullScreen()) {
                    stage.setFullScreen(false);
                } else {
                    stage.close();
                }
            }
        });

        // When the key is released, stop steering.
        scene.setOnKeyReleased(event -> {

            KeyCode key = event.getCode();

            if (key == KeyCode.LEFT || key == KeyCode.A) {
                leftPressed = false;
            }

            if (key == KeyCode.RIGHT || key == KeyCode.D) {
                rightPressed = false;
            }
        });

        // ========================================================
        // WINDOW / FULLSCREEN SETTINGS
        // ========================================================

        stage.setTitle("Night Racer");

        stage.setScene(scene);

        // Make the game fullscreen.
        stage.setFullScreen(true);

        // Do not display JavaFX's default fullscreen hint.
        stage.setFullScreenExitHint("");

        // IMPORTANT:
        // This method expects a KeyCombination, not a KeyCode.
        //
        // This is the compilation error we fixed.
        stage.setFullScreenExitKeyCombination(
                new KeyCodeCombination(KeyCode.ESCAPE)
        );

        stage.show();

        // ========================================================
        // RESPONSIVE CANVAS SCALING
        // ========================================================

        // Whenever the fullscreen window changes size,
        // recalculate the Canvas scale.
        root.widthProperty().addListener(
                (obs, oldValue, newValue) ->
                        scaleCanvas(canvas, root)
        );

        root.heightProperty().addListener(
                (obs, oldValue, newValue) ->
                        scaleCanvas(canvas, root)
        );

        // Apply the initial scale.
        scaleCanvas(canvas, root);

        // Make sure keyboard input goes to the game.
        scene.getRoot().requestFocus();

        // Start the game loop.
        startGameLoop();
    }

    // ============================================================
    // CANVAS SCALING
    // ============================================================

    private void scaleCanvas(Canvas canvas, StackPane root) {

        // Calculate how much the game can be enlarged horizontally.
        double scaleX = root.getWidth() / W;

        // Calculate how much the game can be enlarged vertically.
        double scaleY = root.getHeight() / H;

        // Use the smaller value so the game keeps its
        // original 480 x 800 aspect ratio.
        double scale = Math.min(scaleX, scaleY);

        canvas.setScaleX(scale);
        canvas.setScaleY(scale);
    }

    // ============================================================
    // GAME LOOP
    // ============================================================

    private void startGameLoop() {

        gameLoop = new AnimationTimer() {

            @Override
            public void handle(long now) {

                // First frame.
                if (lastTime == 0) {
                    lastTime = now;
                }

                // Calculate elapsed time in seconds.
                double deltaTime =
                        (now - lastTime) / 1_000_000_000.0;

                lastTime = now;

                // Prevent a huge jump if the application was paused
                // or the computer was busy.
                deltaTime = Math.min(deltaTime, 0.05);

                // Update game logic only while playing.
                if (state == GameState.PLAYING) {
                    update(deltaTime);
                }

                // Draw the current frame.
                render();
            }
        };

        gameLoop.start();
    }

    // ============================================================
    // START / RESET GAME
    // ============================================================

    private void startGame() {

        state = GameState.PLAYING;

        playerX = W / 2 - CAR_W / 2;

        speed = START_SPEED;

        score = 0;

        coinsCollected = 0;

        enemySpawnTimer = 0;
        coinSpawnTimer = 0;
        treeSpawnTimer = 0;

        roadOffset = 0;

        enemies.clear();
        coins.clear();
        trees.clear();
        particles.clear();

        playSound(startSound);

        lastTime = System.nanoTime();
    }

    // ============================================================
    // UPDATE GAME
    // ============================================================

    private void update(double dt) {

        // --------------------------------------------------------
        // PLAYER MOVEMENT
        // --------------------------------------------------------

        double steeringAmount = STEERING * dt;

        if (leftPressed) {
            playerX -= steeringAmount;
        }

        if (rightPressed) {
            playerX += steeringAmount;
        }

        // Keep player inside the road.
        double minX = ROAD_X + 8;
        double maxX = ROAD_X + ROAD_W - CAR_W - 8;

        playerX = Math.max(minX, Math.min(maxX, playerX));

        // --------------------------------------------------------
        // ACCELERATION
        // --------------------------------------------------------

        speed += ACCELERATION * dt;

        speed = Math.min(speed, MAX_SPEED);

        // --------------------------------------------------------
        // ROAD MOVEMENT
        // --------------------------------------------------------

        roadOffset += speed * dt;

        if (roadOffset > 80) {
            roadOffset -= 80;
        }

        // --------------------------------------------------------
        // SCORE
        // --------------------------------------------------------

        score += (int) (speed * dt * 0.02);

        // --------------------------------------------------------
        // SPAWNING
        // --------------------------------------------------------

        enemySpawnTimer -= dt;
        coinSpawnTimer -= dt;
        treeSpawnTimer -= dt;

        // Enemy spawning.
        if (enemySpawnTimer <= 0) {

            spawnEnemy();

            // Faster speeds make enemies appear more frequently.
            enemySpawnTimer =
                    Math.max(0.35, 1.1 - speed / 1800.0);
        }

        // Coin spawning.
        if (coinSpawnTimer <= 0) {

            spawnCoin();

            coinSpawnTimer = 0.65;
        }

        // Tree spawning.
        if (treeSpawnTimer <= 0) {

            spawnTree();

            treeSpawnTimer = 0.25;
        }

        // --------------------------------------------------------
        // MOVE ENEMIES
        // --------------------------------------------------------

        for (Enemy enemy : enemies) {

            enemy.y += speed * dt;

            // Give some enemies slight horizontal movement.
            enemy.x += enemy.drift * dt;

            // Keep enemies on the road.
            double minEnemyX = ROAD_X + 8;
            double maxEnemyX = ROAD_X + ROAD_W - CAR_W - 8;

            if (enemy.x < minEnemyX ||
                    enemy.x > maxEnemyX) {

                enemy.drift *= -1;
            }
        }

        // Remove enemies that have gone below the screen.
        enemies.removeIf(enemy -> enemy.y > H + 100);

        // --------------------------------------------------------
        // MOVE COINS
        // --------------------------------------------------------

        for (Coin coin : coins) {
            coin.y += speed * dt;
            coin.rotation += 4 * dt;
        }

        // --------------------------------------------------------
        // MOVE TREES
        // --------------------------------------------------------

        for (Tree tree : trees) {
            tree.y += speed * dt;
        }

        trees.removeIf(tree -> tree.y > H + 100);

        // --------------------------------------------------------
        // CHECK COIN COLLECTION
        // --------------------------------------------------------

        Iterator<Coin> coinIterator = coins.iterator();

        while (coinIterator.hasNext()) {

            Coin coin = coinIterator.next();

            if (collides(
                    playerX,
                    PLAYER_Y,
                    CAR_W,
                    CAR_H,
                    coin.x - 12,
                    coin.y - 12,
                    24,
                    24
            )) {

                coinsCollected++;

                score += 100;

                createCoinParticles(coin.x, coin.y);

                playSound(coinSound);

                coinIterator.remove();
            }
        }

        // --------------------------------------------------------
        // CHECK ENEMY COLLISIONS
        // --------------------------------------------------------

        for (Enemy enemy : enemies) {

            if (collides(
                    playerX + 5,
                    PLAYER_Y + 8,
                    CAR_W - 10,
                    CAR_H - 16,
                    enemy.x + 5,
                    enemy.y + 8,
                    CAR_W - 10,
                    CAR_H - 16
            )) {

                crash();

                break;
            }
        }

        // --------------------------------------------------------
        // PARTICLES
        // --------------------------------------------------------

        Iterator<Particle> particleIterator =
                particles.iterator();

        while (particleIterator.hasNext()) {

            Particle particle = particleIterator.next();

            particle.x += particle.vx * dt;
            particle.y += particle.vy * dt;

            particle.vy += 500 * dt;

            particle.life -= dt;

            if (particle.life <= 0) {
                particleIterator.remove();
            }
        }
    }

    // ============================================================
    // SPAWN ENEMY
    // ============================================================

    private void spawnEnemy() {

        int lane = random.nextInt(LANES);

        double x =
                ROAD_X +
                lane * LANE_W +
                (LANE_W - CAR_W) / 2;

        double y = -CAR_H - random.nextDouble() * 150;

        Enemy enemy = new Enemy();

        enemy.x = x;
        enemy.y = y;

        // Random enemy color.
        int colorType = random.nextInt(5);

        if (colorType == 0) {
            enemy.color = Color.web("#e53935");
        } else if (colorType == 1) {
            enemy.color = Color.web("#42a5f5");
        } else if (colorType == 2) {
            enemy.color = Color.web("#ffca28");
        } else if (colorType == 3) {
            enemy.color = Color.web("#ab47bc");
        } else {
            enemy.color = Color.web("#eeeeee");
        }

        // Small amount of horizontal drift.
        enemy.drift =
                (random.nextDouble() - 0.5) * 25;

        enemies.add(enemy);
    }

    // ============================================================
    // SPAWN COIN
    // ============================================================

    private void spawnCoin() {

        int lane = random.nextInt(LANES);

        Coin coin = new Coin();

        coin.x =
                ROAD_X +
                lane * LANE_W +
                LANE_W / 2;

        coin.y =
                -30 -
                random.nextDouble() * 100;

        coins.add(coin);
    }

    // ============================================================
    // SPAWN TREE
    // ============================================================

    private void spawnTree() {

        Tree tree = new Tree();

        // Randomly place tree on left or right roadside.
        boolean leftSide = random.nextBoolean();

        if (leftSide) {

            tree.x =
                    ROAD_X -
                    30 -
                    random.nextDouble() * 70;

        } else {

            tree.x =
                    ROAD_X +
                    ROAD_W +
                    30 +
                    random.nextDouble() * 70;
        }

        tree.y =
                -60 -
                random.nextDouble() * 150;

        tree.size =
                0.75 +
                random.nextDouble() * 0.6;

        trees.add(tree);
    }

    // ============================================================
    // COLLISION DETECTION
    // ============================================================

    private boolean collides(
            double x1,
            double y1,
            double w1,
            double h1,
            double x2,
            double y2,
            double w2,
            double h2
    ) {

        return x1 < x2 + w2 &&
                x1 + w1 > x2 &&
                y1 < y2 + h2 &&
                y1 + h1 > y2;
    }

    // ============================================================
    // CRASH
    // ============================================================

    private void crash() {

        state = GameState.GAME_OVER;

        playSound(crashSound);

        // Create crash particles.
        for (int i = 0; i < 40; i++) {

            Particle particle = new Particle();

            particle.x =
                    playerX + CAR_W / 2;

            particle.y =
                    PLAYER_Y + CAR_H / 2;

            particle.vx =
                    (random.nextDouble() - 0.5) * 500;

            particle.vy =
                    (random.nextDouble() - 0.5) * 500;

            particle.life =
                    0.4 + random.nextDouble() * 0.8;

            particle.size =
                    2 + random.nextDouble() * 5;

            particles.add(particle);
        }

        // Update high score.
        if (score > highScore) {

            highScore = score;

            saveHighScore();
        }
    }

    // ============================================================
    // COIN PARTICLES
    // ============================================================

    private void createCoinParticles(double x, double y) {

        for (int i = 0; i < 10; i++) {

            Particle particle = new Particle();

            particle.x = x;
            particle.y = y;

            particle.vx =
                    (random.nextDouble() - 0.5) * 180;

            particle.vy =
                    (random.nextDouble() - 0.5) * 180;

            particle.life =
                    0.25 + random.nextDouble() * 0.3;

            particle.size =
                    2 + random.nextDouble() * 3;

            particles.add(particle);
        }
    }

    // ============================================================
    // RENDER / DRAW EVERYTHING
    // ============================================================

    private void render() {

        // Clear the entire game screen.
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, W, H);

        // Draw background.
        drawBackground();

        // Draw road.
        drawRoad();

        // Draw trees.
        for (Tree tree : trees) {
            drawTree(tree);
        }

        // Draw coins.
        for (Coin coin : coins) {
            drawCoin(coin);
        }

        // Draw enemy cars.
        for (Enemy enemy : enemies) {
            drawCar(enemy.x, enemy.y, enemy.color, false);
        }

        // Draw player car.
        drawCar(
                playerX,
                PLAYER_Y,
                Color.web("#00e5ff"),
                true
        );

        // Draw particles above everything.
        drawParticles();

        // Draw HUD.
        if (state == GameState.PLAYING ||
                state == GameState.PAUSED ||
                state == GameState.GAME_OVER) {

            drawHUD();
        }

        // Menu screen.
        if (state == GameState.MENU) {
            drawMenu();
        }

        // Pause overlay.
        if (state == GameState.PAUSED) {
            drawPauseOverlay();
        }

        // Game-over overlay.
        if (state == GameState.GAME_OVER) {
            drawGameOver();
        }
    }

    // ============================================================
    // BACKGROUND
    // ============================================================

    private void drawBackground() {

        // Dark night sky / surroundings.
        gc.setFill(Color.web("#07101c"));
        gc.fillRect(0, 0, W, H);

        // Distant glow.
        gc.setFill(Color.web("#0d1b2a"));
        gc.fillRect(0, 0, W, H * 0.45);

        // Ground on both sides of the road.
        gc.setFill(Color.web("#08140d"));

        gc.fillRect(
                0,
                0,
                ROAD_X,
                H
        );

        gc.fillRect(
                ROAD_X + ROAD_W,
                0,
                W - ROAD_X - ROAD_W,
                H
        );
    }

    // ============================================================
    // ROAD
    // ============================================================

    private void drawRoad() {

        // Road surface.
        gc.setFill(Color.web("#252936"));

        gc.fillRect(
                ROAD_X,
                0,
                ROAD_W,
                H
        );

        // Road edges.
        gc.setFill(Color.web("#cfd8dc"));

        gc.fillRect(
                ROAD_X,
                0,
                5,
                H
        );

        gc.fillRect(
                ROAD_X + ROAD_W - 5,
                0,
                5,
                H
        );

        // Lane markings.
        gc.setFill(Color.web("#8a9099"));

        double dashHeight = 45;
        double gap = 35;
        double spacing = dashHeight + gap;

        for (int lane = 1; lane < LANES; lane++) {

            double x =
                    ROAD_X +
                    lane * LANE_W;

            for (
                    double y = -spacing + roadOffset;
                    y < H;
                    y += spacing
            ) {

                gc.fillRect(
                        x - 2,
                        y,
                        4,
                        dashHeight
                );
            }
        }
    }

    // ============================================================
    // DRAW TREE
    // ============================================================

    private void drawTree(Tree tree) {

        double s = tree.size;

        // Tree trunk.
        gc.setFill(Color.web("#4e342e"));

        gc.fillRect(
                tree.x - 6 * s,
                tree.y,
                12 * s,
                35 * s
        );

        // Tree leaves.
        gc.setFill(Color.web("#1b5e20"));

        gc.fillOval(
                tree.x - 25 * s,
                tree.y - 40 * s,
                50 * s,
                55 * s
        );

        gc.setFill(Color.web("#2e7d32"));

        gc.fillOval(
                tree.x - 18 * s,
                tree.y - 60 * s,
                36 * s,
                50 * s
        );
    }

    // ============================================================
    // DRAW COIN
    // ============================================================

    private void drawCoin(Coin coin) {

        double radius = 12;

        // Outer glow.
        gc.setFill(Color.rgb(255, 215, 0, 0.20));

        gc.fillOval(
                coin.x - radius - 5,
                coin.y - radius - 5,
                (radius + 5) * 2,
                (radius + 5) * 2
        );

        // Coin.
        gc.setFill(Color.web("#ffd700"));

        gc.fillOval(
                coin.x - radius,
                coin.y - radius,
                radius * 2,
                radius * 2
        );

        // Highlight.
        gc.setFill(Color.web("#fff59d"));

        gc.fillOval(
                coin.x - 5,
                coin.y - 7,
                4,
                7
        );

        // Dollar-like center mark.
        gc.setFill(Color.web("#b8860b"));

        gc.setFont(
                Font.font(
                        "Arial",
                        FontWeight.BOLD,
                        12
                )
        );

        gc.fillText(
                "$",
                coin.x - 4,
                coin.y + 4
        );
    }

    // ============================================================
    // DRAW CAR
    // ============================================================

    private void drawCar(
            double x,
            double y,
            Color color,
            boolean player
    ) {

        // Headlight glow for the player's car.
        if (player) {

            gc.setFill(
                    Color.rgb(
                            255,
                            255,
                            220,
                            0.12
                    )
            );

            gc.fillOval(
                    x - 30,
                    y - 30,
                    CAR_W + 60,
                    100
            );
        }

        // Shadow underneath car.
        gc.setFill(
                Color.rgb(0, 0, 0, 0.45)
        );

        gc.fillRoundRect(
                x - 3,
                y + 5,
                CAR_W + 6,
                CAR_H,
                12,
                12
        );

        // Main body.
        gc.setFill(color);

        gc.fillRoundRect(
                x,
                y,
                CAR_W,
                CAR_H,
                10,
                10
        );

        // Dark windshield.
        gc.setFill(Color.web("#101820"));

        gc.fillRoundRect(
                x + 7,
                y + 12,
                CAR_W - 14,
                22,
                7,
                7
        );

        // Rear / front window depending on orientation.
        gc.setFill(Color.web("#17212b"));

        gc.fillRoundRect(
                x + 7,
                y + 43,
                CAR_W - 14,
                17,
                6,
                6
        );

        // Wheels.
        gc.setFill(Color.web("#111111"));

        gc.fillRoundRect(
                x - 5,
                y + 12,
                7,
                22,
                3,
                3
        );

        gc.fillRoundRect(
                x + CAR_W - 2,
                y + 12,
                7,
                22,
                3,
                3
        );

        gc.fillRoundRect(
                x - 5,
                y + 48,
                7,
                22,
                3,
                3
        );

        gc.fillRoundRect(
                x + CAR_W - 2,
                y + 48,
                7,
                22,
                3,
                3
        );

        // Lights.
        if (player) {

            // Front headlights.
            gc.setFill(Color.web("#fffde7"));

            gc.fillRoundRect(
                    x + 5,
                    y + 2,
                    10,
                    6,
                    3,
                    3
            );

            gc.fillRoundRect(
                    x + CAR_W - 15,
                    y + 2,
                    10,
                    6,
                    3,
                    3
            );

        } else {

            // Enemy rear lights.
            gc.setFill(Color.web("#ff1744"));

            gc.fillRoundRect(
                    x + 5,
                    y + CAR_H - 8,
                    10,
                    5,
                    2,
                    2
            );

            gc.fillRoundRect(
                    x + CAR_W - 15,
                    y + CAR_H - 8,
                    10,
                    5,
                    2,
                    2
            );
        }

        // Small highlight.
        gc.setFill(
                Color.rgb(255, 255, 255, 0.25)
        );

        gc.fillRoundRect(
                x + 5,
                y + 7,
                4,
                35,
                3,
                3
        );
    }

    // ============================================================
    // PARTICLES
    // ============================================================

    private void drawParticles() {

        for (Particle particle : particles) {

            double alpha =
                    Math.max(
                            0,
                            Math.min(1, particle.life * 2)
                    );

            gc.setFill(
                    Color.rgb(
                            255,
                            190,
                            40,
                            alpha
                    )
            );

            gc.fillOval(
                    particle.x - particle.size / 2,
                    particle.y - particle.size / 2,
                    particle.size,
                    particle.size
            );
        }
    }

    // ============================================================
    // HUD
    // ============================================================

    private void drawHUD() {

        // Semi-transparent top panel.
        gc.setFill(
                Color.rgb(0, 0, 0, 0.45)
        );

        gc.fillRect(
                0,
                0,
                W,
                70
        );

        // Score.
        gc.setFill(Color.WHITE);

        gc.setFont(
                Font.font(
                        "Arial",
                        FontWeight.BOLD,
                        20
                )
        );

        gc.fillText(
                "SCORE  " + score,
                18,
                28
        );

        // Coins.
        gc.setFill(Color.web("#ffd700"));

        gc.fillText(
                "COINS  " + coinsCollected,
                18,
                53
        );

        // Speed.
        gc.setFill(Color.web("#00e5ff"));

        gc.fillText(
                "SPEED  " + (int) speed,
                W - 125,
                28
        );

        // High score.
        gc.setFill(Color.WHITE);

        gc.setFont(
                Font.font(
                        "Arial",
                        FontWeight.NORMAL,
                        14
                )
        );

        gc.fillText(
                "BEST  " + highScore,
                W - 100,
                53
        );
    }

    // ============================================================
    // MENU
    // ============================================================

    private void drawMenu() {

        // Dark transparent overlay.
        gc.setFill(
                Color.rgb(0, 0, 0, 0.60)
        );

        gc.fillRect(
                0,
                0,
                W,
                H
        );

        // Title.
        gc.setFill(Color.web("#00e5ff"));

        gc.setFont(
                Font.font(
                        "Arial",
                        FontWeight.BOLD,
                        48
                )
        );

        gc.fillText(
                "NIGHT",
                120,
                250
        );

        gc.setFill(Color.WHITE);

        gc.fillText(
                "RACER",
                120,
                305
        );

        // Subtitle.
        gc.setFill(Color.web("#b0bec5"));

        gc.setFont(
                Font.font(
                        "Arial",
                        FontWeight.NORMAL,
                        16
                )
        );

        gc.fillText(
                "ENDLESS NIGHT DRIVE",
                135,
                340
        );

        // Start instruction.
        gc.setFill(Color.WHITE);

        gc.setFont(
                Font.font(
                        "Arial",
                        FontWeight.BOLD,
                        22
                )
        );

        gc.fillText(
                "PRESS ENTER TO START",
                105,
                460
        );

        // Controls.
        gc.setFont(
                Font.font(
                        "Arial",
                        FontWeight.NORMAL,
                        15
                )
        );

        gc.setFill(Color.web("#90a4ae"));

        gc.fillText(
                "← →  /  A D    STEER",
                150,
                515
        );

        gc.fillText(
                "P    PAUSE",
                195,
                545
        );

        gc.fillText(
                "ESC    EXIT",
                192,
                575
        );

        // High score.
        gc.setFill(Color.web("#ffd700"));

        gc.setFont(
                Font.font(
                        "Arial",
                        FontWeight.BOLD,
                        17
                )
        );

        gc.fillText(
                "BEST SCORE: " + highScore,
                165,
                650
        );
    }

    // ============================================================
    // PAUSE OVERLAY
    // ============================================================

    private void drawPauseOverlay() {

        gc.setFill(
                Color.rgb(0, 0, 0, 0.65)
        );

        gc.fillRect(
                0,
                0,
                W,
                H
        );

        gc.setFill(Color.WHITE);

        gc.setFont(
                Font.font(
                        "Arial",
                        FontWeight.BOLD,
                        42
                )
        );

        gc.fillText(
                "PAUSED",
                165,
                350
        );

        gc.setFont(
                Font.font(
                        "Arial",
                        FontWeight.NORMAL,
                        18
                )
        );

        gc.fillText(
                "PRESS P TO RESUME",
                150,
                400
        );
    }

    // ============================================================
    // GAME OVER
    // ============================================================

    private void drawGameOver() {

        gc.setFill(
                Color.rgb(0, 0, 0, 0.72)
        );

        gc.fillRect(
                0,
                0,
                W,
                H
        );

        gc.setFill(Color.web("#ff1744"));

        gc.setFont(
                Font.font(
                        "Arial",
                        FontWeight.BOLD,
                        44
                )
        );

        gc.fillText(
                "CRASHED!",
                145,
                300
        );

        gc.setFill(Color.WHITE);

        gc.setFont(
                Font.font(
                        "Arial",
                        FontWeight.BOLD,
                        22
                )
        );

        gc.fillText(
                "SCORE  " + score,
                175,
                355
        );

        gc.setFill(Color.web("#ffd700"));

        gc.fillText(
                "COINS  " + coinsCollected,
                175,
                390
        );

        gc.setFill(Color.WHITE);

        gc.setFont(
                Font.font(
                        "Arial",
                        FontWeight.NORMAL,
                        18
                )
        );

        gc.fillText(
                "BEST  " + highScore,
                185,
                430
        );

        gc.setFont(
                Font.font(
                        "Arial",
                        FontWeight.BOLD,
                        21
                )
        );

        gc.fillText(
                "PRESS ENTER TO RETRY",
                125,
                510
        );
    }

    // ============================================================
    // SOUND LOADING
    // ============================================================

    private void loadSounds() {

        try {

            coinSound =
                    createSound("/sounds/coin.wav");

            crashSound =
                    createSound("/sounds/crash.wav");

            startSound =
                    createSound("/sounds/start.wav");

            pauseSound =
                    createSound("/sounds/pause.wav");

        } catch (Exception e) {

            // The game can still run even if
            // sound files are missing.
            System.out.println(
                    "Sound files not loaded yet."
            );
        }
    }

    // ============================================================
    // CREATE AUDIO CLIP
    // ============================================================

    private AudioClip createSound(String resource) {

        var url = getClass().getResource(resource);

        if (url == null) {

            System.out.println(
                    "Sound not found: " + resource
            );

            return null;
        }

        return new AudioClip(
                url.toExternalForm()
        );
    }

    // ============================================================
    // PLAY SOUND
    // ============================================================

    private void playSound(AudioClip sound) {

        // Check for null so the game does not crash
        // when a sound file is missing.
        if (sound != null) {
            sound.play();
        }
    }

    // ============================================================
    // HIGH SCORE - LOAD
    // ============================================================

    private void loadHighScore() {

        try {

            if (Files.exists(highScoreFile)) {

                String value =
                        Files.readString(
                                highScoreFile
                        ).trim();

                highScore =
                        Integer.parseInt(value);
            }

        } catch (Exception e) {

            highScore = 0;
        }
    }

    // ============================================================
    // HIGH SCORE - SAVE
    // ============================================================

    private void saveHighScore() {

        try {

            Files.writeString(
                    highScoreFile,
                    String.valueOf(highScore)
            );

        } catch (Exception e) {

            System.out.println(
                    "Could not save high score."
            );
        }
    }

    // ============================================================
    // ENEMY CLASS
    // ============================================================

    private static class Enemy {

        double x;
        double y;

        double drift;

        Color color;
    }

    // ============================================================
    // COIN CLASS
    // ============================================================

    private static class Coin {

        double x;
        double y;

        double rotation;
    }

    // ============================================================
    // TREE CLASS
    // ============================================================

    private static class Tree {

        double x;
        double y;

        double size;
    }

    // ============================================================
    // PARTICLE CLASS
    // ============================================================

    private static class Particle {

        double x;
        double y;

        double vx;
        double vy;

        double life;
        double size;
    }

    // ============================================================
    // MAIN METHOD
    // ============================================================

    public static void main(String[] args) {

        // Launch the JavaFX application.
        launch(args);
    }
}