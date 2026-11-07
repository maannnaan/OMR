package com.omrscanner.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExamDao {
    @Query("SELECT * FROM exams ORDER BY createdAt DESC") fun exams(): Flow<List<ExamEntity>>
    @Query("SELECT * FROM students ORDER BY roll") fun students(): Flow<List<StudentEntity>>
    @Query("SELECT * FROM answer_keys ORDER BY setCode") fun keys(): Flow<List<AnswerKeyEntity>>
    @Query("SELECT * FROM sheets ORDER BY scannedAt") fun sheets(): Flow<List<SheetEntity>>

    @Query("SELECT COUNT(*) FROM exams") suspend fun examCount(): Int
    @Query("SELECT * FROM exams WHERE id = :id") suspend fun exam(id: Long): ExamEntity?
    @Insert suspend fun insertExam(e: ExamEntity): Long
    @Update suspend fun updateExam(e: ExamEntity)
    @Query("DELETE FROM exams WHERE id = :id") suspend fun deleteExam(id: Long)

    @Insert suspend fun insertStudents(s: List<StudentEntity>)
    @Query("DELETE FROM students WHERE examId = :examId") suspend fun clearStudents(examId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertKeys(k: List<AnswerKeyEntity>)
    @Query("SELECT * FROM answer_keys WHERE examId = :examId") suspend fun keysFor(examId: Long): List<AnswerKeyEntity>
    @Query("DELETE FROM answer_keys WHERE examId = :examId") suspend fun clearKeys(examId: Long)
    @Query("DELETE FROM answer_keys WHERE examId = :examId AND setCode = :set") suspend fun deleteKey(examId: Long, set: String)

    @Insert suspend fun insertSheet(s: SheetEntity): Long
    @Update suspend fun updateSheet(s: SheetEntity)
    @Query("SELECT * FROM sheets WHERE examId = :examId") suspend fun sheetsFor(examId: Long): List<SheetEntity>
    @Query("DELETE FROM sheets WHERE id = :id") suspend fun deleteSheet(id: Long)
    @Query("DELETE FROM sheets WHERE examId = :examId") suspend fun clearSheets(examId: Long)
}

@Dao
interface DocDao {
    @Query("SELECT * FROM documents ORDER BY createdAt DESC") fun documents(): Flow<List<DocumentEntity>>
    @Query("SELECT * FROM doc_pages ORDER BY docId, position") fun pages(): Flow<List<DocPageEntity>>

    @Insert suspend fun insertDoc(d: DocumentEntity): Long
    @Update suspend fun updateDoc(d: DocumentEntity)
    @Query("DELETE FROM documents WHERE id = :id") suspend fun deleteDoc(id: Long)

    @Query("SELECT * FROM doc_pages WHERE docId = :docId ORDER BY position") suspend fun pagesFor(docId: Long): List<DocPageEntity>
    @Insert suspend fun insertPages(p: List<DocPageEntity>)
    @Update suspend fun updatePages(p: List<DocPageEntity>)
    @Query("DELETE FROM doc_pages WHERE id = :id") suspend fun deletePage(id: Long)
    @Query("DELETE FROM doc_pages WHERE docId = :docId") suspend fun deletePagesOf(docId: Long)
}
