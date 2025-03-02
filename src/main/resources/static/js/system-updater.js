/**
 * Скрипт для автоматического обновления системных ресурсов
 * Добавьте его в конец вашего HTML перед закрывающим тегом </body>
 */

document.addEventListener('DOMContentLoaded', function() {
    // Регистрируем обработчики событий после загрузки DOM
    setupSystemResourcesUpdater();
    setupChartsUpdater();
});

/**
 * Настраивает автоматическое обновление системных ресурсов
 */
function setupSystemResourcesUpdater() {
    // Сначала проверяем, существуют ли необходимые элементы на странице
    const memoryProgressBar = document.querySelector('.progress-bar:first-of-type');
    const diskProgressBar = document.querySelector('.progress-bar.bg-info');

    if (!memoryProgressBar || !diskProgressBar) {
        console.warn('Элементы системных ресурсов не найдены на странице');
        return;
    }

    // Добавляем кнопку для ручного обновления (если её ещё нет)
    const cardHeader = document.querySelector('.card-header');
    if (cardHeader && !document.getElementById('refresh-system-resources')) {
        const refreshButton = document.createElement('button');
        refreshButton.id = 'refresh-system-resources';
        refreshButton.className = 'btn btn-sm btn-outline-primary me-2';
        refreshButton.innerHTML = '<i class="fas fa-sync-alt"></i> Обновить';

        // Добавляем обработчик клика
        refreshButton.addEventListener('click', function() {
            const icon = this.querySelector('i');
            icon.classList.add('fa-spin');

            updateSystemResources().finally(() => {
                setTimeout(() => {
                    icon.classList.remove('fa-spin');
                }, 1000);
            });
        });

        // Добавляем кнопку к заголовку карточки
        const headerContent = cardHeader.querySelector('div');
        if (headerContent) {
            headerContent.prepend(refreshButton);
        } else {
            // Если div не существует, создаем новый
            const headerDiv = document.createElement('div');
            headerDiv.className = 'd-flex justify-content-between align-items-center';
            headerDiv.appendChild(cardHeader.querySelector('h5')); // Перемещаем заголовок
            headerDiv.appendChild(refreshButton);

            // Добавляем элемент для отображения времени обновления
            const timeSpan = document.createElement('small');
            timeSpan.id = 'last-updated-time';
            timeSpan.textContent = 'Обновлено: ' + getCurrentFormattedTime();
            headerDiv.appendChild(timeSpan);

            // Очищаем и заполняем заголовок карточки
            cardHeader.innerHTML = '';
            cardHeader.appendChild(headerDiv);
        }
    }

    // Добавляем секцию для отображения CPU, если её нет
    const systemResourcesCard = document.querySelector('.card');
    const cardBody = systemResourcesCard.querySelector('.card-body');

    if (cardBody && !document.querySelector('.cpu-usage')) {
        // Создаем новую строку
        const newRow = document.createElement('div');
        newRow.className = 'row mb-3';

        // Добавляем секцию CPU
        newRow.innerHTML = `
            <div class="col-md-6 cpu-usage">
                <h6>Использование CPU</h6>
                <div class="progress">
                    <div class="progress-bar bg-danger" role="progressbar" style="width: 0%" aria-valuenow="0" aria-valuemin="0" aria-valuemax="100">0%</div>
                </div>
                <small class="text-muted">Средняя загрузка процессора</small>
            </div>
        `;

        // Добавляем строку в начало содержимого карточки
        cardBody.prepend(newRow);

        // Перемещаем существующую секцию памяти в новую строку
        const memorySection = document.querySelector('.progress-bar').closest('.col-md-6');
        if (memorySection) {
            newRow.appendChild(memorySection.cloneNode(true));
            memorySection.remove();
        }
    }

    // Улучшаем информацию о диске, если необходимо
    const diskUsageText = document.querySelector('.progress-bar.bg-info').closest('.col-md-6 ,.col-md-12').querySelector('small.text-muted');
    if (diskUsageText && !diskUsageText.textContent.includes('из')) {
        diskUsageText.textContent = 'Используется: 0 GB из 0 GB (0%)';
    }

    // Запускаем первое обновление
    updateSystemResources();

    // Устанавливаем интервал обновления каждые 30 секунд
    setInterval(updateSystemResources, 30000);
}

