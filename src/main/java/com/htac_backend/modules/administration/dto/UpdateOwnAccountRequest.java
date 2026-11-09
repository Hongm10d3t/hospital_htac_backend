package com.htac_backend.modules.administration.dto;

import jakarta.validation.constraints.Size;

public record UpdateOwnAccountRequest(@Size(min = 1, max = 150) String fullName,
                                      @Size(min = 1, max = 20) String phone) {
}
