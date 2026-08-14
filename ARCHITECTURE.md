# Архитектура приложения «Фитнес-дневник» (Fitness)

## Обзор

Android-приложение для ведения дневника тренировок на **Kotlin** с **Jetpack Compose**
(Material 3), **Room** (SQLite) и **BLE**-клиентом для пульсометра. Следует шаблону **MVVM**:

```
            ┌────────────────────────────┐
            │      UI (Compose)          │
            │   Screens + Components     │
            └─────────────┬──────────────┘
                          │ collectAsState
            ┌─────────────▼──────────────┐
            │      ViewModel (UI-состояние)│
            │  + HeartRateSensor (BLE)   │
            └─────────────┬──────────────┘
                          │ Flow
            ┌─────────────▼──────────────┐
            │      FitnessRepository      │  ← единая точка доступа к данным
            └─────────────┬──────────────┘
                          │
            ┌─────────────▼──────────────┐
            │  DAO × 4  (Room)  AppDatabase │  → SQLite (fitness.db)
            └────────────────────────────┘
```

- **UI** не знает о БД — работает только со `StateFlow` из ViewModel.
- **ViewModel** не знает о деталях Room/DAO — обращается только через `FitnessRepository`.
- **Repository** инкапсулирует все сущности, DAO и запросы.
- **BLE** инкапсулирован в `HeartRateSensor`; ViewModel подписывается на его `StateFlow`
  и сохраняет показания через Repository.

## Слои и компоненты

### 1. Application-слой (инициализация)
| Файл | Назначение |
|------|------------|
| `FitnessApp.kt` | `Application`. Обёртка DI вручную: лениво создаёт `AppDatabase`, `FitnessRepository`, `SettingsStorage`. Доступны как `fitnessApp.repository` / `settings`. |
| `MainActivity.kt` | `ComponentActivity`. `setContent { FitnessTheme { FitnessRoot() } }` + навигация. Включает `FLAG_KEEP_SCREEN_ON`. |
| `AppViewModelProvider.kt` | Фабрика ViewModel. Через `initializer` расписывает создание каждого ViewModel, извлекая аргументы (`workoutId`, `restSeconds`) из `SavedStateHandle`. |

### 2. UI-слой (`ru.besuglovs.fitness.ui`)
- `screens/` — экраны-`@Composable`: `HomeScreen`, `WorkoutScreen`, `CircuitScreen`,
  `WorkoutDetailScreen`, `HistoryScreen`, `ProgressScreen`, `SettingsScreen`,
  `ExerciseLibraryScreen`.
- `components/` — переиспользуемые Compose-компоненты: `LineChart.kt` (самодельный график),
  `HeartRateWidget.kt` (карточка пульсометра: подключить/отключить, текущий пульс, счётчик записанных показаний).
- `viewmodel/` — 8 ViewModel (см. ниже).
- `theme/` — тема `Theme.kt` (светлая/тёмная схемы Material 3, зелёно-оранжевая палитра).

**Навигация** построена на Navigation Compose. Основное окно — `Scaffold` с нижней панелью
вкладок: Главная, История, Прогресс, Настройки. Остальные маршруты (тренировка, круговая,
библиотека, детали) открываются поверх без нижней панели:

| Маршрут | Аргументы | Элемент |
|---------|-----------|---------|
| `home` | — | HomeScreen |
| `history` | — | HistoryScreen |
| `progress` | — | ProgressScreen |
| `settings` | — | SettingsScreen |
| `workout/{workoutId}?restSeconds=` | Long, Int(по умолч. 90) | WorkoutScreen |
| `circuit/{workoutId}?restSeconds=` | Long, Int(90) | CircuitScreen |
| `library` | — | ExerciseLibraryScreen |
| `detail/{workoutId}` | Long | WorkoutDetailScreen |

Дефолт маршрута `restSeconds` (90 с) используется только когда параметр не передан
(например, при возобновлении сессии); при старте новой тренировки из `Home` подставляется
значение из настроек `SettingsStorage` (по умолч. 240 с).

### 3. Data-слой (`ru.besuglovs.fitness.data`)
**Сущности (таблицы):**
- `Exercise` — упражнение (id, name, muscleGroup, category, createdAt).
- `Workout` — тренировка (id, startTime, endTime?, notes, isCircuit, sessionJson?, pausedAt?).
  Поля `sessionJson`/`pausedAt` хранят состояние незавершённой тренировки для последующего возобновления.
