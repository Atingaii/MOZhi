package cn.zy.mozhi.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record DraftUpdateRequestDTO(
        @NotBlank(message = "title must not be blank")
        String title,
        @NotBlank(message = "content must not be blank")
        String content,
        @NotNull(message = "expectedVersion must not be null")
        @PositiveOrZero(message = "expectedVersion must not be negative")
        Long expectedVersion
) {
}
