import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * 修复 Launcher icon 的 lint 告警（仅使用 JDK 标准库，无需额外依赖）。
 *
 * 目标：
 * 1) ic_launcher.png 不要填满整个正方形（增加透明留白）
 * 2) ic_launcher_round.png 变为“圆形轮廓”（透明角 + 抗锯齿边缘）
 * 3) ic_launcher 与 ic_launcher_round 内容不再重复（避免 IconDuplicates）
 *
 * 用法：
 *   javac scripts/FixLauncherIcons.java -d /tmp/fix_launcher_icons
 *   java -Djava.awt.headless=true -cp /tmp/fix_launcher_icons FixLauncherIcons /path/to/repo 0.84 1.0
 *
 * 参数：
 * - launcherScale：square icon 缩放比例（建议 0.82~0.88）
 * - roundScale：round icon 缩放比例（通常 1.0 即可）
 */
public final class FixLauncherIcons {
  private static final String ICON_NAME = "ic_launcher.png";
  private static final String ROUND_ICON_NAME = "ic_launcher_round.png";

  public static void main(String[] args) throws Exception {
    if (args.length < 1) {
      System.err.println("用法：FixLauncherIcons <repoRoot> [launcherScale] [roundScale]");
      System.exit(2);
    }

    Path repoRoot = Path.of(args[0]).toAbsolutePath().normalize();
    double launcherScale = 0.84;
    double roundScale = 1.0;
    if (args.length >= 2) {
      launcherScale = Double.parseDouble(args[1]);
    }
    if (args.length >= 3) {
      roundScale = Double.parseDouble(args[2]);
    }
    if (launcherScale <= 0.0 || launcherScale > 1.0) {
      throw new IllegalArgumentException("launcherScale 需要在 (0, 1] 之间，当前=" + launcherScale);
    }
    if (roundScale <= 0.0 || roundScale > 1.0) {
      throw new IllegalArgumentException("roundScale 需要在 (0, 1] 之间，当前=" + roundScale);
    }

    Path resRoot = repoRoot.resolve("app/src/main/res");
    if (!Files.isDirectory(resRoot)) {
      throw new IllegalArgumentException("找不到 res 目录：" + resRoot);
    }

    List<Path> launcherIcons = findIcons(resRoot, ICON_NAME);
    List<Path> roundIcons = findIcons(resRoot, ROUND_ICON_NAME);

    if (launcherIcons.isEmpty() && roundIcons.isEmpty()) {
      System.out.println("未发现待处理的 launcher icon，跳过。res=" + resRoot);
      return;
    }

    System.out.println("FixLauncherIcons: repo=" + repoRoot);
    System.out.println("FixLauncherIcons: launcherScale=" + launcherScale);
    System.out.println("FixLauncherIcons: roundScale=" + roundScale);

    for (Path p : launcherIcons) {
      processLauncherIcon(p, launcherScale);
    }
    for (Path p : roundIcons) {
      processRoundIcon(p, roundScale);
    }

    System.out.println("FixLauncherIcons: OK");
  }

  private static List<Path> findIcons(Path resRoot, String filename) throws Exception {
    List<Path> out = new ArrayList<>();
    try (var stream = Files.list(resRoot)) {
      for (Path child : stream.toList()) {
        String name = child.getFileName().toString();
        if (!name.startsWith("mipmap-")) continue;
        if (!Files.isDirectory(child)) continue;
        Path icon = child.resolve(filename);
        if (Files.isRegularFile(icon)) out.add(icon);
      }
    }
    out.sort(Path::compareTo);
    return out;
  }

  private static void processLauncherIcon(Path path, double scale) throws Exception {
    BufferedImage src = ImageIO.read(path.toFile());
    if (src == null) {
      System.err.println("跳过：无法读取图片：" + path);
      return;
    }
    if (src.getWidth() <= 0 || src.getHeight() <= 0) {
      System.err.println("跳过：图片尺寸非法：" + path);
      return;
    }

    BufferedImage out = padAndScale(toArgb(src), scale);
    writePng(path, out);
    System.out.println("处理 ic_launcher: " + path + " (" + src.getWidth() + "x" + src.getHeight() + ")");
  }

  private static void processRoundIcon(Path path, double scale) throws Exception {
    BufferedImage src = ImageIO.read(path.toFile());
    if (src == null) {
      System.err.println("跳过：无法读取图片：" + path);
      return;
    }
    if (src.getWidth() <= 0 || src.getHeight() <= 0) {
      System.err.println("跳过：图片尺寸非法：" + path);
      return;
    }

    BufferedImage out = padAndScale(toArgb(src), scale);
    // feather 太大时，圆边缘像素会过度透明，可能仍被 lint 判定为“非圆形”。
    // 这里保持轻微抗锯齿即可（0.5px），让圆在边缘仍足够“实”。
    maskCircle(out, 0.5);
    writePng(path, out);
    System.out.println("处理 ic_launcher_round: " + path + " (" + src.getWidth() + "x" + src.getHeight() + ")");
  }

  private static BufferedImage toArgb(BufferedImage src) {
    if (src.getType() == BufferedImage.TYPE_INT_ARGB) return src;
    BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = out.createGraphics();
    g.setComposite(AlphaComposite.Src);
    g.drawImage(src, 0, 0, null);
    g.dispose();
    return out;
  }

  private static BufferedImage padAndScale(BufferedImage src, double scale) {
    int w = src.getWidth();
    int h = src.getHeight();

    int nw = Math.max(1, (int) Math.round(w * scale));
    int nh = Math.max(1, (int) Math.round(h * scale));
    int x = (w - nw) / 2;
    int y = (h - nh) / 2;

    BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = out.createGraphics();
    try {
      g.setComposite(AlphaComposite.Src);
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.drawImage(src, x, y, nw, nh, null);
    } finally {
      g.dispose();
    }
    return out;
  }

  private static void maskCircle(BufferedImage image, double featherPx) {
    int w = image.getWidth();
    int h = image.getHeight();
    double cx = w / 2.0;
    double cy = h / 2.0;
    double r = Math.min(w, h) / 2.0;

    for (int y = 0; y < h; y++) {
      double dy = (y + 0.5) - cy;
      for (int x = 0; x < w; x++) {
        double dx = (x + 0.5) - cx;
        double d = Math.sqrt(dx * dx + dy * dy);

        double mul;
        if (d <= r - featherPx) {
          mul = 1.0;
        } else if (d >= r) {
          mul = 0.0;
        } else {
          mul = (r - d) / featherPx; // 线性过渡（抗锯齿）
        }

        int argb = image.getRGB(x, y);
        int a = (argb >>> 24) & 0xFF;
        if (a == 0) continue;
        int na = (int) Math.round(a * mul);
        int rgb = argb & 0x00FF_FFFF;
        image.setRGB(x, y, (na << 24) | rgb);
      }
    }
  }

  private static void writePng(Path path, BufferedImage image) throws Exception {
    File f = path.toFile();
    if (!ImageIO.write(image, "PNG", f)) {
      throw new IllegalStateException("ImageIO.write 失败：" + path);
    }
  }
}

