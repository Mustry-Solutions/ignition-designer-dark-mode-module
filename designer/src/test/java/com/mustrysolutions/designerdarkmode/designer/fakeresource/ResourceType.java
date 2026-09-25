package com.mustrysolutions.designerdarkmode.designer.fakeresource;

/** Stands in for either line's ResourceType: same package as ResourcePath, (module, type) constructor. */
public final class ResourceType {
    final String module;
    final String type;

    public ResourceType(String module, String type) {
        this.module = module;
        this.type = type;
    }
}
