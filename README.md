# SOAP Messenger

Учебный проект мобильного мессенджера.

## Запуск

1. Создать `.env` на основе `.env.example` и задать `POSTGRES_PASSWORD` и `JWT_SECRET`.
2. Запустить стек:

```bash
docker compose up --build -d
```

или 

```bash
make up
```

**WSDL:** http://localhost:8080/ws/soap-messenger.wsdl

## Как устроено
* **XSD** — `soap-messenger-server/src/main/resources/META-INF/schemas/soap-messenger.xsd`
* **WSDL** генерируется из XSD при старте приложения

## Структура репозитория
* `docs/` — техническое задание, UML, ER-модель, отчёты
* `soap-messenger-server/` — Spring Boot-приложение, XSD, Flyway-миграции, Dockerfile

## Как открыть UML-диаграммы

1. Открыть сайт https://app.diagrams.net/
2. Выбрать `File` → `Open From` → `Device`.
3. Выбрать файл `docs/uml/soap-messenger-uml.drawio`.

## Как открыть ER-модель

1. Распаковать архив `docs/database/ERConstructor 2.0.zip`.
2. Запустить программу ERConstructor 2.0.
3. Открыть файл `docs/database/soap-messenger-er.ercmdl`.
