package cc.pscly.onememos.data.mapper

import cc.pscly.onememos.core.database.entity.TodoItemEntity
import cc.pscly.onememos.core.database.entity.TodoListEntity
import cc.pscly.onememos.core.database.entity.TodoOccurrenceEntity
import cc.pscly.onememos.domain.model.TodoItem
import cc.pscly.onememos.domain.model.TodoList
import cc.pscly.onememos.domain.model.TodoOccurrence
import cc.pscly.onememos.domain.todo.TodoTagsTextCodec

fun TodoListEntity.toDomain(): TodoList =
    TodoList(
        id = id,
        name = name,
        color = color,
        sortOrder = sortOrder,
        archived = archived,
        deletedAt = deletedAt,
        clientUpdatedAtMs = clientUpdatedAtMs,
        updatedAt = updatedAt,
    )

fun TodoItemEntity.toDomain(): TodoItem =
    TodoItem(
        id = id,
        listId = listId,
        parentId = parentId,
        title = title,
        note = note,
        status = status,
        priority = priority,
        sortOrder = sortOrder,
        tags = TodoTagsTextCodec.decode(tagsText),
        remindersJson = remindersJson,
        dueAtLocal = dueAtLocal,
        completedAtLocal = completedAtLocal,
        isRecurring = isRecurring,
        rrule = rrule,
        dtstartLocal = dtstartLocal,
        tzid = tzid,
        deletedAt = deletedAt,
        clientUpdatedAtMs = clientUpdatedAtMs,
        updatedAt = updatedAt,
    )

fun TodoOccurrenceEntity.toDomain(): TodoOccurrence =
    TodoOccurrence(
        id = id,
        itemId = itemId,
        tzid = tzid,
        recurrenceIdLocal = recurrenceIdLocal,
        statusOverride = statusOverride,
        titleOverride = titleOverride,
        noteOverride = noteOverride,
        dueAtOverrideLocal = dueAtOverrideLocal,
        completedAtLocal = completedAtLocal,
        deletedAt = deletedAt,
        clientUpdatedAtMs = clientUpdatedAtMs,
        updatedAt = updatedAt,
    )

