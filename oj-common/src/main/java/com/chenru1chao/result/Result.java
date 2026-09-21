package com.chenru1chao.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Result <T> {

    private Integer code;
    private String errorMsg;
    private T data;

    public static <T> Result<T> success() {
        Result<T> result = new Result<>();
        result.setCode(1);
        return result;
    }

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(1);
        result.setData(data);
        return result;
    }

    public static <T> Result<T> error(String errorMsg) {
        Result<T> result = new Result<>();
        result.setCode(0);
        result.setErrorMsg(errorMsg);
        return result;
    }
}
