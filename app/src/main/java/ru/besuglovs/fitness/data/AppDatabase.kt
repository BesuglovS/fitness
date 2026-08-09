package ru.besuglovs.fitness.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Exercise::class,
        Workout::class,
        WorkoutExercise::class,
        SetEntry::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun statsDao(): StatsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `set_entries_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`workoutExerciseId` INTEGER NOT NULL, " +
                        "`setNumber` INTEGER NOT NULL, " +
                        "`weightKg` REAL, " +
                        "`reps` INTEGER, " +
                        "`restSeconds` INTEGER, " +
                        "`doneAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`workoutExerciseId`) REFERENCES `workout_exercises`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "INSERT INTO `set_entries_new` (`id`, `workoutExerciseId`, `setNumber`, " +
                        "`weightKg`, `reps`, `restSeconds`, `doneAt`) " +
                        "SELECT `id`, `workoutExerciseId`, `setNumber`, `weightKg`, `reps`, " +
                        "`restSeconds`, `doneAt` FROM `set_entries`"
                )
                db.execSQL("DROP TABLE `set_entries`")
                db.execSQL("ALTER TABLE `set_entries_new` RENAME TO `set_entries`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_set_entries_workoutExerciseId` " +
                        "ON `set_entries`(`workoutExerciseId`)"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `workouts` ADD COLUMN `isCircuit` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `set_entries` ADD COLUMN `durationSeconds` INTEGER"
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `workouts` ADD COLUMN `sessionJson` TEXT")
                db.execSQL("ALTER TABLE `workouts` ADD COLUMN `pausedAt` INTEGER")
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fitness.db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