- `WorkoutExercise` — упражнение в рамках тренировки (id, workoutId FK→CASCADE, exerciseId, orderIndex).
- `SetEntry` — подход (id, workoutExerciseId FK→CASCADE, setNumber, weightKg?, reps?,
  restSeconds?, durationSeconds?, setStartTime?, avgHeartRate?, maxHeartRate?, doneAt).
  Поля `setStartTime`/`avgHeartRate`/`maxHeartRate` хранят пульсовые метрики подхода.
- `HeartRateSample` — показание пульса (id, workoutId FK→CASCADE, timestamp, bpm),
  индекс по `workoutId`.

Связи:
`Workout 1—N WorkoutExercise 1—N SetEntry`, причём `SetEntry` ссылается на библиотеку упражнений
через `WorkoutExercise.exerciseId → Exercise`. Показания пульса ссылаются на тренировку
(`HeartRateSample.workoutId → Workout`).

**DAO (×4):**
- `ExerciseDao` — CRUD упражнений (Flow + suspend «once»-варианты).
- `WorkoutDao` — создание/завершение/удаление тренировок; `saveSession()` для сохранения
  JSON незавершённой сессии; транзакционный `saveWorkoutWithSets()` для атомарного сохранения
  тренировки с упражнениями и подходами.
- `StatsDao` — агрегирующие запросы: прогрессия упражнения (макс. вес/повторения/объём),
  общий объём, количество тренировок/упражнений, сводки для истории.
- `HeartRateDao` — вставка и чтение показаний пульса (Flow + «once», по всем / по тренировке).

**`Relations.kt`** — POJO-представления для Room-отношений:
`WorkoutWithDetails`, `WorkoutExerciseWithExercise`, `ProgressPoint`, `WorkoutSummary`, `ExerciseWithSets`.

**`AppDatabase`** — `@Database(version = 8)`, ручной синглтон через `synchronized`,
`fallbackToDestructiveMigration`. Миграции:
- `2→3` — пересоздание таблицы `set_entries` с индексом;
- `3→4` — колонка `workouts.isCircuit`;
- `4→5` — колонка `set_entries.durationSeconds`;
- `5→6` — колонки `workouts.sessionJson`, `workouts.pausedAt`;
- `6→7` — таблица `heart_rate_samples` с индексом по `workoutId`;
- `7→8` — колонки `set_entries.setStartTime`, `set_entries.avgHeartRate`, `set_entries.maxHeartRate`.

**`FitnessRepository`** — единственная точка работы с данными: оборачивает все DAO,
предоставляет Flow и suspend-методы для упражнений, тренировок, статистики, пульса и экспорта.

### 4. BLE-слой (`ru.besuglovs.fitness.ble`)
- `HeartRateSensor` — клиент BLE-пульсометра H808S (COOSPO). Использует стандартный сервис
  Heart Rate (0x180D) и характеристику Heart Rate Measurement (0x2A37):
  - сканирование устройств (имя содержит «h808» или объявляет сервис 0x180D) с таймаутом 15 с;
  - запоминание MAC последнего устройства в `SharedPreferences` для быстрого переподключения;
  - запрос прав на выполнение сканирования начиная с Android 12 и прав на определение
    местоположения на более старых версиях;
  - разбор данных ЧСС (формат 8/16 бит по флагам характеристики);
  - реактивная модель: `status`/`bpm`/`deviceName` как `StateFlow`, поток показаний `readings` как `SharedFlow`.

### 5. DI / Прочие
- `SettingsStorage.kt` — `SharedPreferences` (`fitness_settings`), хранит `defaultRestSeconds` (по умолч. 240).
- `util/` — `Formats.kt` (форматирование дат/времени/веса, 1ПМ по формуле Эпли),
  `ExportUtils.kt` (экспорт всего в JSON в Downloads через MediaStore, включая показания пульса).

## ViewModel («тонкие», держат UI-состояние в MutableStateFlow)

