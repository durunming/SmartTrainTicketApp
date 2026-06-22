package com.example.ticket

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.Transaction
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun AuthScreen(onLoginSuccess: (User) -> Unit) {

    val db = FirebaseDatabase.getInstance().reference.child("users")

    var isLogin by remember { mutableStateOf(true) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }

    var passwordVisible by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(if (isLogin) "登录" else "注册", fontSize = 22.sp)

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("用户名") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { passwordVisible = !passwordVisible }) {
                            Text(if (passwordVisible) "隐藏" else "显示", fontSize = 12.sp)
                        }
                    }
                )

                Spacer(Modifier.height(8.dp))

                Text(msg, color = Color.Red, fontSize = 12.sp)

                Spacer(Modifier.height(16.dp))

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (username.isBlank() || password.isBlank()) {
                            msg = "用户名和密码不能为空"
                            return@Button
                        }

                        val ref = db.child(username)

                        if (isLogin) {
                            // 登录逻辑：使用协程 + await()
                            scope.launch {
                                try {
                                    val snap = ref.get().await()
                                    val pwd = snap.child("password").value?.toString()
                                    if (pwd == password) {
                                        onLoginSuccess(User(username, password))
                                    } else {
                                        msg = "用户名或密码错误"
                                    }
                                } catch (e: Exception) {
                                    msg = "网络错误，请重试"
                                }
                            }
                        } else {
                            // 注册逻辑：使用事务确保原子性，防止并发重名
                            ref.runTransaction(object : com.google.firebase.database.Transaction.Handler {
                                override fun doTransaction(currentData: com.google.firebase.database.MutableData): com.google.firebase.database.Transaction.Result {
                                    if (currentData.value != null) {
                                        return com.google.firebase.database.Transaction.abort() // 用户名已存在
                                    }
                                    currentData.value = mapOf(
                                        "username" to username,
                                        "password" to password
                                    )
                                    return com.google.firebase.database.Transaction.success(currentData)
                                }
                                override fun onComplete(
                                    error: com.google.firebase.database.DatabaseError?,
                                    committed: Boolean,
                                    snapshot: com.google.firebase.database.DataSnapshot?
                                ) {
                                    when {
                                        committed -> {
                                            msg = "注册成功"
                                            isLogin = true
                                            username = ""
                                            password = ""
                                        }
                                        error != null -> msg = "网络错误，请重试"
                                        else -> msg = "用户名已存在，请更换用户名"
                                    }
                                }
                            })
                        }
                    }
                ) {
                    Text(if (isLogin) "登录" else "注册", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }

                TextButton(
                    onClick = {
                        isLogin = !isLogin
                        msg = ""
                        username = ""
                        password = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(if (isLogin) "注册账号" else "返回登录")
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
