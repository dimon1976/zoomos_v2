-- Скрипт оптимизации запросов для работы с заданиями и отчетами
-- Миграция Flyway V4: создание индексов для ускорения фильтрации и обогащения данных

-- Индекс для быстрого поиска по номеру задания в таблице products
CREATE INDEX IF NOT EXISTS idx_products_datasource_task
    ON products (data_source, product_additional1);

-- Индекс для быстрого поиска по ключам валидации
-- (три поля, используемые для построения ключа)
CREATE INDEX IF NOT EXISTS idx_products_validation_keys
    ON products (product_id, product_category1, product_additional1);

-- Индекс для быстрого доступа к данным по конкурентам
CREATE INDEX IF NOT EXISTS idx_competitor_data_product_id
    ON site_data (product_id);

-- Индекс для быстрого доступа к данным по розничной сети
CREATE INDEX IF NOT EXISTS idx_competitor_data_additional
    ON site_data (competitor_additional);

-- Индекс для справочника розничных сетей
CREATE INDEX IF NOT EXISTS idx_retail_network_code
    ON retail_network_directory (retail_code);

-- Индекс для файловых данных
CREATE INDEX IF NOT EXISTS idx_products_file_id
    ON products (file_id);

-- Анализ таблиц для обновления статистики
ANALYZE products;
ANALYZE site_data;
ANALYZE retail_network_directory;

-- Создание функции для быстрого поиска соответствий между заданием и отчетом
CREATE OR REPLACE FUNCTION match_task_records(
    task_number_param VARCHAR,
    data_source_param VARCHAR
)
    RETURNS TABLE (
                      validation_key VARCHAR,
                      product_id VARCHAR,
                      category VARCHAR,
                      retail_code VARCHAR
                  ) AS $$
BEGIN
    RETURN QUERY
        SELECT
            UPPER(CONCAT(p.product_id, '_', p.product_category1, '_', cd.competitor_additional)) AS validation_key,
            p.product_id,
            p.product_category1 AS category,
            cd.competitor_additional AS retail_code
        FROM
            products p
                JOIN
            site_data cd ON cd.product_id = p.product_id -- Исправлено: p.id -> p.product_id
        WHERE
                p.data_source = data_source_param
          AND p.product_additional1 = task_number_param;
END;
$$ LANGUAGE plpgsql;