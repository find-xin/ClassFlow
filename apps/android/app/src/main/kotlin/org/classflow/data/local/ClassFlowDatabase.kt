package org.classflow.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// MARK: - Room Entities

@Entity(tableName = "semesters")
data class SemesterEntity(
    @PrimaryKey val id: String,
    val name: String,
    val startDate: String,
    val endDate: String,
    val totalWeeks: Int,
    val isCurrent: Boolean,
    val timeZone: String,
    val sectionsJson: String
)

@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey val id: String,
    val semesterId: String,
    val name: String,
    val teacher: String?,
    val color: String,
    val notes: String?
)

@Entity(tableName = "course_schedules")
data class ScheduleEntity(
    @PrimaryKey val id: String,
    val courseId: String,
    val dayOfWeek: Int,
    val startSection: Int,
    val endSection: Int,
    val classroom: String?,
    val weekPattern: String,
    val customWeeksJson: String
)

@Entity(tableName = "schedule_exceptions")
data class ExceptionEntity(
    @PrimaryKey val id: String,
    val scheduleId: String,
    val originalDate: String,
    val type: String,
    val targetDate: String?,
    val targetDayOfWeek: Int?,
    val targetStartSection: Int?,
    val targetEndSection: Int?,
    val targetClassroom: String?,
    val reason: String?
)

@Entity(tableName = "calendar_mappings")
data class CalendarMappingEntity(
    @PrimaryKey val id: String,
    val semesterId: String,
    val courseId: String,
    val scheduleId: String,
    val occurrenceDate: String,
    val startSection: Int,
    val endSection: Int,
    val externalEventId: String,
    val contentHash: String,
    val lastSyncedAt: String
)

// MARK: - DAOs

@Dao
interface SemesterDao {
    @Query("SELECT * FROM semesters ORDER BY startDate DESC")
    fun getAllSemesters(): Flow<List<SemesterEntity>>

    @Query("SELECT * FROM semesters WHERE isCurrent = 1 LIMIT 1")
    suspend fun getCurrentSemester(): SemesterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSemester(semester: SemesterEntity)

    @Delete
    suspend fun deleteSemester(semester: SemesterEntity)
}

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses WHERE semesterId = :semesterId")
    fun getCoursesForSemester(semesterId: String): Flow<List<CourseEntity>>

    @Query("SELECT * FROM course_schedules WHERE courseId = :courseId")
    suspend fun getSchedulesForCourse(courseId: String): List<ScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourse(course: CourseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedules(schedules: List<ScheduleEntity>)

    @Query("DELETE FROM courses WHERE id = :courseId")
    suspend fun deleteCourse(courseId: String)

    @Query("DELETE FROM course_schedules WHERE courseId = :courseId")
    suspend fun deleteSchedulesForCourse(courseId: String)
}

@Dao
interface ExceptionDao {
    @Query("SELECT * FROM schedule_exceptions")
    fun getAllExceptions(): Flow<List<ExceptionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertException(exception: ExceptionEntity)

    @Query("DELETE FROM schedule_exceptions WHERE scheduleId = :scheduleId")
    suspend fun deleteExceptionsForSchedule(scheduleId: String)
}

@Dao
interface CalendarMappingDao {
    @Query("SELECT * FROM calendar_mappings WHERE semesterId = :semesterId")
    suspend fun getMappingsForSemester(semesterId: String): List<CalendarMappingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMappings(mappings: List<CalendarMappingEntity>)

    @Query("DELETE FROM calendar_mappings WHERE id = :id")
    suspend fun deleteMapping(id: String)

    @Query("DELETE FROM calendar_mappings WHERE semesterId = :semesterId")
    suspend fun clearMappingsForSemester(semesterId: String)
}

// MARK: - Database

@Database(
    entities = [
        SemesterEntity::class,
        CourseEntity::class,
        ScheduleEntity::class,
        ExceptionEntity::class,
        CalendarMappingEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class ClassFlowDatabase : RoomDatabase() {
    abstract fun semesterDao(): SemesterDao
    abstract fun courseDao(): CourseDao
    abstract fun exceptionDao(): ExceptionDao
    abstract fun calendarMappingDao(): CalendarMappingDao
}
