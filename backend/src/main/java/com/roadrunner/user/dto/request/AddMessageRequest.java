package com.roadrunner.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddMessageRequest {

    @NotBlank
    private String role;

    @NotBlank
    private String content;

    private String toolUsed;
    private String toolParams;
}
