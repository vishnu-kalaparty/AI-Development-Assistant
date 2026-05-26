package com.aidevassistant.dto;

import java.util.ArrayList;
import java.util.List;

public class SandboxNode {
    private String name;
    private String path;
    private String type;
    private List<SandboxNode> children;

    public SandboxNode() {
    }

    public SandboxNode(String name, String path, String type) {
        this.name = name;
        this.path = path;
        this.type = type;
        if ("directory".equals(type)) {
            this.children = new ArrayList<>();
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<SandboxNode> getChildren() {
        return children;
    }

    public void setChildren(List<SandboxNode> children) {
        this.children = children;
    }
}
