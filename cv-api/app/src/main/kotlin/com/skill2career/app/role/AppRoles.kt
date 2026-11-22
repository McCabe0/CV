package com.skill2career.app.role

/** The roles this process runs, from `app.roles`, eg `api,cv-worker`. */
data class AppRoles(
    val roles: Set<AppRole>,
) {
    fun runs(role: AppRole): Boolean = role in roles

    companion object {
        const val PROPERTY = "app.roles"

        fun parse(value: String): AppRoles {
            val roles =
                value
                    .split(",")
                    .filter { it.isNotBlank() }
                    .map { AppRole.of(it) }
                    .toSet()
            require(roles.isNotEmpty()) { "$PROPERTY names no roles, so this process would do nothing" }
            return AppRoles(roles)
        }
    }
}
