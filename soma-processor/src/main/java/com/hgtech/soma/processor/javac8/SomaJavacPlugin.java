package com.hgtech.soma.processor.javac8;

import com.hgtech.soma.processor.internal.CompilerProtocol;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.api.BasicJavacTask;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCImport;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCPrimitiveTypeTree;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Full JDK 8 javac parse-phase adapter for {@code @SomaValue} lowering.
 */
public final class SomaJavacPlugin implements Plugin {
    private static final String PLUGIN_NAME = "SomaValue";

    private static final String SOMA_VALUE = "com.hgtech.soma.annotation.SomaValue";
    private static final String SOMA_FIELD = "com.hgtech.soma.annotation.SomaField";
    private static final String SOMA_IGNORE = "com.hgtech.soma.annotation.SomaIgnore";

    @Override
    public String getName() {
        return PLUGIN_NAME;
    }

    @Override
    public void init(JavacTask task, String... args) {
        // Check the public runtime identity before touching any javac-internal type.
        // This preserves a stable SOMA diagnostic on modular JDKs instead of leaking
        // an IllegalAccessError from their non-exported compiler implementation.
        if (!"1.8".equals(System.getProperty("java.specification.version"))) {
            throw new IllegalStateException(
                    "[SOMA-COMP-002] SomaValue requires full JDK 8 javac");
        }

        if (!(task instanceof BasicJavacTask)) {
            throw new IllegalStateException("[SOMA-COMP-002] unsupported javac task implementation: "
                    + task.getClass().getName());
        }

        Context context = ((BasicJavacTask) task).getContext();
        final Log log = Log.instance(context);
        final CompilerProtocol.Session compilerSession =
                CompilerProtocol.instance(context);
        compilerSession.activatePlugin(CompilerProtocol.LOWERING_IDENTITY);
        final Lowerer lowerer = new Lowerer(
                TreeMaker.instance(context), Names.instance(context), log, compilerSession);
        task.addTaskListener(new TaskListener() {
            private boolean processorChecked;

            @Override
            public void started(TaskEvent event) {
                if (event.getKind() == TaskEvent.Kind.ANALYZE
                        && (lowerer.hasSomaValue() || lowerer.hasFailure())
                        && !processorChecked) {
                    processorChecked = true;
                    String processorIdentity = compilerSession.getProcessorIdentity();
                    if (!CompilerProtocol.PROCESSOR_IDENTITY.equals(processorIdentity)) {
                        for (String diagnostic : compilerSession.getPluginDiagnostics()) {
                            log.printRawLines(Log.WriterKind.ERROR, diagnostic);
                        }
                        String message =
                                "[SOMA-COMP-005] SomaValue plugin requires active processor "
                                        + CompilerProtocol.PROCESSOR_IDENTITY;
                        log.printRawLines(Log.WriterKind.ERROR, message);
                        log.rawError(0, message);
                    }
                }
            }

            @Override
            public void finished(TaskEvent event) {
                if (event.getKind() == TaskEvent.Kind.PARSE) {
                    CompilationUnitTree unit = event.getCompilationUnit();
                    if (unit instanceof JCCompilationUnit) {
                        lowerer.lower((JCCompilationUnit) unit);
                    }
                }
            }
        });
    }

    private static final class Lowerer {
        private final TreeMaker maker;
        private final Names names;
        private final Log log;
        private final CompilerProtocol.Session compilerSession;
        private final Set<JCClassDecl> processed = Collections.newSetFromMap(
                new IdentityHashMap<JCClassDecl, Boolean>());
        private boolean somaValueSeen;

        private Lowerer(
                TreeMaker maker,
                Names names,
                Log log,
                CompilerProtocol.Session compilerSession) {
            this.maker = maker;
            this.names = names;
            this.log = log;
            this.compilerSession = compilerSession;
        }

