package com.upb.dfs.namenode.service;

public final class PathUtil {
    private PathUtil() {}

    public static String normalize(String input) {
        if (input == null || input.isBlank()) return "/";
        String s = input.replace("\\", "/").trim();
        if (!s.startsWith("/")) s = "/" + s;
        // collapse repeated slashes and resolve . and ..
        String[] parts = s.split("/");
        java.util.Deque<String> stack = new java.util.ArrayDeque<>();
        for (String p : parts) {
            if (p.isEmpty() || p.equals(".")) continue;
            if (p.equals("..")) { if (!stack.isEmpty()) stack.pollLast(); continue; }
            stack.offerLast(p);
        }
        if (stack.isEmpty()) return "/";
        StringBuilder sb = new StringBuilder();
        for (String p : stack) sb.append('/').append(p);
        return sb.toString();
    }

    public static String parent(String path) {
        String n = normalize(path);
        if (n.equals("/")) return "/";
        int idx = n.lastIndexOf('/');
        return idx <= 0 ? "/" : n.substring(0, idx);
    }

    public static String name(String path) {
        String n = normalize(path);
        int idx = n.lastIndexOf('/');
        return idx < 0 ? n : n.substring(idx + 1);
    }
}
