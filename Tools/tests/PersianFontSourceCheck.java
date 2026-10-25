import com.sun.source.util.JavacTask;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/** Syntax-only check; intentionally does not claim Android compilation. */
public final class PersianFontSourceCheck {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a UTF-8 source-file manifest");
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("A JDK is required");
        }
        List<String> paths = Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8);
        if (paths.isEmpty()) {
            throw new IllegalArgumentException("The source-file manifest is empty");
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            JavacTask task = (JavacTask) compiler.getTask(null, files, diagnostics,
                    Arrays.asList("-proc:none", "-encoding", "UTF-8"), null,
                    files.getJavaFileObjectsFromStrings(paths));
            int parsed = 0;
            for (Object tree : task.parse()) {
                parsed++;
            }
            int errors = 0;
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                    System.err.println(diagnostic);
                    errors++;
                }
            }
            System.out.println("Parsed Java files: " + parsed + ", syntax errors: " + errors);
            if (errors != 0) {
                System.exit(1);
            }
        }
    }
}
