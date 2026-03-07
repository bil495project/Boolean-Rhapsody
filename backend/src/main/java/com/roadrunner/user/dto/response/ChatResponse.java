package com.roadrunner.user.dto.response;

import java.util.List;

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
public class ChatResponse {
    private String id;
    private String title;
    private String duration;
    private long createdAt;
    private long updatedAt;
    private List<MessageResponse> messages;
}
