package by.zoomos_v2.service;

import by.zoomos_v2.repository.ClientRepository;
import by.zoomos_v2.util.PathResolver;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Сервис для расчета и кэширования информации о дисковом пространстве
 * с добавленной отладочной информацией
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiskSpaceService {

    private final PathResolver pathResolver;
    private final ClientRepository clientRepository;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    // Кэшированные данные
    private ConcurrentHashMap<String, Object> diskInfoCache = new ConcurrentHashMap<>();
    private AtomicBoolean isUpdating = new AtomicBoolean(false);

    @PostConstruct
    public void init() {
        // Запускаем первоначальное вычисление дискового пространства
        log.info("Инициализация DiskSpaceService, запуск первоначального вычисления");
        updateDiskInfoAsync();
    }

    /**
     * Регулярное обновление информации о дисковом пространстве (каждые 15 минут)
     */
    @Scheduled(fixedRate = 900000) // 15 минут
    public void scheduledDiskUpdate() {
        log.info("Запуск планового обновления информации о дисковом пространстве");
        updateDiskInfoAsync();
    }

    /**
     * Асинхронное обновление информации о дисковом пространстве
     */
    @Async
    public CompletableFuture<Void> updateDiskInfoAsync() {
        // Если обновление уже идет, не запускаем новое
        if (isUpdating.compareAndSet(false, true)) {
            log.info("Старт асинхронного обновления информации о дисковом пространстве");
            return CompletableFuture.runAsync(() -> {
                try {
                    updateDiskInfo();
                } catch (Exception e) {
                    log.error("Ошибка при обновлении информации о дисковом пространстве", e);
                    // Сохраняем информацию об ошибке в кэш
                    diskInfoCache.put("diskError", "Ошибка вычисления: " + e.getMessage());
                } finally {
                    isUpdating.set(false);
                    log.info("Завершено асинхронное обновление информации о дисковом пространстве");
                }
            });
        }
        log.info("Пропуск асинхронного обновления - уже выполняется");
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Обновление информации о дисковом пространстве
     */
    private void updateDiskInfo() {
        log.info("Начало обновления информации о дисковом пространстве");
        long startTime = System.currentTimeMillis();

        try {
            // Получаем все ID клиентов из репозитория
            List<Long> clientIds = clientRepository.findAllIds();
            log.info("Получено {} ID клиентов", clientIds.size());

            // Получаем путь к базовой директории
            Path uploadDirectory = null;
            try {
                uploadDirectory = pathResolver.getUploadDirectory();
                log.info("Получен путь к базовой директории: {}", uploadDirectory);
            } catch (Exception e) {
                log.error("Ошибка при получении пути к базовой директории: {}", e.getMessage());
                diskInfoCache.put("diskError", "Ошибка при получении пути к директории: " + e.getMessage());
                return;
            }

            if (uploadDirectory == null) {
                log.error("Путь к базовой директории не определен");
                diskInfoCache.put("diskError", "Путь к базовой директории не определен");
                return;
            }

            // Получаем File из Path
            File baseDirectory = uploadDirectory.toFile();

            // Проверяем существование и доступность директории
            if (!baseDirectory.exists()) {
                log.error("Базовая директория не существует: {}", baseDirectory.getAbsolutePath());
                diskInfoCache.put("diskError", "Базовая директория не существует: " + baseDirectory.getAbsolutePath());
                return;
            }

            if (!baseDirectory.canRead()) {
                log.error("Нет прав на чтение базовой директории: {}", baseDirectory.getAbsolutePath());
                diskInfoCache.put("diskError", "Нет прав на чтение базовой директории");
                return;
            }

            // Общая информация о диске
            long totalSpace = baseDirectory.getTotalSpace();
            long usableSpace = baseDirectory.getUsableSpace();
            long usedSpace = totalSpace - usableSpace;

            log.info("Общая информация о диске: всего={}, использовано={}, свободно={}",
                    formatSize(totalSpace), formatSize(usedSpace), formatSize(usableSpace));

            diskInfoCache.put("totalDiskSpace", formatSize(totalSpace));
            diskInfoCache.put("usedDiskSpace", formatSize(usedSpace));
            diskInfoCache.put("freeDiskSpace", formatSize(usableSpace));
            diskInfoCache.put("diskUsagePercentage", calculatePercentage(usedSpace, totalSpace));

            // Сохраняем абсолютные значения для возможных вычислений
            diskInfoCache.put("totalDiskSpaceBytes", totalSpace);
            diskInfoCache.put("usedDiskSpaceBytes", usedSpace);
            diskInfoCache.put("freeDiskSpaceBytes", usableSpace);

            // Суммарные показатели для файлов клиентов
            long totalClientsSize = 0;
            int totalFilesCount = 0;

            // Информация по клиентам
            ConcurrentHashMap<Long, Object> clientsInfo = new ConcurrentHashMap<>();

            log.info("Начало обхода директорий клиентов");

            // Подсчитываем для каждого клиента
            for (Long clientId : clientIds) {
                try {
                    Path clientDir = pathResolver.getClientDirectory(clientId);
                    log.debug("Обработка директории клиента {}: {}", clientId, clientDir);

                    if (Files.exists(clientDir)) {
                        ClientDiskInfo info = calculateClientDiskInfo(clientDir);

                        ConcurrentHashMap<String, Object> clientData = new ConcurrentHashMap<>();
                        clientData.put("size", info.getTotalSize());
                        clientData.put("sizeFormatted", formatSize(info.getTotalSize()));
                        clientData.put("filesCount", info.getFilesCount());

                        clientsInfo.put(clientId, clientData);

                        totalClientsSize += info.getTotalSize();
                        totalFilesCount += info.getFilesCount();

                        log.debug("Клиент {}: размер={}, файлов={}",
                                clientId, formatSize(info.getTotalSize()), info.getFilesCount());
                    } else {
                        log.debug("Директория клиента {} не существует: {}", clientId, clientDir);
                    }
                } catch (Exception e) {
                    log.error("Ошибка при обработке директории клиента {}: {}", clientId, e.getMessage());
                }
            }

            log.info("Завершен обход директорий клиентов. Общий размер: {}, файлов: {}",
                    formatSize(totalClientsSize), totalFilesCount);

            // Сохраняем в кэш информацию о файлах клиентов
            diskInfoCache.put("clientsDataSize", formatSize(totalClientsSize));
            diskInfoCache.put("clientsDataSizeBytes", totalClientsSize);
            diskInfoCache.put("clientsFileCount", totalFilesCount);
            diskInfoCache.put("clientsInfo", clientsInfo);

            // Рассчитываем процент от общего дискового пространства
            double clientsDataPercentage = calculatePercentage(totalClientsSize, totalSpace);
            diskInfoCache.put("clientsDataPercentage", clientsDataPercentage);

            // Сохраняем время последнего обновления
            LocalDateTime now = LocalDateTime.now();
            diskInfoCache.put("lastUpdated", now);
            diskInfoCache.put("lastUpdatedFormatted", dateFormatter.format(now));

            // Сохраняем путь к базовой директории
            diskInfoCache.put("clientsDataPath", baseDirectory.getAbsolutePath());

            // Удаляем флаг ошибки, если он был
            diskInfoCache.remove("diskError");

            long endTime = System.currentTimeMillis();
            log.info("Обновление информации о дисковом пространстве завершено за {} мс", (endTime - startTime));

        } catch (Exception e) {
            log.error("Необработанная ошибка при вычислении дискового пространства", e);
            // Сохраняем информацию об ошибке
            diskInfoCache.put("diskError", "Ошибка: " + e.getMessage());
        }
    }

    /**
     * Получить кэшированную информацию о дисковом пространстве
     */
    public ConcurrentHashMap<String, Object> getDiskInfo() {
        if (diskInfoCache.isEmpty()) {
            // Если кэш пуст, добавляем статус обновления
            log.info("Запрос getDiskInfo() при пустом кэше");
            diskInfoCache.put("status", "updating");

            // Запускаем асинхронное обновление, если оно еще не запущено
            if (!isUpdating.get()) {
                updateDiskInfoAsync();
            }
        }
        return new ConcurrentHashMap<>(diskInfoCache);
    }

    /**
     * Принудительно обновить информацию о дисковом пространстве
     */
    public ConcurrentHashMap<String, Object> forceUpdateDiskInfo() {
        log.info("Запрос forceUpdateDiskInfo()");

        if (isUpdating.compareAndSet(false, true)) {
            try {
                log.info("Выполняется принудительное синхронное обновление");
                updateDiskInfo();
            } finally {
                isUpdating.set(false);
            }
        } else {
            log.info("Принудительное обновление не выполнено - уже выполняется");
            diskInfoCache.put("status", "updating");
        }

        return new ConcurrentHashMap<>(diskInfoCache);
    }

    /**
     * Рассчитывает информацию о дисковом пространстве для клиента
     * с дополнительной обработкой исключений
     */
    private ClientDiskInfo calculateClientDiskInfo(Path clientDir) {
        long totalSize = 0;
        int filesCount = 0;

        try {
            if (!Files.exists(clientDir)) {
                log.debug("Директория клиента не существует: {}", clientDir);
                return new ClientDiskInfo(0, 0);
            }

            if (!Files.isReadable(clientDir)) {
                log.warn("Директория клиента не доступна для чтения: {}", clientDir);
                return new ClientDiskInfo(0, 0);
            }

            // Пытаемся использовать Files.walk для обхода всех файлов в директории
            try (Stream<Path> pathStream = Files.walk(clientDir)) {
                List<Path> files = pathStream
                        .filter(path -> {
                            try {
                                return Files.isRegularFile(path) && Files.isReadable(path);
                            } catch (Exception e) {
                                log.debug("Ошибка при проверке файла {}: {}", path, e.getMessage());
                                return false;
                            }
                        })
                        .collect(Collectors.toList());

                for (Path file : files) {
                    try {
                        totalSize += Files.size(file);
                        filesCount++;
                    } catch (IOException e) {
                        log.debug("Ошибка при получении размера файла {}: {}", file, e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            log.error("Ошибка при обходе директории {}: {}", clientDir, e.getMessage());
        }

        return new ClientDiskInfo(totalSize, filesCount);
    }

    /**
     * Вычисляет процентное отношение
     */
    private double calculatePercentage(long part, long total) {
        if (total <= 0) return 0;
        return Math.round((double) part / total * 10000) / 100.0;
    }

    /**
     * Форматирует размер в байтах в читаемый формат
     */
    private String formatSize(long size) {
        if (size <= 0) return "0 B";

        final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));

        // Ограничиваем индекс в пределах массива
        digitGroups = Math.min(digitGroups, units.length - 1);

        // Округляем до 2 знаков после запятой
        double formattedSize = Math.round((size / Math.pow(1024, digitGroups)) * 100) / 100.0;

        return formattedSize + " " + units[digitGroups];
    }

    /**
     * Класс для хранения информации о дисковом пространстве клиента
     */
    private static class ClientDiskInfo {
        private final long totalSize;
        private final int filesCount;

        public ClientDiskInfo(long totalSize, int filesCount) {
            this.totalSize = totalSize;
            this.filesCount = filesCount;
        }

        public long getTotalSize() {
            return totalSize;
        }

        public int getFilesCount() {
            return filesCount;
        }
    }
}