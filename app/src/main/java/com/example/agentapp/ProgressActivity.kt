// ProgressActivity.kt
package com.example.agentapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.example.agentapp.databinding.ActivityProgressBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ProgressActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProgressBinding
    private lateinit var progressAdapter: ProgressAdapter
    private val screenshotList = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProgressBinding.inflate(layoutInflater)
        setContentView(binding.root)

        progressAdapter = ProgressAdapter(screenshotList)
        binding.progressRecyclerView.apply {
            layoutManager = GridLayoutManager(this@ProgressActivity, 2)
            adapter = progressAdapter
        }

        fetchProgress()
    }

    private fun fetchProgress() {
        RetrofitClient.api.getHistory().enqueue(object : Callback<HistoryResponse> {
            override fun onResponse(call: Call<HistoryResponse>, response: Response<HistoryResponse>) {
                if (response.isSuccessful) {
                    response.body()?.let {
                        screenshotList.clear()
                        screenshotList.addAll(it.images)
                        progressAdapter.notifyDataSetChanged()
                    }
                }
            }
            override fun onFailure(call: Call<HistoryResponse>, t: Throwable) {
                // Optionally handle error
            }
        })
    }
}
