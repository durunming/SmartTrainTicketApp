package com.example.ticket

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProfileScreen(user: User, onLogout: () -> Unit) {
    var showChangePwd by remember { mutableStateOf(false) }
    var oldPwd by remember { mutableStateOf("") }
    var newPwd by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }

    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("空闲") }

    // 用户订单统计
    var totalOrders by remember { mutableStateOf(0) }
    var pendingOrders by remember { mutableStateOf(0) }
    var completedOrders by remember { mutableStateOf(0) }

    var showLogoutDialog by remember { mutableStateOf(false) }

    val db = FirebaseDatabase.getInstance().reference

    // 加载用户订单统计
    DisposableEffect(Unit) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var total = 0
                var pending = 0
                var completed = 0
                snapshot.children.forEach { orderNode ->
                    val order = orderNode.getValue(Order::class.java)
                    if (order != null) {
                        total++
                        if (order.status == "待支付") pending++
                        if (order.status == "已支付") completed++
                    }
                }
                totalOrders = total
                pendingOrders = pending
                completedOrders = completed
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        val ref = db.child("orders").child(user.username)
        ref.addValueEventListener(listener)
        onDispose {
            ref.removeEventListener(listener)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // 标题
        Text("个人中心", fontSize = 20.sp, fontWeight = FontWeight.Bold)

        Spacer(Modifier.height(16.dp))

        // 用户信息卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                // 头像区域
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.size(60.dp),
                        shape = CircleShape,
                        color = Color(0xFFE3F2FD)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                user.username.take(1).uppercase(),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1565C0)
                            )
                        }
                    }
                }

                // 用户名
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("用户名", fontSize = 13.sp, color = Color.Gray)
                    Text(user.username, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }

                Spacer(Modifier.height(8.dp))

                // 系统状态
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("系统状态", fontSize = 13.sp, color = Color.Gray)
                    Surface(
                        color = when {
                            status.contains("完成") -> Color(0xFFE8F5E9)
                            status.contains("中") -> Color(0xFFFFF3E0)
                            else -> Color(0xFFF5F5F5)
                        },
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            status,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            color = when {
                                status.contains("完成") -> Color(0xFF4CAF50)
                                status.contains("中") -> Color(0xFFFF9800)
                                else -> Color.Gray
                            }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Divider()
                Spacer(Modifier.height(12.dp))

                // 订单统计
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("总订单", fontSize = 11.sp, color = Color.Gray)
                        Text("$totalOrders", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("待支付", fontSize = 11.sp, color = Color.Gray)
                        Text("$pendingOrders", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9800))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("已完成", fontSize = 11.sp, color = Color.Gray)
                        Text("$completedOrders", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 系统操作卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Text("系统操作", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                Spacer(Modifier.height(12.dp))

                // 更新数据按钮
                Button(
                    onClick = {
                        loading = true
                        status = "更新铁路数据中..."

                        Thread {
                            try {
                                val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                                val data = RealRailGenerator.generateBatchForDays(date, 3)
                                TrainFirebaseService.uploadAll(data)
                                Handler(Looper.getMainLooper()).post {
                                    status = "更新完成：${data.size}条"
                                    loading = false
                                }
                            } catch (e: Exception) {
                                Handler(Looper.getMainLooper()).post {
                                    status = "更新失败：${e.message}"
                                    loading = false
                                }
                            }
                        }.start()
                    },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(if (loading) "更新中..." else "更新全国铁路数据", fontSize = 14.sp, color = Color.White)
                }

                Spacer(Modifier.height(10.dp))

                // 修改密码按钮
                OutlinedButton(
                    onClick = { showChangePwd = !showChangePwd },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF2196F3)
                    )
                ) {
                    Text("修改密码", fontSize = 14.sp)
                }

                Spacer(Modifier.height(10.dp))

                // 退出登录按钮
                Button(
                    onClick = { showLogoutDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("退出登录", fontSize = 14.sp, color = Color.White)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 说明卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            ) {
                Text("说明", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF757575))
                Spacer(Modifier.height(6.dp))
                Text(
                    "• 点击可更新全量车次数据\n• 该操作会覆盖现有数据库内容",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    lineHeight = 16.sp
                )
            }
        }

        // 版本信息
        Spacer(Modifier.height(16.dp))
        Text(
            "版本 1.0.0",
            fontSize = 10.sp,
            color = Color.Gray,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }

    // 修改密码对话框
    if (showChangePwd) {
        AlertDialog(
            onDismissRequest = {
                showChangePwd = false
                oldPwd = ""
                newPwd = ""
                msg = ""
            },
            title = { Text("修改密码", fontSize = 16.sp) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = oldPwd,
                        onValueChange = { oldPwd = it },
                        label = { Text("原密码") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newPwd,
                        onValueChange = { newPwd = it },
                        label = { Text("新密码") },
                        singleLine = true
                    )
                    if (msg.isNotBlank()) {
                        Text(msg, color = Color.Red, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val db = FirebaseDatabase.getInstance()
                            .reference.child("users").child(user.username)

                        db.get().addOnSuccessListener { snap ->
                            val currentPwd = snap.child("password").value?.toString()
                            if (currentPwd != oldPwd) {
                                msg = "原密码错误"
                                return@addOnSuccessListener
                            }
                            if (newPwd.isBlank()) {
                                msg = "请输入新密码"
                                return@addOnSuccessListener
                            }
                            db.child("password").setValue(newPwd)
                                .addOnSuccessListener {
                                    msg = "修改成功"
                                    oldPwd = ""
                                    newPwd = ""
                                    showChangePwd = false
                                }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showChangePwd = false
                    oldPwd = ""
                    newPwd = ""
                    msg = ""
                }) {
                    Text("取消")
                }
            }
        )
    }
    // 退出登录确认对话框
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("确认退出", fontSize = 16.sp) },
            text = { Text("确定要退出登录吗？", fontSize = 14.sp) },
            confirmButton = {
                Button(
                    onClick = onLogout,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350))
                ) {
                    Text("退出", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}