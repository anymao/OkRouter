package com.anymore.okrouter.demo.login

import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.anymore.okrouter.annotation.Router
import com.anymore.okrouter.demo.base.Common.host
import com.anymore.okrouter.demo.base.Common.scheme
import com.anymore.okrouter.demo.base.toast

@Router(scheme = scheme, host = host, path = "/login", desc = "登录页面")
class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        val etUsername = findViewById<EditText>(R.id.et_username)
        val etPassword = findViewById<EditText>(R.id.et_password)
        findViewById<View>(R.id.btn_login).setOnClickListener {
            val username = etUsername.text?.toString()
            val password = etPassword.text?.toString()
            if (username.isNullOrBlank()){
                toast("用户名不能为空!")
                return@setOnClickListener
            }
            if (password.isNullOrBlank()){
                toast("密码不能为空!")
                return@setOnClickListener
            }
            val result = LoginManager.doLogin(this,username,password)
            if (result){
                toast("登录成功")
                finish()
                return@setOnClickListener
            }else{
                toast("登录失败")
            }
        }
    }
}