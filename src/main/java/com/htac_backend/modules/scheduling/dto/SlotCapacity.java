package com.htac_backend.modules.scheduling.dto;

public record SlotCapacity(Long slotId, int capacity, int occupied, int available) { }
