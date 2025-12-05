/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.executors;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.executors.PropagatedContext;
import io.opentelemetry.javaagent.bootstrap.executors.TaskAdviceHelper;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.context.Context;
public class RunnableInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named(Runnable.class.getName()));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("run").and(takesArguments(0)).and(isPublic()),
        RunnableInstrumentation.class.getName() + "$RunnableAdvice");
  }

  @SuppressWarnings("unused")
  public static class RunnableAdvice {
    public static final Instrumenter<Runnable, Void> INSTRUMENTER =
      AsyncRunnableInstrumenter.instrumenter();

    @Advice.OnMethodEnter//(suppress = false)
    public static Object enter(@Advice.This Runnable thiz) {
      VirtualField<Runnable, PropagatedContext> virtualField =
          VirtualField.find(Runnable.class, PropagatedContext.class);

      Scope ctx = TaskAdviceHelper.makePropagatedContextCurrent(virtualField, thiz);

      // System.out.println(Thread.currentThread().getName()+": !!! In run() " + thiz +", " + Java8BytecodeBridge.currentSpan());
      // return ctx;
      if(ctx == null) {return null;}
      System.out.println("scope: " + ctx);
      Context parentContext = Java8BytecodeBridge.currentContext();
      if (!INSTRUMENTER.shouldStart(parentContext, thiz)) {
        return null;
      }
      Context newer = INSTRUMENTER.start(parentContext, thiz);
      System.out.println("NEWER:"+newer);
      return new Object[]{parentContext,newer.makeCurrent(),ctx,newer};

      // PropagatedContext ctx = virtualField.get(thiz);
      // if (ctx == null) return null;
      // Context parentContext = ctx.getAndClear();
      // return parentContext.makeCurrent();
      // return Java8BytecodeBridge.currentSpan()
      //   .spanBuilder("async-task")
      //   .startSpan().makeCurrent();
      // span.makeCurrent();
      // return span;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Enter Object states, 
        @Advice.Thrown Throwable error,
        @Advice.This Runnable thiz) {
      if (states == null) return;
      Object[] state = (Object[]) states;
      Context context = (Context) state[0];
      Scope scope = (Scope) state[1];

      try {
          INSTRUMENTER.end((Context)state[3], thiz, null, error);
      } finally {
          scope.close();
          ((Scope)state[2]).close();
      }
    }
  }
}
