package com.dailyreading.controller;

import com.dailyreading.common.ApiResponse;
import com.dailyreading.entity.Word;
import com.dailyreading.service.WordService;
import com.dailyreading.service.ocr.OcrService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/words")
@RequiredArgsConstructor
public class WordController {

    private final WordService wordService;
    private final OcrService ocrService;

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
                "engine", ocrService.engine()
        ));
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
                                     @RequestAttribute("userId") Long userId) throws IOException {
        if (file == null || file.isEmpty()) {
            return ApiResponse.fail(400, "没有收到图片");
        }
        LocalDate d = date == null || date.isBlank() ? LocalDate.now() : LocalDate.parse(date);
        List<String> words;
        try {
            words = ocrService.recognize(file.getBytes());
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ApiResponse.fail(500, e.getMessage());
        }
        if (words.isEmpty()) {
            return ApiResponse.fail(400, "没从图片里识别出英文单词");
        }
        Map<String, Object> data = wordService.importWords(userId, d, words);
        data.put("engine", ocrService.engine());
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
