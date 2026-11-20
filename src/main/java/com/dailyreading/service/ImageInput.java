package com.dailyreading.service;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
public final class ImageInput {
    private ImageInput() {}
    public static void validate(byte[] bytes) {
        if(bytes==null || bytes.length==0 || bytes.length>10*1024*1024) throw new IllegalArgumentException("图片必须在 10 MB 以内");
        try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext()) throw new IllegalArgumentException("无法识别图片，请使用 PNG 或 JPEG");
            var reader=readers.next();
            try {
                String format=reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
                if(!java.util.Set.of("png","jpeg","jpg").contains(format)) throw new IllegalArgumentException("只支持 PNG 或 JPEG 图片");
                reader.setInput(input);
                int w=reader.getWidth(0), h=reader.getHeight(0);
                if(w<16 || h<16 || w>12000 || h>12000 || (long)w*h>20_000_000) throw new IllegalArgumentException("图片尺寸应为 16–12000 像素，总像素不超过 2000 万");
            } finally { reader.dispose(); }
        } catch(IllegalArgumentException e) { throw e; }
        catch(Exception e) { throw new IllegalArgumentException("图片损坏或格式不受支持"); }
    }
}
