package cn.zy.mozhi.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record DraftMediaConfirmRequestDTO(
        @NotBlank(message = "objectKey must not be blank")
        String objectKey,
        @NotBlank(message = "uploadTicket must not be blank")
        String uploadTicket,
        @PositiveOrZero(message = "sortOrder must not be negative")
        Integer sortOrder
) {
}
