package com.htac_backend.security;

/** Implemented by the principal installed by M01 after authentication. */
public interface AuthenticatedActor {
    Long userId();
}
