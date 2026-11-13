package com.kaori.browser.ai;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** Shared executors so AI work never blocks the Android main thread. */
public final class AiExecutors {

    private static final int MAX_BACKGROUND_THREADS = 3;

    private static final ExecutorService BACKGROUND =
            Executors.newFixedThreadPool(MAX_BACKGROUND_THREADS, new NamedFactory("kaori-ai"));

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static final Executor MAIN_EXECUTOR = command -> {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            command.run();
        } else {
            MAIN.post(command);
        }
    };

    private AiExecutors() {
    }

    public static ExecutorService background() {
        return BACKGROUND;
    }

    public static Handler mainHandler() {
        return MAIN;
    }

    public static Executor main() {
        return MAIN_EXECUTOR;
    }

    private static final class NamedFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger counter = new AtomicInteger(1);

        NamedFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, prefix + "-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
