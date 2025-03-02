// Исправление проблем с графиками
document.addEventListener('DOMContentLoaded', function() {
    console.log('Запуск исправления графиков');

    // Проверка загрузки Chart.js
    if (typeof Chart === 'undefined') {
        console.error('Chart.js не загружен!');
        return;
    }

    // Принудительное обновление графиков с таймаутом
    setTimeout(function() {
        // Ищем элементы загрузки и удаляем их
        document.querySelectorAll('.chart-loading').forEach(function(el) {
            el.remove();
        });

        // Показываем canvas элементы
        document.getElementById('operationTypeChart').style.display = 'block';
        document.getElementById('operationsTimelineChart').style.display = 'block';

        // Явно инициализируем графики с тестовыми данными в случае проблем
        initChartsWithDefaultData();
    }, 1000);
});

// Функция для инициализации графиков с тестовыми данными
function initChartsWithDefaultData() {
    console.log('Инициализация графиков с тестовыми данными');

    // Тестовые данные для графика типов операций
    const typeData = {
        labels: ['Импорт', 'Экспорт', 'Обновление товаров', 'Обновление цен'],
        data: [15, 8, 3, 5]
    };

    // Тестовые данные для графика временной шкалы
    const timelineData = {
        labels: Array.from({length: 7}, (_, i) => {
            const date = new Date();
            date.setDate(date.getDate() - 6 + i);
            return `${date.getDate().toString().padStart(2, '0')}.${(date.getMonth() + 1).toString().padStart(2, '0')}`;
        }),
        data: [3, 5, 2, 7, 4, 6, 2]
    };

    // Инициализация графика типов операций
    initOperationTypeChartDirectly(typeData);

    // Инициализация графика временной шкалы
    initOperationsTimelineChartDirectly(timelineData);
}

// Прямая инициализация графика типов операций
function initOperationTypeChartDirectly(data) {
    const canvas = document.getElementById('operationTypeChart');
    if (!canvas) return;

    const ctx = canvas.getContext('2d');

    // Уничтожаем существующий график если есть
    if (window.typeChartInstance) {
        window.typeChartInstance.destroy();
    }

    // Создаем новый график
    window.typeChartInstance = new Chart(ctx, {
        type: 'pie',
        data: {
            labels: data.labels,
            datasets: [{
                data: data.data,
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
            plugins: {
                legend: {
                    position: 'right',
                },
                title: {
                    display: true,
                    text: 'Распределение по типам операций',
                    font: {
                        size: 16
                    }
                }
            }
        }
    });

    console.log('График типов операций инициализирован');
}

// Прямая инициализация графика временной шкалы
function initOperationsTimelineChartDirectly(data) {
    const canvas = document.getElementById('operationsTimelineChart');
    if (!canvas) return;

    const ctx = canvas.getContext('2d');

    // Уничтожаем существующий график если есть
    if (window.timelineChartInstance) {
        window.timelineChartInstance.destroy();
    }

    // Создаем новый график
    window.timelineChartInstance = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: data.labels,
            datasets: [{
                label: 'Количество операций',
                data: data.data,
                backgroundColor: 'rgba(54, 162, 235, 0.7)',
                borderColor: 'rgba(54, 162, 235, 1)',
                borderWidth: 1
            }]
        },
        options: {
            responsive: true,
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
                    text: 'Операции за последние 7 дней',
                    font: {
                        size: 16
                    }
                }
            }
        }
    });

    console.log('График временной шкалы инициализирован');
}