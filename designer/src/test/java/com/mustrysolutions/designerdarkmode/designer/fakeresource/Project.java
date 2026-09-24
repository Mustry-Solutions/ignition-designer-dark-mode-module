package com.mustrysolutions.designerdarkmode.designer.fakeresource;

import java.util.Optional;

/**
 * The shape both lines share: a public interface with
 * {@code Optional<?> getResource(ResourcePath)} beside an overload that takes
 * something else, as 8.1's {@code Project} has one for a resource id.
 */
public interface Project {

    Optional<Object> getResource(ResourcePath path);

    Optional<Object> getResource(String resourceId);

    /** A project holding one resource, by module/type/path; the class is not public. */
    static Project holding(String key) {
        return new Holding(key);
    }
}

final class Holding implements Project {
    private final String key;

    Holding(String key) {
        this.key = key;
    }

    @Override
    public Optional<Object> getResource(ResourcePath path) {
        return key.equals(path.key()) ? Optional.of(new Object()) : Optional.empty();
    }

    @Override
    public Optional<Object> getResource(String resourceId) {
        throw new AssertionError("the id overload must never be picked");
    }
}
