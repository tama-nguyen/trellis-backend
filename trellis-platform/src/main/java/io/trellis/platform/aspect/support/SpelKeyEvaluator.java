package io.trellis.platform.aspect.support;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

/**
 * Evaluates the SpEL keys of AOP annotations ({@code key = "#cmd.notificationId()"}). Parsed
 * expressions are cached per expression string.
 *
 * <p>Variables: every method parameter by name (needs {@code -parameters}, set by the build),
 * {@code #p0..#pN}, and {@code #result} where the aspect has one ({@code @Audited}). The evaluation
 * context is {@link SimpleEvaluationContext}: no type references, constructors or bean lookups, so
 * an annotation cannot be turned into code execution.
 */
public final class SpelKeyEvaluator {
  public static final String RESULT_VARIABLE = "result";
  private static final String POSITIONAL_PREFIX = "p";

  private final ExpressionParser parser = new SpelExpressionParser();
  private final Map<String, Expression> cache = new ConcurrentHashMap<>();

  public String evaluate(String expression, Method method, Object[] args) {
    return evaluate(expression, method, args, Map.of());
  }

  public String evaluate(
      String expression, Method method, Object[] args, Map<String, Object> extraVariables) {
    Expression parsed = cache.computeIfAbsent(expression, parser::parseExpression);
    EvaluationContext context =
        SimpleEvaluationContext.forReadOnlyDataBinding().withInstanceMethods().build();
    var parameters = method.getParameters();
    for (int i = 0; i < args.length; i++) {
      context.setVariable(POSITIONAL_PREFIX + i, args[i]);
      if (i < parameters.length && parameters[i].isNamePresent()) {
        context.setVariable(parameters[i].getName(), args[i]);
      }
    }
    extraVariables.forEach(context::setVariable);
    Object value = parsed.getValue(context);
    return value == null ? null : String.valueOf(value);
  }
}
