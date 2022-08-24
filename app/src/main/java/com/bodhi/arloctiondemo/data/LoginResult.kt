package com.bodhi.arloctiondemo.data

import com.bodhi.arloctiondemo.data.LoggedInUserView

/**
 * Authentication result : success (user details) or error message.
 */
data class LoginResult(
    val success: LoggedInUserView? = null,
    val error: String? = null
)