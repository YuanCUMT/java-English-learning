package com.dailyreading.common;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一返回体：{code, message, data}。code=0 表示成功。
 */
@Data
public class ApiResponse {

    private int code;
    private String message;
    private Object data;

    public static ApiResponse ok(Object data) {
        ApiResponse r = new ApiResponse();
        r.code = 0;
        r.message = "ok";
        r.data = data;
        return r;
    }

    public static ApiResponse ok() {
        return ok(null);
    }

    public static ApiResponse fail(int code, String message) {
        ApiResponse r = new ApiResponse();
        r.code = code;
        r.message = message;
        r.data = null;
        return r;
    }

    public static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}
