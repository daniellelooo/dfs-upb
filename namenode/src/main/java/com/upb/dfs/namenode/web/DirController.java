package com.upb.dfs.namenode.web;

import com.upb.dfs.common.dto.FileDtos;
import com.upb.dfs.namenode.security.AuthContext;
import com.upb.dfs.namenode.service.NamespaceService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/dirs")
public class DirController {

    private final NamespaceService namespace;

    public DirController(NamespaceService namespace) {
        this.namespace = namespace;
    }

    @GetMapping
    public FileDtos.ListDirResponse list(@RequestParam(value = "path", defaultValue = "/") String path) {
        return namespace.list(AuthContext.get().userId, path);
    }

    @PostMapping
    public Map<String, Object> mkdir(@RequestBody FileDtos.CreateDirRequest req) {
        namespace.mkdir(AuthContext.get().userId, req.path);
        return Map.of("status", "ok");
    }

    @DeleteMapping
    public Map<String, Object> rmdir(@RequestParam("path") String path) {
        namespace.rmdir(AuthContext.get().userId, path);
        return Map.of("status", "ok");
    }
}
