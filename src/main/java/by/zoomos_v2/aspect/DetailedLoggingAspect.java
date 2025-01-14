package by.zoomos_v2.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;


@Slf4j
@Aspect
@Component
public class DetailedLoggingAspect {
    @Around("@annotation(LogExecution)")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        String className = methodSignature.getDeclaringType().getSimpleName();
        String methodName = methodSignature.getName();
        LogExecution logExecution = methodSignature.getMethod().getAnnotation(LogExecution.class);

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        log.info("Starting {} - {}:{}",
                logExecution.value().isEmpty() ? "method" : logExecution.value(),
                className,
                methodName);

        try {
            Object result = joinPoint.proceed();
            stopWatch.stop();

            log.info("Completed {} - {}:{} in {} ms",
                    logExecution.value().isEmpty() ? "method" : logExecution.value(),
                    className,
                    methodName,
                    stopWatch.getTotalTimeMillis());

            return result;
        } catch (Exception e) {
            stopWatch.stop();
            log.error("Failed {} - {}:{} in {} ms. Error: {}",
                    logExecution.value().isEmpty() ? "method" : logExecution.value(),
                    className,
                    methodName,
                    stopWatch.getTotalTimeMillis(),
                    e.getMessage(),
                    e);
            throw e;
        }
    }
}
