/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.executors;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static java.util.logging.Level.FINE;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.executors.ExecutorAdviceHelper;
import io.opentelemetry.javaagent.bootstrap.executors.PropagatedContext;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.Future;
import java.util.logging.Logger;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.context.Context;

public class FutureInstrumentation implements TypeInstrumentation {
  private static final Logger logger = Logger.getLogger(FutureInstrumentation.class.getName());

  /**
   * Only apply executor instrumentation to allowed executors. In the future, this restriction may
   * be lifted to include all executors.
   */
  private static final Collection<String> ALLOWED_FUTURES;

  static {
    String[] allowed = {
      "akka.dispatch.forkjoin.ForkJoinTask",
      "akka.dispatch.forkjoin.ForkJoinTask$AdaptedCallable",
      "akka.dispatch.forkjoin.ForkJoinTask$AdaptedRunnable",
      "akka.dispatch.forkjoin.ForkJoinTask$AdaptedRunnableAction",
      "akka.dispatch.ForkJoinExecutorConfigurator$AkkaForkJoinTask",
      "akka.dispatch.Mailbox",
      "com.google.common.util.concurrent.AbstractFuture",
      "com.google.common.util.concurrent.AbstractFuture$TrustedFuture",
      "com.google.common.util.concurrent.ListenableFutureTask",
      "com.google.common.util.concurrent.SettableFuture",
      "io.netty.util.concurrent.CompleteFuture",
      "io.netty.util.concurrent.FailedFuture",
      "io.netty.util.concurrent.ScheduledFutureTask",
      "java.util.concurrent.CompletableFuture$BiApply",
      "java.util.concurrent.CompletableFuture$BiCompletion",
      "java.util.concurrent.CompletableFuture$BiRelay",
      "java.util.concurrent.CompletableFuture$ThreadPerTaskExecutor",
      "java.util.concurrent.CountedCompleter",
      "java.util.concurrent.ExecutorCompletionService$QueueingFuture",
      "java.util.concurrent.ForkJoinTask",
      "java.util.concurrent.ForkJoinTask$AdaptedCallable",
      "java.util.concurrent.ForkJoinTask$RunnableExecuteAction",
      "java.util.concurrent.FutureTask",
      "java.util.concurrent.RecursiveAction",
      "java.util.concurrent.ScheduledThreadPoolExecutor$ScheduledFutureTask",
      "scala.collection.parallel.AdaptiveWorkStealingForkJoinTasks$WrappedTask",
      "scala.concurrent.forkjoin.ForkJoinTask",
      "scala.concurrent.forkjoin.ForkJoinTask$AdaptedCallable",
      "scala.concurrent.forkjoin.ForkJoinTask$AdaptedRunnable",
      "scala.concurrent.forkjoin.ForkJoinTask$AdaptedRunnableAction",
      "scala.concurrent.impl.ExecutionContextImpl$AdaptedForkJoinTask",
    };
    ALLOWED_FUTURES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(allowed)));
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    ElementMatcher.Junction<TypeDescription> hasFutureInterfaceMatcher =
        implementsInterface(named(Future.class.getName()));
    return new ElementMatcher.Junction.AbstractBase<TypeDescription>() {
      @Override
      public boolean matches(TypeDescription target) {
        boolean allowed = ALLOWED_FUTURES.contains(target.getName());
        if (!allowed && hasFutureInterfaceMatcher.matches(target)) {
          logger.log(FINE, "Skipping future instrumentation for {0}", target.getName());
        }
        return allowed;
      }
    }.and(hasFutureInterfaceMatcher); // Apply expensive matcher last.
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("cancel").and(returns(boolean.class)),
        FutureInstrumentation.class.getName() + "$CanceledFutureAdvice");
    transformer.applyAdviceToMethod(
        named("get").and(takesArguments(0)),
        FutureInstrumentation.class.getName() + "$FutureGetAdvice");
  }

  @SuppressWarnings("unused")
  public static class CanceledFutureAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void exit(@Advice.This Future<?> future) {
      // Try to clear parent span even if future was not cancelled:
      // the expectation is that parent span should be cleared after 'cancel'
      // is called, one way or another
      VirtualField<Future<?>, PropagatedContext> virtualField =
          VirtualField.find(Future.class, PropagatedContext.class);
      ExecutorAdviceHelper.cleanPropagatedContext(virtualField, future);
    }
  }

  public static class EndParentInfo{
    public String epSpanID;
    public EndParentInfo(String id){
      this.epSpanID = id;
    }
  }

  public static class FutureGetAdvice {

    public static final Instrumenter<Future<?>, Void> INSTRUMENTER =
      FutureGetInstrumenter.instrumenter();
    @Advice.OnMethodEnter
    public static Object enter(@Advice.This Future<?> future) {
      try{
          VirtualField<Future<?>,EndParentInfo> virtualField = VirtualField.find(Future.class, EndParentInfo.class);
          EndParentInfo epinfo = new EndParentInfo(Java8BytecodeBridge.currentSpan().getSpanContext().getSpanId());
          System.out.println("$$ " + epinfo.epSpanID +"==>" + future);
          virtualField.set(future, epinfo);
      }
      catch(Throwable e){
        System.out.println("!!! " + e);
      }
      return null;
      // Context parent = Java8BytecodeBridge.currentContext();
      // if(parent == null){
      //   return null;
      // }
      // // return null;
      // Instrumenter<Future<?>, Void> inst = FutureGetInstrumenter.instrumenter();
      // if (!inst.shouldStart(parent, future)) {
      //   return null;
      // }

      // Context ctx = inst.start(parent, future);
      // Scope scope = ctx.makeCurrent();
      // return new Object[]{scope,ctx};

    }
    @Advice.OnMethodExit
    public static void exit(
        // @Advice.Enter Object states,
        // @Advice.Thrown Throwable error,
        @Advice.This Future<?> future
        ) {
        // if (states == null) {
        //   return;
        // }
        // Object[] state = (Object[]) states;
        // try {
        //   FutureGetInstrumenter.instrumenter().end((Context)state[1], future, null, error);
        // } finally {
        //   ((Scope)state[0]).close();
        // }

    }
  }
}
