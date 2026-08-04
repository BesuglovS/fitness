# Фитнес-дневник (Fitness)

Android-приложение для ведения дневника тренировок: отслеживание упражнений, подходов, весов и прогресса.

## Возможности

- **Тренировки** — создание и ведение тренировок с упражнениями, подходами, весами и количеством повторений
- **Круговые тренировки** — режим круговых тренировок с таймером отдыха
- **Библиотека упражнений** — каталог упражнений с группировкой по мышечным группам и категориям
- **История** — просмотр завершённых тренировок с деталями (объём, количество подходов, упражнений)
- **Прогресс** — графики прогресса по упражнениям: максимальный вес, повторения, объём, расчёт 1ПМ (по формуле Эпли)
- **Настройки** — настройка времени отдыха по умолчанию
- **Экспорт данных** — выгрузка всех данных в JSON-файл (в папку Downloads)

## Технологии

- **Язык:** Kotlin
- **UI:** Jetpack Compose (Material 3)
- **Навигация:** Navigation Compose
- **База данных:** Room (SQLite) с миграциями
- **Архитектура:** MVVM (ViewModel + Repository + Flow)
- **Сборка:** Gradle (Kotlin DSL), KSP

## Структура проекта

```
app/src/main/java/ru/besuglovs/fitness/
├── FitnessApp.kt              # Application: инициализация БД, репозитория, настроек
├── MainActivity.kt            # Главная активность, навигация, нижняя панель вкладок
├── SettingsStorage.kt         # Хранение настроек (SharedPreferences)
├── data/                      # Room: сущности, DAO, репозиторий, связи
│   ├── AppDatabase.kt
│   ├── Exercise.kt
│   ├── Workout.kt
│   ├── WorkoutExercise.kt
│   ├── SetEntry.kt
│   ├── ExerciseDao.kt
│   ├── WorkoutDao.kt
│   ├── StatsDao.kt
│   ├── FitnessRepository.kt
│   └── Relations.kt
├── ui/
│   ├── screens/               # Экраны (Home, Workout, Circuit, History, Progress, Settings, Library, Detail)
│   ├── viewmodel/             # ViewModel'и
│   ├── components/            # Переиспользуемые компоненты (LineChart и др.)
│   └── theme/                 # Тема оформления
└── util/
    ├── ExportUtils.kt         # Экспорт данных в JSON
    └── Formats.kt             # Форматирование дат, времени, весов, 1ПМ
```

## Модель данных

- **Exercise** — упражнение (название, мышечная группа, категория)
- **Workout** — тренировка (время начала/окончания, заметки)
- **WorkoutExercise** — упражнение в рамках тренировки (порядок)
- **SetEntry** — подход (вес, повторения, время отдыха)

## Требования

- Android 8.0 (API 26) и выше
- JDK 17
- Android SDK 36

## Сборка

```bash
./gradlew assembleDebug
```

APK будет создан в `app/build/outputs/apk/debug/`.

## Лицензия

Проект распространяется под лицензией [Apache License 2.0](LICENSE).