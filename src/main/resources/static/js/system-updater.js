/**
 * Полный скрипт для обновления информации о системных ресурсах
 * Добавьте этот код перед закрывающим тегом </body>
 */
document.addEventListener('DOMContentLoaded', function() {
    // Инициализация обработчиков событий
    initSystemResourcesUpdater();

    // Запуск первого обновления
    updateSystemResources(false);

    // Установка интервала обновления (каждые 60 секунд)
    setInterval(function() {
        updateSystemResources(false);
    }, 60000);
});

/**
 * Инициализирует обработчики событий
 */
function initSystemResourcesUpdater() {
    // Обработчик для кнопки обновления системных ресурсов
    const refreshSystemBtn = document.getElementById('refresh-system-btn');
    if (refreshSystemBtn) {
        refreshSystemBtn.addEventListener('click', function() {
            updateSystemResources(true);
        });
    }
}

/**
 * Обновляет всю информацию о системных ресурсах
 * @param {boolean} forceUpdate - флаг принудительного обновления
 */
function updateSystemResources(forceUpdate = false) {
    // Показываем индикатор загрузки
    if (forceUpdate) {
        const refreshBtn = document.getElementById('refresh-system-btn');
        if (refreshBtn) {
            const icon = refreshBtn.querySelector('i');
            if (icon) {
                icon.className = 'fas fa-spinner fa-spin';
            }
            refreshBtn.disabled = true;
        }
    }

    // Показываем индикатор загрузки данных о диске
    showDiskLoadingIndicator();

    // Запрос к API
    fetch(`/api/system/resources?forceUpdate=${forceUpdate ? 'true' : 'false'}`)
        .then(response => {
            if (!response.ok) {
                throw new Error(`Ошибка HTTP: ${response.status}`);
            }
            return response.json();
        })
        .then(data => {
            console.log('Получены данные о системных ресурсах:', data);

            // Обновляем информацию о CPU
            updateCpuInfo(data);

            // Обновляем информацию о памяти
            updateMemoryInfo(data);

            // Обновляем информацию о диске
            updateDiskInfo(data);

            // Обновляем время последнего обновления
            updateLastUpdatedTime(data);

            // Убираем индикатор загрузки
            hideDiskLoadingIndicator();
        })
        .catch(error => {
            console.error('Ошибка при получении данных о системных ресурсах:', error);
            showErrorMessage(error.message);
            hideDiskLoadingIndicator();
        })
        .finally(() => {
            // Возвращаем кнопку в исходное состояние
            if (forceUpdate) {
                const refreshBtn = document.getElementById('refresh-system-btn');
                if (refreshBtn) {
                    const icon = refreshBtn.querySelector('i');
                    if (icon) {
                        icon.className = 'fas fa-sync-alt';
                    }
                    refreshBtn.disabled = false;
                }
            }
        });
}

/**
 * Показывает индикатор загрузки данных о дисковом пространстве
 */
function showDiskLoadingIndicator() {
    // Находим область сообщений
    const messageContainer = document.querySelector('.alert.alert-info.p-2.mb-0.small');

    // Если индикатор уже есть, ничего не делаем
    if (messageContainer) {
        messageContainer.style.display = 'block';
        return;
    }

    // Создаем новый индикатор загрузки
    const alertDiv = document.createElement('div');
    alertDiv.className = 'alert alert-info p-2 mb-0 small';
    alertDiv.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Загрузка информации о дисковом пространстве...';

    // Находим область, куда добавить индикатор
    const containingRow = document.querySelector('.row.mt-3');
    if (containingRow) {
        const col = containingRow.querySelector('.col-md-12');
        if (col) {
            col.appendChild(alertDiv);
        }
    }
}


/**
 * Скрывает индикатор загрузки данных о дисковом пространстве
 */
function hideDiskLoadingIndicator() {
    const loadingIndicator = document.querySelector('.alert.alert-info.p-2.mb-0.small');
    if (loadingIndicator) {
        loadingIndicator.style.display = 'none';
    }
}

