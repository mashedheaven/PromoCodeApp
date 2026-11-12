package com.promocodeapp.data.local

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun dateToEpochDay(date: LocalDate?): Long? = date?.toEpochDay()

    @TypeConverter
    fun epochDayToDate(day: Long?): LocalDate? = day?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun idsToJson(ids: List<String>): String = Json.encodeToString(idListSerializer, ids)

    @TypeConverter
    fun jsonToIds(json: String): List<String> =
        runCatching { Json.decodeFromString(idListSerializer, json) }.getOrDefault(emptyList())

    private companion object {
        val idListSerializer = ListSerializer(String.serializer())
    }
}
