package org.telegram.messenger;

import java.util.ArrayList;

public class Timer {

    public static Timer create(String name) {
        if (!BuildVars.LOGS_ENABLED)
            return null;
        return new Timer(name);
    }

    public static Task start(Timer logger, String task) {
        if (logger != null) {
            return logger.start(task);
        }
        return null;
    }

    public static void log(Timer logger, String log) {
        if (logger != null) {
            logger.log(log);
        }
    }

    public static void finish(Timer logger) {
        if (logger != null) {
            logger.finish();
        }
    }

    public static void done(Task task) {
        if (task != null) {
            task.done();
        }
    }


    int pad = 0;

    final String name;
    final long startTime;

    public Timer(String name) {
        this.name = name;
        this.startTime = System.currentTimeMillis();
    }

    public ArrayList<Task> tasks = new ArrayList<>();

    private Task start(String task) {
        Task timer = new Task(task);
        tasks.add(timer);
        return timer;
    }

    private void log(String log) {
        tasks.add(new Log(log));
    }

    private void finish() {
        final long totalTime = System.currentTimeMillis() - startTime;
        StringBuilder s = new StringBuilder();
        s.append(name).append(" total=").append(totalTime).append("ms\n");
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i) == null) continue;
            s.append("#").append(i);
            final int pad = tasks.get(i).pad;
            for (int j = 0; j < pad; ++j) s.append(" ");
            s.append(" ").append(tasks.get(i)).append("\n");
        }
        FileLog.d(s.toString());
    }

    public class Task {
        final long startTime;
        long endTime = -1;

        final String task;
        int pad;

        public Task(String task) {
            startTime = System.currentTimeMillis();
            this.task = task;
            Timer.this.pad++;
        }

        private void done() {
            if (this.endTime < 0)
                this.pad = Timer.this.pad--;
            this.endTime = System.currentTimeMillis();
        }

        @Override
        public String toString() {
            return task + ": " + (endTime < 0 ? "not done" : (endTime - startTime) + "ms");
        }
    }

    public class Log extends Task {
        public Log(String task) {
            super(task);
        }
        @Override
        public String toString() {
            return task;
        }
    }

}
