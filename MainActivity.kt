package com.rmdaye.ziker

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: ZikViewModel

    private val requestPerms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel = ZikViewModel(applicationContext)
        viewModel.searchForHeadset()
        setContentView()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPerms.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
        } else {
            viewModel = ZikViewModel(applicationContext)
            viewModel.searchForHeadset()
            setContentView()
        }
    }

    private fun setContentView() {
        setContent {
            MaterialTheme {
                Surface {
                    MainScreen(viewModel)
                }
            }
        }
    }
}

