package com.upb.dfs.namenode.security;

public class AuthContext {
    private static final ThreadLocal<Principal> CURRENT = new ThreadLocal<>();

    public static void set(Principal p) { CURRENT.set(p); }
    public static Principal get() { return CURRENT.get(); }
    public static void clear() { CURRENT.remove(); }

    public static class Principal {
        public final Long userId;
        public final String username;
        public Principal(Long userId, String username) {
            this.userId = userId; this.username = username;
        }
    }
}
