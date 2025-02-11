package com.example.agentapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.agentapp.databinding.ActivityProgressBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ProgressActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProgressBinding
    private lateinit var progressAdapter: ProgressAdapter
    // This list now holds ScreenshotEntryResponse objects.
    private val screenshotList = mutableListOf<ScreenshotEntryResponse>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProgressBinding.inflate(layoutInflater)
        setContentView(binding.root)

        progressAdapter = ProgressAdapter(screenshotList)
        binding.progressRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.progressRecyclerView.adapter = progressAdapter

        fetchProgress()
    }

    private fun fetchProgress() {
        RetrofitClient.api.getHistory().enqueue(object : Callback<HistoryResponse> {
            override fun onResponse(call: Call<HistoryResponse>, response: Response<HistoryResponse>) {
                if (response.isSuccessful) {
                    response.body()?.let { history ->
                        screenshotList.clear()
                        // Now images is a list of ScreenshotEntryResponse.
                        screenshotList.addAll(history.images)
                        progressAdapter.notifyDataSetChanged()
                    }
                }
            }
            override fun onFailure(call: Call<HistoryResponse>, t: Throwable) {
                // Optionally handle error here.
            }
        })
    }
}
