package com.example.myapplication

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.example.myapplication.databinding.ActivityInfoBinding

class InfoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInfoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Anti-screenshot protection
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        
        binding = ActivityInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "App Information"
        
        // Set up GitHub link
        binding.githubLinkTextView.setOnClickListener {
            val githubUrl = "https://github.com/navarro165/encrypted-files/blob/main/README.md"
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, githubUrl.toUri())
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                android.widget.Toast.makeText(this, "No browser found", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        
        // Set up funding link
        binding.fundingLinkTextView.setOnClickListener {
            val fundingUrl = "https://buymeacoffee.com/navarro165"
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, fundingUrl.toUri())
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                android.widget.Toast.makeText(this, "No browser found", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        @Suppress("DEPRECATION")
        onBackPressed()
        return true
    }
}
