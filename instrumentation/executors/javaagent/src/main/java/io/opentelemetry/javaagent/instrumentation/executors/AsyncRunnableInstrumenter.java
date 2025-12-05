package io.opentelemetry.javaagent.instrumentation.executors;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.code.CodeAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.code.CodeAttributesGetter;
import io.opentelemetry.instrumentation.api.instrumenter.code.CodeSpanNameExtractor;

public class AsyncRunnableInstrumenter {

  private static final String INSTRUMENTATION_NAME = "async";

  private static final Instrumenter<Runnable, Void> INSTRUMENTER;

  static {
      CodeAttributesGetter<Runnable> codeGetter = new CodeAttributesGetter<Runnable>() {
        @Override
        public Class<?> getCodeClass(Runnable runnable) {
          return runnable.getClass();
        }

        @Override
        public String getMethodName(Runnable runnable) {
          return "run";
        }
      };

    INSTRUMENTER =
        Instrumenter.<Runnable, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                CodeSpanNameExtractor.create(codeGetter))
            .addAttributesExtractor(CodeAttributesExtractor.create(codeGetter))
            .buildInstrumenter(SpanKindExtractor.alwaysInternal());
  }

  public static Instrumenter<Runnable, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private AsyncRunnableInstrumenter() {}
}
