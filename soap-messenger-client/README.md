# SOAP Messenger Client

Kotlin Multiplatform-клиент (Android) для учебного мессенджера.

## Запуск сервера

Из корня репозитория:

```bash
docker compose up --build -d
```

Сервер доступен на порту `8080`.

## Запуск клиента

```bash
cd soap-messenger-client
gradlew.bat :androidApp:assembleDebug
```

Установите APK на Android Emulator и запустите приложение.

## Сеть

Эмулятор Android обращается к хост-машине по адресу `10.0.2.2`.
SOAP endpoint клиента: `http://10.0.2.2:8080/ws`.

В debug-сборке разрешён HTTP-только для `10.0.2.2`.

## Текущие возможности

- регистрация пользователя (`RegisterUser`);
- вход (`AuthenticateUser`);
- список диалогов (`GetDialogs`) с последним сообщением и временем его отправки;
- поиск пользователя (`FindUser`);
- открытие или создание личного диалога (`OpenOrCreateDialog`).
