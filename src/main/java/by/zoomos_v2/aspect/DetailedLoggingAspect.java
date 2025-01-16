package by.zoomos_v2.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;


@Slf4j
@Aspect
@Component
public class DetailedLoggingAspect {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Логирует время выполнения и детали методов с аннотацией @LogExecution
     */
    @Around("@annotation(LogExecution)")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        String className = methodSignature.getDeclaringType().getSimpleName();
        String methodName = methodSignature.getName();
        LogExecution logExecution = methodSignature.getMethod().getAnnotation(LogExecution.class);

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        // Добавляем логирование параметров метода
        logMethodParameters(joinPoint, methodSignature);

        log.info("[{}] Starting {} - {}:{}",
                LocalDateTime.now().format(FORMATTER),
                logExecution.value().isEmpty() ? "method" : logExecution.value(),
                className,
                methodName);

        try {
            Object result = joinPoint.proceed();
            stopWatch.stop();

            // Добавляем логирование результата
            logMethodResult(result, methodName);

            log.info("[{}] Completed {} - {}:{} in {} ms",
                    LocalDateTime.now().format(FORMATTER),
                    logExecution.value().isEmpty() ? "method" : logExecution.value(),
                    className,
                    methodName,
                    stopWatch.getTotalTimeMillis());

            return result;
        } catch (Exception e) {
            stopWatch.stop();
            log.error("[{}] Failed {} - {}:{} in {} ms. Error: {}",
                    LocalDateTime.now().format(FORMATTER),
                    logExecution.value().isEmpty() ? "method" : logExecution.value(),
                    className,
                    methodName,
                    stopWatch.getTotalTimeMillis(),
                    e.getMessage(),
                    e);
            throw e;
        }
    }

    /**
     * Логирует параметры метода
     */
    private void logMethodParameters(ProceedingJoinPoint joinPoint, MethodSignature methodSignature) {
        String[] parameterNames = methodSignature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        if (parameterNames != null && parameterNames.length > 0) {
            StringBuilder params = new StringBuilder("Method parameters: ");
            for (int i = 0; i < parameterNames.length; i++) {
                params.append(parameterNames[i])
                        .append("=")
                        .append(formatParameter(args[i]))
                        .append(", ");
            }
            log.debug(params.substring(0, params.length() - 2));
        }
    }

    /**
     * Логирует результат выполнения метода
     */
    private void logMethodResult(Object result, String methodName) {
        if (result != null) {
            log.debug("Method {} returned: {}", methodName, formatParameter(result));
        }
    }

    /**
     * Форматирует параметр для логирования
     */
    private String formatParameter(Object param) {
        if (param == null) {
            return "null";
        }
        // Для файлов логируем только имя и размер
        if (param instanceof MultipartFile) {
            MultipartFile file = (MultipartFile) param;
            return String.format("File(name=%s, size=%d bytes)",
                    file.getOriginalFilename(), file.getSize());
        }
        // Для коллекций логируем размер
        if (param instanceof Collection) {
            return String.format("Collection(size=%d)", ((Collection<?>) param).size());
        }
        return param.toString();
    }
}
