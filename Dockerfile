FROM bellsoft/liberica-openjdk-alpine:8

# Установка зависимостей
RUN apk add --no-cache bash gradle

# Создание рабочей директории
WORKDIR /app

# Копирование файлов проекта
COPY . /app/

# Установка прав на выполнение gradlew
RUN chmod +x gradlew || true

# Сборка проекта через gradle вместо gradlew
RUN gradle clean build -x test

# Создаем директорию для результатов матчей
RUN mkdir -p /app/matches

# Создаем скрипт для запуска компонентов
COPY docker-entry.sh /app/
RUN chmod +x /app/docker-entry.sh

# Определяем энтрипоинт
ENTRYPOINT ["/app/docker-entry.sh"]