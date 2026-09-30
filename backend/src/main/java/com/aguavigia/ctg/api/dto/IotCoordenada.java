package com.aguavigia.ctg.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

public record IotCoordenada(
    @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
    @DecimalMin("-180.0") @DecimalMax("180.0") Double lon
) {}