        private void lower(JCCompilationUnit unit) {
            for (JCTree definition : unit.defs) {
                if (definition instanceof JCClassDecl) {
                    lowerClass(unit, (JCClassDecl) definition, true);
                }
            }
        }

        private void lowerClass(
                JCCompilationUnit unit, JCClassDecl classDecl, boolean topLevel) {
            if (!processed.add(classDecl)) {
                return;
            }

            if (hasAnnotation(unit, classDecl.mods.annotations, SOMA_VALUE)) {
                somaValueSeen = true;
                if (!topLevel) {
                    fail(classDecl.pos, "SOMA-VALUE-001",
                            "@SomaValue must be a top-level class");
                } else {
                    lowerValue(unit, classDecl);
                }
            }

            for (JCTree definition : classDecl.defs) {
                definition.accept(new TreeScanner() {
                    @Override
                    public void visitClassDef(JCClassDecl nestedClass) {
                        lowerClass(unit, nestedClass, false);
                    }
                });
            }
        }

        private boolean hasSomaValue() {
            return somaValueSeen;
        }

        private boolean hasFailure() {
            return !compilerSession.getPluginDiagnostics().isEmpty();
        }

        private void lowerValue(JCCompilationUnit unit, JCClassDecl classDecl) {
            boolean valid = true;
            long classFlags = classDecl.mods.flags;
            if ((classFlags & Flags.PUBLIC) == 0L
                    || (classFlags & (Flags.ABSTRACT | Flags.INTERFACE | Flags.ENUM)) != 0L
                    || classDecl.extending != null
                    || !classDecl.implementing.isEmpty()
                    || !classDecl.typarams.isEmpty()) {
                fail(classDecl.pos, "SOMA-VALUE-001",
                        "@SomaValue must be a public non-generic concrete class without inheritance");
                valid = false;
            }

            ListBuffer<JCVariableDecl> fields = new ListBuffer<JCVariableDecl>();
            for (JCTree definition : classDecl.defs) {
                if (definition instanceof JCVariableDecl) {
                    JCVariableDecl field = (JCVariableDecl) definition;
                    if ((field.mods.flags & Flags.STATIC) != 0L) {
                        continue;
                    }
                    boolean schemaField =
                            hasAnnotation(unit, field.mods.annotations, SOMA_FIELD);
                    boolean ignored =
                            hasAnnotation(unit, field.mods.annotations, SOMA_IGNORE);
                    if (schemaField && ignored) {
                        fail(field.pos, "SOMA-VALUE-001",
                                "value field cannot be both @SomaField and @SomaIgnore");
                        valid = false;
                    } else if (ignored) {
                        fail(field.pos, "SOMA-VALUE-003",
                                "@SomaValue cannot declare non-static @SomaIgnore state");
                        valid = false;
                    } else if (schemaField) {
                        long forbidden = Flags.PRIVATE | Flags.PROTECTED | Flags.VOLATILE
                                | Flags.TRANSIENT | Flags.STATIC;
                        if ((field.mods.flags & forbidden) != 0L || field.init != null) {
                            fail(field.pos, "SOMA-VALUE-001",
                                    "@SomaField value field must not declare conflicting modifiers or initializer");
                            valid = false;
                        }
                        fields.append(field);
                    } else {
                        fail(field.pos, "SOMA-VALUE-001",
                                "value instance field must declare @SomaField");
                        valid = false;
                    }
                }
            }

            List<JCVariableDecl> fieldList = fields.toList();
            for (JCTree definition : classDecl.defs) {
                if (definition instanceof JCMethodDecl) {
                    JCMethodDecl method = (JCMethodDecl) definition;
                    if (conflictsWithGeneratedMember(method, fieldList)) {
                        fail(method.pos, "SOMA-VALUE-002",
                                "source member conflicts with canonical @SomaValue member: " + method.name);
                        valid = false;
                    }
                }
            }

            if (fields.isEmpty()) {
                fail(classDecl.pos, "SOMA-VALUE-001", "@SomaValue requires at least one @SomaField");
                valid = false;
            }
            if (!valid) {
                return;
            }

            classDecl.mods.flags |= Flags.FINAL;

            for (JCVariableDecl field : fieldList) {
                field.mods.flags |= Flags.PUBLIC | Flags.FINAL;
            }

            ListBuffer<JCTree> generated = new ListBuffer<JCTree>();
            generated.append(makeConstructor(classDecl, fieldList));
            generated.append(makeEquals(classDecl, fieldList));
            generated.append(makeHashCode(classDecl, fieldList));
            generated.append(makeToString(classDecl, fieldList));
            classDecl.defs = classDecl.defs.appendList(generated.toList());
        }

