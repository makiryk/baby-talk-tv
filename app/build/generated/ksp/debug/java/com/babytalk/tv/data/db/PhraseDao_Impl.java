package com.babytalk.tv.data.db;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class PhraseDao_Impl implements PhraseDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<CachedPhraseEntity> __insertionAdapterOfCachedPhraseEntity;

  private final EntityInsertionAdapter<BatchSyncJobEntity> __insertionAdapterOfBatchSyncJobEntity;

  private final EntityDeletionOrUpdateAdapter<BatchSyncJobEntity> __updateAdapterOfBatchSyncJobEntity;

  private final SharedSQLiteStatement __preparedStmtOfIncrementUsage;

  public PhraseDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfCachedPhraseEntity = new EntityInsertionAdapter<CachedPhraseEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR IGNORE INTO `cached_phrases` (`id`,`textHash`,`category`,`tone`,`timeOfDay`,`text`,`audioFilePath`,`usedCount`,`lastUsedAt`,`createdAt`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final CachedPhraseEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getTextHash());
        statement.bindString(3, entity.getCategory());
        statement.bindString(4, entity.getTone());
        statement.bindString(5, entity.getTimeOfDay());
        statement.bindString(6, entity.getText());
        if (entity.getAudioFilePath() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getAudioFilePath());
        }
        statement.bindLong(8, entity.getUsedCount());
        statement.bindLong(9, entity.getLastUsedAt());
        statement.bindLong(10, entity.getCreatedAt());
      }
    };
    this.__insertionAdapterOfBatchSyncJobEntity = new EntityInsertionAdapter<BatchSyncJobEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `batch_sync_jobs` (`batchId`,`status`,`createdAt`,`resultsUrl`,`errorMessage`) VALUES (?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final BatchSyncJobEntity entity) {
        statement.bindString(1, entity.getBatchId());
        statement.bindString(2, entity.getStatus());
        statement.bindLong(3, entity.getCreatedAt());
        if (entity.getResultsUrl() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getResultsUrl());
        }
        if (entity.getErrorMessage() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getErrorMessage());
        }
      }
    };
    this.__updateAdapterOfBatchSyncJobEntity = new EntityDeletionOrUpdateAdapter<BatchSyncJobEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `batch_sync_jobs` SET `batchId` = ?,`status` = ?,`createdAt` = ?,`resultsUrl` = ?,`errorMessage` = ? WHERE `batchId` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final BatchSyncJobEntity entity) {
        statement.bindString(1, entity.getBatchId());
        statement.bindString(2, entity.getStatus());
        statement.bindLong(3, entity.getCreatedAt());
        if (entity.getResultsUrl() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getResultsUrl());
        }
        if (entity.getErrorMessage() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getErrorMessage());
        }
        statement.bindString(6, entity.getBatchId());
      }
    };
    this.__preparedStmtOfIncrementUsage = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE cached_phrases SET usedCount = usedCount + 1, lastUsedAt = ? WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insertPhrases(final List<CachedPhraseEntity> phrases,
      final Continuation<? super List<Long>> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<List<Long>>() {
      @Override
      @NonNull
      public List<Long> call() throws Exception {
        __db.beginTransaction();
        try {
          final List<Long> _result = __insertionAdapterOfCachedPhraseEntity.insertAndReturnIdsList(phrases);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertJob(final BatchSyncJobEntity job,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfBatchSyncJobEntity.insert(job);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateJob(final BatchSyncJobEntity job,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfBatchSyncJobEntity.handle(job);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object incrementUsage(final long id, final long timestamp,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfIncrementUsage.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, timestamp);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfIncrementUsage.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object getCount(final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM cached_phrases";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getExactPhrase(final String category, final String tone, final String timeOfDay,
      final Continuation<? super CachedPhraseEntity> $completion) {
    final String _sql = "\n"
            + "        SELECT * FROM cached_phrases\n"
            + "        WHERE category = ? \n"
            + "          AND tone = ? \n"
            + "          AND (timeOfDay = ? OR timeOfDay = 'ANY')\n"
            + "        ORDER BY usedCount ASC, lastUsedAt ASC, RANDOM()\n"
            + "        LIMIT 1\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 3);
    int _argIndex = 1;
    _statement.bindString(_argIndex, category);
    _argIndex = 2;
    _statement.bindString(_argIndex, tone);
    _argIndex = 3;
    _statement.bindString(_argIndex, timeOfDay);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<CachedPhraseEntity>() {
      @Override
      @Nullable
      public CachedPhraseEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTextHash = CursorUtil.getColumnIndexOrThrow(_cursor, "textHash");
          final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
          final int _cursorIndexOfTone = CursorUtil.getColumnIndexOrThrow(_cursor, "tone");
          final int _cursorIndexOfTimeOfDay = CursorUtil.getColumnIndexOrThrow(_cursor, "timeOfDay");
          final int _cursorIndexOfText = CursorUtil.getColumnIndexOrThrow(_cursor, "text");
          final int _cursorIndexOfAudioFilePath = CursorUtil.getColumnIndexOrThrow(_cursor, "audioFilePath");
          final int _cursorIndexOfUsedCount = CursorUtil.getColumnIndexOrThrow(_cursor, "usedCount");
          final int _cursorIndexOfLastUsedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "lastUsedAt");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final CachedPhraseEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTextHash;
            _tmpTextHash = _cursor.getString(_cursorIndexOfTextHash);
            final String _tmpCategory;
            _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
            final String _tmpTone;
            _tmpTone = _cursor.getString(_cursorIndexOfTone);
            final String _tmpTimeOfDay;
            _tmpTimeOfDay = _cursor.getString(_cursorIndexOfTimeOfDay);
            final String _tmpText;
            _tmpText = _cursor.getString(_cursorIndexOfText);
            final String _tmpAudioFilePath;
            if (_cursor.isNull(_cursorIndexOfAudioFilePath)) {
              _tmpAudioFilePath = null;
            } else {
              _tmpAudioFilePath = _cursor.getString(_cursorIndexOfAudioFilePath);
            }
            final int _tmpUsedCount;
            _tmpUsedCount = _cursor.getInt(_cursorIndexOfUsedCount);
            final long _tmpLastUsedAt;
            _tmpLastUsedAt = _cursor.getLong(_cursorIndexOfLastUsedAt);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _result = new CachedPhraseEntity(_tmpId,_tmpTextHash,_tmpCategory,_tmpTone,_tmpTimeOfDay,_tmpText,_tmpAudioFilePath,_tmpUsedCount,_tmpLastUsedAt,_tmpCreatedAt);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getPhraseRelaxTone(final String category, final String timeOfDay,
      final Continuation<? super CachedPhraseEntity> $completion) {
    final String _sql = "\n"
            + "        SELECT * FROM cached_phrases\n"
            + "        WHERE category = ? \n"
            + "          AND (timeOfDay = ? OR timeOfDay = 'ANY')\n"
            + "        ORDER BY usedCount ASC, lastUsedAt ASC, RANDOM()\n"
            + "        LIMIT 1\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindString(_argIndex, category);
    _argIndex = 2;
    _statement.bindString(_argIndex, timeOfDay);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<CachedPhraseEntity>() {
      @Override
      @Nullable
      public CachedPhraseEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTextHash = CursorUtil.getColumnIndexOrThrow(_cursor, "textHash");
          final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
          final int _cursorIndexOfTone = CursorUtil.getColumnIndexOrThrow(_cursor, "tone");
          final int _cursorIndexOfTimeOfDay = CursorUtil.getColumnIndexOrThrow(_cursor, "timeOfDay");
          final int _cursorIndexOfText = CursorUtil.getColumnIndexOrThrow(_cursor, "text");
          final int _cursorIndexOfAudioFilePath = CursorUtil.getColumnIndexOrThrow(_cursor, "audioFilePath");
          final int _cursorIndexOfUsedCount = CursorUtil.getColumnIndexOrThrow(_cursor, "usedCount");
          final int _cursorIndexOfLastUsedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "lastUsedAt");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final CachedPhraseEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTextHash;
            _tmpTextHash = _cursor.getString(_cursorIndexOfTextHash);
            final String _tmpCategory;
            _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
            final String _tmpTone;
            _tmpTone = _cursor.getString(_cursorIndexOfTone);
            final String _tmpTimeOfDay;
            _tmpTimeOfDay = _cursor.getString(_cursorIndexOfTimeOfDay);
            final String _tmpText;
            _tmpText = _cursor.getString(_cursorIndexOfText);
            final String _tmpAudioFilePath;
            if (_cursor.isNull(_cursorIndexOfAudioFilePath)) {
              _tmpAudioFilePath = null;
            } else {
              _tmpAudioFilePath = _cursor.getString(_cursorIndexOfAudioFilePath);
            }
            final int _tmpUsedCount;
            _tmpUsedCount = _cursor.getInt(_cursorIndexOfUsedCount);
            final long _tmpLastUsedAt;
            _tmpLastUsedAt = _cursor.getLong(_cursorIndexOfLastUsedAt);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _result = new CachedPhraseEntity(_tmpId,_tmpTextHash,_tmpCategory,_tmpTone,_tmpTimeOfDay,_tmpText,_tmpAudioFilePath,_tmpUsedCount,_tmpLastUsedAt,_tmpCreatedAt);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getPhraseAnyTime(final String category,
      final Continuation<? super CachedPhraseEntity> $completion) {
    final String _sql = "\n"
            + "        SELECT * FROM cached_phrases\n"
            + "        WHERE category = ?\n"
            + "        ORDER BY usedCount ASC, lastUsedAt ASC, RANDOM()\n"
            + "        LIMIT 1\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, category);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<CachedPhraseEntity>() {
      @Override
      @Nullable
      public CachedPhraseEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTextHash = CursorUtil.getColumnIndexOrThrow(_cursor, "textHash");
          final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
          final int _cursorIndexOfTone = CursorUtil.getColumnIndexOrThrow(_cursor, "tone");
          final int _cursorIndexOfTimeOfDay = CursorUtil.getColumnIndexOrThrow(_cursor, "timeOfDay");
          final int _cursorIndexOfText = CursorUtil.getColumnIndexOrThrow(_cursor, "text");
          final int _cursorIndexOfAudioFilePath = CursorUtil.getColumnIndexOrThrow(_cursor, "audioFilePath");
          final int _cursorIndexOfUsedCount = CursorUtil.getColumnIndexOrThrow(_cursor, "usedCount");
          final int _cursorIndexOfLastUsedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "lastUsedAt");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final CachedPhraseEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTextHash;
            _tmpTextHash = _cursor.getString(_cursorIndexOfTextHash);
            final String _tmpCategory;
            _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
            final String _tmpTone;
            _tmpTone = _cursor.getString(_cursorIndexOfTone);
            final String _tmpTimeOfDay;
            _tmpTimeOfDay = _cursor.getString(_cursorIndexOfTimeOfDay);
            final String _tmpText;
            _tmpText = _cursor.getString(_cursorIndexOfText);
            final String _tmpAudioFilePath;
            if (_cursor.isNull(_cursorIndexOfAudioFilePath)) {
              _tmpAudioFilePath = null;
            } else {
              _tmpAudioFilePath = _cursor.getString(_cursorIndexOfAudioFilePath);
            }
            final int _tmpUsedCount;
            _tmpUsedCount = _cursor.getInt(_cursorIndexOfUsedCount);
            final long _tmpLastUsedAt;
            _tmpLastUsedAt = _cursor.getLong(_cursorIndexOfLastUsedAt);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _result = new CachedPhraseEntity(_tmpId,_tmpTextHash,_tmpCategory,_tmpTone,_tmpTimeOfDay,_tmpText,_tmpAudioFilePath,_tmpUsedCount,_tmpLastUsedAt,_tmpCreatedAt);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getActiveJob(final Continuation<? super BatchSyncJobEntity> $completion) {
    final String _sql = "SELECT * FROM batch_sync_jobs WHERE status IN ('SUBMITTED', 'IN_PROGRESS') ORDER BY createdAt DESC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<BatchSyncJobEntity>() {
      @Override
      @Nullable
      public BatchSyncJobEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfBatchId = CursorUtil.getColumnIndexOrThrow(_cursor, "batchId");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfResultsUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "resultsUrl");
          final int _cursorIndexOfErrorMessage = CursorUtil.getColumnIndexOrThrow(_cursor, "errorMessage");
          final BatchSyncJobEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpBatchId;
            _tmpBatchId = _cursor.getString(_cursorIndexOfBatchId);
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final String _tmpResultsUrl;
            if (_cursor.isNull(_cursorIndexOfResultsUrl)) {
              _tmpResultsUrl = null;
            } else {
              _tmpResultsUrl = _cursor.getString(_cursorIndexOfResultsUrl);
            }
            final String _tmpErrorMessage;
            if (_cursor.isNull(_cursorIndexOfErrorMessage)) {
              _tmpErrorMessage = null;
            } else {
              _tmpErrorMessage = _cursor.getString(_cursorIndexOfErrorMessage);
            }
            _result = new BatchSyncJobEntity(_tmpBatchId,_tmpStatus,_tmpCreatedAt,_tmpResultsUrl,_tmpErrorMessage);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
