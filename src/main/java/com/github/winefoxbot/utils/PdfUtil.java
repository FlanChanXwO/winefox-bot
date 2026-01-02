package com.github.winefoxbot.utils;

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Image;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-21-23:33
 */
public final class PdfUtil {
    private PdfUtil() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static String wrapByteImagesIntoPdf(List<byte[]> images, String outputPath) {
        // 1. 确定输出文件路径
        Path outputFilePath = null;
        try {
            Path outputDir = Paths.get(outputPath);
            Files.createDirectories(outputDir);
            String randomFileName = UUID.randomUUID().toString().replace("-", "").substring(0, 8) + ".pdf";
            outputFilePath = outputDir.resolve(randomFileName);
        } catch (IOException e) {
            e.printStackTrace();
            return null; // 如果路径创建失败，直接返回
        }

        // 2. 第一阶段：生成 PDF 文件
        // 使用 try-with-resources 管理 Document 和 PdfWriter
        Document document = new Document();
        try {
            // 使用 try-finally 确保资源关闭
            PdfWriter writer = null;
            try {
                writer = PdfWriter.getInstance(document, new FileOutputStream(outputFilePath.toFile()));
                writer.createXmpMetadata();
                document.open();

                float printableWidth = document.getPageSize().getWidth() - document.leftMargin() - document.rightMargin();

                // 在循环外部处理文档，在内部处理图片
                for (byte[] image : images) {
                    Image img = Image.getInstance(image);
                    PdfPTable table = new PdfPTable(1);
                    table.setWidthPercentage(100);
                    img.scaleToFit(printableWidth, Float.MAX_VALUE);
                    PdfPCell cell = new PdfPCell(img, true);
                    cell.setBorder(Rectangle.NO_BORDER);
                    cell.setPadding(0);
                    table.addCell(cell);
                    table.setSpacingAfter(10f);
                    document.add(table);
                }
            } finally {
                // 关键：在所有内容添加完毕后，关闭 document。
                // 这会确保 writer 和文件流也被关闭，文件锁被完全释放。
                if (document.isOpen()) {
                    document.close();
                }
                // writer 会被 document.close() 自动关闭，但以防万一可以加上
                if (writer != null && !writer.isCloseStream()) {
                    writer.close();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            // 如果生成失败，删除可能已创建的不完整文件
            try {
                Files.deleteIfExists(outputFilePath);
            } catch (IOException ioException) {
                // ignore
            }
            return null;
        }

        // 3. 第二阶段：修改 PDF 文件
        // 此时，PDF文件已经完全生成并关闭，可以安全地进行修改。
        try {
            PdfUtil.modifyPdfAndChangeMd5(outputFilePath.toAbsolutePath().toString());
        } catch (Exception e) {
            e.printStackTrace();
            // 如果修改失败，也删除文件
            try {
                Files.deleteIfExists(outputFilePath);
            } catch (IOException ioException) {
                // ignore
            }
            return null;
        }

        // 4. 返回最终文件的路径
        return outputFilePath.toAbsolutePath().toString().replace("\\", "/");
    }


    /**
     * 修改PDF的元数据以改变其MD5值，并覆盖原始文件。
     *
     * @param inputPdfPath  原始PDF文件路径
     * @throws IOException       如果文件读写失败
     * @throws DocumentException 如果PDF处理失败
     */
    public static void modifyPdfAndChangeMd5(String inputPdfPath) throws IOException, DocumentException {
        File inputFile = new File(inputPdfPath);
        File tempFile = new File(inputFile.getParent(), inputFile.getName() + ".tmp");

        PdfReader reader = null;
        PdfStamper stamper = null;

        try {
            reader = new PdfReader(inputPdfPath);
            stamper = new PdfStamper(reader, new FileOutputStream(tempFile));

            HashMap<String, String> info = reader.getInfo();
            info.put("ModificationGuid", UUID.randomUUID().toString());
            info.put("ModDate", new PdfDate().getW3CDate());

            stamper.setMoreInfo(info);
        } finally {
            // 使用独立的 try-catch 块来关闭每个资源
            // 确保 stamper.close() 的异常不会阻止 reader.close() 的执行
            if (stamper != null) {
                try {
                    stamper.close();
                } catch (Exception e) {
                    // 记录或忽略关闭时的异常，以允许后续的关闭操作继续
                    // 在这个场景下，我们更关心 reader 的关闭
                    e.printStackTrace(); // 或者使用日志记录
                }
            }
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e) {
                    // 记录或忽略
                    e.printStackTrace();
                }
            }
        }
        System.gc();
        try {
            TimeUnit.MILLISECONDS.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 重新设置中断状态
            throw new IOException("Thread was interrupted while waiting for file handle release.", e);
        }
        Files.move(tempFile.toPath(), inputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

}