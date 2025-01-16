package by.zoomos_v2.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.util.Arrays;

/**
 * Аспект для автоматического логирования всех методов в контроллерах и сервисах.
 * Использует Spring AOP для перехвата вызовов методов и логирования их выполнения.
 * <p>
 * Возможности:
 * - Логирование входа в метод с параметрами
 * - Логирование успешного завершения с результатом
 * - Логирование ошибок с полным стектрейсом
 * - Измерение времени выполнения методов
 */
@Slf4j
@Aspect
@Component
public class LoggingAspect {

    /**
     * Pointcut для всех методов в классах с аннотацией @RestController
     */
    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *)")
    public void restControllerPointcut() {
    }

    /**
     * Pointcut для всех методов в классах с аннотацией @Service
     */
    @Pointcut("within(@org.springframework.stereotype.Service *)")
    public void servicePointcut() {
    }

    /**
     * Метод, выполняющий логирование вызовов методов в контроллерах и сервисах
     *
     * @param joinPoint точка выполнения метода
     * @return результат выполнения метода
     * @throws Throwable если произошла ошибка при выполнении метода
     */
    @Around("restControllerPointcut() || servicePointcut()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        String className = methodSignature.getDeclaringType().getSimpleName();
        String methodName = methodSignature.getName();

        // Создаем StopWatch для измерения времени выполнения
        StopWatch stopWatch = new StopWatch();

        log.debug("Entering method [{}:{}] with arguments {}",
                className,
                methodName,
                Arrays.toString(joinPoint.getArgs()));

        try {
            stopWatch.start();
            Object result = joinPoint.proceed();
            stopWatch.stop();

            log.debug("Method [{}:{}] completed in {} ms with result: {}",
                    className,
                    methodName,
                    stopWatch.getTotalTimeMillis(),
                    result);

            return result;
        } catch (Exception e) {
            log.error("Exception in [{}:{}] with cause = '{}' and message = '{}'",
                    className,
                    methodName,
                    e.getCause() != null ? e.getCause() : "NULL",
                    e.getMessage());
            throw e;
        }
    }
}