/**
 * Обновляет информацию о системных ресурсах через AJAX запрос
 */
async function updateSystemResources() {
    try {
        const response = await fetch('/api/system/resources');
        if (!response.ok) {
            throw new Error(`Ошибка HTTP: ${response.status}`);
        }

        const data = await response.json();
        console.log('Получены данные о системных ресурсах:', data);

        // Обновляем CPU (если есть)
        const cpuProgressBar = document.querySelector('.progress-bar.bg-danger');
        if (cpuProgressBar && data.cpuUsagePercentage !== undefined) {
            cpuProgressBar.style.width = data.cpuUsagePercentage + '%';
            cpuProgressBar.textContent = data.cpuUsagePercentage + '%';
            cpuProgressBar.setAttribute('aria-valuenow', data.cpuUsagePercentage);
        }

        // Обновляем память
        const memoryProgressBar = document.querySelector('.progress-bar:not(.bg-danger):not(.bg-info)');
        if (memoryProgressBar && data.memoryUsagePercentage !== undefined) {
            memoryProgressBar.style.width = data.memoryUsagePercentage + '%';
            memoryProgressBar.textContent = data.memoryUsagePercentage + '%';
            memoryProgressBar.setAttribute('aria-valuenow', data.memoryUsagePercentage);

            // Обновляем текст с информацией о памяти
            const memoryText = memoryProgressBar.closest('.col-md-6').querySelector('small.text-muted');
            if (memoryText && data.currentMemoryUsage) {
                memoryText.textContent = `Используется: ${data.currentMemoryUsage}${data.totalMemory ? ' из ' + data.totalMemory : ''}`;
            }
        }

        // Обновляем диск
        const diskProgressBar = document.querySelector('.progress-bar.bg-info');
        if (diskProgressBar && data.diskUsagePercentage !== undefined) {
            diskProgressBar.style.width = data.diskUsagePercentage + '%';
            diskProgressBar.textContent = data.diskUsagePercentage + '%';
            diskProgressBar.setAttribute('aria-valuenow', data.diskUsagePercentage);

            // Обновляем текст с информацией о диске
            const diskText = diskProgressBar.closest('.col-md-6, .col-md-12').querySelector('small.text-muted');
            if (diskText) {
                diskText.textContent = `Используется: ${data.usedDiskSpace || '0 GB'} из ${data.totalDiskSpace || '0 GB'} (${data.diskUsagePercentage || 0}%)`;
            }
        }

        // Обновляем время последнего обновления
        const lastUpdatedElement = document.getElementById('last-updated-time');
        if (lastUpdatedElement) {
            if (data.lastUpdatedFormatted) {
                lastUpdatedElement.textContent = 'Обновлено: ' + data.lastUpdatedFormatted;
            } else {
                lastUpdatedElement.textContent = 'Обновлено: ' + getCurrentFormattedTime();
            }
        }

        console.log('Системные ресурсы успешно обновлены');
    } catch (error) {
        console.error('Ошибка при обновлении системных ресурсов:', error);
    }
}

/**
 * Возвращает текущее время в формате "дд.мм.гггг чч:мм:сс"
 */
function getCurrentFormattedTime() {
    const now = new Date();

    // Форматирование компонентов даты и времени
    const day = String(now.getDate()).padStart(2, '0');
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const year = now.getFullYear();
    const hours = String(now.getHours()).padStart(2, '0');
    const minutes = String(now.getMinutes()).padStart(2, '0');
    const seconds = String(now.getSeconds()).padStart(2, '0');

    return `${day}.${month}.${year} ${hours}:${minutes}:${seconds}`;
}

/**
 * Настраивает автоматическое обновление графиков
 */
