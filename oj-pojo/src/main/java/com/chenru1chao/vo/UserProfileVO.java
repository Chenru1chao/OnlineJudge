package com.chenru1chao.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserProfileVO {
    private Integer id;
    private String avatar;
    private String username;
    private String mood;
    private String school;
    private String major;
    private String github;
    private LocalDateTime createTime;
    private Integer totalAccept;
    private Integer totalSubmit;
}
