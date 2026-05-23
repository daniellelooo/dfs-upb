package com.upb.dfs.datanode.web;

import com.upb.dfs.datanode.config.DataNodeProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    private final DataNodeProperties props;

    public HealthController(DataNodeProperties props) {
        this.props = props;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "service", "datanode", "id", props.getDatanodeId());
    }
}
