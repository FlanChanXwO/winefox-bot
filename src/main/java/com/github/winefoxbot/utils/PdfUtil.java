package com.github.winefoxbot.utils;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.UUID;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-21-23:33
 */
public final class PdfUtil {
    private PdfUtil() {}


    public static String wrapImagesIntoPdf(List<Path> images, String outputPath) {
        // 1. 创建 Document 对象
        Document document = new Document();
        try {
            String outputFileName = outputPath + File.pathSeparator + UUID.randomUUID().toString().replace("-","").substring(0,8) + ".pdf";
            // 2. 创建 PdfWriter
            PdfWriter.getInstance(document, new FileOutputStream(outputFileName));
            // 3. 打开文档
            document.open();
            // 计算页面可打印宽度，用于设置表格宽度
            float printableWidth = document.getPageSize().getWidth() - document.leftMargin() - document.rightMargin();
            for (Path image : images) {
                String imagePath = image.toAbsolutePath().toString();
                // 通过路径获取图片实例
                Image img = Image.getInstance(imagePath);
                // 1. 创建一个只有一列的表格
                PdfPTable table = new PdfPTable(1);
                // 2. 设置表格宽度为100%页面可用宽度
                table.setWidthPercentage(100);
                // 3. 图片宽度等于页面可用宽度，高度等比缩放
                img.scaleToFit(printableWidth, Float.MAX_VALUE);
                // 4. 创建一个单元格，将图片放进去
                //    通过设置为0，移除单元格的边框和内边距，让图片紧贴边缘
                com.itextpdf.text.pdf.PdfPCell cell = new com.itextpdf.text.pdf.PdfPCell(img, true);
                cell.setBorder(Rectangle.NO_BORDER);
                cell.setPadding(0);
                // 5. 将单元格添加到表格
                table.addCell(cell);
                // 6. (重要) 在表格后添加一些间距，而不是在图片后
                //    这比直接添加 Paragraph 更可控
                table.setSpacingAfter(10f); // 在表格下方增加 10pt 的间距
                // 7. 将完整的表格添加到文档中
                document.add(table);
                return outputFileName;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 4. 关闭文档
            document.close();
        }
        return null;
    }

    public static String wrapByteImagesIntoPdf(List<byte[]> images, String outputPath) {
        // 1. 创建 Document 对象
        Document document = new Document();
        try {
            String userHome = System.getProperty("user.home");

            // 1. Define the directory where you want to save the file
            // (Assuming 'outputPath' is a subdirectory like "setu")
            Path outputDir = Paths.get(userHome, "pdfs", outputPath);

            // 2. Create the directory AND any missing parent directories.
            // This is the key fix. It replaces your 'if' block.
            Files.createDirectories(outputDir);

            // 3. Construct the full path for the NEW file
            String randomFileName = UUID.randomUUID().toString().replace("-", "").substring(0, 8) + ".pdf";
            Path outputFilePath = outputDir.resolve(randomFileName); // Use resolve() for clean path joining

            // 4. Create the PdfWriter with the final file path
            PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(outputFilePath.toFile()));
            // 2. 创建 PdfWriter
            writer.createXmpMetadata();
            // 3. 打开文档
            document.open();
            // 计算页面可打印宽度，用于设置表格宽度
            float printableWidth = document.getPageSize().getWidth() - document.leftMargin() - document.rightMargin();
            for (byte[] image : images) {
                // 通过路径获取图片实例
                Image img = Image.getInstance(image);
                // 1. 创建一个只有一列的表格
                PdfPTable table = new PdfPTable(1);
                // 2. 设置表格宽度为100%页面可用宽度
                table.setWidthPercentage(100);
                // 3. 图片宽度等于页面可用宽度，高度等比缩放
                img.scaleToFit(printableWidth, Float.MAX_VALUE);
                // 4. 创建一个单元格，将图片放进去
                //    通过设置为0，移除单元格的边框和内边距，让图片紧贴边缘
                com.itextpdf.text.pdf.PdfPCell cell = new com.itextpdf.text.pdf.PdfPCell(img, true);
                cell.setBorder(Rectangle.NO_BORDER);
                cell.setPadding(0);
                // 5. 将单元格添加到表格
                table.addCell(cell);
                // 6. (重要) 在表格后添加一些间距，而不是在图片后
                //    这比直接添加 Paragraph 更可控
                table.setSpacingAfter(10f); // 在表格下方增加 10pt 的间距
                // 7. 将完整的表格添加到文档中
                document.add(table);
                // 4. 关闭文档
                document.close();
                PdfUtil.modifyPdfAndChangeMd5(outputFilePath.toAbsolutePath().toString());
                return outputFilePath.toAbsolutePath().toString();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
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
        // 1. 创建一个临时文件用于写入修改后的PDF
        File tempFile = new File(inputPdfPath + ".tmp");

        PdfReader reader = null;
        PdfStamper stamper = null;

        try {
            // 2. 创建 PdfReader 和 PdfStamper
            reader = new PdfReader(inputPdfPath);
            stamper = new PdfStamper(reader, new FileOutputStream(tempFile));

            // 3. 获取并修改元数据
            HashMap<String, String> info = reader.getInfo();
            info.put("ModificationGuid", UUID.randomUUID().toString());
            info.put("ModDate", new PdfDate().getW3CDate());

            // 4. 将修改后的元数据设置回 Stamper
            stamper.setMoreInfo(info);

        } finally {
            // 5. 确保在任何情况下都关闭 stamper 和 reader
            // 这会完成写入临时文件的过程并释放对文件的锁定
            if (stamper != null) {
                stamper.close();
            }
            if (reader != null) {
                reader.close();
            }
        }

        // 6. 用临时文件覆盖原始文件
        // 使用 Files.move 来保证操作的原子性
        System.gc();
        Files.move(tempFile.toPath(), inputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
}