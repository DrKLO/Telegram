package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;

import java.util.ArrayList;
import java.util.Calendar;

public class FireworksOverlay extends View {

    private static Paint[] paint;
    private static Paint[] heartPaint;
    private RectF rect = new RectF();
    private long lastUpdateTime;
    private boolean started;
    private boolean startedFall;
    private float speedCoef = 1.0f;
    private int fallingDownCount;
    private static Drawable[] heartDrawable;
    private static final int particlesCount = SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_LOW ? 50 : 60;
    private static final int fallParticlesCount = SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_LOW ? 20 : 30;
    private boolean isFebruary14;

    private static int[] colors = new int[] {
            0xff2CBCE8,
            0xff9E04D0,
            0xffFECB02,
            0xffFD2357,
            0xff278CFE,
            0xff59B86C
    };

    private static int[] heartColors = new int[] {
            0xffE2557B,
            0xff5FCDF2,
            0xffFFDA69,
            0xffDB6363,
            0xffE376B0
    };

    static {
        paint = new Paint[colors.length];
        for (int a = 0; a < paint.length; a++) {
            paint[a] = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint[a].setColor(colors[a]);
        }
    }

    private class Particle {
        byte type;
        byte colorType;
        byte side;
        byte typeSize;
        byte xFinished;
        byte finishedStart;

        float x;
        float y;
        short rotation;
        float moveX;
        float moveY;

        private void draw(Canvas canvas) {
            if (type == 0) {
                canvas.drawCircle(x, y, AndroidUtilities.dp(typeSize), paint[colorType]);
            } else if (type == 1) {
                rect.set(x - AndroidUtilities.dp(typeSize), y - AndroidUtilities.dp(2), x + AndroidUtilities.dp(typeSize), y + AndroidUtilities.dp(2));
                canvas.save();
                canvas.rotate(rotation, rect.centerX(), rect.centerY());
                canvas.drawRoundRect(rect, AndroidUtilities.dp(2), AndroidUtilities.dp(2), paint[colorType]);
                canvas.restore();
            } else if (type == 2) {
                Drawable drawable = heartDrawable[colorType];
                int w = drawable.getIntrinsicWidth() / 2;
                int h = drawable.getIntrinsicHeight() / 2;
                drawable.setBounds((int) x - w, (int) y - h, (int) x + w, (int) y + h);
                canvas.save();
                canvas.rotate(rotation, x, y);
                canvas.scale(typeSize / 6.0f, typeSize / 6.0f, x, y);
                drawable.draw(canvas);
                canvas.restore();
            }
        }

        private boolean update(int dt) {
            float moveCoef = dt / 16.0f;
            x += moveX * moveCoef;
            y += moveY * moveCoef;
            if (xFinished != 0) {
                float dp = AndroidUtilities.dp(1) * 0.5f;
                if (xFinished == 1) {
                    moveX += dp * moveCoef * 0.05f;
                    if (moveX >= dp) {
                        xFinished = 2;
                    }
                } else {
                    moveX -= dp * moveCoef * 0.05f;
                    if (moveX <= -dp) {
                        xFinished = 1;
                    }
                }
            } else {
                if (side == 0) {
                    if (moveX > 0) {
                        moveX -= moveCoef * 0.05f;
                        if (moveX <= 0) {
                            moveX = 0;
                            xFinished = finishedStart;
                        }
                    }
                } else {
                    if (moveX < 0) {
                        moveX += moveCoef * 0.05f;
                        if (moveX >= 0) {
                            moveX = 0;
                            xFinished = finishedStart;
                        }
                    }
                }
            }
            float yEdge = -AndroidUtilities.dp(1.0f) / 2.0f;
            boolean wasNegative = moveY < yEdge;
            if (moveY > yEdge) {
                moveY += AndroidUtilities.dp(1.0f) / 3.0f * moveCoef * speedCoef;
            } else {
                moveY += AndroidUtilities.dp(1.0f) / 3.0f * moveCoef;
            }
            if (wasNegative && moveY > yEdge) {
                fallingDownCount++;
            }
            if (type == 1 || type == 2) {
                rotation += moveCoef * 10;
                if (rotation > 360) {
                    rotation -= 360;
                }
            }
            return y >= getMeasuredHeight();
        }
    }

