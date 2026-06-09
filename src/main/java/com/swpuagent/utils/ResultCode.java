package com.swpuagent.utils;

public enum ResultCode {
    // 1. 枚举常量必须放在最前面，用逗号隔开，最后一个用分号结束
    SUCCESS(200, "操作成功"),
    FAIL(400, "操作失败"),
    VALIDATE_FAILED(400, "参数校验失败"),
    ERROR(500, "系统错误");

    private Integer code;
    private String message;
    // 2. 构造方法 (必须是私有的，虽然不用写 private 默认也是)
    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
    // 3. Getter 方法 (Result 类需要通过这些方法拿数字和文字)
    public Integer getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
