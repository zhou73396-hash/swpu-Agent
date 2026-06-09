package com.swpuagent.utils;


import lombok.Data;

@Data

public class Result<T> {

    private Integer code;

    private String message;


    private T data; // 核心：将 Object 改为 T

    // 全参构造函数
    public Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // 无数据构造函数
    public Result(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    // 静态方法：成功返回 (带数据)
    // 关键点：<T> 表示这是一个泛型静态方法
    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    // 静态方法：成功返回 (带数据和自定义消息)
    public static <T> Result<T> success(T data, String message) {
        return new Result<>(ResultCode.SUCCESS.getCode(), message, data);
    }

    // 静态方法：成功返回 (无数据)
    public static <T> Result<T> success() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage());
    }

    // 静态方法：系统错误（默认500）
    public static <T> Result<T> error(String message) {
        return new Result<>(ResultCode.ERROR.getCode(), message);
    }

    // 静态方法：自定义错误码和消息
    public static <T> Result<T> error(Integer code, String message) {
        return new Result<>(code, message);
    }

    // 静态方法：通过 ResultCode 枚举返回错误
    public static <T> Result<T> error(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage());
    }

    // 静态方法：校验失败 (400)
    public static <T> Result<T> validateFailed(String message) {
        return new Result<>(ResultCode.VALIDATE_FAILED.getCode(), message);
    }
}