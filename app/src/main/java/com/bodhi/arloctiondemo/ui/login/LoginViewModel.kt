package com.bodhi.arloctiondemo.ui.login

import android.util.Log
import android.util.Patterns
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.bodhi.arloctiondemo.R
import com.bodhi.arloctiondemo.data.LoggedInUserView
import com.bodhi.arloctiondemo.data.LoginFormState
import com.bodhi.arloctiondemo.data.LoginResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import java.util.concurrent.Executors

class LoginViewModel : ViewModel() {
    private var mAuth: FirebaseAuth = FirebaseAuth.getInstance()

    private val _loginForm = MutableLiveData<LoginFormState>()
    val loginFormState: LiveData<LoginFormState> = _loginForm

    private val _loginResult = MutableLiveData<LoginResult>()
    val loginResult: LiveData<LoginResult> = _loginResult

    fun login(username: String, password: String) {
        mAuth.createUserWithEmailAndPassword(
            username,
            password
        ).addOnCompleteListener(Executors.newCachedThreadPool()) { task ->
            if (task.isSuccessful) {
                // Sign in success, update UI with the signed-in user's information
                Log.d(TAG, "createUserWithEmail:success")
                val user = mAuth.currentUser
                _loginResult.postValue(
                    LoginResult(
                        LoggedInUserView(user?.displayName ?: "")
                    )
                )
            } else {
                if (task.exception is FirebaseAuthException &&
                    (task.exception as
                            FirebaseAuthException).errorCode == "ERROR_EMAIL_ALREADY_IN_USE"
                ) {
                    signInWithExistingUser(username, password)
                } else
                    _loginResult.postValue(
                        LoginResult(
                            success = null,
                            error = task.exception?.message
                        )
                    )
                // If sign in fails, display a message to the user.
                Log.w(TAG, "createUserWithEmail:failure", task.exception)
            }
        }
    }

    private fun signInWithExistingUser(username: String, password: String) {
        mAuth.signInWithEmailAndPassword(
            username,
            password
        ).addOnSuccessListener {
            Log.d(TAG, "SignInWithEmail:success")
            val user = it.user
            _loginResult.postValue(
                LoginResult(
                    LoggedInUserView(user?.displayName ?: "")
                )
            )
        }.addOnFailureListener {
            _loginResult.postValue(
                LoginResult(
                    success = null,
                    error = it.message
                )
            )
        }
    }

    fun loginDataChanged(username: String, password: String) {
        if (!isUserNameValid(username)) {
            _loginForm.value = LoginFormState(usernameError = R.string.invalid_username)
        } else if (!isPasswordValid(password)) {
            _loginForm.value = LoginFormState(passwordError = R.string.invalid_password)
        } else {
            _loginForm.value = LoginFormState(isDataValid = true)
        }
    }

    // A placeholder username validation check
    private fun isUserNameValid(username: String): Boolean {
        return if (username.contains('@')) {
            Patterns.EMAIL_ADDRESS.matcher(username).matches()
        } else {
            username.isNotBlank()
        }
    }

    // A placeholder password validation check
    private fun isPasswordValid(password: String): Boolean {
        return password.length > 5
    }
}