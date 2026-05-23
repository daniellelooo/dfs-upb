package com.upb.dfs.client;

import com.upb.dfs.common.dto.AuthDtos;
import com.upb.dfs.common.dto.FileDtos;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(name = "dfs",
        mixinStandardHelpOptions = true,
        version = "dfs-client 1.0.0",
        description = "DFS client CLI",
        subcommands = {
                DfsCli.RegisterCmd.class,
                DfsCli.LoginCmd.class,
                DfsCli.LogoutCmd.class,
                DfsCli.WhoamiCmd.class,
                DfsCli.PutCmd.class,
                DfsCli.GetCmd.class,
                DfsCli.LsCmd.class,
                DfsCli.RmCmd.class,
                DfsCli.MkdirCmd.class,
                DfsCli.RmdirCmd.class,
                DfsCli.StatCmd.class
        })
public class DfsCli implements Callable<Integer> {

    @Option(names = {"--namenode"}, description = "NameNode base URL (default env DFS_NAMENODE_URL or http://localhost:8080)")
    String namenodeUrlOpt;

    public static void main(String[] args) {
        int code = new CommandLine(new DfsCli()).execute(args);
        System.exit(code);
    }

    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return 0;
    }

    static String resolveNamenode(DfsCli root) {
        if (root != null && root.namenodeUrlOpt != null && !root.namenodeUrlOpt.isBlank()) return root.namenodeUrlOpt;
        String env = System.getenv("DFS_NAMENODE_URL");
        if (env != null && !env.isBlank()) return env;
        Map<String, Object> s = Session.load();
        Object u = s.get("namenodeUrl");
        if (u != null) return u.toString();
        return "http://localhost:8080";
    }

    static String requireToken() {
        Map<String, Object> s = Session.load();
        Object t = s.get("token");
        if (t == null) {
            System.err.println("Not logged in. Run 'dfs login -u USER'");
            System.exit(2);
        }
        return t.toString();
    }

    @Command(name = "register", description = "Register a new user")
    static class RegisterCmd implements Callable<Integer> {
        @CommandLine.ParentCommand DfsCli parent;
        @Option(names = {"-u", "--user"}, required = true) String user;
        @Option(names = {"-p", "--pass"}, required = true, interactive = true,
                arity = "0..1") String pass;
        @Override public Integer call() throws Exception {
            DfsClient.register(resolveNamenode(parent), user, pass);
            System.out.println("User '" + user + "' registered.");
            return 0;
        }
    }

    @Command(name = "login", description = "Login and store JWT")
    static class LoginCmd implements Callable<Integer> {
        @CommandLine.ParentCommand DfsCli parent;
        @Option(names = {"-u", "--user"}, required = true) String user;
        @Option(names = {"-p", "--pass"}, required = true, interactive = true,
                arity = "0..1") String pass;
        @Override public Integer call() throws Exception {
            String url = resolveNamenode(parent);
            AuthDtos.LoginResponse resp = DfsClient.login(url, user, pass);
            Map<String, Object> s = new HashMap<>();
            s.put("namenodeUrl", url);
            s.put("token", resp.token);
            s.put("username", resp.username);
            s.put("expiresAt", resp.expiresAt);
            Session.save(s);
            System.out.println("Logged in as " + resp.username);
            return 0;
        }
    }

    @Command(name = "logout", description = "Discard saved session")
    static class LogoutCmd implements Callable<Integer> {
        @Override public Integer call() throws Exception {
            Session.clear();
            System.out.println("Logged out.");
            return 0;
        }
    }

    @Command(name = "whoami", description = "Show current session info")
    static class WhoamiCmd implements Callable<Integer> {
        @Override public Integer call() {
            Map<String, Object> s = Session.load();
            if (s.isEmpty()) { System.out.println("(not logged in)"); return 0; }
            System.out.println("User:     " + s.get("username"));
            System.out.println("NameNode: " + s.get("namenodeUrl"));
            System.out.println("Expires:  " + s.get("expiresAt"));
            return 0;
        }
    }

    @Command(name = "put", description = "Upload a local file to the DFS")
    static class PutCmd implements Callable<Integer> {
        @CommandLine.ParentCommand DfsCli parent;
        @Parameters(index = "0", paramLabel = "LOCAL", description = "Local file path") Path local;
        @Parameters(index = "1", paramLabel = "REMOTE", description = "Remote DFS path") String remote;
        @Option(names = {"--block-size-mb"}, description = "Block size in MB (overrides default)")
        Integer blockSizeMb;
        @Override public Integer call() throws Exception {
            DfsClient c = new DfsClient(resolveNamenode(parent), requireToken());
            long bsBytes = (blockSizeMb == null ? 64L : blockSizeMb) * 1024L * 1024L;
            System.out.println("Uploading " + local + " -> " + remote);
            c.put(local, remote, bsBytes);
            System.out.println("Done.");
            return 0;
        }
    }

    @Command(name = "get", description = "Download a remote file from the DFS")
    static class GetCmd implements Callable<Integer> {
        @CommandLine.ParentCommand DfsCli parent;
        @Parameters(index = "0", paramLabel = "REMOTE") String remote;
        @Parameters(index = "1", paramLabel = "LOCAL") Path local;
        @Override public Integer call() throws Exception {
            DfsClient c = new DfsClient(resolveNamenode(parent), requireToken());
            System.out.println("Downloading " + remote + " -> " + local);
            c.get(remote, local);
            System.out.println("Done.");
            return 0;
        }
    }

    @Command(name = "ls", description = "List a directory")
    static class LsCmd implements Callable<Integer> {
        @CommandLine.ParentCommand DfsCli parent;
        @Parameters(index = "0", paramLabel = "PATH", arity = "0..1") String path;
        @Override public Integer call() throws Exception {
            DfsClient c = new DfsClient(resolveNamenode(parent), requireToken());
            String p = path == null ? "/" : path;
            FileDtos.ListDirResponse r = c.ls(p);
            System.out.println("Listing " + r.path + ":");
            if (r.entries.isEmpty()) { System.out.println("  (empty)"); return 0; }
            for (FileDtos.DirEntry e : r.entries) {
                String sz = "FILE".equals(e.type) ? String.valueOf(e.sizeBytes) : "-";
                System.out.printf("  %-4s  %12s  %s%n", e.type, sz, e.name);
            }
            return 0;
        }
    }

    @Command(name = "rm", description = "Remove a file")
    static class RmCmd implements Callable<Integer> {
        @CommandLine.ParentCommand DfsCli parent;
        @Parameters(index = "0", paramLabel = "PATH") String path;
        @Override public Integer call() throws Exception {
            DfsClient c = new DfsClient(resolveNamenode(parent), requireToken());
            c.rm(path);
            System.out.println("Removed " + path);
            return 0;
        }
    }

    @Command(name = "mkdir", description = "Create a directory")
    static class MkdirCmd implements Callable<Integer> {
        @CommandLine.ParentCommand DfsCli parent;
        @Parameters(index = "0", paramLabel = "PATH") String path;
        @Override public Integer call() throws Exception {
            DfsClient c = new DfsClient(resolveNamenode(parent), requireToken());
            c.mkdir(path);
            System.out.println("Created " + path);
            return 0;
        }
    }

    @Command(name = "rmdir", description = "Remove an empty directory")
    static class RmdirCmd implements Callable<Integer> {
        @CommandLine.ParentCommand DfsCli parent;
        @Parameters(index = "0", paramLabel = "PATH") String path;
        @Override public Integer call() throws Exception {
            DfsClient c = new DfsClient(resolveNamenode(parent), requireToken());
            c.rmdir(path);
            System.out.println("Removed " + path);
            return 0;
        }
    }

    @Command(name = "stat", description = "Show file metadata and replica locations")
    static class StatCmd implements Callable<Integer> {
        @CommandLine.ParentCommand DfsCli parent;
        @Parameters(index = "0", paramLabel = "PATH") String path;
        @Override public Integer call() throws Exception {
            DfsClient c = new DfsClient(resolveNamenode(parent), requireToken());
            FileDtos.FileMetadataResponse m = c.stat(path);
            System.out.println("path:       " + m.path);
            System.out.println("owner:      " + m.owner);
            System.out.println("size:       " + m.sizeBytes + " bytes");
            System.out.println("blockSize:  " + m.blockSize + " bytes");
            System.out.println("status:     " + m.status);
            System.out.println("blocks:     " + m.blocks.size());
            for (FileDtos.BlockLocation bl : m.blocks) {
                System.out.printf("  [%d] %s (%d B, sha=%s)%n",
                        bl.sequenceIndex, bl.blockId, bl.sizeBytes,
                        bl.hashSha256 == null ? "?" : bl.hashSha256.substring(0, 12) + "...");
                for (FileDtos.DataNodeRef dn : bl.replicas) {
                    System.out.printf("       -> %s @ %s:%d%n", dn.id, dn.host, dn.port);
                }
            }
            return 0;
        }
    }
}
