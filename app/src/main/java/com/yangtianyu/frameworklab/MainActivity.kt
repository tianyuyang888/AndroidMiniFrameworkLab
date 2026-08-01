package com.yangtianyu.frameworklab

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.yangtianyu.frameworklab.databinding.ActivityMainBinding

/**
 * 单 Activity 架构的唯一宿主，只负责承载应用的 Navigation Host。
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}