/**
 * Обновляет информацию о CPU
 */
function updateCpuInfo(data) {
    // Обновляем прогресс-бар CPU
    const cpuBar = document.querySelector('#cpu-usage-bar');
    if (cpuBar && data.cpuUsagePercentage !== undefined) {
        // Округляем до десятых и обеспечиваем, что значение находится в диапазоне 0-100
        const cpuPercentage = Math.min(Math.max(parseFloat(data.cpuUsagePercentage) || 0, 0), 100).toFixed(1);

        cpuBar.style.width = cpuPercentage + '%';
        cpuBar.textContent = cpuPercentage + '%';
        cpuBar.setAttribute('aria-valuenow', cpuPercentage);

        // Изменяем цвет в зависимости от загрузки
        if (cpuPercentage > 90) {
            cpuBar.className = 'progress-bar bg-danger';
        } else if (cpuPercentage > 70) {
            cpuBar.className = 'progress-bar bg-warning';
        } else {
            cpuBar.className = 'progress-bar bg-success';
        }
    }

    // Обновляем детали CPU
    const cpuDetailsText = document.querySelector('#cpu-usage-bar').closest('.col-md-6').querySelector('small.text-muted');
    if (cpuDetailsText) {
        const cores = data.availableProcessors || 0;
        const systemLoad = parseFloat(data.systemCpuLoad || data.cpuUsagePercentage || 0).toFixed(2);
        // Форматируем вывод как в диспетчере задач: "Ядер: X, Загрузка системы: Y.YY%"
        cpuDetailsText.textContent = `Ядер: ${cores}, Загрузка системы: ${systemLoad}%`;
    }
}

/**
 * Обновляет информацию о памяти
 */
function updateMemoryInfo(data) {
    // Обновляем прогресс-бар памяти
    const memoryBar = document.querySelector('#memory-usage-bar');
    if (memoryBar && data.memoryUsagePercentage !== undefined) {
        const memoryPercentage = parseFloat(data.memoryUsagePercentage) || 0;
        memoryBar.style.width = memoryPercentage + '%';
        memoryBar.textContent = memoryPercentage + '%';
        memoryBar.setAttribute('aria-valuenow', memoryPercentage);

        // Изменяем цвет в зависимости от загрузки
        if (memoryPercentage > 90) {
            memoryBar.className = 'progress-bar bg-danger';
        } else if (memoryPercentage > 70) {
            memoryBar.className = 'progress-bar bg-warning';
        } else {
            memoryBar.className = 'progress-bar bg-info';
        }
    }

    // Обновляем детали памяти
    const memoryDetailsText = document.querySelector('#memory-usage-bar').closest('.col-md-6').querySelector('small.text-muted');
    if (memoryDetailsText) {
        const used = data.currentMemoryUsage || data.usedHeapMemory || '0 MB';
        const total = data.totalMemory || data.maxHeapMemory || '0 GB';
        memoryDetailsText.textContent = `Используется: ${used} из ${total}`;
    }
}

/**
 * Обновляет информацию о дисковом пространстве
 */