| ViewModel | Источники данных | Отвечает за |
|-----------|------------------|-------------|
| `HomeViewModel` | repository.workouts/count + settings | недавние тренировки, счётчики, незавершённая тренировка, создание тренировки |
| `WorkoutViewModel` | repository.exercises + HeartRateSensor | обычная тренировка: выбор упражнений, весов/повторов, таймеры подхода и отдыха с паузой, пульсометр, сохранение/возобновление сессии |
| `CircuitViewModel` | repository.exercises + HeartRateSensor | круговая тренировка: фазы `SETUP→EXERCISE→REP_ENTRY`, кольца, таймеры, пульсометр, сохранение/возобновление сессии |
| `HistoryViewModel` | repository.summaries | список завершённых тренировок, удаление |
| `WorkoutDetailViewModel` | repository.workoutDetails + heartRateSamples | детали одной тренировки; графики пульса по тренировке/подходам/кругам; вычисление отдыха между упражнениями |
| `ProgressViewModel` | repository.exercises/progression/maxWeight | графики прогресса по выбранному упражнению |
| `SettingsViewModel` | settings + repository (экспорт) | настройки отдыха, экспорт в JSON |
| `ExerciseLibraryViewModel` | repository.exercises | CRUD упражнений |

State-реактивность строится на `MutableStateFlow` + `stateIn(WhileSubscribed(5000))`
и `flatMapLatest`/`combine` (ProgressViewModel). Показания таймеров реализованы через
`Job` + `delay(1000)` в `viewModelScope`.

**Пульс во время тренировки:** ViewModel подписывается на `readings` пульсометра, накапливает
`HeartRateSample` в памяти (уже сохранённые подгружаются из БД), а при завершении/выходе
сохраняет новые показания в БД. Средний/максимальный пульс подхода вычисляется по
временному интервалу `setStartTime..doneAt`.

**Сохранение незавершённой сессии:** `saveAndExit()` сериализует состояние ViewModel
(фаза, выбранные упражнения, веса/повторы, таймеры, завершённые подходы) в JSON
(функции-энкодеры в `SessionJson.kt`) и пишет его в `workouts.sessionJson` вместе с
`pausedAt`. При возобновлении состояние восстанавливается, а диалог предлагает засчитать
отсутствие как отдых.

## Потоки данных (пример обычной тренировки)

1. `HomeViewModel.startWorkout()` → `repository.createWorkout()` → id новой незавершённой тренировки.
2. Навигация на `workout/{id}` → `WorkoutViewModel` создаётся через фабрику.
3. `WorkoutScreen` вызывает действия (выбрать упражнение → вес → старт подхода → повторы),
   параллельно `HeartRateWidget` подключает пульсометр.
4. `finishWorkout()` → собирает `List<ExerciseWithSets>` → `repository.saveWorkoutWithSets(...)`
   + сохранение накопленных показаний пульса.
5. `WorkoutDao.saveWorkoutWithSets` в одной транзакции завершает тренировку и пишет все связи/подходы.
6. `HistoryScreen` обновляется автоматически из `summaries (Flow)`; в `detail/{id}`
   показываются подходы, объём и графики пульса.

## Сборка

- Gradle 8.13 (wrapper) + Kotlin DSL + KSP для Room-компилятора.
- Плагины: AGP 8.9.1, Kotlin 2.1.0, Compose Compiler, KSP 2.1.0-1.0.29.
- Важные зависимости: Compose BOM 2024.12.01 (включая material-icons-extended),
  Navigation Compose 2.8.5, Room 2.6.1, lifecycle-viewmodel/runtime-compose 2.8.7,
  activity-compose 1.9.3, core-ktx 1.15.0.
- `minSdk 26`, `compileSdk 36`, `targetSdk 35`, JVM target 17.
- Release: `minifyEnabled = true` + ProGuard.

Сборка: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/`.

## Ключевые решения
- Ручная передача зависимостей через `FitnessApp` (без DI-фреймворка / Hilt).
- Room-отношения + транзакционный DAO для целостности вложенных данных.
- Два режима тренировки (обычный и круговой) с общей моделью сохранения.
- Пульсометр через стандартный сервис Heart Rate (0x180D) — совместимо с широким кругом BLE-датчиков.
- Сохранение сессии в JSON-поле строки `workouts` вместо отдельных таблиц — простое
  возобновление незавершённой тренировки в исходном состоянии.
- Реактивность на Kotlin Flow/StateFlow без RxJava.