        private boolean conflictsWithGeneratedMember(JCMethodDecl method, List<JCVariableDecl> fields) {
            if (method.name == names.init) {
                if (method.params.size() != fields.size()) {
                    return false;
                }
                int index = 0;
                for (JCVariableDecl parameter : method.params) {
                    if (!simpleType(parameter.vartype.toString()).equals(
                            simpleType(fields.get(index).vartype.toString()))) {
                        return false;
                    }
                    index++;
                }
                return true;
            }
            String name = method.name.toString();
            if ("equals".equals(name) && method.params.size() == 1) {
                return "Object".equals(method.params.head.vartype.toString())
                        || "java.lang.Object".equals(method.params.head.vartype.toString());
            }
            return ("hashCode".equals(name) || "toString".equals(name)) && method.params.isEmpty();
        }

        private String simpleType(String type) {
            int separator = type.lastIndexOf('.');
            return separator < 0 ? type : type.substring(separator + 1);
        }

        private JCMethodDecl makeConstructor(JCClassDecl owner, List<JCVariableDecl> fields) {
            ListBuffer<JCVariableDecl> parameters = new ListBuffer<JCVariableDecl>();
            ListBuffer<JCStatement> statements = new ListBuffer<JCStatement>();
            for (JCVariableDecl field : fields) {
                JCVariableDecl parameter = maker.at(field.pos).VarDef(
                        maker.Modifiers(Flags.PARAMETER), field.name, field.vartype, null);
                parameters.append(parameter);
                JCExpression value = maker.Ident(field.name);
                if (primitiveTag(field) == null) {
                    value = staticCall(
                            "java.util.Objects",
                            "requireNonNull",
                            value,
                            maker.Literal(field.name.toString()));
                }
                statements.append(maker.Exec(maker.Assign(
                        maker.Select(maker.Ident(names._this), field.name),
                        value)));
            }
            JCBlock body = maker.Block(0L, statements.toList());
            return maker.at(owner.pos).MethodDef(
                    maker.Modifiers(Flags.PUBLIC), names.init, null,
                    List.nil(), parameters.toList(), List.nil(), body, null);
        }

        private JCMethodDecl makeEquals(JCClassDecl owner, List<JCVariableDecl> fields) {
            Name otherName = names.fromString("other");
            Name thatName = names.fromString("that");
            JCVariableDecl other = maker.VarDef(
                    maker.Modifiers(Flags.PARAMETER), otherName, qualified("java.lang.Object"), null);

            ListBuffer<JCStatement> statements = new ListBuffer<JCStatement>();
            statements.append(maker.If(
                    maker.Binary(JCTree.Tag.EQ, maker.Ident(names._this), maker.Ident(otherName)),
                    maker.Return(maker.Literal(true)), null));
            statements.append(maker.If(
                    maker.Unary(JCTree.Tag.NOT,
                            maker.TypeTest(maker.Ident(otherName), maker.Ident(owner.name))),
                    maker.Return(maker.Literal(false)), null));
            statements.append(maker.VarDef(
                    maker.Modifiers(0L), thatName, maker.Ident(owner.name),
                    maker.TypeCast(maker.Ident(owner.name), maker.Ident(otherName))));

            JCExpression equality = maker.Literal(true);
            for (JCVariableDecl field : fields) {
                equality = maker.Binary(JCTree.Tag.AND, equality,
                        equalityExpression(field, thatName));
            }
            statements.append(maker.Return(equality));

            return maker.at(owner.pos).MethodDef(
                    maker.Modifiers(Flags.PUBLIC), names.fromString("equals"),
                    maker.TypeIdent(TypeTag.BOOLEAN), List.nil(), List.of(other),
                    List.nil(), maker.Block(0L, statements.toList()), null);
        }

