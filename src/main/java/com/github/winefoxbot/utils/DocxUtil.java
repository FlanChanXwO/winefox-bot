package com.github.winefoxbot.utils;

import org.docx4j.dml.wordprocessingDrawing.Inline;
import org.docx4j.docProps.custom.Properties;
import org.docx4j.jaxb.Context;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.DocPropsCustomPart;
import org.docx4j.openpackaging.parts.WordprocessingML.BinaryPartAbstractImage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.wml.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-02
 * (Utility class to create DOCX files with animated GIFs using docx4j)
 */
public final class DocxUtil {
    private DocxUtil() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * 使用 docx4j 将图片字节数组（特别是GIF）打包成一个 DOCX 文件。
     * 每个图片/GIF将独占一页，并尽可能大地在页面中央显示。
     *
     * @param images     图片字节数组的列表
     * @param outputPath 输出目录的路径
     * @return 生成的 DOCX 文件的绝对路径，如果失败则返回 null
     */
    public static String wrapImagesIntoDocx(List<byte[]> images, String outputPath) {
        // 1. 确定输出文件路径
        Path outputFilePath;
        try {
            Path outputDir = Paths.get(outputPath);
            Files.createDirectories(outputDir);
            String randomFileName = UUID.randomUUID().toString().replace("-", "").substring(0, 8) + ".docx";
            outputFilePath = outputDir.resolve(randomFileName);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        // 2. 使用 docx4j 创建并保存文档
        try {
            // 创建一个新的 Word 文档包
            WordprocessingMLPackage wordPackage = WordprocessingMLPackage.createPackage();
            MainDocumentPart mainDocumentPart = wordPackage.getMainDocumentPart();
            ObjectFactory factory = Context.getWmlObjectFactory();

            // 遍历所有图片数据
            boolean isFirstImage = true;
            for (byte[] imageData : images) {

                // 非第一个图片前，添加分页符，确保每个图片独占一页
                if (!isFirstImage) {
                    addPageBreak(mainDocumentPart, factory);
                }

                // 将图片添加到文档包中，并获取其关系ID
                BinaryPartAbstractImage imagePart = BinaryPartAbstractImage.createImagePart(wordPackage, imageData);

                // --- 关键：调整图片大小以适应页面宽度，并保持宽高比 ---
                // 获取页面可用宽度 (单位: twips, 1 inch = 1440 twips, 1 EMU = 1/914400 inch)
                // docx4j的createImageInline方法使用的单位是EMUs
                long availableWidthTwips = wordPackage.getDocumentModel().getSections().get(0).getPageDimensions().getWritableWidthTwips();
                long availableWidthEmus = availableWidthTwips * 635; // 1 twip = 635 EMUs

                // 读取图片原始尺寸以计算宽高比
                long originalWidthPx;
                long originalHeightPx;
                try (ByteArrayInputStream bais = new ByteArrayInputStream(imageData)) {
                    BufferedImage bufferedImage = ImageIO.read(bais);
                    originalWidthPx = bufferedImage.getWidth();
                    originalHeightPx = bufferedImage.getHeight();
                } catch (IOException e) {
                    // 如果无法读取图片尺寸（例如对于某些特殊的GIF），则默认宽高比为1:1
                    originalWidthPx = 1;
                    originalHeightPx = 1;
                }
                double aspectRatio = (double) originalHeightPx / originalWidthPx;

                // 根据可用宽度和宽高比，计算图片在文档中的高度
                long finalHeightEmus = (long) (availableWidthEmus * aspectRatio);

                // 创建一个内联的图片对象，指定文件名、替代文本和尺寸
                // ID 都是从1开始的，这里用随机数避免冲突
                // docx4j 内部会管理ID，这里提供的ID主要是为了docPr元素
                int docPrId = (int) (Math.random() * 10000);
                Inline inline = imagePart.createImageInline(
                        "Image " + docPrId, "Image",
                        docPrId, 1, // ID和cNvPr的ID，可以不同
                        availableWidthEmus, finalHeightEmus, false);

                // 将内联图片包装在 Drawing 对象中
                Drawing drawing = factory.createDrawing();
                drawing.getAnchorOrInline().add(inline);

                // 将 Drawing 对象包装在 Run (R) 中
                R run = factory.createR();
                run.getContent().add(drawing);

                // --- 关键：创建段落并设置为居中对齐 ---
                P paragraph = factory.createP();

                // 1. 设置段落属性 (PPr)
                PPr pPr = factory.createPPr();

                // 2. 设置对齐方式 (Jc) 为居中 (center)
                Jc jc = factory.createJc();
                jc.setVal(JcEnumeration.CENTER);
                pPr.setJc(jc);

                // 3. (可选) 增加段落上下间距，实现更好的垂直居中视觉效果
                PPrBase.Spacing spacing = factory.createPPrBaseSpacing();
                spacing.setBefore(BigInteger.valueOf(240)); // 上方间距，单位：twips
                spacing.setAfter(BigInteger.valueOf(240));  // 下方间距，单位：twips
                pPr.setSpacing(spacing);

                // 4. 将段落属性应用到段落
                paragraph.setPPr(pPr);

                // 将包含图片的Run添加到段落中
                paragraph.getContent().add(run);

                // 将段落添加到主文档部分
                mainDocumentPart.addObject(paragraph);

                isFirstImage = false;
            }

            // 修改文档元数据
            randomizeDocxHash(wordPackage);

            // 3. 保存 DOCX 文件
            wordPackage.save(outputFilePath.toFile());

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

        // 4. 返回最终文件的路径
        return outputFilePath.toAbsolutePath().toString().replace("\\", "/");
    }

    /**
     * 在文档末尾添加一个分页符。
     * @param mainDocumentPart 主文档部分
     * @param factory ObjectFactory
     */
    private static void addPageBreak(MainDocumentPart mainDocumentPart, ObjectFactory factory) {
        P p = factory.createP();
        R r = factory.createR();
        Br br = factory.createBr();
        br.setType(STBrType.PAGE); // 设置为分页符
        r.getContent().add(br);
        p.getContent().add(r);
        mainDocumentPart.addObject(p);
    }


    /**
     * 使 DOCX 文件的哈希值（如MD5）随机化。
     * 通过添加或更新一个包含唯一UUID的自定义文档属性来实现。
     *
     * @param wordPackage 要修改的 WordprocessingMLPackage 对象。
     */
    private static void randomizeDocxHash(WordprocessingMLPackage wordPackage) throws Exception {
        // 1. 尝试获取自定义属性部分
        DocPropsCustomPart customProps = wordPackage.getDocPropsCustomPart();

        // 2. 检查它是否存在。如果不存在，就创建它。
        if (customProps == null) {
            customProps = new DocPropsCustomPart();
            wordPackage.addTargetPart(customProps);

            // !! 关键步骤：为新创建的 Part 初始化其 JAXB 内容 !!
            // 创建一个 Properties 根元素并设置给它
            customProps.setJaxbElement(new Properties());
        } else {
            // 2a. 如果 Part 存在，但内容为空，也要初始化
            if (customProps.getJaxbElement() == null) {
                customProps.setJaxbElement(new Properties());
            }
        }

        // 3. 现在 customProps 及其内部的 JAXB 元素都已准备就绪，可以安全地设置属性了。
        // setProperty 方法会处理添加新属性或更新现有属性的逻辑。
        customProps.setProperty("RandomModificationGuid", UUID.randomUUID().toString());
    }
}