function setupChartsUpdater() {
    // Проверяем наличие графиков на странице
    const typeChart = document.getElementById('operationTypeChart');
    const timelineChart = document.getElementById('operationsTimelineChart');

    if (!typeChart && !timelineChart) {
        console.warn('Элементы графиков не найдены на странице');
        return;
    }

    // Загружаем данные и инициализируем графики
    updateCharts();
}

/**
 * Обновляет все графики на странице
 */
async function updateCharts() {
    try {
        // Загружаем данные для графиков
        const operationTypeCounts = await loadChartData('/api/charts/operation-types');
        const operationsTimeline = await loadChartData('/api/charts/operations-timeline');

        // Инициализируем графики
        if (operationTypeCounts) {
            initOperationTypeChart(operationTypeCounts);
        }

        if (operationsTimeline) {
            initOperationsTimelineChart(operationsTimeline);
        }

    } catch (error) {
        console.error('Ошибка при обновлении графиков:', error);
    }
}

/**
 * Загружает данные для графика с указанного URL
 */
async function loadChartData(url) {
    try {
        const response = await fetch(url);
        if (!response.ok) {
            throw new Error(`Ошибка HTTP: ${response.status}`);
        }

        const data = await response.json();
        return data;
    } catch (error) {
        console.error(`Ошибка при загрузке данных с ${url}:`, error);
        return null;
    }
}

// Глобальные переменные для хранения экземпляров графиков
let _typeChart = null;
let _timelineChart = null;

/**
 * Инициализирует график распределения типов операций
 */
function initOperationTypeChart(data) {
    const ctx = document.getElementById('operationTypeChart');
    if (!ctx) return;

    // Уничтожаем существующий график, если он есть
    if (_typeChart) {
        _typeChart.destroy();
    }

    // Проверяем наличие данных
    if (!data || !data.labels || data.labels.length === 0) {
        ctx.parentNode.innerHTML = '<div class="alert alert-info">Нет данных для отображения</div>';
        return;
    }

    // Создаем новый график
    _typeChart = new Chart(ctx.getContext('2d'), {
        type: 'pie',
        data: {
            labels: Array.isArray(data.labels) ?
                data.labels.map(type => data.descriptions && data.descriptions[type] ? data.descriptions[type] : type) :
                [],
            datasets: [{
                data: data.data || [],
                backgroundColor: [
                    'rgba(255, 99, 132, 0.7)',
                    'rgba(54, 162, 235, 0.7)',
                    'rgba(255, 206, 86, 0.7)',
                    'rgba(75, 192, 192, 0.7)'
                ],
                borderWidth: 1
            }]
        },
        options: {
            responsive: true,
            animation: {
                duration: 500
            },
            plugins: {
                legend: {
                    position: 'right',
                },
                title: {
                    display: true,
                    text: 'Распределение по типам операций'
                }
            }
        }
    });
}

/**
 * Инициализирует график временной шкалы операций
 */
function initOperationsTimelineChart(data) {
    const ctx = document.getElementById('operationsTimelineChart');
    if (!ctx) return;

    // Уничтожаем существующий график, если он есть
    if (_timelineChart) {
        _timelineChart.destroy();
    }

    // Проверяем наличие данных
    if (!data || !data.labels || data.labels.length === 0) {
        ctx.parentNode.innerHTML = '<div class="alert alert-info">Нет данных для отображения</div>';
        return;
    }

    // Создаем новый график
    _timelineChart = new Chart(ctx.getContext('2d'), {
        type: 'bar',
        data: {
            labels: data.labels || [],
            datasets: [{
                label: 'Количество операций',
                data: data.data || [],
                backgroundColor: 'rgba(54, 162, 235, 0.7)',
                borderColor: 'rgba(54, 162, 235, 1)',
                borderWidth: 1
            }]
        },
        options: {
            responsive: true,
            animation: {
                duration: 500
            },
            scales: {
                y: {
                    beginAtZero: true,
                    ticks: {
                        precision: 0
                    }
                }
            },
            plugins: {
                title: {
                    display: true,
                    text: 'Операции за последние 7 дней'
                }
            }
        }
    });
}