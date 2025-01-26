package by.zoomos_v2.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Конфигурация для асинхронной обработки файлов.
 * Настраивает пул потоков для выполнения асинхронных задач.
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "fileProcessingExecutor")
    public Executor fileProcessingExecutor() {
        log.info("Инициализация пула потоков для обработки файлов");

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Runtime.getRuntime().availableProcessors());
        executor.setMaxPoolSize(Runtime.getRuntime().availableProcessors() * 2);
        executor.setQueueCapacity(250);
        executor.setThreadNamePrefix("file-proc-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        // Мониторинг состояния пула
        executor.setThreadFactory(r -> {
            Thread t = new Thread(r);
            t.setUncaughtExceptionHandler((thread, ex) ->
                    log.error("Необработанное исключение в потоке {}: {}",
                            thread.getName(), ex.getMessage(), ex));
            return t;
        });

        executor.initialize();
        return executor;
    }
}