function updateDiskInfo(data) {
    // Проверяем наличие ошибок
    if (data.diskError) {
        showDiskError(data.diskError);
        return;
    }

    // Общее использование диска
    const diskBar = document.querySelector('#disk-usage-bar');
    if (diskBar && data.diskUsagePercentage !== undefined) {
        const diskPercentage = parseFloat(data.diskUsagePercentage) || 0;
        diskBar.style.width = diskPercentage + '%';
        diskBar.textContent = diskPercentage + '%';
        diskBar.setAttribute('aria-valuenow', diskPercentage);

        // Изменяем цвет в зависимости от заполнения
        if (diskPercentage > 90) {
            diskBar.className = 'progress-bar bg-danger';
        } else if (diskPercentage > 75) {
            diskBar.className = 'progress-bar bg-warning';
        } else {
            diskBar.className = 'progress-bar bg-info';
        }
    }

    // Обновляем текст с информацией об использовании диска
    const diskUsageText = document.getElementById('disk-usage-text');
    if (diskUsageText) {
        const used = data.usedDiskSpace || '0 GB';
        const total = data.totalDiskSpace || '0 GB';
        const percentage = data.diskUsagePercentage || 0;
        diskUsageText.textContent = `Используется: ${used} из ${total} (${percentage}%)`;
    }

    // Обновляем текст с информацией о свободном пространстве
    const diskFreeText = document.getElementById('disk-free-text');
    if (diskFreeText && data.freeDiskSpace) {
        diskFreeText.textContent = `Свободно: ${data.freeDiskSpace}`;
    }

    // Пространство файлов клиентов
    // Обновляем прогресс-бар файлов клиентов
    const clientsBar = document.querySelector('#clients-usage-bar');
    if (clientsBar && data.clientsDataPercentage !== undefined) {
        const percentage = parseFloat(data.clientsDataPercentage) || 0;
        clientsBar.style.width = percentage + '%';
        clientsBar.textContent = percentage + '%';
        clientsBar.setAttribute('aria-valuenow', percentage);
    }

    // Обновляем информацию о размере файлов клиентов
    const clientsSizeText = document.getElementById('clients-size-text');
    if (clientsSizeText) {
        const size = data.clientsDataSize || '0 GB';
        const percentage = data.clientsDataPercentage || 0;
        clientsSizeText.textContent = `Размер файлов: ${size} (${percentage}% от общего)`;
    }

    // Обновляем информацию о количестве файлов
    const clientsCountText = document.getElementById('clients-count-text');
    if (clientsCountText) {
        const count = data.clientsFileCount || 0;
        clientsCountText.textContent = `Файлов: ${count}`;
    }
}

/**
 * Обновляет время последнего обновления
 */
function updateLastUpdatedTime(data) {
    const lastUpdatedElement = document.querySelector('.card-header small');
    if (lastUpdatedElement) {
        if (data.lastUpdatedFormatted) {
            lastUpdatedElement.textContent = 'Обновлено: ' + data.lastUpdatedFormatted;
        } else {
            lastUpdatedElement.textContent = 'Обновлено: ' + formatDateTime(new Date());
        }
    }
}

/**
 * Показывает ошибку при получении информации о диске
 */
function showDiskError(errorMessage) {
    const diskInfoElement = document.querySelector('.fa-spinner')?.parentNode;
    if (diskInfoElement) {
        diskInfoElement.innerHTML = `<i class="fas fa-exclamation-circle text-danger"></i> Ошибка: ${errorMessage}`;
    }
}

/**
 * Показывает общую ошибку при получении данных
 */
function showErrorMessage(errorMessage) {
    // Вариант 1: Показать в блоке системных ресурсов
    const systemCard = document.querySelector('.card');
    if (systemCard) {
        const alertDiv = document.createElement('div');
        alertDiv.className = 'alert alert-danger mt-3';
        alertDiv.innerHTML = `<i class="fas fa-exclamation-triangle"></i> Ошибка обновления: ${errorMessage}`;

        // Удаляем предыдущее сообщение об ошибке
        const existingAlert = systemCard.querySelector('.alert');
        if (existingAlert) {
            existingAlert.remove();
        }

        systemCard.querySelector('.card-body').appendChild(alertDiv);

        // Удаляем сообщение через 5 секунд
        setTimeout(() => {
            alertDiv.remove();
        }, 5000);
    }
}

/**
 * Форматирует дату и время
 */
function formatDateTime(date) {
    const day = String(date.getDate()).padStart(2, '0');
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const year = date.getFullYear();
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    const seconds = String(date.getSeconds()).padStart(2, '0');

    return `${day}.${month}.${year} ${hours}:${minutes}:${seconds}`;
}