-- Создаем enum тип
CREATE TYPE data_source_type AS ENUM ('TASK', 'REPORT', 'COMMON');

-- Добавляем колонку
ALTER TABLE products ADD COLUMN data_source data_source_type DEFAULT 'COMMON';

-- Обновляем существующие записи
UPDATE products SET data_source = 'COMMON' WHERE data_source IS NULL;