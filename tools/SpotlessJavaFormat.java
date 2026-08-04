import com.diffplug.spotless.Formatter;
import com.diffplug.spotless.LineEnding;
import com.diffplug.spotless.PaddedCell;
import com.diffplug.spotless.Provisioner;
import com.diffplug.spotless.generic.IndentStep;
import com.diffplug.spotless.java.GoogleJavaFormatStep;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** Standalone Spotless runner for repositories which do not use Gradle or Maven. */
public final class SpotlessJavaFormat {
    private static final String GOOGLE_JAVA_FORMAT_VERSION = "1.28.0";
    private static final String GOOGLE_JAVA_FORMAT_STYLE = "AOSP";
    private static final List<String> FORMATTED_DIRECTORIES = List.of("src", "data", "tools");

    private SpotlessJavaFormat() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3 || !(args[0].equals("apply") || args[0].equals("check"))) {
            System.err.println(
                    "Usage: SpotlessJavaFormat <apply|check> <repository-root> <google-java-format-jar>");
            System.exit(2);
        }

        boolean checkOnly = args[0].equals("check");
        Path repositoryRoot = Path.of(args[1]).toAbsolutePath().normalize();
        Path googleJavaFormatJar = Path.of(args[2]).toAbsolutePath().normalize();

        if (!Files.isRegularFile(googleJavaFormatJar)) {
            throw new IllegalArgumentException(
                    "google-java-format JAR does not exist: " + googleJavaFormatJar);
        }

        List<Path> javaFiles = new ArrayList<>();
        for (String directory : FORMATTED_DIRECTORIES) {
            Path javaRoot = repositoryRoot.resolve(directory);
            if (!Files.isDirectory(javaRoot)) {
                throw new IllegalArgumentException("Java directory does not exist: " + javaRoot);
            }
            try (Stream<Path> paths = Files.walk(javaRoot)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".java"))
                        .forEach(javaFiles::add);
            }
        }
        javaFiles.sort(Path::compareTo);

        Provisioner provisioner =
                (withTransitives, coordinates) -> Set.of(googleJavaFormatJar.toFile());
        List<Path> violations = new ArrayList<>();
        int paddedCellResolutions = 0;

        try (Formatter formatter =
                Formatter.builder()
                        .lineEndingsPolicy(LineEnding.UNIX.createPolicy())
                        .encoding(StandardCharsets.UTF_8)
                        .steps(
                                List.of(
                                        GoogleJavaFormatStep.create(
                                                GOOGLE_JAVA_FORMAT_VERSION,
                                                GOOGLE_JAVA_FORMAT_STYLE,
                                                provisioner),
                                        IndentStep.Type.SPACE.create(4)))
                        .build()) {
            for (Path javaFile : javaFiles) {
                String original = Files.readString(javaFile, StandardCharsets.UTF_8);
                PaddedCell paddedCell =
                        PaddedCell.check(formatter, javaFile.toFile(), LineEnding.toUnix(original));
                if (!paddedCell.isResolvable()) {
                    throw new IllegalStateException(
                            "Spotless cannot resolve formatter behavior for "
                                    + repositoryRoot.relativize(javaFile)
                                    + ": "
                                    + paddedCell.userMessage());
                }
                if (paddedCell.misbehaved()) {
                    paddedCellResolutions++;
                }
                String formatted = LineEnding.toUnix(paddedCell.canonical());
                if (original.equals(formatted)) {
                    continue;
                }

                violations.add(repositoryRoot.relativize(javaFile));
                if (!checkOnly) {
                    writeAtomically(javaFile, formatted);
                }
            }
        }

        if (checkOnly && !violations.isEmpty()) {
            System.err.println("Spotless found formatting violations:");
            for (Path violation : violations) {
                System.err.println("  " + violation);
            }
            System.err.println("Run the formatter without --check to fix them.");
            System.exit(1);
        }

        String action = checkOnly ? "checked" : "formatted";
        System.out.printf(
                "Spotless %s %d Java files; %d file(s) changed; "
                        + "%d padded-cell resolution(s).%n",
                action, javaFiles.size(), violations.size(), paddedCellResolutions);
    }

    private static void writeAtomically(Path target, String content) throws IOException {
        Path temporary =
                Files.createTempFile(
                        target.getParent(), target.getFileName().toString(), ".spotless");
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
