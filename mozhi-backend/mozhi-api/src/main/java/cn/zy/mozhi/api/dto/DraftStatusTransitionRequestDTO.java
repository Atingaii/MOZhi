package cn.zy.mozhi.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record DraftStatusTransitionRequestDTO(
        @NotBlank(message = "targetStatus must not be blank")
        String targetStatus,
        @NotNull(message = "expectedVersion must not be null")
        @PositiveOrZero(message = "expectedVersion must not be negative")
        Long expectedVersion
) {
}
