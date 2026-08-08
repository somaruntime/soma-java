package io.github.somaruntime.soma.processor;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;

/** Java 8 compiler-tree proof for the deliberately data-only schema declaration shape. */
final class SourceShapeInspector {

    enum Result {
        VALID,
        DECLARATION_MEMBER,
        FIELD_INITIALIZER,
        UNSAFE_SOURCE,
        UNAVAILABLE
    }

    private final Trees trees;
    private final SourcePositions positions;
    private final Map<String, Result> captured = new LinkedHashMap<String, Result>();

    private SourceShapeInspector(Trees trees) {
        this.trees = trees;
        this.positions = trees == null ? null : trees.getSourcePositions();
    }

    static SourceShapeInspector create(ProcessingEnvironment environment) {
        try {
            return new SourceShapeInspector(Trees.instance(environment));
        } catch (IllegalArgumentException failure) {
            return unavailable();
        } catch (LinkageError failure) {
            return unavailable();
        }
    }

    void capture(TypeElement type) {
        captured.put(type.getQualifiedName().toString(), inspectLive(type));
    }

    Result inspect(TypeElement type) {
        Result existing = captured.get(type.getQualifiedName().toString());
        return existing == null ? inspectLive(type) : existing;
    }

    private Result inspectLive(TypeElement type) {
        if (trees == null || positions == null) return Result.UNAVAILABLE;
        try {
            TreePath path = trees.getPath(type);
            if (path == null || !(path.getLeaf() instanceof ClassTree)) {
                return Result.UNAVAILABLE;
            }
            CompilationUnitTree unit = path.getCompilationUnit();
            ClassTree declaration = (ClassTree) path.getLeaf();
            JavaFileObject sourceFile = unit.getSourceFile();
            if (sourceFile == null
                    || containsUnsafeHiddenCodePointInCode(
                            sourceFile.getCharContent(true))) {
                return sourceFile == null ? Result.UNAVAILABLE : Result.UNSAFE_SOURCE;
            }
            for (Tree member : declaration.getMembers()) {
                long start = positions.getStartPosition(unit, member);
                long end = positions.getEndPosition(unit, member);
                if (start < 0L) continue;
                if (member.getKind() == Tree.Kind.VARIABLE) {
                    if (((VariableTree) member).getInitializer() != null) {
                        return Result.FIELD_INITIALIZER;
                    }
                } else if (member.getKind() == Tree.Kind.METHOD) {
                    MethodTree method = (MethodTree) member;
                    if (end < 0L && method.getName().contentEquals("<init>")) {
                        continue;
                    }
                    return Result.DECLARATION_MEMBER;
                } else if (member.getKind() == Tree.Kind.BLOCK) {
                    return Result.DECLARATION_MEMBER;
                }
            }
            return Result.VALID;
        } catch (IOException failure) {
            return Result.UNAVAILABLE;
        } catch (RuntimeException failure) {
            return Result.UNAVAILABLE;
        }
    }

    private static boolean containsUnsafeHiddenCodePointInCode(CharSequence raw) {
        String decoded = decodeUnicodeEscapes(raw);
        int state = 0;
        for (int offset = 0; offset < decoded.length();) {
            int codePoint = decoded.codePointAt(offset);
            int width = Character.charCount(codePoint);
            int next = offset + width < decoded.length()
                    ? decoded.codePointAt(offset + width) : -1;
            if (state == 0) {
                if (codePoint == '/' && next == '/') {
                    state = 1;
                    offset += width + Character.charCount(next);
                    continue;
                }
                if (codePoint == '/' && next == '*') {
                    state = 2;
                    offset += width + Character.charCount(next);
                    continue;
                }
                if (codePoint == '"') {
                    state = 3;
                } else if (codePoint == '\'') {
                    state = 4;
                } else if (unsafe(codePoint)) {
                    return true;
                }
            } else if (state == 1) {
                if (codePoint == '\n' || codePoint == '\r') state = 0;
            } else if (state == 2) {
                if (codePoint == '*' && next == '/') {
                    state = 0;
                    offset += width + Character.charCount(next);
                    continue;
                }
            } else if (codePoint == '\\') {
                if (next >= 0) {
                    offset += width + Character.charCount(next);
                    continue;
                }
            } else if (state == 3 && codePoint == '"'
                    || state == 4 && codePoint == '\'') {
                state = 0;
            }
            offset += width;
        }
        return false;
    }

    private static boolean unsafe(int codePoint) {
        return Character.isIdentifierIgnorable(codePoint)
                || Character.getType(codePoint) == Character.FORMAT
                || Character.isISOControl(codePoint) && !allowedWhitespace(codePoint);
    }

    /** Applies the JLS backslash-eligibility rule; Unicode translation is one pass. */
    private static String decodeUnicodeEscapes(CharSequence raw) {
        StringBuilder decoded = new StringBuilder(raw.length());
        int consecutiveBackslashes = 0;
        for (int index = 0; index < raw.length();) {
            char current = raw.charAt(index);
            boolean eligible = current == '\\'
                    && (consecutiveBackslashes & 1) == 0;
            if (eligible && index + 1 < raw.length() && raw.charAt(index + 1) == 'u') {
                int hex = index + 2;
                while (hex < raw.length() && raw.charAt(hex) == 'u') hex++;
                if (hex + 4 <= raw.length()) {
                    int value = 0;
                    boolean valid = true;
                    for (int digit = 0; digit < 4; digit++) {
                        int part = Character.digit(raw.charAt(hex + digit), 16);
                        if (part < 0) {
                            valid = false;
                            break;
                        }
                        value = (value << 4) | part;
                    }
                    if (valid) {
                        decoded.append((char) value);
                        index = hex + 4;
                        consecutiveBackslashes = 0;
                        continue;
                    }
                }
            }
            decoded.append(current);
            consecutiveBackslashes = current == '\\'
                    ? consecutiveBackslashes + 1 : 0;
            index++;
        }
        return decoded.toString();
    }

    private static boolean allowedWhitespace(int codePoint) {
        return codePoint == '\t'
                || codePoint == '\n'
                || codePoint == '\r'
                || codePoint == '\f';
    }

    private static SourceShapeInspector unavailable() {
        return new SourceShapeInspector(null);
    }
}
