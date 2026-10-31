package com.dailyreading.service.ocr;

import com.dailyreading.config.OcrProperties;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.Word;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 离线 OCR：Tesseract（tess4j）。不需要任何账号，只要 tessdata/eng.traineddata。
 *
 * <p>关键不是「把字都认出来」，而是「只留单词卡上的词」：百词斩 / 不背单词的卡片上，
 * 目标词的字号最大，音标、释义、例句、水印都小一号。所以按识别出的包围盒高度筛一遍，
 * 只留接近最大字号的那些词，杂字（界面文字、水印、例句里的词）自然就被滤掉了。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.ocr.provider", havingValue = "tesseract")
public class TesseractOcrService implements OcrService {

    /** 只认纯英文词，中文和标点会被 eng 模型认成乱码，直接丢掉 */
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z][A-Za-z'’-]{1,40}");

    /** 低于最大字号的这个比例就当小字处理（Node 版实测 0.72 偏高，0.62 更稳） */
    private static final double SIZE_RATIO = 0.62;

    private static final int MAX_WORDS = 40;

    private final OcrProperties props;

    @Override
    public List<String> recognize(byte[] image) {
        if (image == null || image.length == 0) {
            throw new IllegalArgumentException("图片是空的");
        }
        BufferedImage img;
        try {
            img = ImageIO.read(new ByteArrayInputStream(image));
        } catch (Exception e) {
            throw new IllegalArgumentException("这张图读不出来，换一张试试", e);
        }
        if (img == null) {
            throw new IllegalArgumentException("这张图读不出来，换一张试试");
        }

        List<Word> words;
        Tesseract t = new Tesseract();
        t.setDatapath(props.getTessdata());
        t.setLanguage(props.getLang());
        try {
            words = t.getWords(img, ITessAPI.TessPageIteratorLevel.RIL_WORD);
        } catch (Exception e) {
            log.warn("tesseract 识别失败", e);
            throw new IllegalStateException("离线识别失败：" + e.getMessage(), e);
        }

        List<String> picked = pickBySize(words);
        if (picked.size() >= 3) return picked;

        // 兜底：整张图的字差不多大（或大字没被认出来），就退回「按行挑短行」的老办法
        try {
            List<String> byLine = extract(t.doOCR(img));
            if (byLine.size() > picked.size()) return byLine;
        } catch (Exception e) {
            log.warn("tesseract 二次识别失败", e);
        }
        return picked;
    }

    /** 按行挑：一行里只有 1–3 个词的，多半是单词卡上的大字 */
    List<String> extract(String text) {
        if (text == null || text.isBlank()) return List.of();

        List<List<String>> lines = new ArrayList<>();
        for (String raw : text.split("\\R")) {
            List<String> tokens = new ArrayList<>();
            var m = TOKEN.matcher(raw);
            while (m.find()) {
                tokens.add(m.group().toLowerCase(Locale.ROOT).replace('’', '\''));
            }
            if (!tokens.isEmpty()) lines.add(tokens);
        }

        Set<String> out = new LinkedHashSet<>();
        for (List<String> line : lines) {
            if (line.size() <= 3) out.addAll(line);
            if (out.size() >= 24) break;
        }
        for (List<String> line : lines) {
            if (out.size() >= MAX_WORDS) break;
            if (line.size() > 3) out.addAll(line);
        }
        return new ArrayList<>(out);
    }

    /** 按字号筛词：只留接近最大高度的那批，再按阅读顺序排 */
    List<String> pickBySize(List<Word> words) {
        if (words == null || words.isEmpty()) return List.of();

        record Cand(String text, int height, int top, int left) {}

        List<Cand> cands = new ArrayList<>();
        for (Word w : words) {
            String text = w.getText() == null ? "" : w.getText().trim();
            if (!TOKEN.matcher(text).matches()) continue;
            if (w.getBoundingBox() == null) continue;
            cands.add(new Cand(
                    text.toLowerCase(Locale.ROOT).replace('’', '\''),
                    w.getBoundingBox().height,
                    w.getBoundingBox().y,
                    w.getBoundingBox().x));
        }
        if (cands.isEmpty()) return List.of();

        int maxHeight = cands.stream().mapToInt(Cand::height).max().orElse(0);
        double floor = maxHeight * SIZE_RATIO;

        cands.sort(Comparator.comparingInt(Cand::top).thenComparingInt(Cand::left));

        Set<String> out = new LinkedHashSet<>();
        for (Cand c : cands) {
            if (c.height() < floor) continue;
            out.add(c.text());
            if (out.size() >= MAX_WORDS) break;
        }
        // 极端情况：全是小字（比如整张图没有特别大的字），至少别返回空
        if (out.isEmpty()) {
            for (Cand c : cands) {
                out.add(c.text());
                if (out.size() >= MAX_WORDS) break;
            }
        }
        return new ArrayList<>(out);
    }

    @Override
    public String engine() {
        return "tesseract（离线，不用账号）";
    }
}
