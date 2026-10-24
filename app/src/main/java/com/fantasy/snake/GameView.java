package com.fantasy.snake;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    private static class Point {
        final int x, y;
        Point(int x, int y) { this.x = x; this.y = y; }
        @Override public boolean equals(Object o) {
            if (!(o instanceof Point)) return false;
            Point p = (Point) o;
            return p.x == x && p.y == y;
        }
        @Override public int hashCode() { return x * 31 + y; }
    }

    private static class Particle {
        float x, y, vx, vy, life;
        int color;
        Particle(float x, float y, float vx, float vy, float life, int color) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.life = life; this.color = color;
        }
    }

    private Thread thread;
    private volatile boolean running;

    private final int cols = 18;
    private final int rows = 30;
    private float cell;

    private List<Point> snake = new ArrayList<>();
    private Point direction = new Point(0, -1);
    private Point pendingDirection = direction;
    private Point food = new Point(9, 10);
    private int score = 0;
    private boolean gameOver = false;
    private long moveIntervalMs = 160L;
    private long lastMoveTime = 0L;

    private final List<Particle> particles = new ArrayList<>();
    private float pulse = 0f;
    private ValueAnimator pulseAnimator;

    private final Random random = new Random();

    private final Paint bgPaint = new Paint();
    private final Paint gridPaint = new Paint();
    private final Paint textPaint = new Paint();
    private final Paint gameOverPaint = new Paint();

    private final List<float[]> stars = new ArrayList<>();

    private float touchStartX = 0f;
    private float touchStartY = 0f;

    public GameView(Context context) {
        super(context);
        getHolder().addCallback(this);
        setFocusable(true);

        snake.add(new Point(9, 15));

        gridPaint.setColor(Color.argb(25, 255, 255, 255));

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(60f);
        textPaint.setAntiAlias(true);
        textPaint.setShadowLayer(12f, 0f, 0f, Color.argb(180, 120, 60, 255));

        gameOverPaint.setColor(Color.WHITE);
        gameOverPaint.setTextSize(90f);
        gameOverPaint.setAntiAlias(true);
        gameOverPaint.setTextAlign(Paint.Align.CENTER);
        gameOverPaint.setShadowLayer(20f, 0f, 0f, Color.MAGENTA);

        for (int i = 0; i < 60; i++) {
            stars.add(new float[]{random.nextFloat(), random.nextFloat(), random.nextFloat() * 3f + 1f});
        }

        pulseAnimator = ValueAnimator.ofFloat(0f, 1f, 0f);
        pulseAnimator.setDuration(900);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                pulse = (float) animation.getAnimatedValue();
            }
        });
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        cell = getWidth() / (float) cols;
        placeFood();
        running = true;
        thread = new Thread(this);
        thread.start();
        pulseAnimator.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        cell = w / (float) cols;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        running = false;
        pulseAnimator.cancel();
        try {
            if (thread != null) thread.join();
        } catch (InterruptedException ignored) {
        }
    }

    @Override
    public void run() {
        while (running) {
            long now = System.currentTimeMillis();
            if (!gameOver && now - lastMoveTime >= moveIntervalMs) {
                lastMoveTime = now;
                update();
            }
            Canvas canvas = getHolder().lockCanvas();
            if (canvas == null) continue;
            try {
                drawGame(canvas);
            } finally {
                getHolder().unlockCanvasAndPost(canvas);
            }
            try {
                Thread.sleep(16);
            } catch (InterruptedException ignored) {
            }
        }
    }

    private void update() {
        direction = pendingDirection;
        Point head = snake.get(0);
        int nx = head.x + direction.x;
        int ny = head.y + direction.y;
        if (nx < 0) nx = cols - 1;
        if (nx >= cols) nx = 0;
        if (ny < 0) ny = rows - 1;
        if (ny >= rows) ny = 0;
        Point newHead = new Point(nx, ny);

        if (snake.contains(newHead)) {
            gameOver = true;
            return;
        }

        snake.add(0, newHead);
        if (newHead.equals(food)) {
            score++;
            moveIntervalMs = Math.max(70, moveIntervalMs - 2);
            spawnParticles(food, Color.rgb(255, 210, 60));
            placeFood();
        } else {
            snake.remove(snake.size() - 1);
        }
    }

    private void placeFood() {
        Point p;
        do {
            p = new Point(random.nextInt(cols), random.nextInt(rows));
        } while (snake.contains(p));
        food = p;
    }

    private void spawnParticles(Point at, int color) {
        float cx = (at.x + 0.5f) * cell;
        float cy = (at.y + 0.5f) * cell;
        for (int i = 0; i < 18; i++) {
            double angle = random.nextFloat() * Math.PI * 2;
            float speed = random.nextFloat() * 6f + 2f;
            particles.add(new Particle(cx, cy, (float) (Math.cos(angle) * speed), (float) (Math.sin(angle) * speed), 1f, color));
        }
    }

    private void drawGame(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();

        LinearGradient bg = new LinearGradient(0f, 0f, 0f, h, new int[]{
                Color.rgb(20, 10, 45), Color.rgb(40, 15, 70), Color.rgb(10, 5, 30)
        }, null, Shader.TileMode.CLAMP);
        bgPaint.setShader(bg);
        canvas.drawRect(0f, 0f, w, h, bgPaint);

        Paint starPaint = new Paint();
        for (float[] s : stars) {
            float sx = s[0], sy = s[1], r = s[2];
            float tw = (float) (Math.sin(System.currentTimeMillis() / 400.0 + sx * 10) + 1f) / 2f;
            starPaint.setColor(Color.argb((int) (80 + tw * 150), 255, 255, 255));
            canvas.drawCircle(sx * w, sy * h, r, starPaint);
        }

        for (int i = 0; i <= cols; i++) canvas.drawLine(i * cell, 0f, i * cell, rows * cell, gridPaint);
        for (int i = 0; i <= rows; i++) canvas.drawLine(0f, i * cell, cols * cell, i * cell, gridPaint);

        drawFood(canvas);
        drawSnake(canvas);
        updateAndDrawParticles(canvas);

        canvas.drawText("Score: " + score, 24f, 70f, textPaint);

        if (gameOver) {
            canvas.drawText("GAME OVER", w / 2f, h / 2f, gameOverPaint);
            Paint tapPaint = new Paint(textPaint);
            tapPaint.setTextAlign(Paint.Align.CENTER);
            tapPaint.setTextSize(45f);
            canvas.drawText("Tap to restart", w / 2f, h / 2f + 70f, tapPaint);
        }
    }

    private void drawFood(Canvas canvas) {
        float cx = (food.x + 0.5f) * cell;
        float cy = (food.y + 0.5f) * cell;
        float glowRadius = cell * (0.9f + pulse * 0.4f);
        Paint glow = new Paint();
        glow.setShader(new RadialGradient(cx, cy, glowRadius, new int[]{
                Color.argb(180, 255, 200, 60), Color.argb(0, 255, 200, 60)
        }, null, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, glowRadius, glow);

        Paint fruitPaint = new Paint();
        fruitPaint.setAntiAlias(true);
        fruitPaint.setShader(new RadialGradient(cx - cell * 0.15f, cy - cell * 0.15f, cell * 0.6f, new int[]{
                Color.rgb(255, 240, 150), Color.rgb(255, 90, 60), Color.rgb(200, 20, 60)
        }, new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, cell * 0.38f, fruitPaint);

        Paint leafPaint = new Paint();
        leafPaint.setColor(Color.rgb(60, 200, 90));
        leafPaint.setAntiAlias(true);
        canvas.drawOval(cx - 4f, cy - cell * 0.55f, cx + 14f, cy - cell * 0.3f, leafPaint);
    }

    private void drawSnake(Canvas canvas) {
        int n = snake.size();
        for (int i = n - 1; i >= 0; i--) {
            Point seg = snake.get(i);
            float cx = (seg.x + 0.5f) * cell;
            float cy = (seg.y + 0.5f) * cell;
            float t = 1f - i / (float) n;
            float hue = System.currentTimeMillis() / 20 % 360;
            int segColor = Color.HSVToColor(new float[]{(hue + i * 6) % 360f, 0.75f, 0.55f + t * 0.4f});

            boolean isHead = i == 0;
            float radius = cell * (isHead ? 0.52f : 0.42f);

            if (isHead) {
                Paint glow = new Paint();
                glow.setShader(new RadialGradient(cx, cy, radius * 2.2f, new int[]{
                        Color.argb(140, 180, 90, 255), Color.argb(0, 180, 90, 255)
                }, null, Shader.TileMode.CLAMP));
                canvas.drawCircle(cx, cy, radius * 2.2f, glow);
            }

            Paint bodyPaint = new Paint();
            bodyPaint.setAntiAlias(true);
            bodyPaint.setShader(new RadialGradient(cx - radius * 0.3f, cy - radius * 0.3f, radius * 1.4f,
                    new int[]{Color.WHITE, segColor, Color.argb(255, 20, 10, 40)},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, radius, bodyPaint);

            if (isHead) {
                Paint eyePaint = new Paint();
                eyePaint.setColor(Color.WHITE);
                eyePaint.setAntiAlias(true);
                Paint pupilPaint = new Paint();
                pupilPaint.setColor(Color.BLACK);
                pupilPaint.setAntiAlias(true);
                float ex = direction.y * radius * 0.4f;
                float ey = -direction.x * radius * 0.4f;
                canvas.drawCircle(cx + ex + direction.x * radius * 0.3f, cy + ey + direction.y * radius * 0.3f, radius * 0.22f, eyePaint);
                canvas.drawCircle(cx - ex + direction.x * radius * 0.3f, cy - ey + direction.y * radius * 0.3f, radius * 0.22f, eyePaint);
                canvas.drawCircle(cx + ex + direction.x * radius * 0.4f, cy + ey + direction.y * radius * 0.4f, radius * 0.1f, pupilPaint);
                canvas.drawCircle(cx - ex + direction.x * radius * 0.4f, cy - ey + direction.y * radius * 0.4f, radius * 0.1f, pupilPaint);
            }
        }
    }

    private void updateAndDrawParticles(Canvas canvas) {
        Iterator<Particle> iterator = particles.iterator();
        Paint paint = new Paint();
        while (iterator.hasNext()) {
            Particle p = iterator.next();
            p.x += p.vx;
            p.y += p.vy;
            p.vy += 0.15f;
            p.life -= 0.03f;
            if (p.life <= 0f) {
                iterator.remove();
                continue;
            }
            int alpha = Math.max(0, Math.min(255, (int) (p.life * 255)));
            paint.setColor(Color.argb(alpha, Color.red(p.color), Color.green(p.color), Color.blue(p.color)));
            canvas.drawCircle(p.x, p.y, 8f * p.life, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchStartX = event.getX();
                touchStartY = event.getY();
                if (gameOver) restart();
                break;
            case MotionEvent.ACTION_UP:
                float dx = event.getX() - touchStartX;
                float dy = event.getY() - touchStartY;
                if (Math.abs(dx) > Math.abs(dy)) {
                    if (dx > 30 && direction.x == 0) pendingDirection = new Point(1, 0);
                    else if (dx < -30 && direction.x == 0) pendingDirection = new Point(-1, 0);
                } else {
                    if (dy > 30 && direction.y == 0) pendingDirection = new Point(0, 1);
                    else if (dy < -30 && direction.y == 0) pendingDirection = new Point(0, -1);
                }
                break;
        }
        return true;
    }

    private void restart() {
        snake = new ArrayList<>();
        snake.add(new Point(9, 15));
        direction = new Point(0, -1);
        pendingDirection = direction;
        score = 0;
        moveIntervalMs = 160L;
        gameOver = false;
        particles.clear();
        placeFood();
    }
}
