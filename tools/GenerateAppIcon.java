import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Generates the JavaFX window icon and the Windows app icon from the same artwork. */
public final class GenerateAppIcon {
    private static final Color DARK_TOP = new Color(18, 48, 39);
    private static final Color DARK_BOTTOM = new Color(10, 27, 23);
    private static final Color MINT = new Color(93, 203, 159);
    private static final Color SUN = new Color(255, 207, 116);

    public static void main(String[] args) throws Exception {
        Path project = Path.of(args.length == 0 ? "." : args[0]).toAbsolutePath();
        Path resources = project.resolve("src/main/resources");
        Path assets = project.resolve("assets");
        Files.createDirectories(resources);
        Files.createDirectories(assets);
        ImageIO.write(draw(256), "png", resources.resolve("morning-athkar.png").toFile());
        writeIco(assets.resolve("morning-athkar.ico"));
    }

    private static BufferedImage draw(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        double s = size / 256.0;
        g.scale(s, s);
        g.setPaint(new GradientPaint(0, 0, DARK_TOP, 256, 256, DARK_BOTTOM));
        g.fill(new RoundRectangle2D.Double(8, 8, 240, 240, 62, 62));

        g.setColor(new Color(255, 255, 255, 220));
        star(g, 67, 77, 5);
        star(g, 185, 64, 4);
        star(g, 211, 105, 3);

        Path2D sun = new Path2D.Double();
        sun.moveTo(72, 157);
        sun.quadTo(128, 71, 184, 157);
        sun.closePath();
        g.setColor(SUN);
        g.fill(sun);

        g.setStroke(new BasicStroke(12, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(MINT);
        Path2D horizon = new Path2D.Double();
        horizon.moveTo(46, 158);
        horizon.quadTo(128, 139, 210, 158);
        g.draw(horizon);

        g.setStroke(new BasicStroke(8, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D lowerArc = new Path2D.Double();
        lowerArc.moveTo(69, 189);
        lowerArc.quadTo(128, 206, 187, 189);
        g.draw(lowerArc);
        g.dispose();
        return image;
    }

    private static void star(Graphics2D g, double x, double y, double radius) {
        Path2D star = new Path2D.Double();
        star.moveTo(x, y - radius);
        star.quadTo(x + 1, y - 1, x + radius, y);
        star.quadTo(x + 1, y + 1, x, y + radius);
        star.quadTo(x - 1, y + 1, x - radius, y);
        star.quadTo(x - 1, y - 1, x, y - radius);
        g.fill(star);
    }

    private static void writeIco(Path output) throws IOException {
        int[] sizes = {16, 32, 48, 256};
        List<byte[]> pngImages = new ArrayList<>();
        for (int size : sizes) {
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(draw(size), "png", png);
            pngImages.add(png.toByteArray());
        }

        int directorySize = 6 + sizes.length * 16;
        int imageOffset = directorySize;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            writeLeShort(out, 0);
            writeLeShort(out, 1);
            writeLeShort(out, sizes.length);
            for (int i = 0; i < sizes.length; i++) {
                int size = sizes[i];
                byte[] png = pngImages.get(i);
                out.writeByte(size == 256 ? 0 : size);
                out.writeByte(size == 256 ? 0 : size);
                out.writeByte(0);
                out.writeByte(0);
                writeLeShort(out, 1);
                writeLeShort(out, 32);
                writeLeInt(out, png.length);
                writeLeInt(out, imageOffset);
                imageOffset += png.length;
            }
            for (byte[] png : pngImages) out.write(png);
        }
        Files.write(output, bytes.toByteArray());
    }

    private static void writeLeShort(DataOutputStream out, int value) throws IOException {
        out.writeByte(value & 0xff);
        out.writeByte((value >>> 8) & 0xff);
    }

    private static void writeLeInt(DataOutputStream out, int value) throws IOException {
        out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array());
    }
}
