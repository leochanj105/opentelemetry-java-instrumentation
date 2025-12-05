
package io.opentelemetry.javaagent.instrumentation.executors;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.code.CodeAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.code.CodeAttributesGetter;
import io.opentelemetry.instrumentation.api.instrumenter.code.CodeSpanNameExtractor;
import java.util.concurrent.Future;
public final class FutureGetInstrumenter {
  private static final String INSTRUMENTATION_NAME = "future-get";

  private static final Instrumenter<Future<?>, Void> INSTRUMENTER;

  static {
      CodeAttributesGetter<Future<?>> codeGetter = new CodeAttributesGetter<Future<?>>() {
        @Override
        public Class<?> getCodeClass(Future<?> runnable) {
          return runnable.getClass();
        }

        @Override
        public String getMethodName(Future<?> runnable) {
          return "get";
        }
      };

    INSTRUMENTER =
        Instrumenter.<Future<?>, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                CodeSpanNameExtractor.create(codeGetter))
            .addAttributesExtractor(CodeAttributesExtractor.create(codeGetter))
            .buildInstrumenter(SpanKindExtractor.alwaysInternal());
  }

  public static Instrumenter<Future<?>, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private FutureGetInstrumenter() {}

}