        private JCExpression equalityExpression(JCVariableDecl field, Name thatName) {
            JCExpression left = maker.Select(maker.Ident(names._this), field.name);
            JCExpression right = maker.Select(maker.Ident(thatName), field.name);
            TypeTag primitive = primitiveTag(field);
            if (primitive == TypeTag.FLOAT) {
                return maker.Binary(JCTree.Tag.EQ,
                        staticCall("java.lang.Float", "floatToIntBits", left),
                        staticCall("java.lang.Float", "floatToIntBits", right));
            }
            if (primitive == TypeTag.DOUBLE) {
                return maker.Binary(JCTree.Tag.EQ,
                        staticCall("java.lang.Double", "doubleToLongBits", left),
                        staticCall("java.lang.Double", "doubleToLongBits", right));
            }
            if (primitive != null) {
                return maker.Binary(JCTree.Tag.EQ, left, right);
            }
            return staticCall("java.util.Objects", "equals", left, right);
        }

        private JCMethodDecl makeHashCode(JCClassDecl owner, List<JCVariableDecl> fields) {
            Name resultName = names.fromString("result");
            ListBuffer<JCStatement> statements = new ListBuffer<JCStatement>();
            statements.append(maker.VarDef(
                    maker.Modifiers(0L), resultName, maker.TypeIdent(TypeTag.INT), maker.Literal(1)));
            for (JCVariableDecl field : fields) {
                JCExpression combined = maker.Binary(JCTree.Tag.PLUS,
                        maker.Binary(JCTree.Tag.MUL, maker.Literal(31), maker.Ident(resultName)),
                        hashExpression(field));
                statements.append(maker.Exec(maker.Assign(maker.Ident(resultName), combined)));
            }
            statements.append(maker.Return(maker.Ident(resultName)));
            return maker.at(owner.pos).MethodDef(
                    maker.Modifiers(Flags.PUBLIC), names.fromString("hashCode"),
                    maker.TypeIdent(TypeTag.INT), List.nil(), List.nil(), List.nil(),
                    maker.Block(0L, statements.toList()), null);
        }

        private JCExpression hashExpression(JCVariableDecl field) {
            JCExpression value = maker.Select(maker.Ident(names._this), field.name);
            TypeTag primitive = primitiveTag(field);
            if (primitive == TypeTag.BOOLEAN) {
                return maker.Conditional(value, maker.Literal(1231), maker.Literal(1237));
            }
            if (primitive == TypeTag.LONG) {
                JCExpression shifted = maker.Binary(JCTree.Tag.USR, value, maker.Literal(32));
                return maker.TypeCast(maker.TypeIdent(TypeTag.INT),
                        maker.Binary(JCTree.Tag.BITXOR, value, shifted));
            }
            if (primitive == TypeTag.FLOAT) {
                return staticCall("java.lang.Float", "floatToIntBits", value);
            }
            if (primitive == TypeTag.DOUBLE) {
                JCExpression bits = staticCall("java.lang.Double", "doubleToLongBits", value);
                return maker.TypeCast(maker.TypeIdent(TypeTag.INT),
                        maker.Binary(JCTree.Tag.BITXOR, bits,
                                maker.Binary(JCTree.Tag.USR, bits, maker.Literal(32))));
            }
            if (primitive != null) {
                return maker.TypeCast(maker.TypeIdent(TypeTag.INT), value);
            }
            JCExpression asObject = maker.TypeCast(qualified("java.lang.Object"), value);
            JCExpression isEnum = maker.TypeTest(asObject, qualified("java.lang.Enum"));
            JCExpression enumValue = maker.TypeCast(
                    qualified("java.lang.Enum"),
                    maker.TypeCast(qualified("java.lang.Object"), value));
            JCExpression enumOrdinal = maker.Apply(
                    List.nil(),
                    maker.Select(enumValue, names.fromString("ordinal")),
                    List.nil());
            JCExpression referenceHash = maker.Conditional(
                    isEnum,
                    enumOrdinal,
                    staticCall("java.util.Objects", "hashCode", value));
            return maker.Conditional(
                    maker.Binary(JCTree.Tag.EQ, value, maker.Literal(TypeTag.BOT, null)),
                    maker.Literal(0),
                    referenceHash);
        }

