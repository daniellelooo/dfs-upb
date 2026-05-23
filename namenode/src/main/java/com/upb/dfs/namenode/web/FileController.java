package com.upb.dfs.namenode.web;

import com.upb.dfs.common.dto.FileDtos;
import com.upb.dfs.namenode.security.AuthContext;
import com.upb.dfs.namenode.service.FileService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping
    public FileDtos.CreateFileResponse create(@RequestBody FileDtos.CreateFileRequest req) {
        return fileService.createFile(AuthContext.get().userId, req);
    }

    @PostMapping("/commit")
    public Map<String, Object> commit(@RequestParam("path") String path,
                                      @RequestBody FileDtos.CommitFileRequest req) {
        fileService.commitFile(AuthContext.get().userId, path, req);
        return Map.of("status", "ok");
    }

    @GetMapping("/meta")
    public FileDtos.FileMetadataResponse get(@RequestParam("path") String path) {
        return fileService.getFile(AuthContext.get().userId, path);
    }

    @DeleteMapping
    public Map<String, Object> delete(@RequestParam("path") String path) {
        fileService.deleteFile(AuthContext.get().userId, path);
        return Map.of("status", "ok");
    }
}
