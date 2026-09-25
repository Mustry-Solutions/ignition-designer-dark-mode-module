package com.mustrysolutions.designerdarkmode.designer.fakeresource;

/** Stands in for either line's ResourcePath: a (ResourceType, String) constructor. */
public final class ResourcePath {
    final ResourceType type;
    final String path;

    public ResourcePath(ResourceType type, String path) {
        this.type = type;
        this.path = path;
    }

    String key() {
        return type.module + "/" + type.type + "/" + path;
    }
}