        private JCMethodDecl makeToString(JCClassDecl owner, List<JCVariableDecl> fields) {
            JCExpression expression = maker.Literal(owner.name.toString() + "{");
            int index = 0;
            for (JCVariableDecl field : fields) {
                String prefix = (index == 0 ? "" : ", ") + field.name + "=";
                expression = maker.Binary(JCTree.Tag.PLUS, expression, maker.Literal(prefix));
                expression = maker.Binary(JCTree.Tag.PLUS, expression,
                        maker.Select(maker.Ident(names._this), field.name));
                index++;
            }
            expression = maker.Binary(JCTree.Tag.PLUS, expression, maker.Literal("}"));
            return maker.at(owner.pos).MethodDef(
                    maker.Modifiers(Flags.PUBLIC), names.fromString("toString"),
                    qualified("java.lang.String"), List.nil(), List.nil(), List.nil(),
                    maker.Block(0L, List.<JCStatement>of(maker.Return(expression))), null);
        }

        private JCExpression staticCall(String owner, String method, JCExpression... arguments) {
            JCExpression target = maker.Select(qualified(owner), names.fromString(method));
            return maker.Apply(List.nil(), target, List.from(arguments));
        }

        private TypeTag primitiveTag(JCVariableDecl field) {
            if (field.vartype instanceof JCPrimitiveTypeTree) {
                return ((JCPrimitiveTypeTree) field.vartype).typetag;
            }
            return null;
        }

        private JCExpression qualified(String qualifiedName) {
            String[] parts = qualifiedName.split("\\.");
            JCExpression expression = maker.Ident(names.fromString(parts[0]));
            for (int i = 1; i < parts.length; i++) {
                expression = maker.Select(expression, names.fromString(parts[i]));
            }
            return expression;
        }

        private boolean hasAnnotation(
                JCCompilationUnit unit, List<JCAnnotation> annotations, String qualifiedName) {
            String simpleName = qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
            for (JCAnnotation annotation : annotations) {
                String name = annotation.annotationType.toString();
                if (name.equals(qualifiedName)) {
                    return true;
                }
                if (!name.equals(simpleName)) {
                    continue;
                }

                boolean exactImport = false;
                boolean wildcardImport = false;
                String annotationPackage = qualifiedName.substring(
                        0, qualifiedName.length() - simpleName.length() - 1);
                for (JCImport importTree : unit.getImports()) {
                    if (importTree.staticImport) {
                        continue;
                    }
                    String imported = importTree.qualid.toString();
                    if (imported.equals(qualifiedName)) {
                        exactImport = true;
                    } else if (imported.equals(annotationPackage + ".*")) {
                        wildcardImport = true;
                    }
                }
                if (exactImport) {
                    return true;
                }
                if (wildcardImport) {
                    fail(annotation.pos, "SOMA-COMP-006",
                            "SOMA compiler annotations require an exact import or FQN: "
                                    + qualifiedName);
                }
            }
            return false;
        }

        private void fail(int position, String code, String message) {
            String diagnostic = "[" + code + "] " + message;
            compilerSession.reportPluginDiagnostic(diagnostic);
        }
    }
}
