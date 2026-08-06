# Архитектура приложения «Фитнес-дневник» (Fitness)

## Обзор

Android-приложение для ведения дневника тренировок на **Kotlin** с **Jetpack Compose**
(Material 3) и **Room** (SQLite). Следует шаблону **MVVM**:

```
            ┌────────────────────────────┐
            │      UI (Compose)          │
            │   Screens + Components     │
            └─────────────┬──────────────┘
                          │ collectAsState
            ┌─────────────▼──────────────┐
            │      ViewModel (UI-состояние)│
            └─────────────┬──────────────┘
                          │ Flow
            ┌─────────────▼──────────────┐
            │      FitnessRepository      │  ← единая точка доступа к данным
            └─────────────┬──────────────┘
                          │
            ┌─────────────▼──────────────┐
            │  DAO  × 3  (Room)  AppDatabase │  → SQLite (fitness.db)
            └────────────────────────────┘
```

- **UI** не знает о БД — работает только со `StateFlow` из ViewModel.
- **ViewModel** не знает о деталях Room/DAO — обращается только через `FitnessRepository`.
- **Repository** инкапсулирует все сущности, DAO и запросы.

## Слои и компоненты

### 1. Application-слой (инициализация)
| Файл | Назначение |
|------|------------|
| `FitnessApp.kt` | `Application`. Обёртка DI вручную: лениво создаёт `AppDatabase`, `FitnessRepository`, `SettingsStorage`. Доступны как `fitnessApp.repository` / `settings`. |
| `MainActivity.kt` | `ComponentActivity`. `setContent { FitnessTheme { FitnessRoot() } }` + навигация. |
| `AppViewModelProvider.kt` | Фабрика ViewModel. Через `initials` расписывает создание каждого ViewModel, извлекая аргументы (`workoutId`, `restSeconds`) из `SavedStateHandle`. |

### 2. UI-слой (`ru.besuglovs.fitness.ui`)
- `screens/` — экраны-`@Composable`: `HomeScreen`, `WorkoutScreen`, `CircuitScreen`,
  `WorkoutDetailScreen`, `HistoryScreen`, `ProgressScreen`, `SettingsScreen`,
  `ExerciseLibraryScreen`.
- `components/` — переиспользуемые Compose-компоненты (`LineChart.kt` — самодельный график).
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

### 3. Data-слой (`ru.besuglovs.fitness.data`)
**Сущности (таблицы):**
- `Exercise` — упражнение (id, name, muscleGroup, category, createdAt).
- `Workout` — тренировка (id, startTime, endTime?, notes).
- `WorkoutExercise` — упражнение в рамках тренировки (id, workoutId FK→CASCADE, exerciseId, orderIndex).
- `SetEntry` — подход (id, workoutExerciseId FK→CASCADE, setNumber, weightKg?, reps?, restSeconds?, doneAt).

Связи:
`Workout 1—N WorkoutExercise 1—N SetEntry`, причём `SetEntry` ссылается на библиотеку упражнений
через `WorkoutExercise.exerciseId → Exercise`.

**DAO:**
- `ExerciseDao` — CRUD упражнений (Flow + suspend «once»-варианты).
- `WorkoutDao` — создание/завершение/удаление тренировок; транзакционный
  `saveWorkoutWithSets()` для атомарного сохранения тренировки с упражнениями и подходами.
- `StatsDao` — агрегирующие запросы: прогрессия упражнения (макс. вес/повторения/объём),
  общий объём, количество тренировок/упражнений, сводки для истории.

**`Relations.kt`** — POJO-представления для Room-отношений:
`WorkoutWithDetails`, `WorkoutExerciseWithExercise`, `ProgressPoint`, `WorkoutSummary` и др.

**`AppDatabase`** — `@Database(version = 5)`, ручной синглтон через `synchronized`,
миграции `2→3` (пересоздание таблицы `set_entries` с индексом), `3→4`
(колонка `workouts.isCircuit`) и `4→5` (колонка `set_entries.durationSeconds`),
`fallbackToDestructiveMigration`.

**`FitnessRepository`** — единственная точка работы с данными: оборачивает все DAO,
предоставляет Flow и suspend-методы для упражнений, тренировок, статистики и экспорта.

### 4. DI / Прочие
- `SettingsStorage.kt` — `SharedPreferences` (`fitness_settings`), хранит `defaultRestSeconds` (по умолч. 90).
- `util/` — `Formats.kt` (форматирование дат/времени/веса, 1ПМ по формуле Эпли),
  `ExportUtils.kt` (экспорт всего в JSON в Downloads через MediaStore).

## ViewModel («тонкие», держат UI-состояние в MutableStateFlow)

| ViewModel | Источники данных | Отвечает за |
|-----------|------------------|-------------|
| `HomeViewModel` | repository.workouts/count + settings | недавние тренировки, счётчики, незавершённая тренировка, создание тренировки |
| `WorkoutViewModel` | repository.exercises | обычная тренировка: выбор упражнений, весов/повторов, таймеры подхода и отдыха |
| `CircuitViewModel` | repository.exercises | круговая тренировка: фазы `SETUP→EXERCISE→REP_ENTRY`, кольца, таймеры |
| `HistoryViewModel` | repository.summaries | список завершённых тренировок, удаление |
| `WorkoutDetailViewModel` | repository.workoutDetails | детали одной тренировки |
| `ProgressViewModel` | repository.exercises/progression/maxWeight | графики прогресса по выбранному упражнению |
| `SettingsViewModel` | settings + repository (экспорт) | настройки отдыха, экспорт в JSON |
| `ExerciseLibraryViewModel` | repository.exercises | CRUD упражнений |

State-реактивность строится на `MutableStateFlow` + `stateIn(WhileSubscribed(5000))`
и `flatMapLatest`/`combine` (ProgressViewModel). Показания таймеров реализованы через
`Job` + `delay(1000)` в `viewModelScope`.

## Потоки данных (пример обычной тренировки)

1. `HomeViewModel.startWorkout()` → `repository.createWorkout()` → id новой незавершённой тренировки.
2. Навигация на `workout/{id}` → `WorkoutViewModel` создаёт через фабрику.
3. `WorkoutScreen` вызывает действия (выбрать упражнение → вес → старт подхода → повторы).
4. `finishWorkout()` → собирает `List<ExerciseWithSets>` → `repository.saveWorkoutWithSets(...)`.
5. `WorkoutDao.saveWorkoutWithSets` в одной транзакции завершает тренировку и пишет все связи/подходы.
6. `HistoryScreen` обновляется автоматически из `summaries (Flow)`.

## Сборка

- Gradle Kotlin DSL + KSP для Room-компилятора.
- Плагины: AGP 8.9.1, Kotlin 2.1.0, Compose Compiler, KSP.
- Важные зависимости: Compose BOM 2024.12.01, Navigation Compose 2.8.5, Room 2.6.1,
  lifecycle-viewmodel/runtime-compose 2.8.7.
- `minSdk 26`, `compileSdk 36`, `targetSdk 35`, JVM target 17.
- Release: `minifyEnabled = true` + ProGuard.

Сборка: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/`.

## Ключевые решения
- Ручная передача зависимостей через `FitnessApp` (без DI-фреймворка / Hilt).
- Room-отношения + транзакционный DAO для целостности вложенных данных.
- Два режима тренировки (обычный и круговой) с общей моделью сохранения.
- Реактивность на Kotlin Flow/StateFlow без RxJava.