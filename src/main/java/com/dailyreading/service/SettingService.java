package com.dailyreading.service;

import com.dailyreading.config.DeepSeekProperties;
import com.dailyreading.mapper.SettingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * API Key 的优先级：用户自己在「设置」里填的 > application-local.yml 里的。
 * 这样别人拿到这个项目，不用改配置文件也能用自己的 Key。
 */
@Service
@RequiredArgsConstructor
public class SettingService {

    public static final String KEY_DEEPSEEK = "deepseek.apiKey";

    private final SettingMapper settingMapper;
    private final DeepSeekProperties props;

    /** 用户在设置页填的 Key，没填返回 null */
    public String userDeepSeekKey(Long userId) {
        if (userId == null) return null;
        String v = settingMapper.get(userId, KEY_DEEPSEEK);
        return v == null || v.isBlank() ? null : v.trim();
    }

    /** 实际可用的 Key：用户自己的优先，其次配置文件里的 */
    public String effectiveDeepSeekKey(Long userId) {
        String own = userDeepSeekKey(userId);
        return own != null ? own : props.getApiKey();
    }

    public boolean deepSeekConfigured(Long userId) {
        String key = effectiveDeepSeekKey(userId);
        return key != null && key.startsWith("sk-") && key.length() > 20;
    }

    public void saveDeepSeekKey(Long userId, String key) {
        String v = key == null ? "" : key.trim();
        if (v.isEmpty()) settingMapper.remove(userId, KEY_DEEPSEEK);
        else settingMapper.put(userId, KEY_DEEPSEEK, v);
    }

    /** 给前端看的掩码，比如 sk-4d53****fe4 */
    public String maskedDeepSeekKey(Long userId) {
        String key = effectiveDeepSeekKey(userId);
        if (key == null || key.isBlank()) return "";
        if (key.length() <= 12) return "****";
        return key.substring(0, 7) + "****" + key.substring(key.length() - 4);
    }
}
