package com.example

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import com.example.databinding.StyledLoginBinding

class LoginActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.styled_login)
        val email = findViewById<EditText>(R.id.email)
        val action = findViewById<Button>(R.id.login_button)
        val binding = StyledLoginBinding.bind(findViewById(android.R.id.content))
        action.setOnClickListener { submit(email.text.toString()) }
        binding.title.text = getString(R.string.login_title)
    }

    private fun submit(email: String) = Unit
}
