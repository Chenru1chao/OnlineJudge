package com.chenru1chao.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserVO {
    private Integer id;

    private String avatar;

    private String username;

    private String email;

    private Integer age;

    private String gender;

    private String mood;

    private Integer totalSubmit;

    private Integer totalAccept;
}
