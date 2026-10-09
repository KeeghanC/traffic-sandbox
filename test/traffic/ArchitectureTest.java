package traffic;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/** Guard package direction and keep Swing out of the simulation core. */
public final class ArchitectureTest {
    public static void main(String[] args) throws Exception {
        Path source = Paths.get("src", "traffic");
        try (Stream<Path> files = Files.walk(source)) {
            files.filter(file -> file.toString().endsWith(".java"))
                    .forEach(
                            file -> {
                                try {
                                    String text =
                                            new String(
                                                    Files.readAllBytes(file),
                                                    StandardCharsets.UTF_8);
                                    String layer = source.relativize(file).getName(0).toString();
                                    boolean core =
                                            layer.equals("model")
                                                    || layer.equals("signals")
                                                    || layer.equals("engine")
                                                    || layer.equals("runtime");
                                    if (core
                                            && (text.contains("import javax.swing")
                                                    || text.contains("import traffic.desktop"))) {
                                        throw new AssertionError(
                                                "Core depends on desktop: " + file);
                                    }
                                    if (layer.equals("model")
                                            && (text.contains("import traffic.engine")
                                                    || text.contains("import traffic.signals"))) {
                                        throw new AssertionError(
                                                "Geometry depends on simulation: " + file);
                                    }
                                    if (layer.equals("signals")
                                            && text.contains("import traffic.engine")) {
                                        throw new AssertionError(
                                                "Controller reads mutable engine state: " + file);
                                    }
                                } catch (java.io.IOException error) {
                                    throw new RuntimeException(error);
                                }
                            });
        }
        System.out.println("PASS: architecture dependency boundaries");
    }
}
