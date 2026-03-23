package git.arch;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class Arch {

  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      System.out.println("Usage:");
      System.out.println("  java git.arch.Arch split <sourceFile> <outDir> [chunkSizeBytes]");
      System.out.println("  java git.arch.Arch join <inDir> <outFile>");
      System.out.println();
      System.out.println("Example:");
      System.out.println("  java git.arch.Arch split \"D:\\Temp\\Postman (x64).exe\" D:\\Temp\\chunks");
      System.out.println("  java git.arch.Arch join D:\\Temp\\chunks D:\\Temp\\Postman_rebuilt.exe");
      return;
    }

    String cmd = args[0];
    if ("split".equalsIgnoreCase(cmd)) {
      if (args.length < 3) {
        System.err.println("split requires sourceFile and outDir");
        return;
      }
      Path source = Paths.get(args[1]);
      Path outDir = Paths.get(args[2]);
      int chunkSize = 24 * 1024 * 1024; // default 24 MB
      if (args.length >= 4) {
        try {
          chunkSize = Integer.parseInt(args[3]);
        } catch (NumberFormatException ignored) {
          System.err.println("Invalid chunkSize, using default 24MB");
        }
      }
      splitFileToPngChunks(source, outDir, chunkSize);
    } else if ("join".equalsIgnoreCase(cmd)) {
      if (args.length < 3) {
        System.err.println("join requires inDir and outFile");
        return;
      }
      Path inDir = Paths.get(args[1]);
      Path outFile = Paths.get(args[2]);
      mergePngChunksToFile(inDir, outFile);
    } else {
      System.err.println("Unknown command: " + cmd);
    }
  }

  /**
   * Разбивает бинарный файл на последовательность файлов с расширением .png
   * Каждый файл содержит не более chunkSize байт. Имена: <original>.partNNNNN.png
   *
   * @param source путь к исходному файлу (например D:\\Temp\\Postman (x64).exe)
   * @param outDir папка для хранения частей
   * @param chunkSize размер части в байтах (например 24*1024*1024)
   * @throws IOException при ошибках ввода-вывода
   */
  public static void splitFileToPngChunks(Path source, Path outDir, int chunkSize) throws IOException {
    if (!Files.exists(source) || !Files.isRegularFile(source)) {
      throw new IOException("Source file does not exist or is not a regular file: " + source);
    }
    if (!Files.exists(outDir)) {
      Files.createDirectories(outDir);
    }

    String baseName = source.getFileName().toString();
    byte[] buffer = new byte[Math.min(chunkSize, 1024 * 1024)]; // буфер чтения (min 1MB)
    int index = 0;

    try (InputStream in = Files.newInputStream(source)) {
      int read;
      while (true) {
        // собираем до chunkSize в временный файл
        String partName = baseName + ".part" + String.format("%05d", index) + ".png";
        Path out = outDir.resolve(partName);
        int written = 0;
        try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(out))) {
          while (written < chunkSize && (read = in.read(buffer)) != -1) {
            os.write(buffer, 0, read);
            written += read;
          }
          os.flush();
        }
        if (written == 0) {
          // ничего не записано — конец файла
          Files.deleteIfExists(out);
          break;
        }
        System.out.println("Wrote: " + out + " (" + written + " bytes)");
        index++;
        if (written < chunkSize) {
          // последний кусок — достигнут EOF
          break;
        }
      }
    }
  }

  /**
   * Читает .png файлы из папки inputDir (обычно созданные splitFileToPngChunks)
   * и склеивает их в один целевой файл outFile в лексикографическом порядке имени.
   *
   * @param inputDir папка с .png частями
   * @param outFile итоговый бинарный файл
   * @throws IOException при ошибках ввода-вывода
   */
  public static void mergePngChunksToFile(Path inputDir, Path outFile) throws IOException {
    if (!Files.exists(inputDir) || !Files.isDirectory(inputDir)) {
      throw new IOException("Input directory does not exist: " + inputDir);
    }

    List<Path> parts;
    try (var stream = Files.list(inputDir)) {
      parts = stream
          .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".png"))
          .sorted(Comparator.comparing(p -> p.getFileName().toString()))
          .collect(Collectors.toList());
    }

    if (parts.isEmpty()) {
      throw new IOException("No .png parts found in " + inputDir);
    }

    if (Files.exists(outFile)) {
      System.out.println("Output file exists and will be overwritten: " + outFile);
      Files.delete(outFile);
    } else {
      Path parent = outFile.getParent();
      if (parent != null && !Files.exists(parent)) {
        Files.createDirectories(parent);
      }
    }

    try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(outFile))) {
      for (Path part : parts) {
        long size = Files.size(part);
        try (InputStream in = Files.newInputStream(part)) {
          byte[] buf = new byte[8192];
          int r;
          while ((r = in.read(buf)) != -1) {
            out.write(buf, 0, r);
          }
        }
        System.out.println("Appended: " + part + " (" + size + " bytes)");
      }
      out.flush();
    }
    System.out.println("Rebuilt file: " + outFile);
  }

}
