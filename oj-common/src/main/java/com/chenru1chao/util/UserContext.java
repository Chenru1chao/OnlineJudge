package com.chenru1chao.util;

public class UserContext {
    private static final ThreadLocal<Integer> threadLocal = new ThreadLocal<>();

    public static void set(Integer userId) {
        threadLocal.set(userId);
    }

    public static Integer get() {
        return threadLocal.get();
    }

    public static void remove() {
        threadLocal.remove();
    }
}
