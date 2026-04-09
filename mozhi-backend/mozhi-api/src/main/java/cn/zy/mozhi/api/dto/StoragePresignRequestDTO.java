package cn.zy.mozhi.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record StoragePresignRequestDTO(
        @NotBlank(message = "purpose must not be blank")
        String purpose,
        @NotNull(message = "draftId must not be null")
        @Positive(message = "draftId must be greater than 0")
        Long draftId,
        @NotBlank(message = "fileName must not be blank")
        String fileName,
        @NotBlank(message = "contentType must not be blank")
        String contentType,
        @NotBlank(message = "mediaType must not be blank")
        String mediaType,
        @NotNull(message = "declaredSizeBytes must not be null")
        @Positive(message = "declaredSizeBytes must be greater than 0")
        Long declaredSizeBytes
) {
}
