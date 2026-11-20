package com.skill2career.adapter.persistence

import com.skill2career.domain.profile.WorkExperience
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

private val json = jacksonObjectMapper()

/** A list of strings as a json array, in a text column. */
@Converter
class StringListConverter : AttributeConverter<List<String>, String> {
    override fun convertToDatabaseColumn(attribute: List<String>): String = json.writeValueAsString(attribute)

    override fun convertToEntityAttribute(dbData: String): List<String> = json.readValue(dbData)
}

@Converter
class WorkHistoryConverter : AttributeConverter<List<WorkExperience>, String> {
    override fun convertToDatabaseColumn(attribute: List<WorkExperience>): String = json.writeValueAsString(attribute)

    override fun convertToEntityAttribute(dbData: String): List<WorkExperience> = json.readValue(dbData)
}
