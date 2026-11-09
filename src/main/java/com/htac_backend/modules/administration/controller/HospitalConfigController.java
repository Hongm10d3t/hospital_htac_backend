package com.htac_backend.modules.administration.controller;

import com.htac_backend.modules.administration.dto.HospitalConfigResponse;
import com.htac_backend.modules.administration.dto.HospitalConfigWriteResult;
import com.htac_backend.modules.administration.dto.UpsertHospitalConfigRequest;
import com.htac_backend.modules.administration.service.HospitalConfigService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HospitalConfigController {
    private final HospitalConfigService configs;

    public HospitalConfigController(HospitalConfigService configs) {
        this.configs = configs;
    }

    @GetMapping("/api/v1/public/hospital")
    public ResponseEntity<HospitalConfigResponse> publicInfo() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(configs.publicInfo());
    }

    @PutMapping("/api/v1/admin/hospital-config")
    public ResponseEntity<HospitalConfigResponse> upsert(@Valid @RequestBody UpsertHospitalConfigRequest body) {
        HospitalConfigWriteResult result = configs.upsert(body);
        if (result.created()) {
            return ResponseEntity.created(URI.create("/api/v1/public/hospital"))
                    .cacheControl(CacheControl.noStore()).body(result.config());
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result.config());
    }
}