    private ArrayList<Particle> particles = new ArrayList<>(particlesCount + fallParticlesCount);

    public FireworksOverlay(Context context) {
        super(context);
    }

    private void loadHeartDrawables() {
        if (heartDrawable != null) {
            return;
        }
        heartDrawable = new Drawable[heartColors.length];
        for (int a = 0; a < heartDrawable.length; a++) {
            heartDrawable[a] = ApplicationLoader.applicationContext.getResources().getDrawable(R.drawable.heart_confetti).mutate();
            heartDrawable[a].setColorFilter(new PorterDuffColorFilter(heartColors[a], PorterDuff.Mode.MULTIPLY));
        }
    }

    private Particle createParticle(boolean fall) {
        Particle particle = new Particle();
        particle.type = (byte) Utilities.random.nextInt(2);
        if (isFebruary14 && particle.type == 0) {
            particle.type = 2;
            particle.colorType = (byte) Utilities.random.nextInt(heartColors.length);
        } else {
            particle.colorType = (byte) Utilities.random.nextInt(colors.length);
        }
        particle.side = (byte) Utilities.random.nextInt(2);
        particle.finishedStart = (byte) (1 + Utilities.random.nextInt(2));
        if (particle.type == 0 || particle.type == 2) {
            particle.typeSize = (byte) (4 + Utilities.random.nextFloat() * 2);
        } else {
            particle.typeSize = (byte) (4 + Utilities.random.nextFloat() * 4);
        }
        if (fall) {
            particle.y = -Utilities.random.nextFloat() * getMeasuredHeight() * 1.2f;
            particle.x = AndroidUtilities.dp(5) + Utilities.random.nextInt(getMeasuredWidth() - AndroidUtilities.dp(10));
            particle.xFinished = particle.finishedStart;
        } else {
            int xOffset = AndroidUtilities.dp(4 + Utilities.random.nextInt(10));
            int yOffset = getMeasuredHeight() / 4;
            if (particle.side == 0) {
                particle.x = -xOffset;
            } else {
                particle.x = getMeasuredWidth() + xOffset;
            }
            particle.moveX = (particle.side == 0 ? 1 : -1) * (AndroidUtilities.dp(1.2f) + Utilities.random.nextFloat() * AndroidUtilities.dp(4));
            particle.moveY = -(AndroidUtilities.dp(4) + Utilities.random.nextFloat() * AndroidUtilities.dp(4));
            particle.y = yOffset / 2 + Utilities.random.nextInt(yOffset * 2);
        }
        return particle;
    }

    public boolean isStarted() {
        return started;
    }

    public void start() {
        particles.clear();
        if (Build.VERSION.SDK_INT >= 18) {
            setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }
        started = true;
        startedFall = false;
        fallingDownCount = 0;
        speedCoef = 1.0f;
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        int month = calendar.get(Calendar.MONTH);
        isFebruary14 = month == 1 && (BuildVars.DEBUG_PRIVATE_VERSION || day == 14);
        if (isFebruary14) {
            loadHeartDrawables();
        }
        for (int a = 0; a < particlesCount; a++) {
            particles.add(createParticle(false));
        }
        invalidate();
    }

    private void startFall() {
        if (startedFall) {
            return;
        }
        startedFall = true;
        for (int a = 0; a < fallParticlesCount; a++) {
            particles.add(createParticle(true));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long newTime = SystemClock.elapsedRealtime();
        int dt = (int) (newTime - lastUpdateTime);
        lastUpdateTime = newTime;
        if (dt > 18) {
            dt = 16;
        }
        for (int a = 0, N = particles.size(); a < N; a++) {
            Particle p = particles.get(a);
            p.draw(canvas);
            if (p.update(dt)) {
                particles.remove(a);
                a--;
                N--;
            }
        }
        if (fallingDownCount >= particlesCount / 2 && speedCoef > 0.2f) {
            startFall();
            speedCoef -= dt / 16.0f * 0.15f;
            if (speedCoef < 0.2f) {
                speedCoef = 0.2f;
            }
        }
        if (!particles.isEmpty()) {
            invalidate();
        } else {
            started = false;
            if (Build.VERSION.SDK_INT >= 18) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (!started) {
                        setLayerType(View.LAYER_TYPE_NONE, null);
                    }
                });
            }
        }
    }
}
