package io.github.somaruntime.soma;

/** SOMA operation 抛出的稳定 structured failure。 */
public final class SomaOperationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final SomaFailureCode code;
    private final SomaOperation operation;
    private final String context;

    private SomaOperationException(
            SomaFailureCode code,
            SomaOperation operation,
            String context,
            Throwable cause) {
        super(context, cause);
        this.code = code;
        this.operation = operation;
        this.context = context;
    }

    static SomaOperationException create(
            SomaFailureCode code,
            SomaOperation operation,
            String context,
            Throwable cause) {
        return new SomaOperationException(code, operation, context, cause);
    }

    /** 返回稳定的 failure code。 */
    public SomaFailureCode code() {
        return code;
    }

    /** 返回拥有该 failure 的公开 operation。 */
    public SomaOperation operation() {
        return operation;
    }

    /** 返回已经净化的诊断上下文；application 不得把它解析为协议。 */
    public String context() {
        return context;
    }

    /** 如果存在，返回可安全保留的原始 cause。 */
    @Override
    public synchronized Throwable getCause() {
        return super.getCause();
    }
}
