package com.htac_backend.shared.api;

import java.time.Instant;
import java.util.Map;

public record ApiError(String code, String message, Instant timestamp, String path,
                       Map<String, String> fieldErrors) {
}
