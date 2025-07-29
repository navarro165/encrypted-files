package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.databinding.ItemFileBinding
import java.io.File

// Wrapper class to include pending state for proper DiffUtil comparison
data class FileItem(
    val file: File,
    val isPending: Boolean
) {
    val name: String get() = file.name
    val isDirectory: Boolean get() = file.isDirectory
    val path: String get() = file.path
}

class FileAdapter(
    private val mainActivity: MainActivity,
    private val onMultiSelect: (List<File>) -> Unit,
    private val onOpenFile: (File) -> Unit,
    private val onOpenFolder: (File) -> Unit
) : ListAdapter<FileItem, FileAdapter.FileViewHolder>(FileDiffCallback()) {

    private var isMultiSelectMode = false
    private val selectedItems = mutableListOf<File>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val binding = ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val fileItem = getItem(position)
        holder.bind(fileItem)
    }

    fun setMultiSelectMode(enabled: Boolean) {
        isMultiSelectMode = enabled
        if (!enabled) {
            selectedItems.clear()
        }
        notifyDataSetChanged()
    }

    fun getSelectedItems(): List<File> {
        return selectedItems
    }

    fun selectAll() {
        selectedItems.clear()
        selectedItems.addAll(currentList.map { it.file })
        notifyDataSetChanged()
        onMultiSelect(selectedItems)
    }

    inner class FileViewHolder(private val binding: ItemFileBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(fileItem: FileItem) {
            val file = fileItem.file
            val isPending = fileItem.isPending
            
            binding.textView.text = file.name

            if (isPending) {
                // Show pending files with lighter grey and encrypting indicator
                binding.root.alpha = 0.7f
                binding.textView.setTextColor(android.graphics.Color.parseColor("#888888")) // Lighter grey
                binding.textView.text = "${file.name} (encrypting)"
                binding.textView.setTypeface(null, android.graphics.Typeface.ITALIC)
                if (file.isDirectory) {
                    binding.imageView.visibility = View.VISIBLE
                    binding.imageView.setImageResource(R.drawable.ic_folder)
                    binding.imageView.setColorFilter(android.graphics.Color.parseColor("#888888"))
                } else {
                    binding.imageView.visibility = View.GONE
                }
            } else {
                // Normal appearance for completed files
                binding.root.alpha = 1.0f
                binding.textView.text = file.name
                binding.textView.setTypeface(null, android.graphics.Typeface.NORMAL)
                // Use default theme text color (don't override)
                if (file.isDirectory) {
                    binding.imageView.visibility = View.VISIBLE
                    binding.imageView.setImageResource(R.drawable.ic_folder)
                    binding.imageView.clearColorFilter()
                } else {
                    binding.imageView.visibility = View.GONE
                }
            }

            if (isMultiSelectMode) {
                binding.checkbox.visibility = View.VISIBLE
                binding.checkbox.isChecked = selectedItems.contains(file)
            } else {
                binding.checkbox.visibility = View.GONE
            }

            binding.root.setOnClickListener {
                // Don't allow interaction with pending files
                if (isPending) {
                    return@setOnClickListener
                }
                
                if (isMultiSelectMode) {
                    if (selectedItems.contains(file)) {
                        selectedItems.remove(file)
                    } else {
                        selectedItems.add(file)
                    }
                    notifyItemChanged(adapterPosition)
                    onMultiSelect(selectedItems)
                } else {
                    if (file.isDirectory) {
                        onOpenFolder(file)
                    } else {
                        onOpenFile(file)
                    }
                }
            }

            binding.root.setOnLongClickListener {
                // Don't allow interaction with pending files
                if (isPending) {
                    return@setOnLongClickListener false
                }
                
                if (!isMultiSelectMode) {
                    setMultiSelectMode(true)
                    selectedItems.add(file)
                    notifyItemChanged(adapterPosition)
                    onMultiSelect(selectedItems)
                }
                true
            }
        }
    }
}

class FileDiffCallback : DiffUtil.ItemCallback<FileItem>() {
    override fun areItemsTheSame(oldItem: FileItem, newItem: FileItem): Boolean {
        return oldItem.path == newItem.path
    }

    override fun areContentsTheSame(oldItem: FileItem, newItem: FileItem): Boolean {
        // Now we can properly compare the pending state
        return oldItem.isPending == newItem.isPending && oldItem.file == newItem.file
    }
}