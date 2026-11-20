package com.skill2career.app.role

import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.context.annotation.Conditional
import org.springframework.core.type.AnnotatedTypeMetadata

/** Loads the bean only in a process that runs [value]. A misspelt `app.roles` fails the start. */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Conditional(OnRoleCondition::class)
annotation class ConditionalOnRole(
    val value: AppRole,
)

class OnRoleCondition : Condition {
    override fun matches(
        context: ConditionContext,
        metadata: AnnotatedTypeMetadata,
    ): Boolean {
        val role = metadata.getAnnotationAttributes(ConditionalOnRole::class.java.name)!!["value"] as AppRole
        val roles = AppRoles.parse(context.environment.getRequiredProperty(AppRoles.PROPERTY))
        return roles.runs(role)
    }
}
