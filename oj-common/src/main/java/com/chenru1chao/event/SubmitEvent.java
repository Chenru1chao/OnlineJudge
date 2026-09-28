package com.chenru1chao.event;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SubmitEvent {
    private Integer userId;
    private Integer problemId;
}
