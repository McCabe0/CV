package com.skill2career.adapter.persistence

import com.skill2career.domain.cv.Answer
import com.skill2career.domain.cv.Cv
import com.skill2career.domain.cv.Question
import com.skill2career.domain.cv.Round
import com.skill2career.domain.profile.Fact
import com.skill2career.domain.profile.WorkExperience
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

// Values the domain derives, eg a review's approval, are written too, but ignored when read back.
private val json =
    JsonMapper
        .builder()
        .addModule(KotlinModule.Builder().build())
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build()

/** A value as json, in a text column. */
abstract class JsonConverter<T : Any>(
    private val type: TypeReference<T>,
) : AttributeConverter<T, String> {
    override fun convertToDatabaseColumn(attribute: T?): String? = attribute?.let(json::writeValueAsString)

    override fun convertToEntityAttribute(dbData: String?): T? = dbData?.let { json.readValue(it, type) }
}

/** A list of strings as a json array. */
@Converter
class StringListConverter : JsonConverter<List<String>>(object : TypeReference<List<String>>() {})

@Converter
class WorkHistoryConverter : JsonConverter<List<WorkExperience>>(object : TypeReference<List<WorkExperience>>() {})

@Converter
class FactsConverter : JsonConverter<List<Fact>>(object : TypeReference<List<Fact>>() {})

@Converter
class QuestionsConverter : JsonConverter<List<Question>>(object : TypeReference<List<Question>>() {})

@Converter
class AnswersConverter : JsonConverter<List<Answer>>(object : TypeReference<List<Answer>>() {})

@Converter
class RoundsConverter : JsonConverter<List<Round>>(object : TypeReference<List<Round>>() {})

@Converter
class CvConverter : JsonConverter<Cv>(object : TypeReference<Cv>() {})
