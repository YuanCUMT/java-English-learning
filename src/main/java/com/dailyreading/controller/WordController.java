package com.dailyreading.controller;

import com.dailyreading.common.ApiResponse;
import com.dailyreading.entity.Word;
import com.dailyreading.service.OcrCacheService;
import com.dailyreading.service.WordService;
import com.dailyreading.service.ocr.OcrResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/words")
@RequiredArgsConstructor
public class WordController {

    private final WordService wordService;
    private final OcrResolver ocrResolver;
    private final OcrCacheService ocrCacheService;

    @GetMapping
    public ApiResponse list(@RequestParam(required = false)
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                            @RequestAttribute("userId") Long userId) {
        LocalDate d = date == null ? LocalDate.now() : date;
        List<Word> words = wordService.listByDate(userId, d);
        return ApiResponse.ok(ApiResponse.map(
                "date", d.toString(),
                "count", words.size(),
                "words", words.stream().map(Word::getWord).toList(),
                "engine", ocrResolver.engine()
        ));
    }

    /** 单词本「每日单词」：按天返回全部词 + 词典释义 */
    @GetMapping("/history")
    public ApiResponse history(@RequestAttribute("userId") Long userId) {
        return ApiResponse.ok(ApiResponse.map("days", wordService.history(userId)));
    }

    /** 给内置词典查不到的词补释义（一次最多 40 个，结果写进 lookup_cache） */
    @PostMapping("/fill-meanings")
    public ApiResponse fillMeanings(@RequestBody(required = false) Map<String, Object> body,
                                    @RequestAttribute("userId") Long userId) {
        List<String> words = new ArrayList<>();
        if (body != null && body.get("words") instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) words.add(String.valueOf(o));
            }
        }
        try {
            return ApiResponse.ok(wordService.fillMeanings(userId, words));
        } catch (Exception e) {
            return ApiResponse.fail(500, "补释义失败：" + e.getMessage());
        }
    }

    /** 粘贴导入：把一段文本里的英文词全抽出来 */
    @PostMapping
    public ApiResponse importByText(@RequestBody Map<String, String> body,
                                    @RequestAttribute("userId") Long userId) {
        String text = body.get("text");
        String dateStr = body.get("date");
        LocalDate d = dateStr == null || dateStr.isBlank() ? LocalDate.now() : LocalDate.parse(dateStr);

        List<String> words = WordService.extract(text);
        if (words.isEmpty()) {
            return ApiResponse.fail(400, "没从这段文字里找到英文单词");
        }
        return ApiResponse.ok(wordService.importWords(userId, d, words));
    }

    /** 截图导入：图片 -> OCR -> 单词 */
    @PostMapping("/ocr")
    public ApiResponse importByImage(@RequestParam("file") MultipartFile file,
                                     @RequestParam(required = false) String date,
                                     @RequestParam(required = false, defaultValue = "true") boolean save,
                                     @RequestAttribute("userId") Long userId) throws IOException {
        if (file == null || file.isEmpty()) {
            return ApiResponse.fail(400, "没有收到图片");
        }
        LocalDate d = date == null || date.isBlank() ? LocalDate.now() : LocalDate.parse(date);
        byte[] bytes = file.getBytes();
        // 同一张图（内容 sha1 相同）只识别一次；命中缓存就不碰外部服务
        String sha1 = OcrCacheService.sha1(bytes);
        // 显式配成 mock 时不读缓存：缓存里可能是以前真引擎认出来的词，读出来就分不清真假了
        OcrCacheService.Cached hit = ocrResolver.explicitlyMock() ? null : ocrCacheService.get(sha1);
        boolean fromCache = hit != null;
        OcrResolver.Result result;
        if (hit != null) {
            result = new OcrResolver.Result(hit.words(), hit.engine(), false);
        } else {
            try {
                result = ocrResolver.recognize(bytes);
            } catch (Exception e) {
                return ApiResponse.fail(500, "识别失败：" + e.getMessage());
            }
            // mock 是假数据，缓存下来会害人：以后配了真引擎，同一张图还会拿到假词
            if (!result.engine().toLowerCase().startsWith("mock")) {
                ocrCacheService.put(sha1, result.words(), result.engine());
            }
        }
        List<String> words = result.words();
        if (words == null || words.isEmpty()) {
            return ApiResponse.fail(400, "没从图片里识别出英文单词");
        }
        // engine 用「这一次实际干活的引擎」，不是链首那个，降级时页面才不会骗人
        // save=false：只把识别结果当「候选词」返回，由前端确认/改错/删掉后再入库
        if (!save) {
            Map<String, Object> preview = new LinkedHashMap<>();
            preview.put("words", words);
            preview.put("total", words.size());
            preview.put("engine", result.engine());
            preview.put("degraded", result.degraded());
            preview.put("cached", fromCache);
            return ApiResponse.ok(preview);
        }
        Map<String, Object> data = wordService.importWords(userId, d, words);
        data.put("engine", result.engine());
        data.put("degraded", result.degraded());
        data.put("cached", fromCache);
        return ApiResponse.ok(data);
    }

    @DeleteMapping
    public ApiResponse clear(@RequestParam(required = false)
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                             @RequestAttribute("userId") Long userId) {
        LocalDate d = date == null ? LocalDate.now() : date;
        int n = wordService.clear(userId, d);
        return ApiResponse.ok(ApiResponse.map("deleted", n, "date", d.toString()));
    }
}
