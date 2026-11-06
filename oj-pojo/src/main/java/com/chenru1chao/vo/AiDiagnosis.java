package com.chenru1chao.vo;

import lombok.Data;

import java.util.List;

@Data
public class AiDiagnosis {

    private String summary;
    private String rootCause;
    private List<String> suggestions;
    private Boolean fallback;
}
