package com.htac_backend.shared.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiContractTest {
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new PublicPingController(), new SampleController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void publicPingReturnsSuccess() throws Exception {
        mvc.perform(get("/api/v1/public/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void validationAndBusinessConflictHaveStableErrorShape() throws Exception {
        mvc.perform(post("/sample").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.reason").exists());
        mvc.perform(get("/sample/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_FULL"))
                .andExpect(jsonPath("$.path").value("/sample/conflict"));
    }

    @RestController
    static class SampleController {
        @PostMapping("/sample")
        String validate(@Valid @RequestBody SampleRequest request) { return request.reason(); }

        @GetMapping("/sample/conflict")
        String conflict() { throw new BusinessException("SLOT_FULL", "Khung khám đã hết chỗ"); }
    }

    record SampleRequest(@NotBlank String reason) { }
}
