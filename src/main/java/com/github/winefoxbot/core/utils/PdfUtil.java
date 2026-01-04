package com.github.winefoxbot.core.utils;

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Image;
import com.itextpdf.text.pdf.*;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@Slf4j
public final class PdfUtil {
    private PdfUtil() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * 将多个图片包装成一个PDF。采用全内存流处理，彻底避免磁盘临时文件和文件句柄问题。
     *
     * @param images    图片文件列表
     * @param outputDir 输出目录
     * @return 成功则返回PDF文件路径，失败返回null
     */
    public static Path wrapImagesIntoPdf(List<File> images, String outputDir) {
        Path finalPdfPath = null;
        try {
            // 1. 确定最终输出路径
            Path tempDir = Paths.get(outputDir);
            Files.createDirectories(tempDir);
            String randomFileName = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            finalPdfPath = tempDir.resolve(randomFileName + ".pdf");

            // 2. 步骤一：将图片生成为内存中的PDF字节流
            byte[] pdfBytes = generatePdfBytesFromImages(images);

            // 3. 步骤二：从内存字节流中读取PDF，修改元数据后，直接写入最终文件
            modifyPdfAndCreateFinal(pdfBytes, finalPdfPath);

            return finalPdfPath;

        } catch (Exception e) {
            log.error("在内存中处理PDF时发生严重错误", e);
            // 如果出错，清理掉可能已经创建的最终文件
            deleteIfExists(finalPdfPath);
            return null;
        }
        // finally 块不再需要，因为没有临时文件需要清理
    }

    /**
     * 从图片列表生成一个PDF，并将其作为字节数组返回。
     */
    private static byte[] generatePdfBytesFromImages(List<File> images) throws DocumentException, IOException {
        // 使用 try-with-resources 确保所有流都被正确关闭
        Document document = new Document();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            PdfWriter.getInstance(document, baos);
            document.open();

            float printableWidth = document.getPageSize().getWidth() - document.leftMargin() - document.rightMargin();
            for (File imageFile : images) {
                Image img = Image.getInstance(imageFile.toURI().toURL());
                img.scaleToFit(printableWidth, Float.MAX_VALUE);
                document.newPage();
                document.add(img);
            }

            // Document.close() 会将所有内容刷入 ByteArrayOutputStream
            document.close();
            return baos.toByteArray();
        } finally {
            document.close();
        }
    }

    /**
     * 读取内存中的PDF字节数组，修改元数据后，写入到一个新的目标PDF文件。
     */
    private static void modifyPdfAndCreateFinal(byte[] sourcePdfBytes, Path targetPdfPath) throws IOException, DocumentException {
        // 使用 try-with-resources 确保所有流和reader/stamper都被正确关闭
        try (FileOutputStream fos = new FileOutputStream(targetPdfPath.toFile())) {
            PdfReader reader = new PdfReader(sourcePdfBytes);
            PdfStamper stamper = new PdfStamper(reader, fos);
            try {
                stamper.createXmpMetadata();

                HashMap<String, String> info = reader.getInfo();
                info.put("ModificationGuid", UUID.randomUUID().toString());
                info.put("ModDate", new PdfDate().getW3CDate());
                stamper.setMoreInfo(info);
            } finally {
                stamper.close();
            }
            reader.close(); // PdfReader 需要手动关闭
        }
    }

    // ================= 以下是辅助方法 =================

    public static Path wrapImageIntoPdf(List<Path> imagePaths, String outputDir) {
        return wrapImagesIntoPdf(
                imagePaths.stream().map(Path::toFile).toList(),
                outputDir
        );
    }

    private static void deleteIfExists(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("删除文件失败: {}", path, e);
        }
    }
}
